#!/usr/bin/env python3
"""Summarize three accepted k6 summary JSON runs with strict context checks."""

from __future__ import annotations

import argparse
import json
import statistics
import sys
from pathlib import Path
from typing import Any


REQUIRED_CONTEXT_EQUAL = [
    "scenario",
    "fixtureFingerprint",
    "fixtureCounts",
    "gitCommitSha",
    "k6Version",
    "executor",
    "vus",
    "loadModel",
    "targetRps",
    "preAllocatedVus",
    "maxVus",
    "duration",
    "requestSleepSeconds",
    "assignmentListRatio",
    "assignmentDetailRatio",
    "jvmOptions",
    "mongodbMode",
    "cpu",
    "memory",
    "warmupCompleted",
]

SUMMARY_METRICS = {
    "assignment_list_p50_ms": ("assignment_list_duration", "med"),
    "assignment_list_p95_ms": ("assignment_list_duration", "p(95)"),
    "assignment_list_p99_ms": ("assignment_list_duration", "p(99)"),
    "assignment_detail_p50_ms": ("assignment_detail_duration", "med"),
    "assignment_detail_p95_ms": ("assignment_detail_duration", "p(95)"),
    "assignment_detail_p99_ms": ("assignment_detail_duration", "p(99)"),
    "http_rps": ("http_reqs", "rate"),
    "business_success_throughput": ("business_success_count", "rate"),
    "assignment_list_success_throughput": ("assignment_list_success_count", "rate"),
    "assignment_detail_success_throughput": ("assignment_detail_success_count", "rate"),
    "http_failure_rate": ("http_req_failed", "rate"),
    "check_success_rate": ("checks", "rate"),
    "dropped_iterations": ("dropped_iterations", "count"),
}


def main() -> int:
    parser = argparse.ArgumentParser(description="Summarize exactly three accepted k6 summary JSON files.")
    parser.add_argument("summary", nargs=3, help="k6 summary JSON files")
    args = parser.parse_args()

    payloads = [load_summary(Path(path)) for path in args.summary]
    reasons = validation_errors(payloads)
    if reasons:
        print(json.dumps({"accepted": False, "reasons": reasons}, ensure_ascii=False, indent=2))
        return 2

    result = {
        "accepted": True,
        "context": context_subset(payloads[0]),
        "runs": [run_values(Path(path).name, payload) for path, payload in zip(args.summary, payloads)],
        "summary": summarize(payloads),
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


def load_summary(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        payload = json.load(f)
    if "context" not in payload or "k6" not in payload:
        raise SystemExit(f"Invalid k6 summary JSON: {path}")
    return payload


def validation_errors(payloads: list[dict[str, Any]]) -> list[str]:
    reasons: list[str] = []
    baseline = payloads[0].get("context", {})
    for index, payload in enumerate(payloads, start=1):
        context = payload.get("context", {})
        for key in REQUIRED_CONTEXT_EQUAL:
            if context.get(key) != baseline.get(key):
                reasons.append(f"run {index} context mismatch: {key}")
        if context.get("executed") is not True:
            reasons.append(f"run {index} was not executed")
        if context.get("skipped") is True:
            reasons.append(f"run {index} was skipped")
        if context.get("thresholdFailed") is True:
            reasons.append(f"run {index} has failed threshold")
        if context.get("gitDirty") != "false":
            reasons.append(f"run {index} gitDirty must be false")
        if context.get("warmupCompleted") != "true":
            reasons.append(f"run {index} warmupCompleted must be true")
        if int(context.get("businessRequestCount") or 0) <= 0:
            reasons.append(f"run {index} has zero business requests")
        if metric(payload, "checks", "fails", 1.0) != 0.0:
            reasons.append(f"run {index} has failed checks")
        if metric(payload, "dropped_iterations", "count", 0.0) != 0.0:
            reasons.append(f"run {index} has dropped iterations")
        if metric(payload, "private_testcase_guard_rate", "rate", 0.0) != 1.0:
            reasons.append(f"run {index} private testcase guard is not 100%")
        if metric(payload, "auth_error_count", "count", 0.0) != 0.0:
            reasons.append(f"run {index} has auth errors")
        if metric(payload, "server_error_count", "count", 0.0) != 0.0:
            reasons.append(f"run {index} has server errors")
        for label, (metric_name, value_name) in SUMMARY_METRICS.items():
            value = metric(payload, metric_name, value_name, default_for_metric(label))
            if value is None:
                reasons.append(f"run {index} missing metric: {label}")
            elif label != "http_failure_rate" and label != "dropped_iterations" and value <= 0:
                reasons.append(f"run {index} non-positive metric: {label}")
    return reasons


def context_subset(payload: dict[str, Any]) -> dict[str, Any]:
    context = payload.get("context", {})
    return {
        "scenario": context.get("scenario"),
        "gitCommitSha": context.get("gitCommitSha"),
        "gitDirty": context.get("gitDirty"),
        "k6Version": context.get("k6Version"),
        "fixtureFingerprint": context.get("fixtureFingerprint"),
        "fixtureCounts": context.get("fixtureCounts"),
        "executor": context.get("executor"),
        "vus": context.get("vus"),
        "loadModel": context.get("loadModel"),
        "targetRps": context.get("targetRps"),
        "preAllocatedVus": context.get("preAllocatedVus"),
        "maxVus": context.get("maxVus"),
        "duration": context.get("duration"),
        "requestSleepSeconds": context.get("requestSleepSeconds"),
        "assignmentListRatio": context.get("assignmentListRatio"),
        "assignmentDetailRatio": context.get("assignmentDetailRatio"),
        "jvmOptions": context.get("jvmOptions"),
        "mongodbMode": context.get("mongodbMode"),
        "cpu": context.get("cpu"),
        "memory": context.get("memory"),
        "warmupCompleted": context.get("warmupCompleted"),
    }


def run_values(file_name: str, payload: dict[str, Any]) -> dict[str, Any]:
    values = {"file": file_name}
    for label, (metric_name, value_name) in SUMMARY_METRICS.items():
        values[label] = metric(payload, metric_name, value_name, default_for_metric(label))
    return values


def summarize(payloads: list[dict[str, Any]]) -> dict[str, Any]:
    result = {}
    for label, (metric_name, value_name) in SUMMARY_METRICS.items():
        values = [metric(payload, metric_name, value_name, default_for_metric(label)) for payload in payloads]
        clean_values = [float(value) for value in values if value is not None]
        result[label] = {
            "median": statistics.median(clean_values),
            "min": min(clean_values),
            "max": max(clean_values),
        }
    return result


def metric(payload: dict[str, Any], metric_name: str, value_name: str, default: float | None) -> float | None:
    value = payload.get("k6", {}).get("metrics", {}).get(metric_name, {}).get("values", {}).get(value_name)
    if value is None:
        return default
    return float(value)


def default_for_metric(label: str) -> float | None:
    if label == "dropped_iterations":
        return 0.0
    return None


if __name__ == "__main__":
    raise SystemExit(main())
