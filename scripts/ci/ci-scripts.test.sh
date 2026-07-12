#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
classifier="${script_dir}/classify-backend-scope.sh"
whitespace_check="${script_dir}/check-changed-whitespace.sh"
test_root="$(mktemp -d)"
repository="${test_root}/repository"
assertion_count=0

cleanup() {
	rm -rf -- "${test_root}"
}
trap cleanup EXIT

fail() {
	echo "FAIL: $*" >&2
	exit 1
}

commit_all() {
	local message="$1"
	git -C "${repository}" add -A
	git -C "${repository}" commit -qm "${message}"
}

assert_classifier() {
	local name="$1"
	local expected_run_backend="$2"
	local expected_scope="$3"
	local base_sha="$4"
	local head_sha="$5"
	local event_name="$6"
	local expected_reason="$7"
	local case_repository="${8:-${repository}}"
	local output_file="${test_root}/${name}.output"
	local log_file="${test_root}/${name}.log"

	(
		cd "${case_repository}"
		GITHUB_OUTPUT="${output_file}" \
			GITHUB_EVENT_NAME="${event_name}" \
			BASE_SHA="${base_sha}" \
			HEAD_SHA="${head_sha}" \
			bash "${classifier}"
	) > "${log_file}" 2>&1 || {
		cat "${log_file}" >&2
		fail "${name}: classifier exited with failure"
	}

	local actual_run_backend
	local actual_scope
	local actual_reason
	local output_line_count
	actual_run_backend="$(sed -n 's/^run_backend=//p' "${output_file}" | tail -n 1)"
	actual_scope="$(sed -n 's/^scope=//p' "${output_file}" | tail -n 1)"
	actual_reason="$(sed -n 's/^reason=//p' "${output_file}" | tail -n 1)"
	output_line_count="$(wc -l < "${output_file}" | tr -d '[:space:]')"

	[[ "${output_line_count}" == "3" ]] || fail "${name}: expected exactly 3 output lines, got ${output_line_count}"
	[[ "${actual_run_backend}" == "${expected_run_backend}" ]] ||
		fail "${name}: expected run_backend=${expected_run_backend}, got ${actual_run_backend}"
	[[ "${actual_scope}" == "${expected_scope}" ]] ||
		fail "${name}: expected scope=${expected_scope}, got ${actual_scope}"
	[[ "${actual_reason}" == "${expected_reason}" ]] ||
		fail "${name}: expected reason=${expected_reason}, got ${actual_reason}"
	assertion_count=$((assertion_count + 1))
}

assert_whitespace_passes() {
	local name="$1"
	local base_sha="$2"
	local head_sha="$3"
	local event_name="$4"
	local log_file="${test_root}/${name}.log"

	(
		cd "${repository}"
		GITHUB_EVENT_NAME="${event_name}" BASE_SHA="${base_sha}" HEAD_SHA="${head_sha}" bash "${whitespace_check}"
	) > "${log_file}" 2>&1 || {
		cat "${log_file}" >&2
		fail "${name}: expected whitespace validation to pass"
	}
	assertion_count=$((assertion_count + 1))
}

assert_whitespace_fails() {
	local name="$1"
	local base_sha="$2"
	local head_sha="$3"
	local event_name="$4"
	local log_file="${test_root}/${name}.log"

	if (
		cd "${repository}"
		GITHUB_EVENT_NAME="${event_name}" BASE_SHA="${base_sha}" HEAD_SHA="${head_sha}" bash "${whitespace_check}"
	) > "${log_file}" 2>&1; then
		cat "${log_file}" >&2
		fail "${name}: expected whitespace validation to fail"
	fi
	assertion_count=$((assertion_count + 1))
}

git init -q "${repository}"
git -C "${repository}" config user.email "ci@example.invalid"
git -C "${repository}" config user.name "CI Script Test"
git -C "${repository}" config commit.gpgsign false

mkdir -p "${repository}/src/main/kotlin"
printf 'fun main() = Unit\n' > "${repository}/src/main/kotlin/App.kt"
commit_all "initial code"
initial_sha="$(git -C "${repository}" rev-parse HEAD)"

printf '# Project\n' > "${repository}/README.md"
commit_all "add readme"
docs_first_sha="$(git -C "${repository}" rev-parse HEAD)"

mkdir -p "${repository}/docs"
printf '# Guide\n' > "${repository}/docs/guide.md"
commit_all "add guide"
docs_second_sha="$(git -C "${repository}" rev-parse HEAD)"

assert_classifier "docs-only" "false" "documentation-only" "${docs_first_sha}" "${docs_second_sha}" "push" "all changed files are documentation"
assert_classifier "multi-commit-docs" "false" "documentation-only" "${initial_sha}" "${docs_second_sha}" "push" "all changed files are documentation"
assert_classifier "manual-dispatch" "true" "full" "${docs_first_sha}" "${docs_second_sha}" "workflow_dispatch" "manual dispatch"
assert_classifier "zero-base" "true" "full" "0000000000000000000000000000000000000000" "${docs_second_sha}" "push" "missing base commit"
assert_classifier "empty-diff" "true" "full" "${docs_second_sha}" "${docs_second_sha}" "push" "no changed files detected"
assert_classifier "missing-base" "true" "full" "1111111111111111111111111111111111111111" "${docs_second_sha}" "push" "base commit fetch failed"
assert_classifier "missing-head" "true" "full" "${docs_first_sha}" "3333333333333333333333333333333333333333" "push" "missing head commit"

git -C "${repository}" mv "docs/guide.md" "docs/guide-renamed.md"
commit_all "rename guide"
docs_rename_sha="$(git -C "${repository}" rev-parse HEAD)"
assert_classifier "docs-to-docs-rename" "false" "documentation-only" "${docs_second_sha}" "${docs_rename_sha}" "pull_request" "all changed files are documentation"

printf 'fun main() = println("changed")\n' > "${repository}/src/main/kotlin/App.kt"
commit_all "change code"
code_sha="$(git -C "${repository}" rev-parse HEAD)"
assert_classifier "code-change" "true" "full" "${docs_rename_sha}" "${code_sha}" "push" "non-documentation change"

printf '# After code\n' > "${repository}/docs/after-code.md"
commit_all "add docs after code"
code_then_docs_sha="$(git -C "${repository}" rev-parse HEAD)"
assert_classifier "multi-commit-code" "true" "full" "${docs_rename_sha}" "${code_then_docs_sha}" "push" "non-documentation change"

bare_repository="${test_root}/remote.git"
shallow_repository="${test_root}/shallow"
git clone -q --bare "${repository}" "${bare_repository}"
git clone -q --depth=2 "file://${bare_repository}" "${shallow_repository}"
assert_classifier "shallow-multi-commit-code" "true" "full" "${docs_rename_sha}" "${code_then_docs_sha}" "push" "non-documentation change" "${shallow_repository}"

mkdir -p "${repository}/docs/source"
git -C "${repository}" mv "src/main/kotlin/App.kt" "docs/source/App.kt"
commit_all "move code into docs"
rename_sha="$(git -C "${repository}" rev-parse HEAD)"
assert_classifier "code-to-docs-rename" "true" "full" "${code_then_docs_sha}" "${rename_sha}" "pull_request" "non-documentation change"

assert_whitespace_passes "clean-range" "${initial_sha}" "${docs_second_sha}" "push"
assert_whitespace_passes "clean-manual-commit" "" "${docs_second_sha}" "workflow_dispatch"
assert_whitespace_passes "clean-new-ref" "0000000000000000000000000000000000000000" "${docs_second_sha}" "push"
assert_whitespace_fails "missing-base-metadata" "" "${docs_second_sha}" "push"

printf 'trailing whitespace \n' > "${repository}/docs/bad.md"
commit_all "add bad whitespace"
bad_whitespace_sha="$(git -C "${repository}" rev-parse HEAD)"
assert_whitespace_fails "bad-range" "${rename_sha}" "${bad_whitespace_sha}" "push"
assert_whitespace_fails "bad-manual-commit" "" "${bad_whitespace_sha}" "workflow_dispatch"

printf '# Clean after bad commit\n' > "${repository}/docs/clean-after-bad.md"
commit_all "add clean commit after bad whitespace"
bad_then_clean_sha="$(git -C "${repository}" rev-parse HEAD)"
assert_whitespace_fails "bad-multi-commit-range" "${rename_sha}" "${bad_then_clean_sha}" "push"
assert_whitespace_fails "missing-whitespace-base" "2222222222222222222222222222222222222222" "${bad_then_clean_sha}" "push"

echo "CI script tests passed: ${assertion_count} assertions"
