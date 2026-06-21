export function envVar(name, defaultValue = "") {
  const value = __ENV[name];
  if (value === undefined || value === null || value === "") {
    return defaultValue;
  }
  return String(value);
}

export function requiredEnv(name) {
  const value = envVar(name);
  if (!value) {
    throw new Error(`[k6 config] Missing required environment variable: ${name}`);
  }
  return value;
}

export function intEnv(name, defaultValue, minValue = 0) {
  const raw = envVar(name, String(defaultValue));
  const trimmed = String(raw).trim();
  if (!/^[0-9]+$/.test(trimmed)) {
    throw new Error(`[k6 config] ${name} must be an integer string.`);
  }
  const parsed = Number(trimmed);
  if (!Number.isSafeInteger(parsed) || parsed < minValue) {
    throw new Error(`[k6 config] ${name} must be an integer greater than or equal to ${minValue}.`);
  }
  return parsed;
}

export function numberEnv(name, defaultValue) {
  const raw = envVar(name, String(defaultValue));
  const trimmed = String(raw).trim();
  if (trimmed === "") {
    throw new Error(`[k6 config] ${name} must be a non-negative number.`);
  }
  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed) || parsed < 0) {
    throw new Error(`[k6 config] ${name} must be a non-negative number.`);
  }
  return parsed;
}

export function durationEnv(name, defaultValue) {
  const raw = envVar(name, defaultValue);
  const trimmed = String(raw).trim();
  if (!/^(?:[0-9]+(?:\.[0-9]+)?(?:ns|us|ms|s|m|h))+$/.test(trimmed)) {
    throw new Error(`[k6 config] ${name} must be a valid k6 duration, e.g. 30s, 1m, or 1m30s.`);
  }
  return trimmed;
}

export const BASE_URL = envVar("BASE_URL", "http://localhost:8080");
export const ACCESS_TOKEN = envVar("ACCESS_TOKEN");
export const ADMIN_ACCESS_TOKEN = envVar("ADMIN_ACCESS_TOKEN");
export const COURSE_SLUG = envVar("COURSE_SLUG");
export const ASSIGNMENT_ID = envVar("ASSIGNMENT_ID");
export const RESULT_DIR = envVar("RESULT_DIR", "performance/results");
export const TARGET_ENVIRONMENT = envVar("TARGET_ENVIRONMENT", envVar("TARGET_ENV", "local"));
export const PRIVATE_TESTCASE_MARKER = envVar("PRIVATE_TESTCASE_MARKER", "PERF_PRIVATE_MUST_NOT_LEAK_001");

export function buildUrl(path) {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${BASE_URL.replace(/\/+$/, "")}${normalizedPath}`;
}

export function baseTags(scenario, role, endpoint, resultType) {
  return {
    name: endpoint,
    scenario,
    role,
    endpoint,
    courseSlug: COURSE_SLUG || "unknown",
    resultType: resultType || "request",
  };
}

export function requireStudentReadEnv() {
  requiredEnv("ACCESS_TOKEN");
  requiredEnv("COURSE_SLUG");
  requiredEnv("ASSIGNMENT_ID");
}

export function requireAdminReadTargetEnv() {
  requiredEnv("COURSE_SLUG");
  requiredEnv("ASSIGNMENT_ID");
}

export function assertSafeBaseUrl() {
  const parsed = parseBaseUrl(BASE_URL);
  const host = normalizeHost(parsed.hostname);
  const targetEnvironment = TARGET_ENVIRONMENT.trim().toLowerCase();

  if (targetEnvironment === "prod" || targetEnvironment === "production") {
    throw new Error("[k6 safety] Refusing to run when TARGET_ENVIRONMENT is production/prod.");
  }

  if (isLocalHost(host)) {
    return;
  }

  if (host === "host.docker.internal") {
    if (envVar("DOCKER_LOCAL_MODE") === "true") {
      return;
    }
    throw new Error("[k6 safety] host.docker.internal requires DOCKER_LOCAL_MODE=true.");
  }

  if (isBlockedRemoteHost(host)) {
    throw new Error(`[k6 safety] Refusing production or blocked BASE_URL host: ${host}`);
  }

  if (parsed.protocol !== "https") {
    throw new Error("[k6 safety] Remote BASE_URL must use HTTPS.");
  }

  if (
    envVar("ALLOW_REMOTE_LOAD_TEST") === "true" &&
    targetEnvironment === "staging" &&
    remoteTargetAllowed(host)
  ) {
    return;
  }
  throw new Error(
    "[k6 safety] Refusing non-local BASE_URL. Set ALLOW_REMOTE_LOAD_TEST=true, TARGET_ENVIRONMENT=staging, and add the exact host to REMOTE_TARGET_ALLOWLIST."
  );
}

export function isLocalBaseUrl(baseUrl) {
  return isLocalHost(baseUrlHostname(baseUrl));
}

export function baseUrlHostname(baseUrl = BASE_URL) {
  return normalizeHost(parseBaseUrl(baseUrl).hostname);
}

export function targetEnvironmentLabel() {
  const host = baseUrlHostname(BASE_URL);
  if (isLocalHost(host)) {
    return "local";
  }
  if (host === "host.docker.internal") {
    return "docker-local";
  }
  return TARGET_ENVIRONMENT || "unknown";
}

export function parseBaseUrl(rawUrl) {
  const value = String(rawUrl || "").trim();
  if (value.indexOf("?") >= 0 || value.indexOf("#") >= 0) {
    throw new Error("[k6 safety] BASE_URL must not include query or fragment.");
  }
  const match = value.match(/^([a-z][a-z0-9+.-]*):\/\/([^/?#]*)(?:\/.*)?$/i);
  if (!match) {
    throw new Error("[k6 safety] BASE_URL parsing failed: missing protocol or authority.");
  }

  const protocol = match[1].toLowerCase();
  if (protocol !== "http" && protocol !== "https") {
    throw new Error("[k6 safety] BASE_URL parsing failed: protocol must be http or https.");
  }

  const authority = match[2];
  if (!authority || /\s/.test(authority)) {
    throw new Error("[k6 safety] BASE_URL parsing failed: invalid authority.");
  }
  if (authority.indexOf("@") >= 0) {
    throw new Error("[k6 safety] BASE_URL must not include username or password credentials.");
  }

  const hostPort = authority;
  const hostname = parseHostname(hostPort);
  if (!hostname) {
    throw new Error("[k6 safety] BASE_URL parsing failed: missing hostname.");
  }

  return {
    protocol,
    hostname,
  };
}

function isLocalHost(host) {
  return host === "localhost" || host === "127.0.0.1" || host === "::1";
}

function normalizeHost(host) {
  return String(host || "").replace(/^\[/, "").replace(/\]$/, "").toLowerCase();
}

function parseHostname(hostPort) {
  if (!hostPort) {
    return "";
  }

  if (hostPort.startsWith("[")) {
    const end = hostPort.indexOf("]");
    if (end < 0) {
      throw new Error("[k6 safety] BASE_URL parsing failed: invalid IPv6 host.");
    }
    const rest = hostPort.slice(end + 1);
    if (rest && !/^:\d+$/.test(rest)) {
      throw new Error("[k6 safety] BASE_URL parsing failed: invalid host port.");
    }
    return hostPort.slice(0, end + 1);
  }

  const parts = hostPort.split(":");
  if (parts.length > 2) {
    throw new Error("[k6 safety] BASE_URL parsing failed: IPv6 host must use brackets.");
  }
  if (parts[1] !== undefined && !/^\d+$/.test(parts[1])) {
    throw new Error("[k6 safety] BASE_URL parsing failed: invalid host port.");
  }
  return parts[0].toLowerCase();
}

function remoteTargetAllowed(host) {
  const allowlist = envVar("REMOTE_TARGET_ALLOWLIST")
    .split(",")
    .map((item) => normalizeHost(item.trim()))
    .filter(Boolean);
  return allowlist.indexOf(host) >= 0;
}

function isBlockedRemoteHost(host) {
  const blocklist = envVar("REMOTE_TARGET_BLOCKLIST", "api.aandiclub.com")
    .split(",")
    .map((item) => normalizeHost(item.trim()))
    .filter(Boolean);
  return blocklist.indexOf(host) >= 0;
}
