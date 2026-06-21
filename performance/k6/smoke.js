import http from "k6/http";
import { smokeOptions, ENDPOINTS } from "./config.js";
import { requestParams } from "./lib/auth.js";
import {
  ACCESS_TOKEN,
  ASSIGNMENT_ID,
  COURSE_SLUG,
  assertSafeBaseUrl,
  baseTags,
  buildUrl,
  requireStudentReadEnv,
} from "./lib/environment.js";
import {
  checkReadiness,
  mergeValidationResults,
  recordEndpointResult,
  validateAssignmentDetail,
  validateAssignmentList,
  validateJsonEnvelope,
} from "./lib/checks.js";
import { createHandleSummary } from "./lib/summary.js";

export const options = smokeOptions();

export function setup() {
  assertSafeBaseUrl();
  requireStudentReadEnv();
  return {
    accessToken: ACCESS_TOKEN,
    courseSlug: COURSE_SLUG,
    assignmentId: ASSIGNMENT_ID,
  };
}

export default function (data) {
  const healthTags = baseTags("smoke", "ANONYMOUS", "readiness", "request");
  const healthResponse = http.get(buildUrl(ENDPOINTS.readiness), {
    tags: healthTags,
    timeout: "10s",
    responseType: "text",
  });
  checkReadiness(healthResponse, healthTags);

  const listTags = baseTags("smoke", "USER", "student_assignment_list", "request");
  const listResponse = http.get(
    buildUrl(ENDPOINTS.assignmentList(data.courseSlug)),
    requestParams(data.accessToken, listTags)
  );
  const listEnvelope = validateJsonEnvelope(listResponse, "assignment list");
  const listDomain = validateAssignmentList(listEnvelope.data, data.assignmentId, listResponse.body, "assignment list");
  recordEndpointResult(listResponse, listTags, mergeValidationResults(listEnvelope, listDomain));

  const detailTags = baseTags("smoke", "USER", "student_assignment_detail", "request");
  const detailResponse = http.get(
    buildUrl(ENDPOINTS.assignmentDetail(data.courseSlug, data.assignmentId)),
    requestParams(data.accessToken, detailTags)
  );
  const detailEnvelope = validateJsonEnvelope(detailResponse, "assignment detail");
  const detailDomain = validateAssignmentDetail(
    detailEnvelope.data,
    data.courseSlug,
    data.assignmentId,
    detailResponse.body,
    "assignment detail"
  );
  recordEndpointResult(detailResponse, detailTags, mergeValidationResults(detailEnvelope, detailDomain));
}

export const handleSummary = createHandleSummary("smoke");
