#!/usr/bin/env python3
"""Per-camera-mode subsystem breakdown from an on-device Perfetto trace.

Fills the gap CesiumRS's `src/testing/analyze_perf.py` leaves for device runs:
that script's ranked breakdown comes from the `SubsystemTimings` JSON the
*desktop* harnesses emit, and its trace handling only lists the
`cesium.scenario.*` markers. Nothing on the device dumps SubsystemTimings, so
for an on-device run the real per-subsystem numbers have to come out of the
ATrace slices in the trace itself — which is what this does.

Slices the trace at the `cesium.scenario.{2,3,4}` markers written by
tools/run_perf_multimode.sh (Free / Tracking / Cockpit), then for every
`cesium.*` span in each window reports count, avg/p50/p90/p99/max and share of
the `cesium.frame` budget, plus RSS slope and CPU-frequency averages so
thermal throttling across the three windows stays visible.

Usage:
    tools/analyze_multimode.py --trace perf_runs/<run>/cesium_multimode.pftrace \\
        [--tp /path/to/trace_processor_shell] [--out report.json]
"""
import argparse
import csv
import io
import json
import shutil
import subprocess
import sys
from pathlib import Path

SCENARIO_LABELS = {2: "Free", 3: "Tracking", 4: "Cockpit"}

# The engine deliberately starts `cesium.frame` *after* the 60fps throttle sleep
# and the battery-saver idle sleep, so these two sit outside the frame span.
# Ranking them as a share of the frame budget would print nonsense (>100%);
# they are reported separately as the headroom left under the frame cap.
SLEEP_SPANS = {"cesium.frame.throttle_sleep", "cesium.frame.idle_sleep"}


def run_query(tp, trace, query):
    """Runs one SQL query through trace_processor_shell, returns list of dicts."""
    # Perfetto v40+ uses subcommands (`tp query <trace> -f -`) and prints CSV by
    # default; the older `-q FILE TRACE` form CesiumRS's analyze_perf.py still
    # uses just prints the help text there.
    result = subprocess.run(
        [tp, "query", str(trace), "-f", "-"],
        input=query,
        capture_output=True,
        text=True,
        timeout=1800,
    )
    if result.returncode != 0:
        sys.exit(f"trace_processor failed ({result.returncode}):\n{result.stderr}")
    # trace_processor prints load progress/warnings before the CSV body; the
    # header row is the first line holding the first selected column name.
    lines = [ln for ln in result.stdout.splitlines() if ln.strip()]
    for i, line in enumerate(lines):
        if line.startswith('"') or "," in line:
            body = "\n".join(lines[i:])
            break
    else:
        return []
    return list(csv.DictReader(io.StringIO(body)))


def find_windows(tp, trace, tail_s):
    rows = run_query(
        tp,
        trace,
        "select s.name as name, s.ts as ts from slice s "
        "where s.name glob 'cesium.scenario.*' order by s.ts;",
    )
    markers = []
    for r in rows:
        try:
            sid = int(r["name"].rsplit(".", 1)[1])
        except (ValueError, IndexError):
            continue
        markers.append((sid, int(r["ts"])))
    if not markers:
        sys.exit(
            "No cesium.scenario.* markers in the trace — the scenario broadcasts "
            "never reached the engine (check that the APK was built with "
            "-Pcesium.profile=profiling)."
        )
    bounds = run_query(tp, trace, "select max(ts+dur) as end_ts from slice;")
    trace_end = int(bounds[0]["end_ts"]) if bounds and bounds[0]["end_ts"] else None

    windows = []
    for i, (sid, ts) in enumerate(markers):
        if i + 1 < len(markers):
            end = markers[i + 1][1]
        else:
            end = ts + int(tail_s * 1e9)
            if trace_end:
                end = min(end, trace_end)
        windows.append(
            {
                "scenario_id": sid,
                "label": SCENARIO_LABELS.get(sid, f"scenario_{sid}"),
                "start_ts": ts,
                "end_ts": end,
                "duration_s": (end - ts) / 1e9,
            }
        )
    return windows


def subsystem_stats(tp, trace, window):
    query = f"""
    with spans as (
      select s.name as name, s.dur as dur,
             row_number() over (partition by s.name order by s.dur) as rn,
             count(*) over (partition by s.name) as cnt
      from slice s
      where s.ts >= {window['start_ts']} and s.ts < {window['end_ts']}
        and s.name glob 'cesium.*'
        and s.name not glob 'cesium.scenario.*'
        and s.dur > 0
    )
    select name, cnt as count,
           avg(dur) as avg_ns,
           max(case when rn = max(1, cast(cnt * 0.5 as int)) then dur end) as p50_ns,
           max(case when rn = max(1, cast(cnt * 0.9 as int)) then dur end) as p90_ns,
           max(case when rn = max(1, cast(cnt * 0.99 as int)) then dur end) as p99_ns,
           max(dur) as max_ns,
           sum(dur) as total_ns
    from spans group by name order by total_ns desc;
    """
    rows = run_query(tp, trace, query)
    stats = {}
    for r in rows:
        stats[r["name"]] = {
            "count": int(r["count"]),
            "avg_us": float(r["avg_ns"]) / 1000.0,
            "p50_us": float(r["p50_ns"] or 0) / 1000.0,
            "p90_us": float(r["p90_ns"] or 0) / 1000.0,
            "p99_us": float(r["p99_ns"] or 0) / 1000.0,
            "max_us": float(r["max_ns"] or 0) / 1000.0,
            "total_us": float(r["total_ns"]) / 1000.0,
        }
    return stats


def frame_children(tp, trace, window):
    """Total time of the spans that are *direct* children of `cesium.frame`.

    Shares in the flat table below can't simply be summed — `cesium.update.extension`
    already contains `cesium.update.camera_mode.*`, `cesium.render.extension`
    contains `cesium.render.cockpit_model`, and so on. Walking parent_id gives the
    one level that does add up, and with it the frame time no span accounts for.
    """
    rows = run_query(
        tp,
        trace,
        f"""select c.name as name, sum(c.dur) as total_ns
            from slice c join slice p on c.parent_id = p.id
            where p.name = 'cesium.frame'
              and c.ts >= {window['start_ts']} and c.ts < {window['end_ts']}
            group by c.name;""",
    )
    return {r["name"]: float(r["total_ns"]) / 1000.0 for r in rows if r.get("total_ns")}


def counters(tp, trace, window):
    """Per-window RSS (slope = leak signal) and average CPU frequency (throttling)."""
    out = {}
    rss = run_query(
        tp,
        trace,
        f"""select c.ts as ts, c.value as value
            from counter c
            join process_counter_track t on c.track_id = t.id
            join process p using(upid)
            where t.name = 'mem.rss' and p.name glob '*blocktime*'
              and c.ts >= {window['start_ts']} and c.ts < {window['end_ts']}
            order by c.ts;""",
    )
    samples = [(int(r["ts"]), float(r["value"])) for r in rss if r.get("value")]
    if len(samples) >= 2:
        dt_s = (samples[-1][0] - samples[0][0]) / 1e9
        out["rss_mb_start"] = samples[0][1] / 1e6
        out["rss_mb_end"] = samples[-1][1] / 1e6
        out["rss_mb_avg"] = sum(v for _, v in samples) / len(samples) / 1e6
        out["rss_slope_mb_per_min"] = (
            ((samples[-1][1] - samples[0][1]) / 1e6) / (dt_s / 60.0) if dt_s > 0 else 0.0
        )
        out["rss_sample_count"] = len(samples)

    freq = run_query(
        tp,
        trace,
        f"""select avg(c.value) as avg_khz, max(c.value) as max_khz
            from counter c
            join cpu_counter_track t on c.track_id = t.id
            where t.name = 'cpufreq'
              and c.ts >= {window['start_ts']} and c.ts < {window['end_ts']};""",
    )
    if freq and freq[0].get("avg_khz"):
        out["cpufreq_avg_mhz"] = float(freq[0]["avg_khz"]) / 1000.0
        out["cpufreq_max_mhz"] = float(freq[0]["max_khz"]) / 1000.0
    return out


def print_window(window, stats, extra, children=None):
    frame = stats.get("cesium.frame")
    budget = frame["avg_us"] if frame else 0.0
    print(f"\n{'=' * 92}")
    print(
        f"=== {window['label']} (scenario {window['scenario_id']}, "
        f"{window['duration_s']:.0f}s, {frame['count'] if frame else 0} frames) ==="
    )
    if frame:
        fps = frame["count"] / window["duration_s"]
        print(
            f"    cesium.frame: avg {frame['avg_us']:.0f}us  p90 {frame['p90_us']:.0f}us  "
            f"p99 {frame['p99_us']:.0f}us  max {frame['max_us']:.0f}us  "
            f"({fps:.1f} frames/s captured)"
        )
    for sleep_name in sorted(SLEEP_SPANS):
        s = stats.get(sleep_name)
        if s:
            print(
                f"    {sleep_name.split('.')[-1]}: avg {s['avg_us']:.0f}us idle per frame "
                f"(outside the frame span — headroom under the 60fps cap)"
            )
    if children and frame:
        attributed = sum(children.values())
        total_frame = frame["total_us"]
        if total_frame > 0:
            unattributed_us = (total_frame - attributed) / frame["count"]
            print(
                f"    unattributed inside cesium.frame: {unattributed_us:.0f}us/frame "
                f"({(total_frame - attributed) / total_frame * 100:.1f}%)"
            )
    if extra:
        bits = []
        if "rss_mb_avg" in extra:
            bits.append(
                f"RSS avg {extra['rss_mb_avg']:.0f}MB "
                f"({extra['rss_mb_start']:.0f}->{extra['rss_mb_end']:.0f}MB, "
                f"{extra['rss_slope_mb_per_min']:+.2f}MB/min)"
            )
        if "cpufreq_avg_mhz" in extra:
            bits.append(f"CPU avg {extra['cpufreq_avg_mhz']:.0f}MHz")
        if bits:
            print("    " + "  |  ".join(bits))
    print(f"{'-' * 92}")
    print(
        f"{'span':<34} {'% frame':>8} {'count':>8} {'avg us':>10} "
        f"{'p90 us':>10} {'p99 us':>10} {'max us':>10}"
    )
    for name, s in sorted(stats.items(), key=lambda kv: -kv[1]["avg_us"]):
        if name == "cesium.frame" or name in SLEEP_SPANS:
            continue
        pct = (s["avg_us"] / budget * 100) if budget else 0.0
        print(
            f"{name:<34} {pct:>7.1f}% {s['count']:>8} {s['avg_us']:>10.1f} "
            f"{s['p90_us']:>10.1f} {s['p99_us']:>10.1f} {s['max_us']:>10.1f}"
        )


def print_comparison(report):
    windows = report["windows"]
    names = sorted({n for w in windows for n in w["subsystems"]})
    labels = [w["label"] for w in windows]
    print(f"\n{'=' * 92}")
    print("=== Cross-mode comparison (avg us per frame) ===")
    print(f"{'span':<34}" + "".join(f"{l:>16}" for l in labels))
    print(f"{'-' * 92}")
    for name in sorted(
        (n for n in names if n not in SLEEP_SPANS),
        key=lambda n: -max(w["subsystems"].get(n, {}).get("avg_us", 0) for w in windows),
    ):
        row = f"{name:<34}"
        for w in windows:
            s = w["subsystems"].get(name)
            row += f"{s['avg_us']:>16.1f}" if s else f"{'-':>16}"
        print(row)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--trace", required=True)
    ap.add_argument("--tp", default=None, help="path to trace_processor_shell")
    ap.add_argument("--out", default=None, help="write JSON report here")
    ap.add_argument(
        "--tail-s",
        type=float,
        default=600.0,
        help="length of the last scenario window when no marker follows it",
    )
    args = ap.parse_args()

    tp = args.tp or shutil.which("trace_processor_shell")
    if not tp:
        sys.exit("trace_processor_shell not found — pass --tp /path/to/trace_processor_shell")
    trace = Path(args.trace)
    if not trace.exists():
        sys.exit(f"no such trace: {trace}")

    windows = find_windows(tp, trace, args.tail_s)
    report = {"trace": str(trace), "windows": []}
    for w in windows:
        stats = subsystem_stats(tp, trace, w)
        extra = counters(tp, trace, w)
        children = frame_children(tp, trace, w)
        entry = dict(w, subsystems=stats, counters=extra, frame_children_us=children)
        report["windows"].append(entry)
        print_window(w, stats, extra, children)

    if len(report["windows"]) > 1:
        print_comparison(report)

    if args.out:
        Path(args.out).write_text(json.dumps(report, indent=2))
        print(f"\nJSON report: {args.out}")


if __name__ == "__main__":
    main()
