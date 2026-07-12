#!/usr/bin/env bash

set -euo pipefail

event_name="${GITHUB_EVENT_NAME:-}"
base_sha="${BASE_SHA:-}"
head_sha="${HEAD_SHA:-${GITHUB_SHA:-HEAD}}"

write_result() {
	local run_backend="$1"
	local scope="$2"
	local reason="$3"

	if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
		{
			echo "run_backend=${run_backend}"
			echo "scope=${scope}"
			echo "reason=${reason}"
		} >> "${GITHUB_OUTPUT}"
	else
		echo "run_backend=${run_backend}"
		echo "scope=${scope}"
		echo "reason=${reason}"
	fi

	echo "Backend validation scope: ${scope} (${reason})"
}

run_full_validation() {
	write_result "true" "full" "$1"
	exit 0
}

if [[ "${event_name}" == "workflow_dispatch" ]]; then
	run_full_validation "manual dispatch"
fi

if [[ -z "${base_sha}" || "${base_sha}" =~ ^0+$ ]]; then
	run_full_validation "missing base commit"
fi

if ! git cat-file -e "${head_sha}^{commit}" 2>/dev/null; then
	run_full_validation "missing head commit"
fi

if ! git cat-file -e "${base_sha}^{commit}" 2>/dev/null; then
	if ! git fetch --no-tags --depth=1 origin "${base_sha}"; then
		run_full_validation "base commit fetch failed"
	fi
fi

if ! changed_files="$(git diff --no-renames --name-only "${base_sha}" "${head_sha}")"; then
	run_full_validation "change detection failed"
fi

if [[ -z "${changed_files}" ]]; then
	run_full_validation "no changed files detected"
fi

while IFS= read -r changed_file; do
	case "${changed_file}" in
		README.md | PACKAGE_STRUCTURE_GUIDE.md | docs/*)
			;;
		*)
			echo "Backend validation required by: ${changed_file}"
			run_full_validation "non-documentation change"
			;;
	esac
done <<< "${changed_files}"

write_result "false" "documentation-only" "all changed files are documentation"
