#!/usr/bin/env python3
"""Generate resume-safe metrics from local reports and committed artifacts."""

from __future__ import annotations

import argparse
import glob
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from xml.etree import ElementTree


CONFIRMED = "확인 완료"
NEEDS_MEASUREMENT = "측정 필요"
NEEDS_REVIEW = "확인 필요"
NOT_RECOMMENDED = "사용 비추천"
ALLOWED_CONFIDENCE = {CONFIRMED, NEEDS_MEASUREMENT, NEEDS_REVIEW, NOT_RECOMMENDED}
FORBIDDEN_CLAIM_PATTERNS = [
    "최대 처리량",
    "maximum throughput",
    "max throughput",
    "peak throughput",
]
DEFAULT_SCHEMA = "scripts/resume/resume_metrics_schema.json"
DEFAULT_EXAMPLE = "docs/metrics/resume-metrics.example.json"
COMMITTED_SNAPSHOT = "docs/metrics/resume-metrics.json"
CURATED_MARKDOWN = "docs/resume-metrics.md"
DEFAULT_OUT_JSON = "build/reports/resume-metrics/resume-metrics.json"
DEFAULT_OUT_MD = "build/reports/resume-metrics/resume-metrics.md"
DISCLAIMER = "이 수치는 운영 환경 최대 처리량이 아니라 로컬/고정 부하 회귀 검증 기준입니다."


def main() -> int:
    args = parse_args()
    if args.validate_only:
        validate_existing_outputs(args)
        return 0

    payload = build_payload(args)
    validate_payload(payload)
    markdown = render_markdown(payload)
    validate_generated_text(markdown, payload)

    write_json(Path(args.out_json), payload)
    Path(args.out_md).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out_md).write_text(markdown, encoding="utf-8")
    print(f"Wrote resume metrics JSON: {args.out_json}")
    print(f"Wrote resume metrics Markdown: {args.out_md}")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate resume metrics from local JaCoCo/JUnit/k6/CI artifacts without network access.",
    )
    parser.add_argument("--junit-glob", default="build/test-results/test/TEST-*.xml")
    parser.add_argument("--jacoco-xml", default="build/reports/jacoco/test/jacocoTestReport.xml")
    parser.add_argument("--assignment-scale-report", default="docs/performance/results/2026-06-29-assignment-scale.json")
    parser.add_argument("--ci-summary", default="docs/metrics/ci-summary.json")
    parser.add_argument("--out-json", default=DEFAULT_OUT_JSON)
    parser.add_argument("--out-md", default=DEFAULT_OUT_MD)
    parser.add_argument("--schema", default=DEFAULT_SCHEMA)
    parser.add_argument("--example-json", default=DEFAULT_EXAMPLE)
    parser.add_argument("--validate-only", action="store_true")
    return parser.parse_args()


def build_payload(args: argparse.Namespace) -> dict[str, Any]:
    metrics = {
        "tests": read_junit(args.junit_glob),
        "coverage": read_jacoco(Path(args.jacoco_xml)),
        "k6": read_assignment_scale_report(Path(args.assignment_scale_report)),
        "ci": read_ci_summary(Path(args.ci_summary)),
    }
    return {
        "schemaVersion": 1,
        "generatedAt": utc_now(),
        "sourcePolicy": {
            "networkAccess": False,
            "githubApiAccess": False,
            "loadTestExecuted": False,
            "workflowExecuted": False,
            "productionAccess": False,
            "allowedInputs": [
                "JaCoCo XML",
                "JUnit TEST XML",
                "assignment scale result JSON",
                "committed CI metrics JSON",
            ],
        },
        "disclaimer": DISCLAIMER,
        "metrics": metrics,
        "resumeSentences": resume_sentences(metrics),
    }


def read_jacoco(path: Path) -> dict[str, Any]:
    metric = base_metric("jacoco", str(path))
    if not path.exists():
        values = {
            "lineCoveragePercent": None,
            "branchCoveragePercent": None,
            "lineCovered": None,
            "lineMissed": None,
            "branchCovered": None,
            "branchMissed": None,
        }
        metric.update(
            confidence=NEEDS_MEASUREMENT,
            reason="JaCoCo XML artifact is missing.",
            values=values,
            valueConfidences=fill_confidences(values, NEEDS_MEASUREMENT),
        )
        return metric

    try:
        root = ElementTree.parse(path).getroot()
    except ElementTree.ParseError as exc:
        metric.update(confidence=NEEDS_REVIEW, reason=f"JaCoCo XML parsing failed: {exc}", values={}, valueConfidences={})
        return metric

    counters = {item.attrib.get("type"): item.attrib for item in root.findall("counter")}
    line = counters.get("LINE")
    branch = counters.get("BRANCH")
    if not line or not branch:
        metric.update(confidence=NEEDS_REVIEW, reason="JaCoCo LINE or BRANCH counter is missing.", values={}, valueConfidences={})
        return metric

    line_missed, line_covered = int(line["missed"]), int(line["covered"])
    branch_missed, branch_covered = int(branch["missed"]), int(branch["covered"])
    values = {
        "lineCoveragePercent": percent(line_covered, line_missed + line_covered),
        "branchCoveragePercent": percent(branch_covered, branch_missed + branch_covered),
        "lineCovered": line_covered,
        "lineMissed": line_missed,
        "branchCovered": branch_covered,
        "branchMissed": branch_missed,
    }
    metric.update(
        confidence=CONFIRMED,
        reason="Read from JaCoCo XML report-level LINE and BRANCH counters.",
        values=values,
        valueConfidences=fill_confidences(values, CONFIRMED),
    )
    return metric


def read_junit(pattern: str) -> dict[str, Any]:
    metric = base_metric("junit", pattern)
    paths = [Path(path) for path in glob.glob(pattern)]
    if not paths:
        values = {"tests": None, "failures": None, "errors": None, "skipped": None, "suites": 0}
        metric.update(
            confidence=NEEDS_MEASUREMENT,
            reason="JUnit TEST XML artifacts are missing.",
            values=values,
            valueConfidences=fill_confidences(values, NEEDS_MEASUREMENT),
        )
        return metric

    suites: dict[str, tuple[float, Path, dict[str, int]]] = {}
    for path in paths:
        try:
            root = ElementTree.parse(path).getroot()
        except ElementTree.ParseError:
            continue
        if root.tag != "testsuite":
            continue
        name = root.attrib.get("name") or path.name
        values = {
            "tests": int(root.attrib.get("tests", "0")),
            "failures": int(root.attrib.get("failures", "0")),
            "errors": int(root.attrib.get("errors", "0")),
            "skipped": int(root.attrib.get("skipped", "0")),
        }
        current = suites.get(name)
        mtime = path.stat().st_mtime
        if current is None or mtime > current[0]:
            suites[name] = (mtime, path, values)

    if not suites:
        metric.update(confidence=NEEDS_REVIEW, reason="No parseable JUnit testsuite XML files found.", values={}, valueConfidences={})
        return metric

    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    for _, _, values in suites.values():
        for key in totals:
            totals[key] += values[key]
    totals["suites"] = len(suites)

    confidence = CONFIRMED
    reason = "Read from latest JUnit TEST XML per testsuite."
    if totals["failures"] > 0 or totals["errors"] > 0:
        confidence = NOT_RECOMMENDED
        reason = "JUnit XML reports failures or errors."

    metric.update(confidence=confidence, reason=reason, values=totals, valueConfidences=fill_confidences(totals, confidence))
    return metric


def read_assignment_scale_report(path: Path) -> dict[str, Any]:
    metric = base_metric("assignment-scale", str(path))
    values: dict[str, Any] = {
        "scenarioCount": None,
        "scenarios": {},
        "targetRps": None,
        "k6Version": None,
        "gitCommitSha": None,
        "gitDirty": None,
        "httpFailureRateMax": None,
        "checkSuccessRateMin": None,
        "droppedIterationsMax": None,
        "productionAccessAllowed": None,
    }
    if not path.exists():
        metric.update(
            confidence=NEEDS_MEASUREMENT,
            reason="Assignment scale result JSON artifact is missing.",
            values=values,
            valueConfidences=fill_confidences(values, NEEDS_MEASUREMENT),
        )
        return metric

    try:
        payload = load_json(path)
    except ValueError as exc:
        metric.update(confidence=NEEDS_REVIEW, reason=str(exc), values=values, valueConfidences=fill_confidences(values, NEEDS_REVIEW))
        return metric

    scenarios = payload.get("scenarios")
    if not isinstance(scenarios, list) or not scenarios:
        metric.update(
            confidence=NEEDS_REVIEW,
            reason="Assignment scale result JSON has no scenarios.",
            values=values,
            valueConfidences=fill_confidences(values, NEEDS_REVIEW),
        )
        return metric

    scenario_values: dict[str, dict[str, Any]] = {}
    context_reasons: list[str] = []
    dropped_maxes: list[float] = []
    failure_rates: list[float] = []
    check_rates: list[float] = []
    target_rps_values: set[str] = set()
    k6_versions: set[str] = set()
    git_shas: set[str] = set()
    git_dirty_values: set[str] = set()

    for scenario in scenarios:
        name = str(scenario.get("name") or "unknown")
        fixture = scenario.get("fixture", {})
        k6 = scenario.get("k6", {})
        context = k6.get("context", {})
        summary = k6.get("summary", {})
        scenario_entry = {
            "assignments": get_path(fixture, ("counts", "assignments")),
            "businessP95Ms": stat(summary, "business_p95_ms"),
            "businessP99Ms": stat(summary, "business_p99_ms"),
            "assignmentListP95Ms": stat(summary, "assignment_list_p95_ms"),
            "assignmentListP99Ms": stat(summary, "assignment_list_p99_ms"),
            "assignmentDetailP95Ms": stat(summary, "assignment_detail_p95_ms"),
            "assignmentDetailP99Ms": stat(summary, "assignment_detail_p99_ms"),
            "httpFailureRate": stat(summary, "http_failure_rate"),
            "checkSuccessRate": stat(summary, "check_success_rate"),
            "throughputRps": stat(summary, "business_success_throughput"),
            "iterations": stat(summary, "iterations"),
            "droppedIterations": stat(summary, "dropped_iterations"),
            "fixtureFingerprint": context.get("fixtureFingerprint"),
        }
        scenario_values[name] = scenario_entry
        context_reasons.extend(f"{name}: {reason}" for reason in k6_context_reasons(context))
        append_number(dropped_maxes, get_path(summary, ("dropped_iterations", "max")))
        append_number(failure_rates, get_path(summary, ("http_failure_rate", "max")))
        append_number(check_rates, get_path(summary, ("check_success_rate", "min")))
        add_present(target_rps_values, context.get("targetRps"))
        add_present(k6_versions, context.get("k6Version"))
        add_present(git_shas, context.get("gitCommitSha"))
        add_present(git_dirty_values, context.get("gitDirty"))

    values.update(
        {
            "scenarioCount": len(scenario_values),
            "scenarios": scenario_values,
            "targetRps": single_or_mixed(target_rps_values),
            "k6Version": single_or_mixed(k6_versions),
            "gitCommitSha": single_or_mixed(git_shas),
            "gitDirty": single_or_mixed(git_dirty_values),
            "httpFailureRateMax": max(failure_rates) if failure_rates else None,
            "checkSuccessRateMin": min(check_rates) if check_rates else None,
            "droppedIterationsMax": max(dropped_maxes) if dropped_maxes else None,
            "productionAccessAllowed": payload.get("safety", {}).get("productionAccessAllowed"),
        }
    )

    missing_required = [
        key
        for key in ("scenarioCount", "targetRps", "k6Version", "gitCommitSha", "gitDirty", "httpFailureRateMax", "checkSuccessRateMin", "droppedIterationsMax")
        if values.get(key) is None
    ]
    missing_scenario_metrics = [
        f"{scenario_name}.{key}"
        for scenario_name, scenario_value in scenario_values.items()
        for key in ("businessP95Ms", "businessP99Ms", "httpFailureRate", "checkSuccessRate", "droppedIterations")
        if scenario_value.get(key) is None
    ]
    safety = payload.get("safety", {})
    unsafe_reasons = []
    if payload.get("scope") != "local-only assignment read scale fixture":
        unsafe_reasons.append("scope must be local-only assignment read scale fixture")
    if safety.get("remoteTargetsAllowed") is not False:
        unsafe_reasons.append("remoteTargetsAllowed must be false")
    if safety.get("productionAccessAllowed") is not False:
        unsafe_reasons.append("productionAccessAllowed must be false")
    if values["gitDirty"] != "false":
        unsafe_reasons.append("gitDirty must be false")
    unsafe_reasons.extend(context_reasons)

    if unsafe_reasons:
        confidence = NOT_RECOMMENDED
        reason = "Assignment scale report is not local-safe: " + ", ".join(unsafe_reasons)
    elif missing_required or missing_scenario_metrics:
        confidence = NEEDS_REVIEW
        reason = "Assignment scale report is missing metrics: " + ", ".join(missing_required + missing_scenario_metrics)
    elif values["droppedIterationsMax"] != 0 or values["httpFailureRateMax"] != 0 or values["checkSuccessRateMin"] != 1:
        confidence = NOT_RECOMMENDED
        reason = "Assignment scale report has failed checks, HTTP failures, or dropped iterations."
    else:
        confidence = CONFIRMED
        reason = "Read from completed local assignment scale result JSON."

    if confidence == NOT_RECOMMENDED:
        value_confidences = fill_confidences(values, NOT_RECOMMENDED)
    else:
        value_confidences = confidence_by_presence(values, CONFIRMED, NEEDS_MEASUREMENT)
    metric.update(confidence=confidence, reason=reason, values=values, valueConfidences=value_confidences)
    return metric


def read_ci_summary(path: Path) -> dict[str, Any]:
    metric = base_metric("ci", str(path))
    values = {
        "ciTotalSeconds": None,
        "ciTestStepSeconds": None,
        "ciBuildStepSeconds": None,
        "dockerBuildPushSeconds": None,
        "ciFailureRate": None,
        "deployFailureRate": None,
        "ciRunCount": None,
        "deployRunCount": None,
    }
    if not path.exists():
        metric.update(
            confidence=NEEDS_MEASUREMENT,
            reason="CI metrics JSON artifact is missing.",
            values=values,
            valueConfidences=fill_confidences(values, NEEDS_MEASUREMENT),
        )
        return metric

    try:
        payload = load_json(path)
    except ValueError as exc:
        metric.update(confidence=NEEDS_REVIEW, reason=str(exc), values=values, valueConfidences=fill_confidences(values, NEEDS_REVIEW))
        return metric

    workflows = payload.get("workflows", {})
    ci = workflows.get("ci_test", {})
    deploy = workflows.get("deploy_tagged_report_release", {})
    values.update(
        {
            "ciTotalSeconds": stats_median(ci.get("duration", {}).get("stats")),
            "ciTestStepSeconds": stats_median(ci.get("steps", {}).get("run_tests", {}).get("stats")),
            "ciBuildStepSeconds": stats_median(deploy.get("steps", {}).get("boot_jar", {}).get("stats")),
            "dockerBuildPushSeconds": stats_median(deploy.get("steps", {}).get("docker_build_push", {}).get("stats")),
            "ciFailureRate": ci.get("failure_rate"),
            "deployFailureRate": deploy.get("failure_rate"),
            "ciRunCount": ci.get("run_count"),
            "deployRunCount": deploy.get("run_count"),
        }
    )
    missing = [key for key, value in values.items() if value is None and key not in ("deployFailureRate",)]
    confidence = CONFIRMED
    reason = "Read from committed CI metrics JSON."
    if missing:
        confidence = NEEDS_REVIEW
        reason = "CI metrics JSON is missing fields: " + ", ".join(missing)
    if (values.get("ciRunCount") is not None and values["ciRunCount"] < 3) or (
        values.get("deployRunCount") is not None and values["deployRunCount"] < 3
    ):
        confidence = NOT_RECOMMENDED
        reason = "CI or deploy metrics have fewer than 3 runs."

    metric.update(confidence=confidence, reason=reason, values=values, valueConfidences=ci_value_confidences(values))
    return metric


def resume_sentences(metrics: dict[str, Any]) -> list[dict[str, str]]:
    tests = metrics["tests"]
    coverage = metrics["coverage"]
    k6 = metrics["k6"]
    ci = metrics["ci"]
    all_sources_confirmed = all(metric["confidence"] == CONFIRMED for metric in (tests, coverage, k6, ci))
    documented_sources = ["테스트", "커버리지"]
    if k6["confidence"] == CONFIRMED:
        documented_sources.append("부하 테스트")
    if ci["confidence"] == CONFIRMED:
        documented_sources.append("CI")
    sentences = [
        {
            "confidence": CONFIRMED,
            "text": f"{'·'.join(documented_sources)} 지표를 docs/resume-metrics.md로 문서화해 이력서 수치의 재현 가능성을 관리",
        },
        {
            "confidence": CONFIRMED if all_sources_confirmed else NEEDS_REVIEW,
            "text": "JaCoCo, JUnit XML, assignment scale report, GitHub Actions metrics를 기반으로 성능·품질 지표를 자동 집계",
        },
    ]

    if tests["confidence"] == CONFIRMED and coverage["confidence"] == CONFIRMED:
        sentences.append(
            {
                "confidence": CONFIRMED,
                "text": (
                    f"JUnit {tests['values']['tests']}개 테스트와 JaCoCo line {fmt_percent(coverage['values']['lineCoveragePercent'])}, "
                    f"branch {fmt_percent(coverage['values']['branchCoveragePercent'])}를 로컬 리포트에서 자동 집계"
                ),
            }
        )
    else:
        sentences.append(
            {
                "confidence": NEEDS_REVIEW,
                "text": "JUnit [확인 필요]개 테스트와 JaCoCo line [확인 필요], branch [확인 필요]를 로컬 리포트에서 자동 집계",
            }
        )

    if k6["confidence"] == CONFIRMED:
        sentences.append(
            {
                "confidence": CONFIRMED,
                "text": f"로컬 고정 부하 회귀 기준에서 {format_assignment_scale_sentence(k6)}",
            }
        )
    else:
        sentences.append(
            {
                "confidence": NEEDS_REVIEW,
                "text": "k6 고정 부하 회귀 기준에서 P50/P90/P95/P99와 HTTP failure [확인 필요]를 관리",
            }
        )

    if ci["confidence"] == CONFIRMED:
        sentences.append(
            {
                "confidence": CONFIRMED,
                "text": f"GitHub Actions CI median {fmt_seconds(ci['values']['ciTotalSeconds'])} 기준으로 PR 검증 시간을 관리",
            }
        )
    else:
        sentences.append(
            {
                "confidence": NEEDS_MEASUREMENT if ci["confidence"] == NEEDS_MEASUREMENT else NEEDS_REVIEW,
                "text": "GitHub Actions CI median [확인 필요] 기준으로 PR 검증 시간을 관리",
            }
        )
    return sentences


def render_markdown(payload: dict[str, Any]) -> str:
    metrics = payload["metrics"]
    lines = [
        "# Resume Metrics",
        "",
        "이력서에 사용할 수 있는 수치와 근거 artifact를 한 문서로 모읍니다.",
        "",
        f"> {payload['disclaimer']}",
        "",
        "## Source Policy",
        "",
        "- 부하 테스트를 실행하지 않습니다.",
        "- GitHub Actions workflow를 실행하지 않습니다.",
        "- GitHub API, AWS, 운영 DB, 운영 API, 운영 로그에 접근하지 않습니다.",
        "- 기존 artifact와 local report만 읽습니다.",
        "",
        "## Summary",
        "",
        "| Area | Confidence | Source | Reason |",
        "| :--- | :--- | :--- | :--- |",
    ]
    for key in ("tests", "coverage", "k6", "ci"):
        metric = metrics[key]
        lines.append(f"| {key} | {metric['confidence']} | `{metric['source']}` | {metric['reason']} |")

    lines.extend(["", "## Test And Coverage", "", "| Metric | Value | Confidence |", "| :--- | ---: | :--- |"])
    lines.extend(
        [
            row("JUnit tests", metrics["tests"], "tests"),
            row("JUnit failures", metrics["tests"], "failures"),
            row("JUnit errors", metrics["tests"], "errors"),
            row("JUnit skipped", metrics["tests"], "skipped"),
            row("JaCoCo line coverage", metrics["coverage"], "lineCoveragePercent", formatter=fmt_percent),
            row("JaCoCo branch coverage", metrics["coverage"], "branchCoveragePercent", formatter=fmt_percent),
        ]
    )

    lines.extend(
        [
            "",
            "## Assignment Scale",
            "",
            "| Scenario | Fixture | Target RPS | P95 | P99 | HTTP failed | Checks | Throughput | Iterations | Dropped | Confidence |",
            "| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | :--- |",
        ]
    )
    scenario_values = metrics["k6"].get("values", {}).get("scenarios", {})
    if scenario_values:
        for name in sorted(scenario_values):
            values = scenario_values[name]
            lines.append(
                "| "
                + " | ".join(
                    [
                        name,
                        fmt_number(values.get("assignments")),
                        fmt_number(metrics["k6"]["values"].get("targetRps")),
                        fmt_ms(values.get("businessP95Ms")),
                        fmt_ms(values.get("businessP99Ms")),
                        fmt_percent(values.get("httpFailureRate")),
                        fmt_percent(values.get("checkSuccessRate")),
                        f"{fmt_number(values.get('throughputRps'))} req/s",
                        fmt_number(values.get("iterations")),
                        fmt_number(values.get("droppedIterations")),
                        metrics["k6"]["confidence"],
                    ]
                )
                + " |"
            )
    else:
        lines.append(f"| [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] | {metrics['k6']['confidence']} |")

    lines.extend(["", "## CI Metrics", "", "| Metric | Value | Confidence |", "| :--- | ---: | :--- |"])
    for label, key, formatter in [
        ("CI total median", "ciTotalSeconds", fmt_seconds),
        ("CI test step median", "ciTestStepSeconds", fmt_seconds),
        ("Build step median", "ciBuildStepSeconds", fmt_seconds),
        ("Docker build/push median", "dockerBuildPushSeconds", fmt_seconds),
        ("CI failure rate", "ciFailureRate", fmt_percent),
        ("Deploy failure rate", "deployFailureRate", fmt_percent),
    ]:
        lines.append(row(label, metrics["ci"], key, formatter=formatter))

    lines.extend(["", "## Resume Sentence Candidates", ""])
    for sentence in [item for item in payload["resumeSentences"] if item["confidence"] == CONFIRMED]:
        lines.append(f"- `{sentence['confidence']}` {sentence['text']}")
    not_ready = [item for item in payload["resumeSentences"] if item["confidence"] != CONFIRMED]
    if not_ready:
        lines.extend(["", "## Not Resume-Ready", ""])
        for sentence in not_ready:
            lines.append(f"- `{sentence['confidence']}` {sentence['text']}")
    lines.extend(
        [
            "",
            "## Usage Rule",
            "",
            "- `확인 완료` 수치만 이력서 숫자로 사용합니다.",
            "- `측정 필요`, `확인 필요`, `사용 비추천` 수치는 문장 안에서 `[확인 필요]`로 남깁니다.",
            "- 확인된 수치와 확인되지 않은 수치를 한 주장 안에 섞지 않습니다.",
            "",
        ]
    )
    return "\n".join(lines)


def row(label: str, metric: dict[str, Any], key: str, formatter: Any = None) -> str:
    value = metric.get("values", {}).get(key)
    rendered = "[확인 필요]" if value is None else (formatter(value) if formatter else str(value))
    confidence = metric.get("valueConfidences", {}).get(key)
    if confidence is None:
        confidence = metric["confidence"] if value is not None else NEEDS_MEASUREMENT
    return f"| {label} | {rendered} | {confidence} |"


def validate_existing_outputs(args: argparse.Namespace) -> None:
    schema_path = Path(args.schema)
    if not schema_path.exists():
        raise SystemExit(f"Schema file is missing: {schema_path}")
    load_json(schema_path)
    candidates = list(
        dict.fromkeys(
            [
                Path(args.example_json),
                Path(COMMITTED_SNAPSHOT),
                Path(args.out_json),
            ]
        )
    )
    payloads: dict[Path, dict[str, Any]] = {}
    found = False
    for path in candidates:
        if path.exists():
            payload = load_json(path)
            validate_payload(payload)
            payloads[path] = payload
            found = True
    if not found:
        raise SystemExit("No resume metrics JSON file found to validate.")

    committed_payload = payloads.get(Path(COMMITTED_SNAPSHOT))
    markdown_candidates = list(dict.fromkeys([Path(CURATED_MARKDOWN), Path(args.out_md)]))
    for path in markdown_candidates:
        if path.exists():
            payload = payloads.get(Path(args.out_json)) if path == Path(args.out_md) else committed_payload
            validate_generated_text(path.read_text(encoding="utf-8"), payload or committed_payload)


def validate_payload(payload: dict[str, Any]) -> None:
    required_paths = [
        ("schemaVersion",),
        ("generatedAt",),
        ("sourcePolicy", "networkAccess"),
        ("sourcePolicy", "githubApiAccess"),
        ("sourcePolicy", "loadTestExecuted"),
        ("sourcePolicy", "workflowExecuted"),
        ("sourcePolicy", "productionAccess"),
        ("disclaimer",),
        ("metrics", "tests", "confidence"),
        ("metrics", "coverage", "confidence"),
        ("metrics", "k6", "confidence"),
        ("metrics", "ci", "confidence"),
        ("resumeSentences",),
    ]
    for path in required_paths:
        if get_path(payload, path) is None:
            raise SystemExit(f"Missing required resume metrics field: {'.'.join(path)}")
    for key in ("networkAccess", "githubApiAccess", "loadTestExecuted", "workflowExecuted", "productionAccess"):
        if payload["sourcePolicy"].get(key) is not False:
            raise SystemExit(f"sourcePolicy.{key} must be false")
    for name, metric in payload["metrics"].items():
        if metric.get("confidence") not in ALLOWED_CONFIDENCE:
            raise SystemExit(f"Invalid confidence for {name}: {metric.get('confidence')}")
        if "source" not in metric or "values" not in metric or "valueConfidences" not in metric:
            raise SystemExit(f"Metric {name} must include source, values, and valueConfidences")
        for key in metric["values"]:
            if key not in metric["valueConfidences"]:
                raise SystemExit(f"Metric {name}.{key} must include value confidence")
        for key, confidence in metric["valueConfidences"].items():
            if confidence not in ALLOWED_CONFIDENCE:
                raise SystemExit(f"Invalid confidence for {name}.{key}: {confidence}")
    if not isinstance(payload["resumeSentences"], list) or not payload["resumeSentences"]:
        raise SystemExit("resumeSentences must be a non-empty list")
    for sentence in payload["resumeSentences"]:
        if sentence.get("confidence") not in ALLOWED_CONFIDENCE:
            raise SystemExit(f"Invalid sentence confidence: {sentence.get('confidence')}")
        if not sentence.get("text"):
            raise SystemExit("Resume sentence text is required")


def validate_generated_text(text: str, payload: dict[str, Any] | None) -> None:
    if DISCLAIMER not in text:
        raise SystemExit("Generated Markdown must include the fixed-load regression disclaimer.")
    sentence_texts = []
    if payload:
        sentence_texts = [item.get("text", "") for item in payload.get("resumeSentences", [])]
    for sentence in sentence_texts:
        lower = sentence.lower()
        for pattern in FORBIDDEN_CLAIM_PATTERNS:
            if pattern in lower:
                raise SystemExit(f"Forbidden resume claim wording found: {pattern}")


def base_metric(name: str, source: str) -> dict[str, Any]:
    return {"name": name, "source": source, "confidence": NEEDS_MEASUREMENT, "reason": "", "values": {}, "valueConfidences": {}}


def fill_confidences(values: dict[str, Any], confidence: str) -> dict[str, str]:
    return {key: confidence for key in values}


def confidence_by_presence(values: dict[str, Any], present: str, missing: str) -> dict[str, str]:
    return {key: present if value is not None else missing for key, value in values.items()}


def ci_value_confidences(values: dict[str, Any]) -> dict[str, str]:
    ci_keys = {"ciTotalSeconds", "ciTestStepSeconds", "ciFailureRate", "ciRunCount"}
    deploy_keys = {"ciBuildStepSeconds", "dockerBuildPushSeconds", "deployFailureRate", "deployRunCount"}
    ci_enough_runs = values.get("ciRunCount") is not None and values["ciRunCount"] >= 3
    deploy_enough_runs = values.get("deployRunCount") is not None and values["deployRunCount"] >= 3
    confidences: dict[str, str] = {}
    for key, value in values.items():
        if value is None:
            confidences[key] = NEEDS_REVIEW
        elif key in ci_keys:
            confidences[key] = CONFIRMED if ci_enough_runs else NOT_RECOMMENDED
        elif key in deploy_keys:
            confidences[key] = CONFIRMED if deploy_enough_runs else NOT_RECOMMENDED
        else:
            confidences[key] = CONFIRMED
    return confidences


def add_present(values: set[str], value: Any) -> None:
    if value is not None and str(value) != "":
        values.add(str(value))


def append_number(values: list[float], value: Any) -> None:
    if value is not None:
        values.append(float(value))


def single_or_mixed(values: set[str]) -> str | None:
    if not values:
        return None
    if len(values) == 1:
        return next(iter(values))
    return "mixed:" + ",".join(sorted(values))


def k6_context_reasons(context: dict[str, Any]) -> list[str]:
    reasons: list[str] = []
    host = normalize_host(context.get("baseUrlHost"))
    target_environment = normalize_host(context.get("targetEnvironment"))
    if host not in {"localhost", "127.0.0.1", "::1"}:
        reasons.append("baseUrlHost must be localhost or 127.0.0.1")
    if host in {"aandiclub.com", "api.aandiclub.com"}:
        reasons.append(f"baseUrlHost is blocked: {host}")
    if target_environment in {"prod", "production"}:
        reasons.append("targetEnvironment must not be production/prod")
    if target_environment != "local":
        reasons.append("targetEnvironment must be local")
    return reasons


def normalize_host(value: Any) -> str:
    return str(value or "").strip().lower().replace("[", "").replace("]", "")


def stat(summary: dict[str, Any], key: str) -> float | None:
    value = summary.get(key)
    if not isinstance(value, dict):
        return None
    median = value.get("median")
    return None if median is None else float(median)


def stats_median(stats: Any) -> float | None:
    if not isinstance(stats, dict):
        return None
    value = stats.get("median_seconds")
    return None if value is None else float(value)


def percent(numerator: int, denominator: int) -> float | None:
    if denominator <= 0:
        return None
    return round((numerator / denominator) * 100.0, 2)


def fmt_percent(value: Any) -> str:
    if value is None:
        return "[확인 필요]"
    number = float(value)
    if number <= 1.0:
        number *= 100.0
    return f"{number:.2f}%"


def format_assignment_scale_sentence(metric: dict[str, Any]) -> str:
    scenarios = metric.get("values", {}).get("scenarios", {})
    parts = []
    for name in sorted(scenarios):
        values = scenarios[name]
        assignments = values.get("assignments")
        label = f"{assignments}개 과제 fixture" if assignments is not None else name
        parts.append(f"{label} P95 {fmt_ms(values.get('businessP95Ms'))}, P99 {fmt_ms(values.get('businessP99Ms'))}")
    http_failure = fmt_percent(metric["values"].get("httpFailureRateMax"))
    dropped = fmt_number(metric["values"].get("droppedIterationsMax"))
    return f"{' / '.join(parts)}, HTTP failure {http_failure}, dropped iterations {dropped}을 관리"


def fmt_ms(value: Any) -> str:
    if value is None:
        return "[확인 필요]"
    return f"{float(value):.2f} ms"


def fmt_seconds(value: Any) -> str:
    if value is None:
        return "[확인 필요]"
    seconds = int(round(float(value)))
    minutes, remainder = divmod(seconds, 60)
    if minutes:
        return f"{minutes}m {remainder:02d}s"
    return f"{remainder}s"


def fmt_number(value: Any) -> str:
    if value is None:
        return "[확인 필요]"
    number = float(value)
    if number.is_integer():
        return str(int(number))
    return f"{number:.2f}"


def load_json(path: Path) -> dict[str, Any]:
    try:
        with path.open("r", encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError as exc:
        raise ValueError(f"JSON file is missing: {path}") from exc
    except json.JSONDecodeError as exc:
        raise ValueError(f"JSON parsing failed for {path}: {exc}") from exc


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def get_path(payload: dict[str, Any], path: tuple[str, ...]) -> Any:
    current: Any = payload
    for part in path:
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current


def utc_now() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


if __name__ == "__main__":
    raise SystemExit(main())
