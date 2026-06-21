import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("summarize_runs.py")


class SummarizeRunsTest(unittest.TestCase):
    def test_outputs_three_run_median(self):
        summaries = [
            summary(list_p95=9.0, detail_p95=4.0, throughput=99.5),
            summary(list_p95=7.0, detail_p95=3.0, throughput=100.1),
            summary(list_p95=8.0, detail_p95=5.0, throughput=100.0),
        ]
        with tempfile.TemporaryDirectory() as tmp:
            paths = [write_json(tmp, f"run{i}.json", payload) for i, payload in enumerate(summaries, start=1)]
            result = subprocess.run(
                [sys.executable, str(SCRIPT), *map(str, paths)],
                check=False,
                capture_output=True,
                text=True,
            )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        payload = json.loads(result.stdout)
        self.assertTrue(payload["accepted"])
        self.assertEqual(payload["summary"]["assignment_list_p95_ms"]["median"], 8.0)
        self.assertEqual(payload["summary"]["assignment_detail_p95_ms"]["median"], 4.0)
        self.assertEqual(payload["summary"]["business_success_throughput"]["median"], 100.0)

    def test_rejects_dirty_run(self):
        summaries = [summary(), summary(), summary(git_dirty="true")]
        with tempfile.TemporaryDirectory() as tmp:
            paths = [write_json(tmp, f"run{i}.json", payload) for i, payload in enumerate(summaries, start=1)]
            result = subprocess.run(
                [sys.executable, str(SCRIPT), *map(str, paths)],
                check=False,
                capture_output=True,
                text=True,
            )
        self.assertEqual(result.returncode, 2)
        self.assertIn("gitDirty must be false", result.stdout)

    def test_treats_missing_dropped_iterations_as_zero(self):
        summaries = [summary(include_dropped=False), summary(include_dropped=False), summary(include_dropped=False)]
        with tempfile.TemporaryDirectory() as tmp:
            paths = [write_json(tmp, f"run{i}.json", payload) for i, payload in enumerate(summaries, start=1)]
            result = subprocess.run(
                [sys.executable, str(SCRIPT), *map(str, paths)],
                check=False,
                capture_output=True,
                text=True,
            )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        payload = json.loads(result.stdout)
        self.assertEqual(payload["summary"]["dropped_iterations"]["median"], 0.0)


def write_json(directory, name, payload):
    path = Path(directory) / name
    path.write_text(json.dumps(payload), encoding="utf-8")
    return path


def summary(list_p95=8.0, detail_p95=4.0, throughput=100.0, git_dirty="false", include_dropped=True):
    context = {
        "scenario": "assignment-read",
        "fixtureFingerprint": "fingerprint",
        "fixtureCounts": {"courses": "1", "assignments": "30", "enrollments": "100", "submissionStatuses": "60"},
        "gitCommitSha": "abc123",
        "gitDirty": git_dirty,
        "k6Version": "k6 v0.52.0",
        "executor": "constant-arrival-rate",
        "targetRps": "100",
        "duration": "2m",
        "requestSleepSeconds": "0",
        "assignmentListRatio": "60",
        "assignmentDetailRatio": "40",
        "executed": True,
        "skipped": False,
        "thresholdFailed": False,
        "warmupCompleted": "true",
        "businessRequestCount": 12000,
    }
    metrics = {
        "assignment_list_duration": {"values": {"med": 4.0, "p(95)": list_p95, "p(99)": list_p95 + 2.0}},
        "assignment_detail_duration": {"values": {"med": 2.0, "p(95)": detail_p95, "p(99)": detail_p95 + 1.0}},
        "http_reqs": {"values": {"rate": 100.0}},
        "business_success_count": {"values": {"rate": throughput}},
        "assignment_list_success_count": {"values": {"rate": 60.0}},
        "assignment_detail_success_count": {"values": {"rate": 40.0}},
        "http_req_failed": {"values": {"rate": 0.0}},
        "checks": {"values": {"rate": 1.0, "fails": 0}},
        "private_testcase_guard_rate": {"values": {"rate": 1.0}},
        "auth_error_count": {"values": {"count": 0}},
        "server_error_count": {"values": {"count": 0}},
    }
    if include_dropped:
        metrics["dropped_iterations"] = {"values": {"count": 0}}
    return {
        "context": context,
        "k6": {"metrics": metrics},
    }


if __name__ == "__main__":
    unittest.main()
