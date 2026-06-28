const FIXTURE_ID = "perf_k6";
const COURSE_ID = "perf_course_k6";
const COURSE_SLUG = "perf-k6";
const WEEK_COUNT = 3;
const ADMIN_USER_ID = "00000000-0000-4000-8000-999999999999";
const TARGET_ASSIGNMENT_ID = "10000000-0000-4000-8000-000000000001";
const PUBLIC_MARKER = "PERF_PUBLIC_VISIBLE_001";
const PRIVATE_MARKER = getenv("PRIVATE_TESTCASE_MARKER", "PERF_PRIVATE_MUST_NOT_LEAK_001");
const PRIVATE_SEQ = intEnv("PRIVATE_TESTCASE_SEQ", 9001, 1);

assertLocalFixtureDb();

const expectedAssignments = intEnv("FIXTURE_ASSIGNMENTS", 30, 10);
const expectedEnrollments = intEnv("FIXTURE_ENROLLMENTS", 100, 1);
const expectedSubmissionStatuses = Math.min(intEnv("FIXTURE_SUBMISSION_STATUSES", 60, 0), expectedEnrollments);
if (expectedEnrollments > 999) {
  throw new Error("FIXTURE_ENROLLMENTS must be <= 999 because publicCode uses #SP001 format.");
}
const assignmentIds = [];
for (let i = 1; i <= expectedAssignments; i += 1) {
  assignmentIds.push(assignmentIdAt(i));
}

const errors = [];
const course = db.courses.findOne({ _id: COURSE_ID, slug: COURSE_SLUG, status: "PUBLISHED" });
if (!course || course.metadata.attributes.fixtureId !== FIXTURE_ID) {
  errors.push("course is missing or not marked as perf fixture");
}
const weeks = db.course_weeks.find({ courseId: COURSE_ID }).toArray();
if (weeks.length !== WEEK_COUNT) {
  errors.push(`course weeks count mismatch: expected ${WEEK_COUNT}, got ${weeks.length}`);
}
for (let weekNo = 1; weekNo <= WEEK_COUNT; weekNo += 1) {
  if (!db.course_weeks.findOne({ _id: weekIdAt(weekNo), courseId: COURSE_ID, weekNo })) {
    errors.push(`course week ${weekNo} is missing`);
    break;
  }
}

const assignments = db.assignments.find({ _id: { $in: assignmentIds }, courseId: COURSE_ID, status: "PUBLISHED" }).toArray();
if (assignments.length !== expectedAssignments) {
  errors.push(`assignments count mismatch: expected ${expectedAssignments}, got ${assignments.length}`);
}
const targetAssignment = db.assignments.findOne({ _id: TARGET_ASSIGNMENT_ID, courseId: COURSE_ID, courseSlug: COURSE_SLUG, status: "PUBLISHED" });
if (!targetAssignment) {
  errors.push("target assignment is missing");
}

const enrollments = db.course_enrollments.find({ courseId: COURSE_ID, status: "ENABLED" }).toArray();
if (enrollments.length !== expectedEnrollments) {
  errors.push(`enrollments count mismatch: expected ${expectedEnrollments}, got ${enrollments.length}`);
}

const users = db.users.find({ _id: { $in: enrollments.map((item) => item.userId).concat([ADMIN_USER_ID]) } }).toArray();
if (users.length !== expectedEnrollments + 1) {
  errors.push(`users count mismatch: expected ${expectedEnrollments + 1}, got ${users.length}`);
}

const targetTestCases = db.assignment_test_cases.find({ assignmentId: TARGET_ASSIGNMENT_ID }).sort({ seq: 1 }).toArray();
const targetPublicTestCases = targetTestCases.filter((item) => item.visibility === "PUBLIC");
const targetPrivateTestCases = targetTestCases.filter((item) => item.visibility !== "PUBLIC");
if (targetPublicTestCases.length < 2) {
  errors.push(`target assignment public testcase count mismatch: expected at least 2, got ${targetPublicTestCases.length}`);
}
if (!targetPublicTestCases.some((item) => item.outputText === PUBLIC_MARKER)) {
  errors.push("target assignment public testcase marker is missing");
}
if (!targetPrivateTestCases.some((item) => item.seq === PRIVATE_SEQ && item.outputText === PRIVATE_MARKER)) {
  errors.push("target assignment private testcase marker is missing");
}

const allTestCases = db.assignment_test_cases.find({ assignmentId: { $in: assignmentIds } }).toArray();
if (allTestCases.length !== expectedAssignments * 3) {
  errors.push(`testcases count mismatch: expected ${expectedAssignments * 3}, got ${allTestCases.length}`);
}
const assignmentIdSet = {};
for (const assignmentId of assignmentIds) {
  assignmentIdSet[assignmentId] = true;
}
for (const testCase of allTestCases) {
  if (!assignmentIdSet[testCase.assignmentId]) {
    errors.push(`testcase references unknown assignment: ${testCase._id}`);
    break;
  }
}

const statuses = db.assignment_submission_statuses.find({ assignmentId: TARGET_ASSIGNMENT_ID }).toArray();
if (statuses.length !== expectedSubmissionStatuses) {
  errors.push(`submission statuses count mismatch: expected ${expectedSubmissionStatuses}, got ${statuses.length}`);
}
const enrollmentPublicCodes = {};
for (const enrollment of enrollments) {
  enrollmentPublicCodes[enrollment.publicCode] = true;
}
for (const status of statuses) {
  if (!enrollmentPublicCodes[status.publicCode]) {
    errors.push(`submission status references unknown publicCode: ${status.publicCode}`);
    break;
  }
}

if (errors.length > 0) {
  emitJson({ status: "invalid", database: db.getName(), errors });
  quit(1);
}

const counts = {
  courses: 1,
  weeks: WEEK_COUNT,
  assignments: expectedAssignments,
  enrollments: expectedEnrollments,
  submissionStatuses: expectedSubmissionStatuses,
};
const fixtureFingerprint = sha256(`${COURSE_SLUG}${counts.assignments}${counts.enrollments}${counts.submissionStatuses}${TARGET_ASSIGNMENT_ID}`);

emitJson({
  status: "verified",
  database: db.getName(),
  courseSlug: COURSE_SLUG,
  assignmentId: TARGET_ASSIGNMENT_ID,
  userId: userIdAt(1),
  adminUserId: ADMIN_USER_ID,
  fixtureFingerprint,
  counts,
});

function weekIdAt(index) {
  return `perf_week_k6_${index}`;
}

function assignmentIdAt(index) {
  if (index === 1) {
    return TARGET_ASSIGNMENT_ID;
  }
  return `10000000-0000-4000-8000-${pad(index, 12)}`;
}

function userIdAt(index) {
  return `00000000-0000-4000-8000-${pad(index, 12)}`;
}

function pad(value, width) {
  return String(value).padStart(width, "0");
}

function sha256(value) {
  const bytes = [];
  for (let i = 0; i < value.length; i += 1) {
    const code = value.charCodeAt(i);
    if (code > 0x7f) {
      throw new Error("fixture fingerprint input must be ASCII");
    }
    bytes.push(code);
  }

  const bitLength = bytes.length * 8;
  bytes.push(0x80);
  while (bytes.length % 64 !== 56) {
    bytes.push(0);
  }
  for (let shift = 56; shift >= 0; shift -= 8) {
    bytes.push((bitLength / Math.pow(2, shift)) & 0xff);
  }

  const hash = [
    0x6a09e667,
    0xbb67ae85,
    0x3c6ef372,
    0xa54ff53a,
    0x510e527f,
    0x9b05688c,
    0x1f83d9ab,
    0x5be0cd19,
  ];
  const k = [
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
  ];

  for (let chunk = 0; chunk < bytes.length; chunk += 64) {
    const w = new Array(64);
    for (let i = 0; i < 16; i += 1) {
      const offset = chunk + i * 4;
      w[i] = (
        (bytes[offset] << 24) |
        (bytes[offset + 1] << 16) |
        (bytes[offset + 2] << 8) |
        bytes[offset + 3]
      ) >>> 0;
    }
    for (let i = 16; i < 64; i += 1) {
      const s0 = rightRotate(w[i - 15], 7) ^ rightRotate(w[i - 15], 18) ^ (w[i - 15] >>> 3);
      const s1 = rightRotate(w[i - 2], 17) ^ rightRotate(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
    }

    let a = hash[0];
    let b = hash[1];
    let c = hash[2];
    let d = hash[3];
    let e = hash[4];
    let f = hash[5];
    let g = hash[6];
    let h = hash[7];

    for (let i = 0; i < 64; i += 1) {
      const s1 = rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25);
      const ch = (e & f) ^ (~e & g);
      const temp1 = (h + s1 + ch + k[i] + w[i]) >>> 0;
      const s0 = rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const temp2 = (s0 + maj) >>> 0;

      h = g;
      g = f;
      f = e;
      e = (d + temp1) >>> 0;
      d = c;
      c = b;
      b = a;
      a = (temp1 + temp2) >>> 0;
    }

    hash[0] = (hash[0] + a) >>> 0;
    hash[1] = (hash[1] + b) >>> 0;
    hash[2] = (hash[2] + c) >>> 0;
    hash[3] = (hash[3] + d) >>> 0;
    hash[4] = (hash[4] + e) >>> 0;
    hash[5] = (hash[5] + f) >>> 0;
    hash[6] = (hash[6] + g) >>> 0;
    hash[7] = (hash[7] + h) >>> 0;
  }

  return hash.map(hex32).join("");
}

function rightRotate(value, bits) {
  return (value >>> bits) | (value << (32 - bits));
}

function hex32(value) {
  return (value >>> 0).toString(16).padStart(8, "0");
}

function getenv(name, defaultValue) {
  if (typeof process === "undefined" || !process.env || !process.env[name]) {
    return defaultValue;
  }
  return process.env[name];
}

function intEnv(name, defaultValue, minValue) {
  const raw = getenv(name, String(defaultValue));
  if (!/^[0-9]+$/.test(raw)) {
    throw new Error(`${name} must be an integer string`);
  }
  const value = Number(raw);
  if (!Number.isSafeInteger(value) || value < minValue) {
    throw new Error(`${name} must be >= ${minValue}`);
  }
  return value;
}

function assertLocalFixtureDb() {
  const dbName = db.getName();
  const mongoHost = getenv("MONGO_HOST", "");
  if (dbName !== "aandi_performance") {
    throw new Error(`Refusing to verify non-fixture database: ${dbName}`);
  }
  if (["localhost", "127.0.0.1", "mongodb"].indexOf(mongoHost) < 0) {
    throw new Error(`Refusing to verify non-local MongoDB host: ${mongoHost || "unknown"}`);
  }
}

function emitJson(value) {
  print(JSON.stringify(value, null, 2));
}
