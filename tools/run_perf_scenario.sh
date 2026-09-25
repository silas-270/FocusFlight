#!/usr/bin/env bash
# Builds a profiling-instrumented debug APK, installs it on the attached device,
# captures a combined Perfetto trace (ATrace spans from CesiumRS + gfx/sched/mem
# tracks) for one test scenario, pulls the trace and a memory-sample log, then
# runs CesiumRS's analyze_perf.py over them.
#
# See CesiumRS/src/testing/analyze_perf.py's docstring for the full design. Usage:
#
#   tools/run_perf_scenario.sh <scenario_id> [duration_seconds]
#
# scenario_id: 2=Free, 3=Tracking, 4=Cockpit (also switches camera mode);
#              any other integer just tags the trace with a
#              "cesium.scenario.<id>" marker (see the scenario matrix in the
#              plan doc for what 1, 5-10 are intended to mean — those involve
#              manual steps, e.g. backgrounding the app, that this script
#              doesn't automate).
#
# Requires: $CESIUM_RS_HOME (or ~/CesiumRS) checked out, `adb` on PATH with
# exactly one device/emulator attached, and (optional, for trace slicing)
# `trace_processor_shell` from the Perfetto SDK on PATH.
set -euo pipefail

SCENARIO_ID="${1:?usage: run_perf_scenario.sh <scenario_id> [duration_seconds]}"
DURATION_S="${2:-30}"
PACKAGE="com.silas270.blocktime"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CESIUM_RS_HOME="${CESIUM_RS_HOME:-$HOME/CesiumRS}"
OUT_DIR="$REPO_ROOT/perf_runs/scenario_${SCENARIO_ID}_$(date +%Y%m%d_%H%M%S)"
mkdir -p "$OUT_DIR"

echo "==> Output directory: $OUT_DIR"

echo "==> Building profiling debug APK (-Pcesium.profile=profiling)"
(cd "$REPO_ROOT" && ./gradlew :app:assembleDebug -Pcesium.profile=profiling)

APK="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
echo "==> Installing $APK"
adb install -r "$APK"

echo "==> Pushing Perfetto trace config"
TRACE_DEVICE_PATH="/data/misc/perfetto-traces/cesium_perf.pftrace"
adb shell perfetto --txt -c - --out "$TRACE_DEVICE_PATH" <<EOF &
buffers { size_kb: 131072 }
data_sources {
  config {
    name: "linux.ftrace"
    ftrace_config {
      atrace_apps: "$PACKAGE"
      atrace_categories: "gfx"
      atrace_categories: "view"
      atrace_categories: "sched"
      atrace_categories: "freq"
    }
  }
}
data_sources {
  config {
    name: "android.heapprofd"
    heapprofd_config {
      process_cmdline: "$PACKAGE"
      sampling_interval_bytes: 4096
    }
  }
}
data_sources { config { name: "android.meminfo" } }
duration_ms: $((DURATION_S * 1000))
EOF
PERFETTO_PID=$!

echo "==> Launching app"
adb shell am start -n "$PACKAGE/.CesiumGameActivity" >/dev/null
sleep 5  # let the engine finish init before tagging the trace with the scenario

echo "==> Triggering scenario $SCENARIO_ID"
adb shell am broadcast -a "$PACKAGE.PERF_SCENARIO" --ei scenario_id "$SCENARIO_ID" >/dev/null

echo "==> Polling memory (fallback alongside Perfetto's android.meminfo track)"
MEM_LOG="$OUT_DIR/mem_samples.log"
: > "$MEM_LOG"
(
  END=$((SECONDS + DURATION_S))
  while [ "$SECONDS" -lt "$END" ]; do
    echo "ts=$(date +%s.%N)" >> "$MEM_LOG"
    adb shell dumpsys meminfo "$PACKAGE" 2>/dev/null | grep -E "TOTAL |Native Heap" >> "$MEM_LOG" || true
    sleep 1
  done
) &
MEM_PID=$!

wait "$PERFETTO_PID" || true
wait "$MEM_PID" || true

echo "==> Pulling trace"
adb pull "$TRACE_DEVICE_PATH" "$OUT_DIR/cesium_perf.pftrace"

SUBSYSTEMS_JSON_ARG=()
if [ -f "$CESIUM_RS_HOME/benchmark_report.json" ]; then
  SUBSYSTEMS_JSON_ARG=(--subsystems-json "$CESIUM_RS_HOME/benchmark_report.json")
  echo "note: using desktop benchmark_report.json for the subsystem breakdown — this on-device" \
       "run doesn't (yet) dump SubsystemTimings JSON from the device itself; the trace above" \
       "still has the real on-device ATrace timeline."
fi

echo "==> Analyzing"
python3 "$CESIUM_RS_HOME/src/testing/analyze_perf.py" \
  --trace "$OUT_DIR/cesium_perf.pftrace" \
  --mem-log "$MEM_LOG" \
  --out "$OUT_DIR/perf_report.json" \
  "${SUBSYSTEMS_JSON_ARG[@]}"

echo "==> Done. Artifacts in $OUT_DIR"
