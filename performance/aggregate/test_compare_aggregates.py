import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("compare_aggregates.py")


class CompareAggregatesTest(unittest.TestCase):
    def test_accepts_comparable_inputs(self):
        before = aggregate(list_p95=(10.0, 10.0, 10.0), query_count=60)
        after = aggregate(list_p95=(6.0, 6.0, 6.0), git_sha="def456", query_count=2)

        result = run_compare(before, after)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        payload = json.loads(result.stdout)
        self.assertTrue(payload["accepted"])
        self.assertEqual(payload["beforeGitCommitSha"], "abc123")
        self.assertEqual(payload["afterGitCommitSha"], "def456")
        self.assertAlmostEqual(payload["queryReduction"]["reductionPercent"], 96.66666666666667)

    def test_rejects_fixture_mismatch(self):
        before = aggregate(fixture_fingerprint="before")
        after = aggregate(fixture_fingerprint="after")

        result = run_compare(before, after)

        self.assertEqual(result.returncode, 2)
        self.assertIn("context mismatch: fixtureFingerprint", result.stdout)

    def test_rejects_k6_version_mismatch(self):
        before = aggregate(k6_version="k6 v0.52.0")
        after = aggregate(k6_version="k6 v0.53.0")

        result = run_compare(before, after)

        self.assertEqual(result.returncode, 2)
        self.assertIn("context mismatch: k6Version", result.stdout)

    def test_rejects_dirty_run(self):
        before = aggregate(git_dirty="true")
        after = aggregate()

        result = run_compare(before, after)

        self.assertEqual(result.returncode, 2)
        self.assertIn("before gitDirty must be false", result.stdout)

    def test_rejects_warmup_mismatch(self):
        before = aggregate(warmup_completed="true")
        after = aggregate(warmup_completed="false")

        result = run_compare(before, after)

        self.assertEqual(result.returncode, 2)
        self.assertIn("context mismatch: warmupCompleted", result.stdout)

    def test_rejects_missing_metric(self):
        before = aggregate()
        after = aggregate()
        del after["summary"]["assignment_list_p95_ms"]

        result = run_compare(before, after)

        self.assertEqual(result.returncode, 2)
        self.assertIn("after missing metric: assignment_list_p95_ms", result.stdout)

    def test_reports_latency_improvement(self):
        before = aggregate(list_p95=(10.0, 11.0, 12.0))
        after = aggregate(list_p95=(5.0, 6.0, 7.0), git_sha="def456")

        result = run_compare(before, after)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        payload = json.loads(result.stdout)
        comparison = payload["latency"]["assignment_list_p95_ms"]
        self.assertEqual(comparison["interpretation"], "improvement")
        self.assertAlmostEqual(comparison["improvementPercent"], 45.45454545454545)
        self.assertEqual(comparison["deltaMs"], -5.0)

    def test_reports_latency_regression(self):
        before = aggregate(list_p95=(5.0, 6.0, 7.0))
        after = aggregate(list_p95=(10.0, 11.0, 12.0), git_sha="def456")

        result = run_compare(before, after)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        payload = json.loads(result.stdout)
        comparison = payload["latency"]["assignment_list_p95_ms"]
        self.assertEqual(comparison["interpretation"], "regression")
        self.assertLess(comparison["improvementPercent"], 0.0)

    def test_rejects_zero_baseline_guard(self):
        before = aggregate(list_p95=(0.0, 0.0, 0.0))
        after = aggregate()

        result = run_compare(before, after)

        self.assertEqual(result.returncode, 2)
        self.assertIn("before zero baseline guard failed: assignment_list_p95_ms", result.stdout)


def run_compare(before, after):
    with tempfile.TemporaryDirectory() as tmp:
        before_path = write_json(tmp, "before.json", before)
        after_path = write_json(tmp, "after.json", after)
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(before_path), str(after_path)],
            check=False,
            capture_output=True,
            text=True,
        )


def write_json(directory, name, payload):
    path = Path(directory) / name
    path.write_text(json.dumps(payload), encoding="utf-8")
    return path


def aggregate(
    *,
    fixture_fingerprint="fingerprint",
    k6_version="k6 v0.52.0",
    git_sha="abc123",
    git_dirty="false",
    warmup_completed="true",
    list_p95=(10.0, 10.0, 10.0),
    query_count=None,
):
    summary = {
        "assignment_list_p50_ms": stats([value - 2.0 for value in list_p95]),
        "assignment_list_p95_ms": stats(list_p95),
        "assignment_list_p99_ms": stats([value + 2.0 for value in list_p95]),
        "assignment_detail_p95_ms": stats([4.0, 4.0, 4.0]),
        "business_success_throughput": stats([100.0, 100.0, 100.0]),
    }
    runs = []
    for index, value in enumerate(list_p95, start=1):
        runs.append(
            {
                "file": f"run{index}.json",
                "assignment_list_p50_ms": value - 2.0,
                "assignment_list_p95_ms": value,
                "assignment_list_p99_ms": value + 2.0,
                "assignment_detail_p95_ms": 4.0,
                "business_success_throughput": 100.0,
            }
        )
    payload = {
        "accepted": True,
        "context": {
            "scenario": "assignment-read",
            "fixtureFingerprint": fixture_fingerprint,
            "fixtureCounts": {
                "courses": "1",
                "assignments": "30",
                "enrollments": "100",
                "submissionStatuses": "60",
            },
            "gitCommitSha": git_sha,
            "gitDirty": git_dirty,
            "k6Version": k6_version,
            "executor": "constant-arrival-rate",
            "targetRps": "100",
            "duration": "2m",
            "requestSleepSeconds": "1",
            "assignmentListRatio": "60",
            "assignmentDetailRatio": "40",
            "jvmOptions": "unknown",
            "mongodbMode": "local-standalone",
            "cpu": "Apple M5 Pro",
            "memory": "48 GiB",
            "warmupCompleted": warmup_completed,
            "loadModel": "arrival-rate",
            "preAllocatedVus": "30",
            "maxVus": "200",
        },
        "runs": runs,
        "summary": summary,
    }
    if query_count is not None:
        payload["queryCounts"] = {"childDocumentQueries": query_count}
    return payload


def stats(values):
    ordered = sorted(float(value) for value in values)
    return {
        "median": ordered[1],
        "min": ordered[0],
        "max": ordered[-1],
    }


if __name__ == "__main__":
    unittest.main()
