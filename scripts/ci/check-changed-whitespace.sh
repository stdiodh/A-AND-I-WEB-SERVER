#!/usr/bin/env bash

set -euo pipefail

event_name="${GITHUB_EVENT_NAME:-}"
base_sha="${BASE_SHA:-}"
head_sha="${HEAD_SHA:-${GITHUB_SHA:-HEAD}}"

check_range() {
	local range_start="$1"
	local range_end="$2"

	echo "Whitespace validation range: ${range_start}..${range_end}"
	git diff --check "${range_start}" "${range_end}" --
}

if ! git cat-file -e "${head_sha}^{commit}" 2>/dev/null; then
	echo "Whitespace validation failed: head commit is unavailable (${head_sha})." >&2
	exit 1
fi

if [[ -z "${base_sha}" ]]; then
	if [[ "${event_name}" != "workflow_dispatch" ]]; then
		echo "Whitespace validation failed: base commit metadata is missing for ${event_name:-unknown event}." >&2
		exit 1
	fi

	if parent_sha="$(git rev-parse --verify "${head_sha}^" 2>/dev/null)"; then
		base_sha="${parent_sha}"
	else
		empty_tree_sha="$(git hash-object -t tree -w /dev/null)"
		check_range "${empty_tree_sha}" "${head_sha}"
		exit 0
	fi
elif [[ "${base_sha}" =~ ^0+$ ]]; then
	if [[ "${event_name}" != "push" ]]; then
		echo "Whitespace validation failed: zero base commit is only valid for a new push ref." >&2
		exit 1
	fi
	empty_tree_sha="$(git hash-object -t tree -w /dev/null)"
	check_range "${empty_tree_sha}" "${head_sha}"
	exit 0
fi

if ! git cat-file -e "${base_sha}^{commit}" 2>/dev/null; then
	git fetch --no-tags --depth=1 origin "${base_sha}"
fi

check_range "${base_sha}" "${head_sha}"
