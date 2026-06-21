import http from "k6/http";
import { ENDPOINTS, preflightOptions } from "./config.js";
import { requestParams } from "./lib/auth.js";
import {
  ACCESS_TOKEN,
  ADMIN_ACCESS_TOKEN,
  ASSIGNMENT_ID,
  COURSE_SLUG,
  assertSafeBaseUrl,
  baseTags,
  buildUrl,
  envVar,
  requireStudentReadEnv,
} from "./lib/environment.js";
import {
  checkReadiness,
  mergeValidationResults,
  recordEndpointResult,
  validateAssignmentDetail,
  validateAssignmentList,
  validateJsonEnvelope,
  validateSubmissionStatuses,
} from "./lib/checks.js";
import { createHandleSummary } from "./lib/summary.js";

const includeAdmin = envVar("PREFLIGHT_INCLUDE_ADMIN", "true") === "true";

export const options = preflightOptions();

export function setup() {
  assertSafeBaseUrl();
  requireStudentReadEnv();
  if (includeAdmin && !ADMIN_ACCESS_TOKEN) {
    throw new Error("[k6 preflight] ADMIN_ACCESS_TOKEN is required when PREFLIGHT_INCLUDE_ADMIN=true.");
  }
  return {
    accessToken: ACCESS_TOKEN,
    adminAccessToken: ADMIN_ACCESS_TOKEN,
    courseSlug: COURSE_SLUG,
    assignmentId: ASSIGNMENT_ID,
  };
}

export default function (data) {
  const healthTags = baseTags("preflight", "ANONYMOUS", "readiness", "request");
  const healthResponse = http.get(buildUrl(ENDPOINTS.readiness), {
    tags: healthTags,
    timeout: "10s",
    responseType: "text",
  });
  checkReadiness(healthResponse, healthTags);

  const listTags = baseTags("preflight", "USER", "student_assignment_list", "request");
  const listResponse = http.get(
    buildUrl(ENDPOINTS.assignmentList(data.courseSlug)),
    requestParams(data.accessToken, listTags)
  );
  const listEnvelope = validateJsonEnvelope(listResponse, "preflight assignment list");
  const listDomain = validateAssignmentList(
    listEnvelope.data,
    data.assignmentId,
    listResponse.body,
    "preflight assignment list"
  );
  recordEndpointResult(listResponse, listTags, mergeValidationResults(listEnvelope, listDomain));

  const detailTags = baseTags("preflight", "USER", "student_assignment_detail", "request");
  const detailResponse = http.get(
    buildUrl(ENDPOINTS.assignmentDetail(data.courseSlug, data.assignmentId)),
    requestParams(data.accessToken, detailTags)
  );
  const detailEnvelope = validateJsonEnvelope(detailResponse, "preflight assignment detail");
  const detailDomain = validateAssignmentDetail(
    detailEnvelope.data,
    data.courseSlug,
    data.assignmentId,
    detailResponse.body,
    "preflight assignment detail"
  );
  recordEndpointResult(detailResponse, detailTags, mergeValidationResults(detailEnvelope, detailDomain));

  if (includeAdmin) {
    const adminTags = baseTags("preflight", "ADMIN", "admin_submission_statuses", "request");
    const adminResponse = http.get(
      buildUrl(ENDPOINTS.adminSubmissionStatuses(data.courseSlug, data.assignmentId)),
      requestParams(data.adminAccessToken, adminTags)
    );
    const adminEnvelope = validateJsonEnvelope(adminResponse, "preflight admin submission statuses");
    const adminDomain = validateSubmissionStatuses(
      adminEnvelope.data,
      data.courseSlug,
      data.assignmentId,
      "preflight admin submission statuses"
    );
    recordEndpointResult(adminResponse, adminTags, mergeValidationResults(adminEnvelope, adminDomain));
  }
}

export const handleSummary = createHandleSummary("preflight", {
  tokenRoleExpected: includeAdmin ? "USER,ADMIN" : "USER",
});
