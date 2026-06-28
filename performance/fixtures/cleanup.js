const COURSE_ID = "perf_course_k6";
const COURSE_SLUG = "perf-k6";
const WEEK_COUNT = 3;
const ADMIN_USER_ID = "00000000-0000-4000-8000-999999999999";
const TARGET_ASSIGNMENT_ID = "10000000-0000-4000-8000-000000000001";

assertLocalFixtureDb();

const cleanupAssignments = Math.max(intEnv("FIXTURE_ASSIGNMENTS", 30, 1), intEnv("FIXTURE_CLEANUP_MAX_ASSIGNMENTS", 500, 1));
const cleanupEnrollments = Math.max(intEnv("FIXTURE_ENROLLMENTS", 100, 1), intEnv("FIXTURE_CLEANUP_MAX_ENROLLMENTS", 999, 1));
if (cleanupEnrollments > 999) {
  throw new Error("FIXTURE_CLEANUP_MAX_ENROLLMENTS must be <= 999 because publicCode uses #SP001 format.");
}

const assignmentIds = [];
for (let i = 1; i <= cleanupAssignments; i += 1) {
  assignmentIds.push(assignmentIdAt(i));
}

const enrollmentIds = [];
const userIds = [ADMIN_USER_ID];
const publicCodes = [];
const weekIds = [];
for (let i = 1; i <= WEEK_COUNT; i += 1) {
  weekIds.push(`perf_week_k6_${i}`);
}
for (let i = 1; i <= cleanupEnrollments; i += 1) {
  enrollmentIds.push(`perf_enrollment_${pad(i, 4)}`);
  userIds.push(userIdAt(i));
  publicCodes.push(publicCodeAt(i));
}

const result = {
  assignment_submission_statuses: db.assignment_submission_statuses.deleteMany({
    assignmentId: TARGET_ASSIGNMENT_ID,
    publicCode: { $in: publicCodes },
  }).deletedCount,
  assignment_test_cases: db.assignment_test_cases.deleteMany({ assignmentId: { $in: assignmentIds } }).deletedCount,
  assignment_requirements: db.assignment_requirements.deleteMany({ assignmentId: { $in: assignmentIds } }).deletedCount,
  assignments: db.assignments.deleteMany({ _id: { $in: assignmentIds }, courseId: COURSE_ID }).deletedCount,
  course_enrollments: db.course_enrollments.deleteMany({ _id: { $in: enrollmentIds }, courseId: COURSE_ID }).deletedCount,
  users: db.users.deleteMany({ _id: { $in: userIds } }).deletedCount,
  course_weeks: db.course_weeks.deleteMany({ _id: { $in: weekIds }, courseId: COURSE_ID }).deletedCount,
  courses: db.courses.deleteMany({ _id: COURSE_ID, slug: COURSE_SLUG }).deletedCount,
};

emitJson({
  status: "cleaned",
  database: db.getName(),
  deleted: result,
});

function assignmentIdAt(index) {
  if (index === 1) {
    return TARGET_ASSIGNMENT_ID;
  }
  return `10000000-0000-4000-8000-${pad(index, 12)}`;
}

function userIdAt(index) {
  return `00000000-0000-4000-8000-${pad(index, 12)}`;
}

function publicCodeAt(index) {
  return `#SP${pad(index, 3)}`;
}

function pad(value, width) {
  return String(value).padStart(width, "0");
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
    throw new Error(`Refusing to cleanup non-fixture database: ${dbName}`);
  }
  if (["localhost", "127.0.0.1", "mongodb"].indexOf(mongoHost) < 0) {
    throw new Error(`Refusing to cleanup non-local MongoDB host: ${mongoHost || "unknown"}`);
  }
}

function emitJson(value) {
  print(JSON.stringify(value, null, 2));
}
