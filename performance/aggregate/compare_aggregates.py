#!/usr/bin/env python3
"""Compare two accepted aggregate k6 results with strict context checks."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


REQUIRED_EQUAL_CONTEXT = [
    "scenario",
    "fixtureFingerprint",
    "fixtureCounts",
    "k6Version",
    "executor",
    "targetRps",
    "duration",
    "requestSleepSeconds",
    "assignmentListRatio",
    "assignmentDetailRatio",
    "jvmOptions",
    "mongodbMode",
    "cpu",
    "memory",
    "warmupCompleted",
    "loadModel",
    "preAllocatedVus",
    "maxVus",
]

LATENCY_METRICS = {
    "assignment_list_p50_ms": "Assignment list P50",
    "assignment_list_p95_ms": "Assignment list P95",
    "assignment_list_p99_ms": "Assignment list P99",
    "assignment_detail_p95_ms": "Assignment detail P95",
}

THROUGHPUT_METRIC = "business_success_throughput"


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare before/after aggregate k6 JSON files.")
    parser.add_argument("before")
    parser.add_argument("after")
    parser.add_argument("--json-output", help="Optional path to write the JSON comparison.")
    parser.add_argument("--markdown-output", help="Optional path to write the Markdown comparison.")
    args = parser.parse_args()

    before = load_json(Path(args.before))
    after = load_json(Path(args.after))
    result = compare(before, after)
    markdown = render_markdown(result)
    result["markdown"] = markdown

    json_text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.json_output:
        Path(args.json_output).write_text(json_text + "\n", encoding="utf-8")
    if args.markdown_output:
        Path(args.markdown_output).write_text(markdown + "\n", encoding="utf-8")
    print(json_text)
    return 0 if result["accepted"] else 2


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        payload = json.load(f)
    if "context" not in payload or "summary" not in payload or "runs" not in payload:
        raise SystemExit(f"Invalid aggregate JSON: {path}")
    return payload


def compare(before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
    reasons = validation_errors(before, after)
    if reasons:
        return {
            "accepted": False,
            "reasons": reasons,
        }

    latency = {
        key: latency_comparison(
            key=key,
            label=label,
            before=metric_summary(before, key),
            after=metric_summary(after, key),
            before_runs=run_values(before, key),
            after_runs=run_values(after, key),
        )
        for key, label in LATENCY_METRICS.items()
    }
    throughput = throughput_comparison(
        before=metric_summary(before, THROUGHPUT_METRIC),
        after=metric_summary(after, THROUGHPUT_METRIC),
        before_runs=run_values(before, THROUGHPUT_METRIC),
        after_runs=run_values(after, THROUGHPUT_METRIC),
    )

    return {
        "accepted": True,
        "context": comparable_context(before),
        "beforeGitCommitSha": before["context"].get("gitCommitSha"),
        "afterGitCommitSha": after["context"].get("gitCommitSha"),
        "latency": latency,
        "throughput": throughput,
        "queryReduction": query_reduction(before, after),
    }


def validation_errors(before: dict[str, Any], after: dict[str, Any]) -> list[str]:
    reasons: list[str] = []
    if before.get("accepted") is not True:
        reasons.append("before aggregate is not accepted")
    if after.get("accepted") is not True:
        reasons.append("after aggregate is not accepted")

    before_context = before.get("context", {})
    after_context = after.get("context", {})
    for key in REQUIRED_EQUAL_CONTEXT:
        if before_context.get(key) != after_context.get(key):
            reasons.append(f"context mismatch: {key}")

    for label, aggregate in (("before", before), ("after", after)):
        context = aggregate.get("context", {})
        if context.get("gitDirty") != "false":
            reasons.append(f"{label} gitDirty must be false")
        if context.get("warmupCompleted") != "true":
            reasons.append(f"{label} warm-up must be completed")
        for metric_name in list(LATENCY_METRICS.keys()) + [THROUGHPUT_METRIC]:
            summary = aggregate.get("summary", {}).get(metric_name)
            if not isinstance(summary, dict):
                reasons.append(f"{label} missing metric: {metric_name}")
                continue
            for key in ("median", "min", "max"):
                if summary.get(key) is None:
                    reasons.append(f"{label} missing metric: {metric_name}.{key}")
            for index, run in enumerate(aggregate.get("runs", []), start=1):
                if run.get(metric_name) is None:
                    reasons.append(f"{label} run {index} missing metric: {metric_name}")
            if float(summary.get("median") or 0.0) <= 0.0:
                reasons.append(f"{label} zero baseline guard failed: {metric_name}")
        if len(aggregate.get("runs", [])) == 0:
            reasons.append(f"{label} aggregate has no runs")

    return reasons


def metric_summary(aggregate: dict[str, Any], metric_name: str) -> dict[str, float]:
    summary = aggregate["summary"][metric_name]
    return {
        "median": float(summary["median"]),
        "min": float(summary["min"]),
        "max": float(summary["max"]),
    }


def run_values(aggregate: dict[str, Any], metric_name: str) -> list[dict[str, Any]]:
    return [
        {
            "file": run.get("file"),
            "value": float(run[metric_name]),
        }
        for run in aggregate["runs"]
    ]


def latency_comparison(
    *,
    key: str,
    label: str,
    before: dict[str, float],
    after: dict[str, float],
    before_runs: list[dict[str, Any]],
    after_runs: list[dict[str, Any]],
) -> dict[str, Any]:
    improvement_percent = ((before["median"] - after["median"]) / before["median"]) * 100.0
    delta_ms = after["median"] - before["median"]
    overlaps = ranges_overlap(before, after)
    if improvement_percent < 0:
        interpretation = "regression"
    elif improvement_percent > 0 and overlaps:
        interpretation = "inconclusive_range_overlap"
    elif improvement_percent > 0:
        interpretation = "improvement"
    else:
        interpretation = "unchanged"
    return {
        "metric": key,
        "label": label,
        "before": before,
        "after": after,
        "beforeRuns": before_runs,
        "afterRuns": after_runs,
        "deltaMs": delta_ms,
        "improvementPercent": improvement_percent,
        "rangeOverlap": overlaps,
        "interpretation": interpretation,
    }


def throughput_comparison(
    *,
    before: dict[str, float],
    after: dict[str, float],
    before_runs: list[dict[str, Any]],
    after_runs: list[dict[str, Any]],
) -> dict[str, Any]:
    improvement_percent = ((after["median"] - before["median"]) / before["median"]) * 100.0
    return {
        "metric": THROUGHPUT_METRIC,
        "before": before,
        "after": after,
        "beforeRuns": before_runs,
        "afterRuns": after_runs,
        "delta": after["median"] - before["median"],
        "improvementPercent": improvement_percent,
        "interpretation": "fixed_rate_reference",
    }


def query_reduction(before: dict[str, Any], after: dict[str, Any]) -> dict[str, Any]:
    before_count = query_count(before)
    after_count = query_count(after)
    if before_count is None or after_count is None:
        return {
            "accepted": False,
            "reason": "query count not supplied in aggregate",
        }
    if before_count <= 0:
        return {
            "accepted": False,
            "reason": "before query count must be greater than zero",
        }
    return {
        "accepted": True,
        "before": before_count,
        "after": after_count,
        "reductionPercent": ((before_count - after_count) / before_count) * 100.0,
    }


def query_count(aggregate: dict[str, Any]) -> float | None:
    query_counts = aggregate.get("queryCounts")
    if not isinstance(query_counts, dict):
        return None
    value = query_counts.get("childDocumentQueries")
    return None if value is None else float(value)


def ranges_overlap(before: dict[str, float], after: dict[str, float]) -> bool:
    return max(before["min"], after["min"]) <= min(before["max"], after["max"])


def comparable_context(aggregate: dict[str, Any]) -> dict[str, Any]:
    context = aggregate.get("context", {})
    return {key: context.get(key) for key in REQUIRED_EQUAL_CONTEXT}


def render_markdown(result: dict[str, Any]) -> str:
    if not result["accepted"]:
        reasons = "\n".join(f"- {reason}" for reason in result["reasons"])
        return "\n".join(["# Aggregate Comparison", "", "accepted: false", "", "## Reasons", "", reasons])

    rows = [
        "| Metric | Before median | After median | Delta | Improvement | Interpretation |",
        "| :--- | ---: | ---: | ---: | ---: | :--- |",
    ]
    for item in result["latency"].values():
        rows.append(
            "| {label} | {before:.3f} ms | {after:.3f} ms | {delta:.3f} ms | {improvement:.2f}% | {interpretation} |".format(
                label=item["label"],
                before=item["before"]["median"],
                after=item["after"]["median"],
                delta=item["deltaMs"],
                improvement=item["improvementPercent"],
                interpretation=item["interpretation"],
            )
        )
    throughput = result["throughput"]
    rows.append(
        "| Business success throughput | {before:.3f} req/s | {after:.3f} req/s | {delta:.3f} req/s | {improvement:.2f}% | fixed-rate reference |".format(
            before=throughput["before"]["median"],
            after=throughput["after"]["median"],
            delta=throughput["delta"],
            improvement=throughput["improvementPercent"],
        )
    )
    query = result["queryReduction"]
    query_text = "not supplied"
    if query.get("accepted"):
        query_text = "{before:.0f} -> {after:.0f} ({reduction:.2f}% reduction)".format(
            before=query["before"],
            after=query["after"],
            reduction=query["reductionPercent"],
        )
    return "\n".join(
        [
            "# Aggregate Comparison",
            "",
            "accepted: true",
            "",
            "## Latency and Throughput",
            "",
            *rows,
            "",
            "## Query Reduction",
            "",
            f"- {query_text}",
        ]
    )


if __name__ == "__main__":
    raise SystemExit(main())
