import { check } from "k6";
import { Counter, Gauge, Rate, Trend } from "k6/metrics";
import { PRIVATE_TESTCASE_MARKER, envVar } from "./environment.js";

export const apiResponseCount = new Counter("api_response_count");
export const apiSuccessRate = new Rate("api_success_rate");
export const apiRequestDurationMs = new Trend("api_request_duration_ms", true);
export const assignmentListDuration = new Trend("assignment_list_duration", true);
export const assignmentDetailDuration = new Trend("assignment_detail_duration", true);
export const submissionStatusDuration = new Trend("submission_status_duration", true);
export const assignmentListSuccessRate = new Rate("assignment_list_success_rate");
export const assignmentDetailSuccessRate = new Rate("assignment_detail_success_rate");
export const submissionStatusSuccessRate = new Rate("submission_status_success_rate");
export const envelopeValidationRate = new Rate("envelope_validation_rate");
export const privateTestcaseGuardRate = new Rate("private_testcase_guard_rate");
export const submissionStatusRowCount = new Gauge("submission_status_row_count");
export const businessSuccessCount = new Counter("business_success_count");
export const assignmentListSuccessCount = new Counter("assignment_list_success_count");
export const assignmentDetailSuccessCount = new Counter("assignment_detail_success_count");
export const submissionStatusSuccessCount = new Counter("submission_status_success_count");
export const authErrorCount = new Counter("auth_error_count");
export const rateLimitedCount = new Counter("rate_limited_count");
export const serverErrorCount = new Counter("server_error_count");
export const unexpectedStatusCount = new Counter("unexpected_status_count");

const RESULT_PRIORITY = [
  "network_error",
  "auth_error",
  "rate_limited",
  "server_error",
  "unexpected_status",
  "json_parse_error",
  "content_type_mismatch",
  "envelope_mismatch",
  "data_missing",
  "fixture_empty",
  "assignment_mismatch",
  "private_testcase_leak",
  "success",
];

const PRIVATE_TESTCASE_SEQ = Number(envVar("PRIVATE_TESTCASE_SEQ", "9001"));

export function validateJsonEnvelope(response, name) {
  const parsed = parseJson(response);
  const body = parsed.body;
  const data = envelopeData(body);
  const statusOk = response.status === 200;
  const contentTypeOk = isJsonContentType(response);
  const jsonOk = parsed.ok;
  const successOk = jsonOk && body.success === true;
  const errorOk = jsonOk && body.error === null;
  const timestampOk = jsonOk && hasPath(body, "timestamp");
  const dataExists = jsonOk && Object.prototype.hasOwnProperty.call(body, "data") && body.data !== null && body.data !== undefined;
  const envelopeOk = statusOk && contentTypeOk && jsonOk && successOk && errorOk && timestampOk && dataExists;

  return validationResult(
    envelopeOk,
    classifyEnvelopeResult(response, {
      jsonOk,
      contentTypeOk,
      successOk,
      errorOk,
      timestampOk,
      dataExists,
    }),
    prefixedChecks(name, {
      "http status is 200": statusOk,
      "content-type is json": contentTypeOk,
      "json parsing succeeds": jsonOk,
      "envelope success is true": successOk,
      "envelope error is null": errorOk,
      "envelope timestamp exists": timestampOk,
      "envelope data exists": dataExists,
    }),
    {
      body,
      data,
      envelopeOk,
    }
  );
}

export function validateAssignmentList(data, expectedAssignmentId, responseBody, name) {
  const isArray = Array.isArray(data);
  const notEmpty = isArray && data.length > 0;
  const allHaveIds = isArray && data.every((item) => item && typeof item.assignmentId === "string" && item.assignmentId.length > 0);
  const allPublished = isArray && data.every((item) => item && item.status === "PUBLISHED");
  const containsExpected = isArray && data.some((item) => item && item.assignmentId === expectedAssignmentId);
  const testCasesPublic = isArray && data.every((item) => testCasesArePublic(item && item.metadata && item.metadata.testCases));
  const noMarker = !stringContainsMarker(responseBody);
  const privateGuardOk = testCasesPublic && noMarker;
  const ok = isArray && notEmpty && allHaveIds && allPublished && containsExpected && privateGuardOk;

  return validationResult(
    ok,
    firstFailure([
      [!isArray, "data_missing"],
      [isArray && !notEmpty, "fixture_empty"],
      [isArray && (!allHaveIds || !allPublished || !containsExpected), "assignment_mismatch"],
      [!privateGuardOk, "private_testcase_leak"],
    ]),
    prefixedChecks(name, {
      "data is array": isArray,
      "data is non-empty": notEmpty,
      "each assignmentId exists": allHaveIds,
      "each status is PUBLISHED": allPublished,
      "expected assignmentId exists in list": containsExpected,
      "metadata.testCases are PUBLIC when present": testCasesPublic,
      "private testcase marker is absent": noMarker,
    }),
    {
      privateGuardOk,
    }
  );
}

export function validateAssignmentDetail(data, expectedCourseSlug, expectedAssignmentId, responseBody, name) {
  const isObject = data !== null && typeof data === "object" && !Array.isArray(data);
  const assignmentIdOk = isObject && data.assignmentId === expectedAssignmentId;
  const courseSlugOk = isObject && data.courseSlug === expectedCourseSlug;
  const statusOk = isObject && data.status === "PUBLISHED";
  const titleOk = isObject && hasPath(data, "metadata.title");
  const testCases = isObject && data.metadata ? data.metadata.testCases : undefined;
  const testCasesArray = Array.isArray(testCases);
  const testCasesNotEmpty = testCasesArray && testCases.length > 0;
  const allPublic = testCasesArray && testCases.every((item) => item && item.visibility === "PUBLIC");
  const noPrivateSeq = testCasesArray && testCases.every((item) => !item || item.seq !== PRIVATE_TESTCASE_SEQ);
  const noMarker = !stringContainsMarker(responseBody);
  const privateGuardOk = testCasesArray && allPublic && noPrivateSeq && noMarker;
  const ok =
    isObject &&
    assignmentIdOk &&
    courseSlugOk &&
    statusOk &&
    titleOk &&
    testCasesArray &&
    testCasesNotEmpty &&
    privateGuardOk;

  return validationResult(
    ok,
    firstFailure([
      [!isObject || !titleOk || !testCasesArray, "data_missing"],
      [testCasesArray && !testCasesNotEmpty, "fixture_empty"],
      [isObject && (!assignmentIdOk || !courseSlugOk || !statusOk), "assignment_mismatch"],
      [!privateGuardOk, "private_testcase_leak"],
    ]),
    prefixedChecks(name, {
      "assignmentId matches request": assignmentIdOk,
      "courseSlug matches request": courseSlugOk,
      "status is PUBLISHED": statusOk,
      "metadata.title exists": titleOk,
      "metadata.testCases is array": testCasesArray,
      "metadata.testCases is non-empty": testCasesNotEmpty,
      "all testCases visibility is PUBLIC": allPublic,
      "private testcase seq is absent": noPrivateSeq,
      "private testcase marker is absent": noMarker,
    }),
    {
      privateGuardOk,
    }
  );
}

export function validateSubmissionStatuses(data, expectedCourseSlug, expectedAssignmentId, name) {
  const isObject = data !== null && typeof data === "object" && !Array.isArray(data);
  const assignmentIdOk = isObject && data.assignmentId === expectedAssignmentId;
  const courseSlugOk = isObject && data.courseSlug === expectedCourseSlug;
  const itemsArray = isObject && Array.isArray(data.items);
  const totalEnrolledOk = isObject && Number.isInteger(data.totalEnrolled) && data.totalEnrolled >= 0;
  const submittedCountOk = isObject && Number.isInteger(data.submittedCount) && data.submittedCount >= 0;
  const notSubmittedCountOk = isObject && Number.isInteger(data.notSubmittedCount) && data.notSubmittedCount >= 0;
  const nonEmptyFixture = itemsArray && data.items.length > 0 && data.totalEnrolled > 0;
  const itemCountOk = itemsArray && totalEnrolledOk && data.items.length === data.totalEnrolled;
  const countSumOk = isObject && submittedCountOk && notSubmittedCountOk && data.submittedCount + data.notSubmittedCount === data.totalEnrolled;
  const submittedItems = itemsArray ? data.items.filter((item) => item && item.submitted === true).length : -1;
  const notSubmittedItems = itemsArray ? data.items.filter((item) => item && item.submitted === false).length : -1;
  const submittedMatches = isObject && submittedCountOk && submittedItems === data.submittedCount;
  const notSubmittedMatches = isObject && notSubmittedCountOk && notSubmittedItems === data.notSubmittedCount;
  const identityOk = itemsArray && data.items.every((item) => item && nonBlank(item.userId) && nonBlank(item.publicCode));
  const ok =
    isObject &&
    assignmentIdOk &&
    courseSlugOk &&
    itemsArray &&
    totalEnrolledOk &&
    submittedCountOk &&
    notSubmittedCountOk &&
    nonEmptyFixture &&
    itemCountOk &&
    countSumOk &&
    submittedMatches &&
    notSubmittedMatches &&
    identityOk;

  return validationResult(
    ok,
    firstFailure([
      [!isObject || !itemsArray || !totalEnrolledOk || !submittedCountOk || !notSubmittedCountOk, "data_missing"],
      [itemsArray && !nonEmptyFixture, "fixture_empty"],
      [isObject && (!assignmentIdOk || !courseSlugOk || !itemCountOk || !countSumOk || !submittedMatches || !notSubmittedMatches || !identityOk), "assignment_mismatch"],
    ]),
    prefixedChecks(name, {
      "assignmentId matches request": assignmentIdOk,
      "courseSlug matches request": courseSlugOk,
      "items is array": itemsArray,
      "items is non-empty fixture": nonEmptyFixture,
      "items.length equals totalEnrolled": itemCountOk,
      "submittedCount plus notSubmittedCount equals totalEnrolled": countSumOk,
      "submittedCount equals submitted=true items": submittedMatches,
      "notSubmittedCount equals submitted=false items": notSubmittedMatches,
      "each item has userId and publicCode": identityOk,
    })
  );
}

export function mergeValidationResults() {
  const mergedChecks = {};
  const merged = validationResult(true, "success", mergedChecks);
  for (let i = 0; i < arguments.length; i += 1) {
    const result = arguments[i] || validationResult(false, "data_missing", {});
    merged.ok = merged.ok && result.ok;
    merged.resultType = higherPriority(merged.resultType, result.resultType);
    copyChecks(mergedChecks, result.checks);
    if (result.body !== undefined) {
      merged.body = result.body;
    }
    if (result.data !== undefined) {
      merged.data = result.data;
    }
    if (result.envelopeOk !== undefined) {
      merged.envelopeOk = result.envelopeOk;
    }
    if (result.privateGuardOk !== undefined) {
      merged.privateGuardOk = merged.privateGuardOk === undefined
        ? result.privateGuardOk
        : merged.privateGuardOk && result.privateGuardOk;
    }
  }
  if (merged.resultType === "success" && !merged.ok) {
    merged.resultType = "data_missing";
  }
  return merged;
}

export function recordEndpointResult(response, tags, validation) {
  const resultType = validation.resultType || "success";
  const resultTags = withResultType(tags, resultType);
  const checksPassed = check(response, validation.checks || {}, resultTags);
  const success = response.status === 200 && validation.ok === true && checksPassed === true && resultType === "success";

  apiResponseCount.add(1, resultTags);
  apiSuccessRate.add(success, resultTags);
  if (success) {
    businessSuccessCount.add(1, resultTags);
  }
  recordEndpointSuccessCount(resultTags, success);
  recordResultTypeCount(resultTags, resultType);

  if (validation.envelopeOk !== undefined) {
    envelopeValidationRate.add(validation.envelopeOk === true, resultTags);
  }
  if (validation.privateGuardOk !== undefined) {
    privateTestcaseGuardRate.add(validation.privateGuardOk === true, resultTags);
  }
  recordSubmissionStatusRows(resultTags, validation.data);
  recordEndpointMetrics(response, resultTags, success);
}

export function checkReadiness(response, tags) {
  const parsed = parseJson(response);
  const body = parsed.body;
  const checks = {
    "readiness: status is 200": () => response.status === 200,
    "readiness: content-type is json": () => isJsonContentType(response),
    "readiness: json parsing succeeds": () => parsed.ok,
    "readiness: status is UP": () => body !== null && body.status === "UP",
  };
  return {
    ok: check(response, checks, withResultType(tags, classifyReadinessResult(response, parsed.ok))),
    body,
  };
}

export function parseJson(response) {
  try {
    return {
      ok: true,
      body: response.json(),
    };
  } catch (_) {
    return {
      ok: false,
      body: null,
    };
  }
}

export function envelopeData(body) {
  if (!body || typeof body !== "object") {
    return null;
  }
  return body.data;
}

function validationResult(ok, resultType, checks, extra) {
  const result = {
    ok,
    resultType: resultType || "success",
    checks: checks || {},
  };
  for (const key in extra || {}) {
    result[key] = extra[key];
  }
  return result;
}

function classifyEnvelopeResult(response, state) {
  if (response.status === 0 || response.error) {
    return "network_error";
  }
  if (response.status === 401 || response.status === 403) {
    return "auth_error";
  }
  if (response.status === 429) {
    return "rate_limited";
  }
  if (response.status >= 500) {
    return "server_error";
  }
  if (response.status !== 200) {
    return "unexpected_status";
  }
  if (!state.jsonOk) {
    return "json_parse_error";
  }
  if (!state.contentTypeOk) {
    return "content_type_mismatch";
  }
  if (!state.successOk || !state.errorOk || !state.timestampOk) {
    return "envelope_mismatch";
  }
  if (!state.dataExists) {
    return "data_missing";
  }
  return "success";
}

function classifyReadinessResult(response, jsonOk) {
  if (response.status === 0 || response.error) {
    return "network_error";
  }
  if (response.status >= 500) {
    return "server_error";
  }
  if (response.status !== 200) {
    return "unexpected_status";
  }
  if (!jsonOk) {
    return "json_parse_error";
  }
  if (!isJsonContentType(response)) {
    return "content_type_mismatch";
  }
  return "success";
}

function firstFailure(items) {
  for (let i = 0; i < items.length; i += 1) {
    if (items[i][0]) {
      return items[i][1];
    }
  }
  return "success";
}

function higherPriority(left, right) {
  const leftIndex = RESULT_PRIORITY.indexOf(left || "success");
  const rightIndex = RESULT_PRIORITY.indexOf(right || "success");
  if (leftIndex < 0) {
    return right || "success";
  }
  if (rightIndex < 0) {
    return left || "success";
  }
  return leftIndex <= rightIndex ? left : right;
}

function prefixedChecks(name, checks) {
  const result = {};
  for (const key in checks) {
    result[`${name}: ${key}`] = constantCheck(checks[key]);
  }
  return result;
}

function constantCheck(value) {
  return function () {
    return value === true;
  };
}

function copyChecks(target, source) {
  for (const key in source || {}) {
    target[key] = source[key];
  }
}

function recordEndpointMetrics(response, tags, success) {
  if (!tags || tags.endpoint === "readiness") {
    return;
  }

  apiRequestDurationMs.add(response.timings.duration, tags);

  if (tags.endpoint === "student_assignment_list") {
    assignmentListDuration.add(response.timings.duration, tags);
    assignmentListSuccessRate.add(success, tags);
    return;
  }

  if (tags.endpoint === "student_assignment_detail") {
    assignmentDetailDuration.add(response.timings.duration, tags);
    assignmentDetailSuccessRate.add(success, tags);
    return;
  }

  if (tags.endpoint === "admin_submission_statuses") {
    submissionStatusDuration.add(response.timings.duration, tags);
    submissionStatusSuccessRate.add(success, tags);
  }
}

function recordEndpointSuccessCount(tags, success) {
  if (!success) {
    return;
  }
  if (tags.endpoint === "student_assignment_list") {
    assignmentListSuccessCount.add(1, tags);
    return;
  }
  if (tags.endpoint === "student_assignment_detail") {
    assignmentDetailSuccessCount.add(1, tags);
    return;
  }
  if (tags.endpoint === "admin_submission_statuses") {
    submissionStatusSuccessCount.add(1, tags);
  }
}

function recordResultTypeCount(tags, resultType) {
  if (resultType === "auth_error") {
    authErrorCount.add(1, tags);
  }
  if (resultType === "rate_limited") {
    rateLimitedCount.add(1, tags);
  }
  if (resultType === "server_error") {
    serverErrorCount.add(1, tags);
  }
  if (resultType === "unexpected_status") {
    unexpectedStatusCount.add(1, tags);
  }
}

function recordSubmissionStatusRows(tags, data) {
  if (!tags || tags.endpoint !== "admin_submission_statuses") {
    return;
  }
  if (data && Array.isArray(data.items)) {
    submissionStatusRowCount.add(data.items.length, tags);
  }
}

function withResultType(tags, resultType) {
  const next = {};
  for (const key in tags || {}) {
    next[key] = tags[key];
  }
  next.resultType = resultType;
  return next;
}

function isJsonContentType(response) {
  const value = response.headers["Content-Type"] || response.headers["content-type"] || "";
  const normalized = String(value).toLowerCase();
  return normalized.indexOf("application/json") >= 0 || normalized.indexOf("+json") >= 0;
}

function hasPath(value, path) {
  if (value === null || value === undefined) {
    return false;
  }
  const parts = String(path).split(".");
  let current = value;
  for (let i = 0; i < parts.length; i += 1) {
    const part = parts[i];
    if (current === null || current === undefined) {
      return false;
    }
    if (Array.isArray(current)) {
      const index = Number(part);
      if (!Number.isInteger(index) || index < 0 || index >= current.length) {
        return false;
      }
      current = current[index];
      continue;
    }
    if (!Object.prototype.hasOwnProperty.call(current, part)) {
      return false;
    }
    current = current[part];
  }
  return current !== null && current !== undefined;
}

function testCasesArePublic(testCases) {
  if (testCases === undefined || testCases === null) {
    return true;
  }
  if (!Array.isArray(testCases)) {
    return false;
  }
  return testCases.every((item) => item && item.visibility === "PUBLIC" && item.seq !== PRIVATE_TESTCASE_SEQ);
}

function stringContainsMarker(value) {
  return typeof value === "string" && value.indexOf(PRIVATE_TESTCASE_MARKER) >= 0;
}

function nonBlank(value) {
  return typeof value === "string" && value.trim().length > 0;
}
