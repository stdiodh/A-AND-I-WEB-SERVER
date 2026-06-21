#!/usr/bin/env python3
"""Compare two k6 summary JSON files only when run conditions match."""

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
    "jvmOptions",
    "mongodbMode",
    "cpu",
    "memory",
    "k6Version",
    "executor",
    "vus",
    "duration",
    "requestSleepSeconds",
    "assignmentListRatio",
    "assignmentDetailRatio",
    "warmupCompleted",
]

TREND_METRICS = {
    "p50": ("api_request_duration_ms", "med"),
    "p95": ("api_request_duration_ms", "p(95)"),
    "p99": ("api_request_duration_ms", "p(99)"),
    "rps": ("http_reqs", "rate"),
}


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare two k6 summary JSON files.")
    parser.add_argument("before")
    parser.add_argument("after")
    args = parser.parse_args()

    before = load_summary(Path(args.before))
    after = load_summary(Path(args.after))
    comparable, reasons = comparable_reasons(before, after)
    if not comparable:
        print(json.dumps({"comparable": False, "differences": reasons}, ensure_ascii=False, indent=2))
        return 2

    result = {
        "comparable": True,
        "scenario": before["context"]["scenario"],
        "metricDelta": {},
    }
    for label, (metric, value_name) in TREND_METRICS.items():
        before_value = metric_value(before, metric, value_name)
        after_value = metric_value(after, metric, value_name)
        result["metricDelta"][label] = {
            "before": before_value,
            "after": after_value,
            "delta": after_value - before_value,
            "deltaPercent": ((after_value - before_value) / before_value * 100.0) if before_value else None,
        }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


def load_summary(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        payload = json.load(f)
    if "context" not in payload or "k6" not in payload:
        raise SystemExit(f"Invalid summary JSON: {path}")
    return payload


def comparable_reasons(before: dict[str, Any], after: dict[str, Any]) -> tuple[bool, list[str]]:
    reasons: list[str] = []
    before_context = before.get("context", {})
    after_context = after.get("context", {})
    for key in REQUIRED_EQUAL_CONTEXT:
        if before_context.get(key) != after_context.get(key):
            reasons.append(f"context mismatch: {key}")

    for label, payload in (("before", before), ("after", after)):
        context = payload.get("context", {})
        if context.get("gitCommitSha") in (None, "", "unknown"):
            reasons.append(f"{label} git SHA is unknown")
        if context.get("executed") is not True:
            reasons.append(f"{label} scenario was not executed")
        if context.get("skipped") is True:
            reasons.append(f"{label} scenario was skipped")
        if int(context.get("businessRequestCount") or 0) <= 0:
            reasons.append(f"{label} has zero business requests")
        if context.get("thresholdFailed") is True:
            reasons.append(f"{label} has failed threshold or zero-request guard")
        if context.get("warmupCompleted") != "true":
            reasons.append(f"{label} warmup was not completed")
        check_metric = metric_value_or_none(payload, "checks", "fails")
        if check_metric is None:
            reasons.append(f"{label} checks metric is missing")
        elif check_metric > 0:
            reasons.append(f"{label} has failed checks")
        for metric, value_name in TREND_METRICS.values():
            value = metric_value_or_none(payload, metric, value_name)
            if value is None:
                reasons.append(f"{label} missing metric: {metric}.{value_name}")
            elif value <= 0:
                reasons.append(f"{label} non-positive metric: {metric}.{value_name}")

    return len(reasons) == 0, reasons


def metric_value(payload: dict[str, Any], metric: str, value_name: str) -> float:
    value = metric_value_or_none(payload, metric, value_name)
    if value is None:
        raise SystemExit(f"Missing metric: {metric}.{value_name}")
    return value


def metric_value_or_none(payload: dict[str, Any], metric: str, value_name: str) -> float | None:
    values = payload.get("k6", {}).get("metrics", {}).get(metric, {}).get("values", {})
    value = values.get(value_name)
    if value is None:
        return None
    return float(value)


if __name__ == "__main__":
    raise SystemExit(main())
