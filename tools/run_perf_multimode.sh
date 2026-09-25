#!/usr/bin/env bash
# One continuous 35-minute Perfetto capture that walks the three steady-state
# camera modes in a SINGLE flight — scenarios 2 (Free), 3 (Tracking) and 4
# (Cockpit) from the matrix in the perf plan, 10 minutes each, with a 2.5-minute
# settle head and tail (35 min total).
#
# Why not three runs of tools/run_perf_scenario.sh: that script rebuilds,
# reinstalls and relaunches per scenario, so each mode would be measured on a
# different flight, from a cold start, over different terrain tiles. Keeping one
# flight and only switching the camera makes the three windows directly
# comparable — which is the point of the split.
#
# Differences from run_perf_scenario.sh's trace config, all forced by the length:
#   * write_into_file — a 35-minute capture cannot sit in a RAM ring buffer;
#     without streaming to disk the early windows get overwritten.
#   * no heapprofd — its allocation-path overhead would land on exactly the
#     per-subsystem CPU numbers the three windows are being compared on. Native
#     heap attribution needs its own short run; RSS/leak slope here comes from
#     linux.process_stats + the dumpsys poll below.
#   * no sched/freq ftrace — a firehose at this duration (GBs). CPU frequency
#     and thermal state are polled via linux.sys_stats instead, which is enough
#     to see throttling across the three windows without the volume.
#   * no gfx/view atrace categories either. Measured at 120MB/min on a 120Hz
#     S23 — those categories are system-wide (SurfaceFlinger, system_server),
#     not scoped by atrace_apps, and would have blown the size cap ~10 minutes
#     before the end, truncating the last window. The `cesium.*` spans this is
#     actually after ride on atrace_apps and are unaffected; what's lost is the
#     Android-side frame pipeline for jank correlation.
#
# Expects the app to be ALREADY RUNNING and in an active flight — this script
# deliberately does not launch or restart it. Build/install first with:
#   ./gradlew :app:assembleDebug -Pcesium.profile=profiling && adb install -r ...
set -euo pipefail

PACKAGE="com.silas270.blocktime"
RECEIVER="$PACKAGE/.engine.live.PerfScenarioReceiver"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${OUT_DIR:-$REPO_ROOT/perf_runs/multimode_$(date +%Y%m%d_%H%M%S)}"

HEAD_S="${HEAD_S:-150}"      # settle before the first mode window
WINDOW_S="${WINDOW_S:-600}"  # per camera mode
TAIL_S="${TAIL_S:-150}"      # after the last window
TOTAL_S=$((HEAD_S + 3 * WINDOW_S + TAIL_S))

TRACE_DEVICE_PATH="/data/misc/perfetto-traces/cesium_multimode.pftrace"
mkdir -p "$OUT_DIR"
MEM_LOG="$OUT_DIR/mem_samples.log"
: > "$MEM_LOG"

log() { echo "[$(date +%H:%M:%S)] $*" | tee -a "$OUT_DIR/run.log"; }

log "Output directory: $OUT_DIR"
log "Total capture: ${TOTAL_S}s (head ${HEAD_S}s, 3x${WINDOW_S}s, tail ${TAIL_S}s)"

# Keep the screen on for the whole run: the engine suspends rendering when the
# activity stops, which would blank the middle of the trace.
adb shell svc power stayon usb || true

log "Starting Perfetto (detached on device, survives an adb hiccup)"
adb shell perfetto --txt -c - --out "$TRACE_DEVICE_PATH" --background <<EOF
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
buffers { size_kb: 8192 fill_policy: RING_BUFFER }
data_sources {
  config {
    name: "linux.ftrace"
    target_buffer: 0
    ftrace_config {
      atrace_apps: "$PACKAGE"
      drain_period_ms: 500
    }
  }
}
data_sources {
  config {
    name: "linux.process_stats"
    target_buffer: 1
    process_stats_config {
      proc_stats_poll_ms: 5000
      scan_all_processes_on_start: true
    }
  }
}
data_sources {
  config {
    name: "linux.sys_stats"
    target_buffer: 1
    sys_stats_config {
      meminfo_period_ms: 5000
      cpufreq_period_ms: 5000
      stat_period_ms: 5000
      stat_counters: STAT_CPU_TIMES
    }
  }
}
write_into_file: true
file_write_period_ms: 2500
max_file_size_bytes: 8589934592
duration_ms: $((TOTAL_S * 1000))
EOF

START_EPOCH=$(date +%s)

# Memory + thermal poll alongside the trace. Same `ts=` / `TOTAL` shape that
# CesiumRS's analyze_perf.py parse_mem_log() expects.
(
  END=$((SECONDS + TOTAL_S))
  while [ "$SECONDS" -lt "$END" ]; do
    echo "ts=$(date +%s.%N)" >> "$MEM_LOG"
    adb shell dumpsys meminfo "$PACKAGE" 2>/dev/null | grep -E "TOTAL |Native Heap" >> "$MEM_LOG" || true
    echo "battery_temp=$(adb shell dumpsys battery 2>/dev/null | grep -m1 '  temperature' | tr -dc '0-9')" >> "$MEM_LOG"
    sleep 5
  done
) &
MEM_PID=$!

fire() { # fire <scenario_id> <label>
  local id="$1" label="$2"
  local t=$(( $(date +%s) - START_EPOCH ))
  log "t+${t}s: scenario $id ($label)"
  adb shell am broadcast -a "$PACKAGE.PERF_SCENARIO" -n "$RECEIVER" --ei scenario_id "$id" \
    | tee -a "$OUT_DIR/run.log" | grep -q "result=0" \
    || log "WARNING: broadcast for scenario $id did not report result=0"
  echo "$t $id $label" >> "$OUT_DIR/scenario_offsets.txt"
}

sleep "$HEAD_S"
fire 2 Free
sleep "$WINDOW_S"
fire 3 Tracking
sleep "$WINDOW_S"
fire 4 Cockpit
sleep "$WINDOW_S"

log "Last window done; letting the tail run out"
REMAIN=$(( TOTAL_S - ($(date +%s) - START_EPOCH) + 10 ))
[ "$REMAIN" -gt 0 ] && sleep "$REMAIN"
wait "$MEM_PID" 2>/dev/null || true

adb shell svc power stayon false || true

log "Pulling trace"
adb pull "$TRACE_DEVICE_PATH" "$OUT_DIR/cesium_multimode.pftrace"
adb shell rm -f "$TRACE_DEVICE_PATH" || true

log "Done. Artifacts in $OUT_DIR"
ls -la "$OUT_DIR"
