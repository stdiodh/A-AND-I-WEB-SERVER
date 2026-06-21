import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("compare_results.py")


class CompareResultsTest(unittest.TestCase):
    def test_rejects_different_fixture_fingerprint(self):
        before = summary(fixture_fingerprint="a")
        after = summary(fixture_fingerprint="b")
        with tempfile.TemporaryDirectory() as tmp:
            before_path = write_json(tmp, "before.json", before)
            after_path = write_json(tmp, "after.json", after)
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(before_path), str(after_path)],
                check=False,
                capture_output=True,
                text=True,
            )
        self.assertEqual(result.returncode, 2)
        self.assertIn("context mismatch: fixtureFingerprint", result.stdout)

    def test_compares_when_conditions_match(self):
        before = summary(p95=100.0, rps=20.0)
        after = summary(p95=90.0, rps=25.0)
        with tempfile.TemporaryDirectory() as tmp:
            before_path = write_json(tmp, "before.json", before)
            after_path = write_json(tmp, "after.json", after)
            result = subprocess.run(
                [sys.executable, str(SCRIPT), str(before_path), str(after_path)],
                check=False,
                capture_output=True,
                text=True,
            )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        payload = json.loads(result.stdout)
        self.assertTrue(payload["comparable"])
        self.assertEqual(payload["metricDelta"]["p95"]["delta"], -10.0)
        self.assertEqual(payload["metricDelta"]["rps"]["delta"], 5.0)


def write_json(directory, name, payload):
    path = Path(directory) / name
    path.write_text(json.dumps(payload), encoding="utf-8")
    return path


def summary(fixture_fingerprint="fingerprint", p95=100.0, rps=20.0):
    context = {
        "scenario": "assignment-read",
        "fixtureFingerprint": fixture_fingerprint,
        "fixtureCounts": {"courses": "1", "assignments": "30", "enrollments": "100", "submissionStatuses": "60"},
        "jvmOptions": "unknown",
        "mongodbMode": "local-docker-compose",
        "cpu": "cpu",
        "memory": "memory",
        "k6Version": "k6 v0.52.0",
        "executor": "constant-vus",
        "vus": "1 VUs",
        "duration": "30s",
        "requestSleepSeconds": "0",
        "assignmentListRatio": "60",
        "assignmentDetailRatio": "40",
        "warmupCompleted": "true",
        "gitCommitSha": "abc123",
        "executed": True,
        "skipped": False,
        "businessRequestCount": 10,
        "thresholdFailed": False,
    }
    return {
        "context": context,
        "k6": {
            "metrics": {
                "api_request_duration_ms": {"values": {"med": 50.0, "p(95)": p95, "p(99)": p95 + 10.0}},
                "http_reqs": {"values": {"rate": rps}},
                "checks": {"values": {"fails": 0}},
            }
        },
    }


if __name__ == "__main__":
    unittest.main()
