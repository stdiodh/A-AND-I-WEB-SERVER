import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("generate_resume_metrics.py").resolve()
REPOSITORY_ROOT = SCRIPT.parents[2]


class ResumeMetricsGeneratorCliTest(unittest.TestCase):
    def test_default_outputs_do_not_overwrite_curated_docs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            workspace = Path(temp_dir)
            curated_markdown = workspace / "docs/resume-metrics.md"
            committed_snapshot = workspace / "docs/metrics/resume-metrics.json"
            curated_markdown.parent.mkdir(parents=True)
            committed_snapshot.parent.mkdir(parents=True)
            curated_markdown.write_text("curated resume evidence\n", encoding="utf-8")
            committed_snapshot.write_text('{"snapshot": true}\n', encoding="utf-8")

            subprocess.run(
                [sys.executable, str(SCRIPT)],
                cwd=workspace,
                check=True,
                capture_output=True,
                text=True,
            )

            self.assertEqual(curated_markdown.read_text(encoding="utf-8"), "curated resume evidence\n")
            self.assertEqual(committed_snapshot.read_text(encoding="utf-8"), '{"snapshot": true}\n')
            self.assertTrue((workspace / "build/reports/resume-metrics/resume-metrics.md").is_file())
            self.assertTrue((workspace / "build/reports/resume-metrics/resume-metrics.json").is_file())

    def test_validate_only_rejects_invalid_committed_snapshot(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            workspace = Path(temp_dir)
            copy_validation_inputs(workspace)
            (workspace / "docs/metrics/resume-metrics.json").write_text("{\n", encoding="utf-8")

            result = run_generator(workspace, "--validate-only")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("JSON parsing failed", result.stderr)

    def test_validate_only_rejects_curated_markdown_without_disclaimer(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            workspace = Path(temp_dir)
            copy_validation_inputs(workspace)
            (workspace / "docs/resume-metrics.md").write_text("# Resume Metrics\n", encoding="utf-8")

            result = run_generator(workspace, "--validate-only")

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("fixed-load regression disclaimer", result.stderr)


def copy_validation_inputs(workspace: Path) -> None:
    for relative_path in (
        "scripts/resume/resume_metrics_schema.json",
        "docs/metrics/resume-metrics.example.json",
        "docs/metrics/resume-metrics.json",
        "docs/resume-metrics.md",
    ):
        source = REPOSITORY_ROOT / relative_path
        destination = workspace / relative_path
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)


def run_generator(workspace: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(SCRIPT), *arguments],
        cwd=workspace,
        check=False,
        capture_output=True,
        text=True,
    )


if __name__ == "__main__":
    unittest.main()
