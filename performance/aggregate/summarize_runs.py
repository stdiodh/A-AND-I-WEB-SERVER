#!/usr/bin/env python3
"""Summarize at least three accepted k6 summary JSON runs with strict context checks."""

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
    "baseUrlHost",
    "targetEnvironment",
    "dockerNetworkMode",
    "jvmOptions",
    "mongodbMode",
    "cpu",
    "memory",
    "warmupCompleted",
]

COMMON_SUMMARY_METRICS = {
    "business_p50_ms": ("api_request_duration_ms", "med"),
    "business_p90_ms": ("api_request_duration_ms", "p(90)"),
    "business_p95_ms": ("api_request_duration_ms", "p(95)"),
    "business_p99_ms": ("api_request_duration_ms", "p(99)"),
    "http_rps": ("http_reqs", "rate"),
    "business_success_throughput": ("business_success_count", "rate"),
    "iterations": ("iterations", "count"),
    "iterations_rate": ("iterations", "rate"),
    "http_failure_rate": ("http_req_failed", "rate"),
    "check_success_rate": ("checks", "rate"),
    "dropped_iterations": ("dropped_iterations", "count"),
}

ASSIGNMENT_LIST_METRICS = {
    "assignment_list_p50_ms": ("assignment_list_duration", "med"),
    "assignment_list_p90_ms": ("assignment_list_duration", "p(90)"),
    "assignment_list_p95_ms": ("assignment_list_duration", "p(95)"),
    "assignment_list_p99_ms": ("assignment_list_duration", "p(99)"),
    "assignment_list_success_throughput": ("assignment_list_success_count", "rate"),
}

ASSIGNMENT_DETAIL_METRICS = {
    "assignment_detail_p50_ms": ("assignment_detail_duration", "med"),
    "assignment_detail_p90_ms": ("assignment_detail_duration", "p(90)"),
    "assignment_detail_p95_ms": ("assignment_detail_duration", "p(95)"),
    "assignment_detail_p99_ms": ("assignment_detail_duration", "p(99)"),
    "assignment_detail_success_throughput": ("assignment_detail_success_count", "rate"),
}

LOCAL_BASE_URL_HOSTS = {"localhost", "127.0.0.1", "::1"}
BLOCKED_BASE_URL_HOSTS = {"aandiclub.com", "api.aandiclub.com"}
PRODUCTION_ENVIRONMENTS = {"prod", "production"}


def main() -> int:
    parser = argparse.ArgumentParser(description="Summarize at least three accepted k6 summary JSON files.")
    parser.add_argument("summary", nargs="+", help="k6 summary JSON files")
    parser.add_argument("--json-output", help="Optional path to write the aggregate JSON.")
    parser.add_argument("--markdown-output", help="Optional path to write the aggregate Markdown.")
    args = parser.parse_args()

    if len(args.summary) < 3:
        print(json.dumps({"accepted": False, "reasons": ["at least three summary files are required"]}, ensure_ascii=False, indent=2))
        return 2

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
    markdown = render_markdown(result)
    result["markdown"] = markdown

    json_text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.json_output:
        Path(args.json_output).write_text(json_text + "\n", encoding="utf-8")
    if args.markdown_output:
        Path(args.markdown_output).write_text(markdown + "\n", encoding="utf-8")
    print(json_text)
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
        base_url_host = normalize_host(context.get("baseUrlHost"))
        target_environment = normalize_host(context.get("targetEnvironment"))
        if base_url_host in BLOCKED_BASE_URL_HOSTS:
            reasons.append(f"run {index} baseUrlHost is blocked: {base_url_host}")
        if base_url_host not in LOCAL_BASE_URL_HOSTS:
            reasons.append(f"run {index} baseUrlHost must be localhost or 127.0.0.1")
        if target_environment in PRODUCTION_ENVIRONMENTS:
            reasons.append(f"run {index} targetEnvironment must not be production/prod")
        if target_environment != "local":
            reasons.append(f"run {index} targetEnvironment must be local")
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
        if not endpoint_metrics_for_context(context):
            reasons.append(f"run {index} has no endpoint ratio greater than zero")
        for label, (metric_name, value_name) in summary_metrics_for_context(context).items():
            value = metric(payload, metric_name, value_name, default_for_metric(label))
            if value is None:
                reasons.append(f"run {index} missing metric: {label}")
            elif requires_positive_value(label) and value <= 0:
                reasons.append(f"run {index} non-positive metric: {label}")
    return reasons


def context_subset(payload: dict[str, Any]) -> dict[str, Any]:
    context = payload.get("context", {})
    return {
        "scenario": context.get("scenario"),
        "baseUrlHost": context.get("baseUrlHost"),
        "targetEnvironment": context.get("targetEnvironment"),
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
        "baseUrlHost": context.get("baseUrlHost"),
        "targetEnvironment": context.get("targetEnvironment"),
        "dockerNetworkMode": context.get("dockerNetworkMode"),
        "jvmOptions": context.get("jvmOptions"),
        "mongodbMode": context.get("mongodbMode"),
        "cpu": context.get("cpu"),
        "memory": context.get("memory"),
        "warmupCompleted": context.get("warmupCompleted"),
    }


def run_values(file_name: str, payload: dict[str, Any]) -> dict[str, Any]:
    values = {"file": file_name}
    for label, (metric_name, value_name) in summary_metrics_for_context(payload.get("context", {})).items():
        values[label] = metric(payload, metric_name, value_name, default_for_metric(label))
    return values


def summarize(payloads: list[dict[str, Any]]) -> dict[str, Any]:
    result = {}
    for label, (metric_name, value_name) in summary_metrics_for_context(payloads[0].get("context", {})).items():
        values = [metric(payload, metric_name, value_name, default_for_metric(label)) for payload in payloads]
        clean_values = [float(value) for value in values if value is not None]
        result[label] = {
            "median": statistics.median(clean_values),
            "min": min(clean_values),
            "max": max(clean_values),
        }
    return result


def render_markdown(result: dict[str, Any]) -> str:
    context = result["context"]
    lines = [
        "# k6 Aggregate - assignment-read",
        "",
        "## Context",
        "",
        f"- Scenario: {context.get('scenario')}",
        f"- Git Commit SHA: {context.get('gitCommitSha')}",
        f"- k6 Version: {context.get('k6Version')}",
        f"- BASE_URL Host: {context.get('baseUrlHost')}",
        f"- Target Environment: {context.get('targetEnvironment')}",
        f"- Fixture Fingerprint: {context.get('fixtureFingerprint')}",
        f"- Fixture Counts: {json.dumps(context.get('fixtureCounts'), ensure_ascii=False, sort_keys=True)}",
        f"- Load Model: {context.get('loadModel')}",
        f"- Target RPS: {context.get('targetRps')}",
        f"- Duration: {context.get('duration')}",
        f"- Pre Allocated VUs: {context.get('preAllocatedVus')}",
        f"- Max VUs: {context.get('maxVus')}",
        f"- JVM Options: {context.get('jvmOptions')}",
        f"- MongoDB Mode: {context.get('mongodbMode')}",
        f"- CPU: {context.get('cpu')}",
        f"- Memory: {context.get('memory')}",
        "",
        "## Metrics",
        "",
        "| Metric | Median | Min | Max |",
        "| :--- | ---: | ---: | ---: |",
    ]
    for name, values in result["summary"].items():
        lines.append(
            f"| {name} | {format_number(values['median'])} | {format_number(values['min'])} | {format_number(values['max'])} |"
        )
    lines.extend(
        [
            "",
            "## Runs",
            "",
            "| File | Business P95 | Business P99 | HTTP failed | Checks | Iterations | Dropped iterations |",
            "| :--- | ---: | ---: | ---: | ---: | ---: | ---: |",
        ]
    )
    for run in result["runs"]:
        lines.append(
            "| "
            + " | ".join(
                [
                    str(run.get("file")),
                    format_number(run.get("business_p95_ms")),
                    format_number(run.get("business_p99_ms")),
                    format_number(run.get("http_failure_rate")),
                    format_number(run.get("check_success_rate")),
                    format_number(run.get("iterations")),
                    format_number(run.get("dropped_iterations")),
                ]
            )
            + " |"
        )
    return "\n".join(lines)


def format_number(value: Any) -> str:
    if value is None:
        return "n/a"
    number = float(value)
    if number.is_integer():
        return str(int(number))
    return f"{number:.4f}".rstrip("0").rstrip(".")


def metric(payload: dict[str, Any], metric_name: str, value_name: str, default: float | None) -> float | None:
    value = payload.get("k6", {}).get("metrics", {}).get(metric_name, {}).get("values", {}).get(value_name)
    if value is None:
        return default
    return float(value)


def default_for_metric(label: str) -> float | None:
    if label == "dropped_iterations":
        return 0.0
    return None


def normalize_host(value: Any) -> str:
    return str(value or "").strip().lower().replace("[", "").replace("]", "")


def summary_metrics_for_context(context: dict[str, Any]) -> dict[str, tuple[str, str]]:
    metrics: dict[str, tuple[str, str]] = {}
    metrics.update(endpoint_metrics_for_context(context))
    metrics.update(COMMON_SUMMARY_METRICS)
    return metrics


def endpoint_metrics_for_context(context: dict[str, Any]) -> dict[str, tuple[str, str]]:
    metrics: dict[str, tuple[str, str]] = {}
    if ratio_value(context.get("assignmentListRatio")) > 0:
        metrics.update(ASSIGNMENT_LIST_METRICS)
    if ratio_value(context.get("assignmentDetailRatio")) > 0:
        metrics.update(ASSIGNMENT_DETAIL_METRICS)
    return metrics


def ratio_value(raw: Any) -> float:
    try:
        return float(raw)
    except (TypeError, ValueError):
        return 0.0


def requires_positive_value(label: str) -> bool:
    return label not in {"http_failure_rate", "dropped_iterations"}


if __name__ == "__main__":
    raise SystemExit(main())
