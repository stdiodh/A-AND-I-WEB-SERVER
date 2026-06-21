import { durationEnv, envVar, intEnv, numberEnv } from "./lib/environment.js";

export const ENDPOINTS = {
  readiness: "/actuator/health/readiness",
  assignmentList: (courseSlug) => `/v2/courses/${encodePathSegment(courseSlug)}/assignments?status=PUBLISHED`,
  assignmentDetail: (courseSlug, assignmentId) =>
    `/v2/courses/${encodePathSegment(courseSlug)}/assignments/${encodePathSegment(assignmentId)}`,
  adminSubmissionStatuses: (courseSlug, assignmentId) =>
    `/v2/admin/courses/${encodePathSegment(courseSlug)}/assignments/${encodePathSegment(assignmentId)}/submission-statuses`,
};

export function smokeOptions() {
  const smokeVus = intEnv("SMOKE_VUS", 1, 1);
  if (smokeVus !== 1) {
    throw new Error("[k6 config] smoke.js requires SMOKE_VUS=1.");
  }
  return withCommonOptions({
    scenarios: {
      smoke: {
        executor: "shared-iterations",
        vus: 1,
        iterations: 1,
        maxDuration: "30s",
      },
    },
    thresholds: defaultThresholds(),
  });
}

export function readLoadOptions() {
  const loadModel = envVar("LOAD_MODEL", "constant-vus");
  const duration = durationEnv("TEST_DURATION", "1m");
  if (loadModel === "arrival-rate") {
    const preAllocatedVUs = intEnv("PRE_ALLOCATED_VUS", 30, 1);
    const maxVUs = intEnv("MAX_VUS", 200, preAllocatedVUs);
    return withCommonOptions({
      scenarios: {
        read_load: {
          executor: "constant-arrival-rate",
          rate: intEnv("TARGET_RPS", 10, 1),
          timeUnit: "1s",
          duration,
          preAllocatedVUs,
          maxVUs,
        },
      },
      thresholds: defaultThresholds(),
    });
  }
  if (loadModel !== "constant-vus") {
    throw new Error("[k6 config] LOAD_MODEL must be constant-vus or arrival-rate.");
  }
  return withCommonOptions({
    scenarios: {
      read_load: {
        executor: "constant-vus",
        vus: intEnv("LOAD_VUS", 5, 1),
        duration,
      },
    },
    thresholds: defaultThresholds(),
  });
}

export function preflightOptions() {
  return withCommonOptions({
    scenarios: {
      preflight: {
        executor: "shared-iterations",
        vus: 1,
        iterations: 1,
        maxDuration: "30s",
      },
    },
    thresholds: {
      checks: ["rate>0.99"],
    },
  });
}

export function warmupOptions() {
  return withCommonOptions({
    scenarios: {
      warmup: {
        executor: "shared-iterations",
        vus: 1,
        iterations: intEnv("WARMUP_ITERATIONS", 5, 1),
        maxDuration: durationEnv("WARMUP_MAX_DURATION", "30s"),
      },
    },
    thresholds: {
      checks: ["rate>0.99"],
    },
  });
}

export function skippedOptions() {
  return withCommonOptions({
    scenarios: {
      skip: {
        executor: "shared-iterations",
        vus: 1,
        iterations: 1,
        maxDuration: "5s",
      },
    },
    thresholds: {},
  });
}

export function defaultThresholds() {
  const thresholds = {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
  };
  const p95ThresholdMs = envVar("P95_THRESHOLD_MS");
  if (p95ThresholdMs) {
    const parsed = Number(p95ThresholdMs);
    if (!Number.isFinite(parsed) || parsed <= 0) {
      throw new Error("[k6 config] P95_THRESHOLD_MS must be a positive number.");
    }
    thresholds.api_request_duration_ms = [`p(95)<${parsed}`];
  }
  return thresholds;
}

export function assignmentReadWeights() {
  const list = numberEnv("ASSIGNMENT_LIST_RATIO", 60);
  const detail = numberEnv("ASSIGNMENT_DETAIL_RATIO", 40);
  const total = list + detail;
  if (total <= 0) {
    throw new Error("[k6 config] ASSIGNMENT_LIST_RATIO + ASSIGNMENT_DETAIL_RATIO must be greater than 0.");
  }
  return {
    list,
    detail,
    listCutoff: list / total,
  };
}

export function requestSleepSeconds() {
  return numberEnv("REQUEST_SLEEP_SECONDS", 1);
}

function commonOptions() {
  return {
    discardResponseBodies: true,
    summaryTrendStats: ["med", "p(90)", "p(95)", "p(99)", "max"],
  };
}

function withCommonOptions(options) {
  const merged = commonOptions();
  for (const key in options) {
    if (Object.prototype.hasOwnProperty.call(options, key)) {
      merged[key] = options[key];
    }
  }
  return merged;
}

function encodePathSegment(value) {
  return encodeURIComponent(String(value));
}
