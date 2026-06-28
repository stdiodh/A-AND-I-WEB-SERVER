#!/usr/bin/env python3
import argparse
import json
import sys
from pathlib import Path


USABLE = "사용 가능"


def is_false(data, key):
    return data.get(key) is False


def validate_safety(data):
    safety = data.get("safety", {})
    errors = []

    for key in (
        "production_deploy_executed",
        "docker_push_executed",
        "aws_ecr_ssh_executed",
    ):
        if not is_false(safety, key):
            errors.append(f"safety.{key} must be false")

    for key in (
        "aws_credentials_used",
        "ecr_login_or_push_executed",
        "ec2_ssh_executed",
        "production_url_accessed",
    ):
        if key in safety and safety[key] is not False:
            errors.append(f"safety.{key} must be false")

    return errors


def validate_usable_metric(metric):
    errors = []
    name = metric.get("name", "<unknown>")

    before_success = metric.get("before_success_count", 0)
    after_success = metric.get("after_success_count", 0)
    before_failure = metric.get("before_failure_count", 0)
    after_failure = metric.get("after_failure_count", 0)
    improvement = metric.get("improvement_percent")

    if before_success < 5:
        errors.append(f"{name}: before_success_count must be at least 5")
    if after_success < 5:
        errors.append(f"{name}: after_success_count must be at least 5")
    if before_failure != 0:
        errors.append(f"{name}: before_failure_count must be 0")
    if after_failure != 0:
        errors.append(f"{name}: after_failure_count must be 0")
    if not isinstance(improvement, (int, float)) or improvement <= 0:
        errors.append(f"{name}: improvement_percent must be greater than 0")

    return errors


def validate_pending(data):
    errors = validate_safety(data)
    for metric in data.get("metrics", []):
        if metric.get("resume_usage") == USABLE:
            errors.append(
                f"{metric.get('name', '<unknown>')}: pending metrics cannot be marked usable"
            )
    return errors


def validate_completed(data):
    errors = validate_safety(data)

    if data.get("measurement_status") != "completed":
        errors.append("measurement_status must be completed")

    usable_metrics = [
        metric for metric in data.get("metrics", []) if metric.get("resume_usage") == USABLE
    ]
    if not usable_metrics:
        errors.append('at least one metric must have resume_usage == "사용 가능"')

    for metric in usable_metrics:
        errors.extend(validate_usable_metric(metric))

    return errors


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--allow-pending",
        action="store_true",
        help="Allow pending or blocked measurement files during local validation.",
    )
    parser.add_argument("metrics_path", type=Path)
    args = parser.parse_args()

    try:
        data = json.loads(args.metrics_path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        print(f"missing metrics file: {args.metrics_path}", file=sys.stderr)
        return 2
    except json.JSONDecodeError as exc:
        print(f"invalid JSON: {exc}", file=sys.stderr)
        return 2

    if args.allow_pending and data.get("measurement_status") != "completed":
        errors = validate_pending(data)
    else:
        errors = validate_completed(data)

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"validated {args.metrics_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
