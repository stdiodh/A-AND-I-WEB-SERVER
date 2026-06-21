import http from "k6/http";
import { sleep } from "k6";
import { ENDPOINTS, readLoadOptions, requestSleepSeconds, skippedOptions } from "./config.js";
import { requestParams } from "./lib/auth.js";
import {
  ADMIN_ACCESS_TOKEN,
  ASSIGNMENT_ID,
  COURSE_SLUG,
  assertSafeBaseUrl,
  baseTags,
  buildUrl,
  envVar,
  requireAdminReadTargetEnv,
} from "./lib/environment.js";
import {
  mergeValidationResults,
  recordEndpointResult,
  validateJsonEnvelope,
  validateSubmissionStatuses,
} from "./lib/checks.js";
import { createHandleSummary } from "./lib/summary.js";

const hasAdminToken = Boolean(ADMIN_ACCESS_TOKEN);
const skipAllowed = envVar("ALLOW_SCENARIO_SKIP") === "true";
const skipReason = "ADMIN_ACCESS_TOKEN is not set";

export const options = hasAdminToken || !skipAllowed ? readLoadOptions() : skippedOptions();

export function setup() {
  assertSafeBaseUrl();
  if (!hasAdminToken) {
    if (!skipAllowed) {
      throw new Error("[k6 config] ADMIN_ACCESS_TOKEN is required for submission-status-read.js. Set ALLOW_SCENARIO_SKIP=true only when an explicit skip is intended.");
    }
    console.warn(`Skipping submission-status-read: ${skipReason}.`);
    return {
      skip: true,
      reason: skipReason,
    };
  }
  requireAdminReadTargetEnv();
  return {
    adminAccessToken: ADMIN_ACCESS_TOKEN,
    courseSlug: COURSE_SLUG,
    assignmentId: ASSIGNMENT_ID,
  };
}

export default function (data) {
  if (data.skip) {
    console.warn(`submission-status-read skipped: ${data.reason}`);
    return;
  }

  const tags = baseTags("submission-status-read", "ADMIN", "admin_submission_statuses", "request");
  const response = http.get(
    buildUrl(ENDPOINTS.adminSubmissionStatuses(data.courseSlug, data.assignmentId)),
    requestParams(data.adminAccessToken, tags)
  );
  const envelope = validateJsonEnvelope(response, "admin submission statuses");
  const domain = validateSubmissionStatuses(envelope.data, data.courseSlug, data.assignmentId, "admin submission statuses");
  recordEndpointResult(response, tags, mergeValidationResults(envelope, domain));
  sleep(requestSleepSeconds());
}

export const handleSummary = createHandleSummary("submission-status-read", {
  executed: hasAdminToken,
  skipped: !hasAdminToken && skipAllowed,
  skipReason: !hasAdminToken && skipAllowed ? skipReason : "",
  tokenRoleExpected: "ADMIN",
});
