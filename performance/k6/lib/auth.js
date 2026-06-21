import { durationEnv, envVar } from "./environment.js";

export function v2Headers(accessToken) {
  const bearerToken = `Bearer ${accessToken}`;
  return {
    Accept: "application/json",
    Authorization: bearerToken,
    Authenticate: bearerToken,
    deviceOS: envVar("DEVICE_OS", "K6"),
    timestamp: new Date().toISOString(),
  };
}

export function requestParams(accessToken, tags) {
  return {
    headers: v2Headers(accessToken),
    tags,
    timeout: durationEnv("REQUEST_TIMEOUT", "30s"),
    responseType: "text",
  };
}
