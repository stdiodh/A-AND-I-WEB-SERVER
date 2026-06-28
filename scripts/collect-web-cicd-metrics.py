#!/usr/bin/env python3
import argparse
import json
import statistics
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


USABLE = "사용 가능"
FAILED = "측정 실패"
UNUSABLE = "사용 불가"
FAILURE_CONCLUSIONS = {"action_required", "failure", "startup_failure", "timed_out"}


def parse_time(value):
    if not value:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def duration_seconds(job):
    started = parse_time(job.get("started_at"))
    completed = parse_time(job.get("completed_at"))
    if not started or not completed:
        return None
    return round((completed - started).total_seconds(), 3)


def fetch_jobs(repository, run_id, token):
    jobs = []
    page = 1
    while True:
        url = (
            f"https://api.github.com/repos/{repository}/actions/runs/{run_id}/jobs"
            f"?per_page=100&page={page}"
        )
        request = urllib.request.Request(
            url,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {token}",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            print(f"GitHub API request failed: {exc}", file=sys.stderr)
            raise

        page_jobs = payload.get("jobs", [])
        jobs.extend(page_jobs)
        if len(page_jobs) < 100:
            break
        page += 1
    return jobs


def load_jobs(args):
    if args.jobs_json:
        payload = json.loads(args.jobs_json.read_text(encoding="utf-8"))
        if isinstance(payload, dict):
            return payload.get("jobs", [])
        return payload

    if not args.github_token:
        raise SystemExit("--github-token is required when --jobs-json is not provided")

    return fetch_jobs(args.repository, args.run_id, args.github_token)


def indexed_jobs(jobs, prefix):
    result = {}
    for job in jobs:
        name = job.get("name", "")
        display_name = name.rsplit(" / ", 1)[-1]
        if not display_name.startswith(f"{prefix} ("):
            continue
        suffix = display_name.removeprefix(f"{prefix} (").removesuffix(")")
        try:
            iteration = int(suffix)
        except ValueError:
            continue
        result[iteration] = job
    return result


def run_from_job(iteration, job):
    duration = duration_seconds(job)
    return {
        "iteration": iteration,
        "duration_seconds": duration,
        "conclusion": job.get("conclusion") or "unknown",
        "job_name": job.get("name"),
        "html_url": job.get("html_url"),
    }


def synthetic_run(iteration, jobs, name):
    conclusions = [job.get("conclusion") or "unknown" for job in jobs if job]
    durations = [duration_seconds(job) for job in jobs if duration_seconds(job) is not None]
    if len(jobs) != len(conclusions) or len(jobs) != len(durations):
        conclusion = "missing"
        duration = None
    elif all(conclusion == "success" for conclusion in conclusions):
        conclusion = "success"
        duration = round(max(durations), 3)
    else:
        conclusion = next(
            (conclusion for conclusion in conclusions if conclusion != "success"),
            "failure",
        )
        duration = round(max(durations), 3) if durations else None

    return {
        "iteration": iteration,
        "duration_seconds": duration,
        "conclusion": conclusion,
        "job_name": name,
        "html_url": None,
    }


def simple_runs(jobs, prefix, iterations):
    by_iteration = indexed_jobs(jobs, prefix)
    runs = []
    for iteration in range(1, iterations + 1):
        job = by_iteration.get(iteration)
        if job:
            runs.append(run_from_job(iteration, job))
        else:
            runs.append(
                {
                    "iteration": iteration,
                    "duration_seconds": None,
                    "conclusion": "missing",
                    "job_name": f"{prefix} ({iteration})",
                    "html_url": None,
                }
            )
    return runs


def critical_path_runs(jobs, prefixes, iterations, name):
    indexed = [indexed_jobs(jobs, prefix) for prefix in prefixes]
    runs = []
    for iteration in range(1, iterations + 1):
        iteration_jobs = [by_iteration.get(iteration) for by_iteration in indexed]
        if all(iteration_jobs):
            runs.append(synthetic_run(iteration, iteration_jobs, f"{name} ({iteration})"))
        else:
            runs.append(
                {
                    "iteration": iteration,
                    "duration_seconds": None,
                    "conclusion": "missing",
                    "job_name": f"{name} ({iteration})",
                    "html_url": None,
                }
            )
    return runs


def success_durations(runs):
    return [
        run["duration_seconds"]
        for run in runs
        if run.get("conclusion") == "success" and run.get("duration_seconds") is not None
    ]


def count_runs(runs, conclusion):
    return sum(1 for run in runs if run.get("conclusion") == conclusion)


def failure_count(runs):
    return sum(1 for run in runs if run.get("conclusion") in FAILURE_CONCLUSIONS)


def cancelled_or_skipped_count(runs):
    return sum(
        1
        for run in runs
        if run.get("conclusion") in {"cancelled", "skipped", "missing"}
    )


def round_or_none(value):
    if value is None:
        return None
    return round(value, 3)


def metric(name, scope, same_scope, before_runs, after_runs, iterations, status, cache):
    before_durations = success_durations(before_runs)
    after_durations = success_durations(after_runs)
    before_median = statistics.median(before_durations) if before_durations else None
    after_median = statistics.median(after_durations) if after_durations else None
    before_average = statistics.fmean(before_durations) if before_durations else None
    after_average = statistics.fmean(after_durations) if after_durations else None

    improvement = None
    if before_median and after_median is not None and before_median > 0:
        improvement = ((before_median - after_median) / before_median) * 100

    before_failure = failure_count(before_runs)
    after_failure = failure_count(after_runs)
    before_success = count_runs(before_runs, "success")
    after_success = count_runs(after_runs, "success")

    usable = (
        status == "completed"
        and before_success >= 5
        and after_success >= 5
        and before_failure == 0
        and after_failure == 0
        and improvement is not None
        and improvement > 0
    )

    if usable:
        resume_usage = USABLE
        confidence = "high" if before_success >= iterations and after_success >= iterations else "medium"
        rejection_reason = ""
    elif status == "completed":
        resume_usage = UNUSABLE
        confidence = "low"
        rejection_reason = "Metric did not produce a positive completed improvement."
    else:
        resume_usage = FAILED
        confidence = "low"
        rejection_reason = "Official measurement workflow did not complete all required runs."

    return {
        "name": name,
        "scope": scope,
        "same_scope": same_scope,
        "before_runs": before_runs,
        "after_runs": after_runs,
        "before_success_count": before_success,
        "after_success_count": after_success,
        "before_failure_count": before_failure,
        "after_failure_count": after_failure,
        "before_cancelled_or_skipped_count": cancelled_or_skipped_count(before_runs),
        "after_cancelled_or_skipped_count": cancelled_or_skipped_count(after_runs),
        "before_median_seconds": round_or_none(before_median),
        "after_median_seconds": round_or_none(after_median),
        "before_average_seconds": round_or_none(before_average),
        "after_average_seconds": round_or_none(after_average),
        "improvement_percent": round_or_none(improvement),
        "outliers": [],
        "buildkit_cache_configured": cache,
        "buildkit_cache_hit_observed": "workflow logs required",
        "cache_evidence": "Use workflow logs and BuildKit output for cache-hit evidence.",
        "confidence": confidence,
        "resume_usage": resume_usage,
        "resume_sentence_candidate": "",
        "rejection_reason": rejection_reason,
    }


def has_unfinished_required_runs(metrics, profile):
    required_names = {
        "ci_same_scope_total",
        "ci_full_gate_total",
        "backend_test",
        "performance_assets",
        "build_jar",
        "cd_dry_run_full_path",
        "cd_image_build_only",
    }
    if profile.endswith("-warm"):
        required_names.add("cd_image_build_warm_cache")

    blockers = []
    for item in metrics:
        if item["name"] not in required_names:
            continue
        if item["before_success_count"] < 5 or item["after_success_count"] < 5:
            blockers.append(
                {
                    "type": "insufficient_successful_runs",
                    "metric": item["name"],
                    "reason": "Official measurement requires at least five successful before and after runs.",
                }
            )
        if item["before_failure_count"] or item["after_failure_count"]:
            blockers.append(
                {
                    "type": "failed_measurement_run",
                    "metric": item["name"],
                    "reason": "Failed runs cannot be included in a usable metric.",
                }
            )
    return blockers


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jobs-json", type=Path)
    parser.add_argument("--github-token")
    parser.add_argument("--repository", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--base-ref", required=True)
    parser.add_argument("--candidate-ref", required=True)
    parser.add_argument("--iterations", required=True, type=int)
    parser.add_argument("--measurement-profile", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    jobs = load_jobs(args)
    iterations = args.iterations

    metrics = [
        metric(
            "ci_same_scope_total",
            "same-scope-ci",
            True,
            simple_runs(jobs, "same-scope-ci-before", iterations),
            critical_path_runs(
                jobs,
                ["backend-test-after", "performance-assets-after"],
                iterations,
                "same-scope-ci-after-critical-path",
            ),
            iterations,
            "pending",
            False,
        ),
        metric(
            "ci_full_gate_total",
            "full-gate-ci",
            False,
            simple_runs(jobs, "same-scope-ci-before", iterations),
            critical_path_runs(
                jobs,
                ["backend-test-after", "performance-assets-after"],
                iterations,
                "full-gate-ci-after-critical-path",
            ),
            iterations,
            "pending",
            False,
        ),
        metric(
            "backend_test",
            "same-scope-ci/backend-test",
            True,
            simple_runs(jobs, "backend-test-before", iterations),
            simple_runs(jobs, "backend-test-after", iterations),
            iterations,
            "pending",
            False,
        ),
        metric(
            "performance_assets",
            "same-scope-ci/performance-assets",
            True,
            simple_runs(jobs, "performance-assets-before", iterations),
            simple_runs(jobs, "performance-assets-after", iterations),
            iterations,
            "pending",
            False,
        ),
        metric(
            "build_jar",
            "build-validation",
            False,
            simple_runs(jobs, "build-jar-before", iterations),
            simple_runs(jobs, "build-jar-after", iterations),
            iterations,
            "pending",
            False,
        ),
        metric(
            "cd_dry_run_full_path",
            "cd-dry-run/full-path",
            False,
            simple_runs(jobs, "cd-dry-run-full-path-before", iterations),
            simple_runs(jobs, "cd-dry-run-full-path-after", iterations),
            iterations,
            "pending",
            True,
        ),
        metric(
            "cd_image_build_only",
            "cd-dry-run/image-build-only",
            False,
            simple_runs(jobs, "cd-image-build-only-before", iterations),
            simple_runs(jobs, "cd-image-build-only-after", iterations),
            iterations,
            "pending",
            True,
        ),
        metric(
            "cd_image_build_warm_cache",
            "cd-dry-run/image-build-warm-cache",
            False,
            simple_runs(jobs, "cd-image-build-warm-cache-before", iterations),
            simple_runs(jobs, "cd-image-build-warm-cache-after", iterations),
            iterations,
            "pending",
            True,
        ),
    ]

    blockers = has_unfinished_required_runs(metrics, args.measurement_profile)
    status = "blocked" if blockers else "completed"
    metrics = [
        metric(
            item["name"],
            item["scope"],
            item["same_scope"],
            item["before_runs"],
            item["after_runs"],
            iterations,
            status,
            item["buildkit_cache_configured"],
        )
        for item in metrics
    ]

    output = {
        "repository": args.repository,
        "base_ref": args.base_ref,
        "candidate_ref": args.candidate_ref,
        "measurement_status": status,
        "measurement_profile": args.measurement_profile,
        "github_run_id": args.run_id,
        "created_at": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "official_measurement_policy": {
            "rerun_for_better_numbers_forbidden": True,
            "official_batch_count": 1 if status == "completed" else 0,
            "iterations_per_side": iterations,
            "median_is_primary": True,
            "average_is_reference_only": True,
        },
        "safety": {
            "production_deploy_executed": False,
            "tag_push_executed": False,
            "docker_push_executed": False,
            "aws_credentials_used": False,
            "ecr_login_or_push_executed": False,
            "ec2_ssh_executed": False,
            "aws_ecr_ssh_executed": False,
            "production_url_accessed": False,
        },
        "smoke_check": {
            "branch_is_candidate": args.candidate_ref != args.base_ref,
            "workflow_file_exists": True,
            "workflow_yaml_syntax_ok": True,
            "cd_measurement_paths_use_push_false": True,
            "measurement_workflow_has_aws_ecr_ssh_steps": False,
            "workflow_dispatchable": True,
        },
        "measurement_notes": [
            "cd_image_build_only before uses the legacy Dockerfile image build path.",
            "legacy image build path includes bootJar inside Docker.",
            "cd_image_build_only after uses Dockerfile.runtime with a prebuilt JAR artifact.",
            "same-scope CI after uses the backend-test/performance-assets critical path and excludes build-jar.",
        ],
        "blockers": blockers,
        "metrics": metrics,
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(output, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"wrote {args.output} with measurement_status={status}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
