import {
  ACCESS_TOKEN,
  ADMIN_ACCESS_TOKEN,
  RESULT_DIR,
  baseUrlHostname,
  envVar,
  targetEnvironmentLabel,
} from "./environment.js";

const RUN_TIMESTAMP = new Date().toISOString();
const RUN_TIMESTAMP_FILE = RUN_TIMESTAMP.replace(/[:.]/g, "-");

export function createHandleSummary(scenarioName, contextOverrides) {
  return function handleSummary(data) {
    const outputDir = RESULT_DIR.replace(/\/+$/, "");
    const fileBase = summaryFileBase(scenarioName);
    const context = summaryContext(scenarioName, data, contextOverrides || {});
    return {
      stdout: renderStdout(data, scenarioName, context),
      [`${outputDir}/${fileBase}.summary.json`]: JSON.stringify(
        {
          context,
          k6: data,
        },
        null,
        2
      ),
      [`${outputDir}/${fileBase}.summary.md`]: renderMarkdown(data, scenarioName, context),
    };
  };
}

function renderStdout(data, scenarioName, context) {
  return [
    "",
    `k6 scenario: ${scenarioName}`,
    `executed: ${context.executed}`,
    `skipped: ${context.skipped}`,
    context.skipReason ? `skip reason: ${context.skipReason}` : "",
    `business requests: ${context.businessRequestCount}`,
    `http requests: ${metricText(data, "http_reqs", "count", formatRaw, "not executed")}`,
    `http failed rate: ${metricText(data, "http_req_failed", "rate", formatPercent, "n/a")}`,
    `check success rate: ${metricText(data, "checks", "rate", formatPercent, "n/a")}`,
    `business success throughput: ${metricText(data, "business_success_count", "rate", formatNumber, "n/a")} req/s`,
    "",
  ].filter((line) => line !== "").join("\n");
}

function renderMarkdown(data, scenarioName, context) {
  const failedChecks = collectFailedChecks(data.root_group);
  const durationMetric = "api_request_duration_ms";
  return [
    `# k6 Result - ${scenarioName}`,
    "",
    "## Run Context",
    "",
    `- Executed At: ${context.executedAt}`,
    `- Timezone: ${context.timezone}`,
    `- k6 Version: ${context.k6Version}`,
    `- Git Commit SHA: ${context.gitCommitSha}`,
    `- Git Dirty: ${context.gitDirty}`,
    `- Scenario: ${context.scenario}`,
    `- Run Label: ${context.runLabel || "n/a"}`,
    `- Executed: ${context.executed}`,
    `- Skipped: ${context.skipped}`,
    `- Skip Reason: ${context.skipReason || "n/a"}`,
    `- Threshold Failed: ${context.thresholdFailed}`,
    `- BASE_URL Host: ${context.baseUrlHost}`,
    `- Target Environment: ${context.targetEnvironment}`,
    `- Docker Network Mode: ${context.dockerNetworkMode}`,
    `- JVM Options: ${context.jvmOptions}`,
    `- CPU: ${context.cpu}`,
    `- Memory: ${context.memory}`,
    `- MongoDB Mode: ${context.mongodbMode}`,
    `- Fixture Fingerprint: ${context.fixtureFingerprint}`,
    `- Fixture Courses: ${context.fixtureCounts.courses}`,
    `- Fixture Assignments: ${context.fixtureCounts.assignments}`,
    `- Fixture Enrollments: ${context.fixtureCounts.enrollments}`,
    `- Fixture Submission Statuses: ${context.fixtureCounts.submissionStatuses}`,
    `- Token Supplied: USER=${context.tokenSupplied.user}, ADMIN=${context.tokenSupplied.admin}`,
    `- Token Role Expected: ${context.tokenRoleExpected}`,
    `- Executor: ${context.executor}`,
    `- VUs / Arrival Rate: ${context.vus}`,
    `- Load Model: ${context.loadModel}`,
    `- Target RPS: ${context.targetRps}`,
    `- Pre Allocated VUs: ${context.preAllocatedVus}`,
    `- Max VUs: ${context.maxVus}`,
    `- Configured Duration: ${context.duration}`,
    `- Actual Test Duration: ${context.actualDuration}`,
    `- Request Sleep Seconds: ${context.requestSleepSeconds}`,
    `- Assignment List Ratio: ${context.assignmentListRatio}`,
    `- Assignment Detail Ratio: ${context.assignmentDetailRatio}`,
    `- Warm-up Completed: ${context.warmupCompleted}`,
    "",
    "## Metrics",
    "",
    "| Metric | Value |",
    "| :--- | ---: |",
    `| Business Requests | ${formatCountOrNA(context.businessRequestCount)} |`,
    `| HTTP Requests | ${metricText(data, "http_reqs", "count", formatRaw, "not executed")} |`,
    `| Checks | ${checksText(data)} |`,
    `| Success Rate (checks) | ${metricText(data, "checks", "rate", formatPercent, "n/a")} |`,
    `| Error Rate (http_req_failed) | ${metricText(data, "http_req_failed", "rate", formatPercent, "n/a")} |`,
    `| RPS | ${metricText(data, "http_reqs", "rate", formatNumber, "n/a")} |`,
    `| Business Success Throughput | ${metricText(data, "business_success_count", "rate", formatNumber, "n/a")} req/s |`,
    `| Business P50 | ${metricText(data, durationMetric, "med", formatMs, "n/a")} |`,
    `| Business P90 | ${metricText(data, durationMetric, "p(90)", formatMs, "n/a")} |`,
    `| Business P95 | ${metricText(data, durationMetric, "p(95)", formatMs, "n/a")} |`,
    `| Business P99 | ${metricText(data, durationMetric, "p(99)", formatMs, "n/a")} |`,
    `| Business Max Response Time | ${metricText(data, durationMetric, "max", formatMs, "n/a")} |`,
    `| Dropped Iterations | ${metricText(data, "dropped_iterations", "count", formatRaw, "n/a")} |`,
    `| Auth Errors | ${metricText(data, "auth_error_count", "count", formatRaw, "0")} |`,
    `| Rate Limited | ${metricText(data, "rate_limited_count", "count", formatRaw, "0")} |`,
    `| Server Errors | ${metricText(data, "server_error_count", "count", formatRaw, "0")} |`,
    `| Unexpected Status | ${metricText(data, "unexpected_status_count", "count", formatRaw, "0")} |`,
    `| Submission Status Row Count Max | ${metricText(data, "submission_status_row_count", "max", formatRaw, "n/a")} |`,
    "",
    "## Endpoint Metrics",
    "",
    "| Metric | P50 | P90 | P95 | P99 | Max | Success Rate | Success Throughput |",
    "| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    endpointMetricRow(data, "assignment_list", "assignment_list_duration", "assignment_list_success_rate", "assignment_list_success_count"),
    endpointMetricRow(data, "assignment_detail", "assignment_detail_duration", "assignment_detail_success_rate", "assignment_detail_success_count"),
    endpointMetricRow(data, "submission_status", "submission_status_duration", "submission_status_success_rate", "submission_status_success_count"),
    "",
    "## Failed Checks",
    "",
    failedChecks.length === 0 ? "- None" : failedChecks.map((item) => `- ${item}`).join("\n"),
    "",
    "## Notes",
    "",
    "- P95/RPS/throughput/improvement rates must not be copied unless this scenario actually executed against the intended fixture.",
    "- Performance improvement rates are intentionally omitted unless before/after runs use the same dataset, JVM options, and execution environment.",
  ].join("\n");
}

function summaryContext(scenarioName, data, overrides) {
  const businessRequestCount = metricValue(data, "api_response_count", "count");
  const skipped = overrides.skipped === true;
  const executed = overrides.executed !== undefined ? overrides.executed === true : !skipped && Number(businessRequestCount || 0) > 0;
  const zeroBusinessRequests = !skipped && Number(businessRequestCount || 0) === 0;
  return {
    executedAt: RUN_TIMESTAMP,
    timezone: envVar("TZ", "unknown"),
    k6Version: envVar("K6_VERSION", "unknown"),
    gitCommitSha: envVar("GIT_COMMIT_SHA", "unknown"),
    gitDirty: envVar("GIT_DIRTY", "unknown"),
    scenario: scenarioName,
    runLabel: envVar("RUN_LABEL", ""),
    executed,
    skipped,
    skipReason: overrides.skipReason || "",
    businessRequestCount: Number(businessRequestCount || 0),
    thresholdFailed: thresholdFailed(data) || zeroBusinessRequests,
    executor: configuredExecutor(),
    vus: configuredLoadShape(),
    loadModel: envVar("LOAD_MODEL", "constant-vus"),
    targetRps: envVar("TARGET_RPS", "n/a"),
    preAllocatedVus: envVar("PRE_ALLOCATED_VUS", "n/a"),
    maxVus: envVar("MAX_VUS", "n/a"),
    duration: envVar("TEST_DURATION", "script default"),
    requestSleepSeconds: envVar("REQUEST_SLEEP_SECONDS", "script default"),
    assignmentListRatio: envVar("ASSIGNMENT_LIST_RATIO", "60"),
    assignmentDetailRatio: envVar("ASSIGNMENT_DETAIL_RATIO", "40"),
    baseUrlHost: safeBaseUrlHostname(),
    targetEnvironment: safeTargetEnvironmentLabel(),
    dockerNetworkMode: envVar("K6_DOCKER_NETWORK_MODE", "unknown"),
    jvmOptions: envVar("JVM_OPTS", envVar("JAVA_TOOL_OPTIONS", "unknown")),
    cpu: envVar("CPU_INFO", "unknown"),
    memory: envVar("MEMORY_INFO", "unknown"),
    mongodbMode: envVar("MONGODB_MODE", "unknown"),
    fixtureFingerprint: envVar("FIXTURE_FINGERPRINT", "unknown"),
    fixtureCounts: {
      courses: envVar("FIXTURE_COURSES", "unknown"),
      assignments: envVar("FIXTURE_ASSIGNMENTS", "unknown"),
      enrollments: envVar("FIXTURE_ENROLLMENTS", "unknown"),
      submissionStatuses: envVar("FIXTURE_SUBMISSION_STATUSES", "unknown"),
    },
    tokenSupplied: {
      user: Boolean(ACCESS_TOKEN),
      admin: Boolean(ADMIN_ACCESS_TOKEN),
    },
    tokenRoleExpected: overrides.tokenRoleExpected || expectedRoleForScenario(scenarioName),
    warmupCompleted: envVar("WARMUP_COMPLETED", "unknown"),
    actualDuration: formatDuration(data.state && data.state.testRunDurationMs),
  };
}

function safeBaseUrlHostname() {
  try {
    return baseUrlHostname();
  } catch (_) {
    return "unknown";
  }
}

function safeTargetEnvironmentLabel() {
  try {
    return targetEnvironmentLabel();
  } catch (_) {
    return "unknown";
  }
}

function summaryFileBase(scenarioName) {
  const gitSha = envVar("GIT_COMMIT_SHA", "unknown").replace(/[^A-Za-z0-9._-]/g, "_");
  return `${scenarioName}-${RUN_TIMESTAMP_FILE}-${gitSha}`;
}

function configuredExecutor() {
  if (envVar("K6_EXECUTOR")) {
    return envVar("K6_EXECUTOR");
  }
  if (envVar("LOAD_MODEL") === "arrival-rate") {
    return "constant-arrival-rate";
  }
  if (envVar("WARMUP_ITERATIONS")) {
    return "shared-iterations";
  }
  if (envVar("LOAD_VUS")) {
    return "constant-vus";
  }
  if (envVar("SMOKE_VUS")) {
    return "shared-iterations";
  }
  return "script default";
}

function configuredLoadShape() {
  if (envVar("LOAD_MODEL") === "arrival-rate") {
    return `${envVar("TARGET_RPS", "unknown")} req/s`;
  }
  const loadVus = envVar("LOAD_VUS");
  const smokeVus = envVar("SMOKE_VUS");
  if (loadVus) {
    return `${loadVus} VUs`;
  }
  if (smokeVus) {
    return `${smokeVus} VUs`;
  }
  return "script default";
}

function expectedRoleForScenario(scenarioName) {
  if (scenarioName === "submission-status-read") {
    return "ADMIN";
  }
  if (scenarioName === "preflight") {
    return envVar("PREFLIGHT_INCLUDE_ADMIN", "true") === "true" ? "USER,ADMIN" : "USER";
  }
  if (scenarioName === "warmup") {
    return envVar("WARMUP_INCLUDE_ADMIN", "false") === "true" ? "USER,ADMIN" : "USER";
  }
  return "USER";
}

function endpointMetricRow(data, label, durationMetric, successMetric, successCountMetric) {
  return `| ${label} | ${metricText(data, durationMetric, "med", formatMs, "n/a")} | ${metricText(data, durationMetric, "p(90)", formatMs, "n/a")} | ${metricText(data, durationMetric, "p(95)", formatMs, "n/a")} | ${metricText(data, durationMetric, "p(99)", formatMs, "n/a")} | ${metricText(data, durationMetric, "max", formatMs, "n/a")} | ${metricText(data, successMetric, "rate", formatPercent, "n/a")} | ${metricText(data, successCountMetric, "rate", formatNumber, "n/a")} req/s |`;
}

function collectFailedChecks(group) {
  const failures = [];
  visit(group);
  return failures;

  function visit(current) {
    if (!current) {
      return;
    }
    for (const item of current.checks || []) {
      if (item.fails && item.fails > 0) {
        failures.push(`${item.path || item.name}: ${item.fails} failed`);
      }
    }
    for (const child of current.groups || []) {
      visit(child);
    }
  }
}

function thresholdFailed(data) {
  const metrics = data.metrics || {};
  for (const metricName in metrics) {
    const thresholds = metrics[metricName].thresholds || {};
    for (const thresholdName in thresholds) {
      if (thresholds[thresholdName] && thresholds[thresholdName].ok === false) {
        return true;
      }
    }
  }
  return false;
}

function checksText(data) {
  const passes = metricValue(data, "checks", "passes");
  const fails = metricValue(data, "checks", "fails");
  if (passes === null && fails === null) {
    return "n/a";
  }
  return `${formatRaw(passes || 0)} passed / ${formatRaw(fails || 0)} failed`;
}

function metricText(data, metricName, valueName, formatter, missingValue) {
  const value = metricValue(data, metricName, valueName);
  if (value === null) {
    return missingValue;
  }
  return formatter(value);
}

function metricValue(data, metricName, valueName) {
  const metric = data.metrics && data.metrics[metricName];
  if (!metric || !metric.values || metric.values[valueName] === undefined) {
    return null;
  }
  return metric.values[valueName];
}

function formatCountOrNA(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return "n/a";
  }
  return String(number);
}

function formatPercent(value) {
  return `${formatNumber(Number(value) * 100)}%`;
}

function formatMs(value) {
  return `${formatNumber(value)} ms`;
}

function formatRaw(value) {
  if (value === null || value === undefined) {
    return "n/a";
  }
  return String(value);
}

function formatNumber(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return "n/a";
  }
  return number.toFixed(2);
}

function formatDuration(ms) {
  const number = Number(ms);
  if (!Number.isFinite(number)) {
    return "unknown";
  }
  return `${(number / 1000).toFixed(2)}s`;
}
