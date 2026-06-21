import http from "k6/http";
import { sleep } from "k6";
import { assignmentReadWeights, ENDPOINTS, readLoadOptions, requestSleepSeconds } from "./config.js";
import { requestParams } from "./lib/auth.js";
import {
  ACCESS_TOKEN,
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
} from "./lib/checks.js";
import { createHandleSummary } from "./lib/summary.js";

export const options = readLoadOptions();

export function setup() {
  assertSafeBaseUrl();
  requireStudentReadEnv();
  return {
    accessToken: ACCESS_TOKEN,
    courseSlug: COURSE_SLUG,
    assignmentId: ASSIGNMENT_ID,
    weights: assignmentReadWeights(),
  };
}

export default function (data) {
  if (Math.random() < data.weights.listCutoff) {
    readAssignmentList(data);
  } else {
    readAssignmentDetail(data);
  }
  const sleepSeconds = requestSleepSeconds();
  if (envVar("LOAD_MODEL", "constant-vus") !== "arrival-rate" && sleepSeconds > 0) {
    sleep(sleepSeconds);
  }
}

function readAssignmentList(data) {
  const tags = baseTags("assignment-read", "USER", "student_assignment_list", "request");
  const response = http.get(
    buildUrl(ENDPOINTS.assignmentList(data.courseSlug)),
    requestParams(data.accessToken, tags)
  );
  const envelope = validateJsonEnvelope(response, "assignment list");
  const domain = validateAssignmentList(envelope.data, data.assignmentId, response.body, "assignment list");
  recordEndpointResult(response, tags, mergeValidationResults(envelope, domain));
}

function readAssignmentDetail(data) {
  const tags = baseTags("assignment-read", "USER", "student_assignment_detail", "request");
  const response = http.get(
    buildUrl(ENDPOINTS.assignmentDetail(data.courseSlug, data.assignmentId)),
    requestParams(data.accessToken, tags)
  );
  const envelope = validateJsonEnvelope(response, "assignment detail");
  const domain = validateAssignmentDetail(
    envelope.data,
    data.courseSlug,
    data.assignmentId,
    response.body,
    "assignment detail"
  );
  recordEndpointResult(response, tags, mergeValidationResults(envelope, domain));
}

export const handleSummary = createHandleSummary("assignment-read");
