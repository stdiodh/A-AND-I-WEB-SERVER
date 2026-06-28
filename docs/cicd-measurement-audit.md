# CI/CD Measurement Audit

## Web CI/CD Remeasurement Audit

Source of truth: `docs/metrics/web-cicd-remeasure.json`

Measurement status: `completed`

### Current Decision

The official Web CI/CD remeasurement completed in GitHub Actions run `28313842657`. Rows marked `사용 가능` in `docs/metrics/web-cicd-remeasure.json` can be used as bounded Web CI/CD improvement evidence.

The measurement workflow enforces completed metrics with at least one usable improvement before the generated JSON can pass validation. The committed JSON has completed before/after samples and passed `scripts/validate-web-cicd-metrics.py`.

### PR Cleanup Decision

PR #61 was an earlier GitHub Actions metadata collection attempt. PR #64 was an earlier CI/CD dry-run measurement attempt. Both are superseded by PR #65 because PR #65 carries the completed same-scope remeasurement policy, completed source JSON, and final usable metric gate.

PR #62 is a separate local assignment scale metrics draft, and PR #63 is a separate resume metrics generator draft. They are not merged into PR #65 because they are not required for the Web CI/CD completed remeasurement and would expand the merge scope.

### PR #64 Measurement Issue

The previous PR #64 measurement could not be treated as a clean before/after comparison because the measured CI total increased and the after side included a `build-jar` path that may not have existed in the same baseline scope. That mixed PR gate validation with build validation, making the result unsuitable for a same-scope claim.

### Scope Definitions

CI same-scope means only the checks equivalent to the original main CI scope:

- backend tests
- JaCoCo coverage verification
- performance asset validation
- shell, JavaScript, k6 inspect, Python syntax, and performance unittest checks
- resume metrics validate-only check

CI full-gate means the PR gate required by the candidate workflow. In this branch, `build-jar` is intentionally outside the same-scope PR gate and must be measured separately.

CD dry-run full path means the non-deploy path that includes building the application artifact and then building an image with `push: false`.

CD image build only means the image build segment is isolated from the full dry-run path. The legacy `Dockerfile` path still performs Gradle build work inside Docker, while `Dockerfile.runtime` expects a prebuilt JAR.

For the after path, the prebuilt JAR is prepared before the image-only measurement job. The image-only after job downloads that artifact and builds only `Dockerfile.runtime`; it does not run Gradle inside the measured runtime image path.

Cold cache means no cache reuse should be assumed. Warm cache means the workflow explicitly seeds and reuses BuildKit cache. This completed batch did not produce a usable warm-cache metric, so cache-hit improvement is not claimed.

### Cache Evidence

BuildKit cache is configured for the CD image measurement jobs in the workflow, and k6 cache hit state is logged for the candidate performance-assets path. The completed JSON marks `cd_image_build_warm_cache` as `사용 불가`, so documentation must not say the improvement came from a cache hit.

### Resume Use

The following completed metrics are usable as bounded resume or portfolio evidence:

- `ci_same_scope_total`: 140.0s -> 109.0s, 22.143% improvement, 5/5 runs
- `ci_full_gate_total`: 140.0s -> 109.0s, 22.143% improvement, 5/5 runs
- `backend_test`: 121.0s -> 109.0s, 9.917% improvement, 5/5 runs
- `performance_assets`: 22.0s -> 20.0s, 9.091% improvement, 5/5 runs
- `cd_dry_run_full_path`: 211.0s -> 82.0s, 61.137% improvement, 5/5 runs
- `cd_image_build_only`: 161.0s -> 29.0s, 81.988% improvement, 5/5 runs

Do not use:

- `build_jar`: median worsened from 52.0s to 63.0s
- `cd_image_build_warm_cache`: no completed before/after samples
- Any claim that the result shortened production deployment time
- Any claim that cache hits caused the image-build improvement
