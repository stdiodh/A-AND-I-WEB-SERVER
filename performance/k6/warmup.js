import http from "k6/http";
import { ENDPOINTS, warmupOptions } from "./config.js";
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
  mergeValidationResults,
  recordEndpointResult,
  validateAssignmentDetail,
  validateAssignmentList,
  validateJsonEnvelope,
  validateSubmissionStatuses,
} from "./lib/checks.js";
import { createHandleSummary } from "./lib/summary.js";

const includeAdmin = envVar("WARMUP_INCLUDE_ADMIN", "false") === "true";

export const options = warmupOptions();

export function setup() {
  assertSafeBaseUrl();
  requireStudentReadEnv();
  if (includeAdmin && !ADMIN_ACCESS_TOKEN) {
    throw new Error("[k6 warmup] ADMIN_ACCESS_TOKEN is required when WARMUP_INCLUDE_ADMIN=true.");
  }
  return {
    accessToken: ACCESS_TOKEN,
    adminAccessToken: ADMIN_ACCESS_TOKEN,
    courseSlug: COURSE_SLUG,
    assignmentId: ASSIGNMENT_ID,
  };
}

export default function (data) {
  const listTags = baseTags("warmup", "USER", "student_assignment_list", "request");
  const listResponse = http.get(
    buildUrl(ENDPOINTS.assignmentList(data.courseSlug)),
    requestParams(data.accessToken, listTags)
  );
  const listEnvelope = validateJsonEnvelope(listResponse, "warmup assignment list");
  const listDomain = validateAssignmentList(listEnvelope.data, data.assignmentId, listResponse.body, "warmup assignment list");
  recordEndpointResult(listResponse, listTags, mergeValidationResults(listEnvelope, listDomain));

  const detailTags = baseTags("warmup", "USER", "student_assignment_detail", "request");
  const detailResponse = http.get(
    buildUrl(ENDPOINTS.assignmentDetail(data.courseSlug, data.assignmentId)),
    requestParams(data.accessToken, detailTags)
  );
  const detailEnvelope = validateJsonEnvelope(detailResponse, "warmup assignment detail");
  const detailDomain = validateAssignmentDetail(
    detailEnvelope.data,
    data.courseSlug,
    data.assignmentId,
    detailResponse.body,
    "warmup assignment detail"
  );
  recordEndpointResult(detailResponse, detailTags, mergeValidationResults(detailEnvelope, detailDomain));

  if (includeAdmin) {
    const adminTags = baseTags("warmup", "ADMIN", "admin_submission_statuses", "request");
    const adminResponse = http.get(
      buildUrl(ENDPOINTS.adminSubmissionStatuses(data.courseSlug, data.assignmentId)),
      requestParams(data.adminAccessToken, adminTags)
    );
    const adminEnvelope = validateJsonEnvelope(adminResponse, "warmup admin submission statuses");
    const adminDomain = validateSubmissionStatuses(
      adminEnvelope.data,
      data.courseSlug,
      data.assignmentId,
      "warmup admin submission statuses"
    );
    recordEndpointResult(adminResponse, adminTags, mergeValidationResults(adminEnvelope, adminDomain));
  }
}

export const handleSummary = createHandleSummary("warmup", {
  tokenRoleExpected: includeAdmin ? "USER,ADMIN" : "USER",
});
