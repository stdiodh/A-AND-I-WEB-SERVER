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

const assignmentCount = intEnv("FIXTURE_ASSIGNMENTS", 30, 10);
const enrollmentCount = intEnv("FIXTURE_ENROLLMENTS", 100, 1);
const submittedCount = Math.min(intEnv("FIXTURE_SUBMISSION_STATUSES", 60, 0), enrollmentCount);
if (enrollmentCount > 999) {
  throw new Error("FIXTURE_ENROLLMENTS must be <= 999 because publicCode uses #SP001 format.");
}
const now = new Date("2026-06-20T00:00:00.000Z");
const startAt = new Date("2026-01-01T00:00:00.000Z");
const endAt = new Date("2027-01-01T00:00:00.000Z");

ensureIndexes();
seedCourse();
seedWeeks();
seedUsersAndEnrollments(enrollmentCount);
seedAssignments(assignmentCount);
seedSubmissionStatuses(submittedCount);

printjson({
  status: "seeded",
  database: db.getName(),
  courseSlug: COURSE_SLUG,
  assignmentId: TARGET_ASSIGNMENT_ID,
  userId: userIdAt(1),
  adminUserId: ADMIN_USER_ID,
  counts: {
    courses: 1,
    weeks: WEEK_COUNT,
    assignments: assignmentCount,
    enrollments: enrollmentCount,
    submissionStatuses: submittedCount,
  },
});

function seedCourse() {
  db.courses.replaceOne(
    { _id: COURSE_ID },
    {
      _id: COURSE_ID,
      _class: "course",
      slug: COURSE_SLUG,
      fieldTag: "SP",
      startDate: new Date("2026-01-01T00:00:00.000Z"),
      endDate: new Date("2027-01-01T00:00:00.000Z"),
      metadata: {
        title: "PERF K6 Course",
        description: "Synthetic local k6 fixture",
        phase: "BASIC",
        attributes: { fixtureId: FIXTURE_ID },
      },
      status: "PUBLISHED",
      createdAt: now,
      updatedAt: now,
    },
    { upsert: true }
  );
}

function seedWeeks() {
  for (let weekNo = 1; weekNo <= WEEK_COUNT; weekNo += 1) {
    db.course_weeks.replaceOne(
      { _id: weekIdAt(weekNo) },
      {
        _id: weekIdAt(weekNo),
        _class: "courseWeek",
        courseId: COURSE_ID,
        weekNo,
        title: `PERF K6 Week ${weekNo}`,
        startDate: new Date(`2026-01-${pad(weekNo * 7 - 6, 2)}T00:00:00.000Z`),
        endDate: new Date(`2026-01-${pad(weekNo * 7, 2)}T00:00:00.000Z`),
        createdAt: now,
        updatedAt: now,
      },
      { upsert: true }
    );
  }
}

function seedUsersAndEnrollments(count) {
  for (let i = 1; i <= count; i += 1) {
    const userId = userIdAt(i);
    const publicCode = publicCodeAt(i);
    db.users.replaceOne(
      { _id: userId },
      {
        _id: userId,
        _class: "reportUser",
        publicCode,
        username: `perf_user_${pad(i, 3)}`,
        role: "USER",
        nickname: `Perf User ${i}`,
        profileImageUrl: null,
        syncedAt: now,
        updatedAt: now,
      },
      { upsert: true }
    );
    db.course_enrollments.replaceOne(
      { _id: `perf_enrollment_${pad(i, 4)}` },
      {
        _id: `perf_enrollment_${pad(i, 4)}`,
        _class: "courseEnrollment",
        courseId: COURSE_ID,
        userId,
        publicCode,
        username: `perf_user_${pad(i, 3)}`,
        status: "ENABLED",
        joinedAt: now,
        bannedAt: null,
        banReason: null,
        updatedAt: now,
      },
      { upsert: true }
    );
  }

  db.users.replaceOne(
    { _id: ADMIN_USER_ID },
    {
      _id: ADMIN_USER_ID,
      _class: "reportUser",
      publicCode: "#SP999",
      username: "perf_admin",
      role: "ADMIN",
      nickname: "Perf Admin",
      profileImageUrl: null,
      syncedAt: now,
      updatedAt: now,
    },
    { upsert: true }
  );
}

function seedAssignments(count) {
  for (let i = 1; i <= count; i += 1) {
    const assignmentId = assignmentIdAt(i);
    const weekNo = Math.min(WEEK_COUNT, Math.floor((i - 1) / 10) + 1);
    const orderInWeek = ((i - 1) % 10) + 1;
    db.assignments.replaceOne(
      { _id: assignmentId },
      {
        _id: assignmentId,
        _class: "assignment",
        courseId: COURSE_ID,
        courseSlug: COURSE_SLUG,
        createdBy: ADMIN_USER_ID,
        weekNo,
        orderInWeek,
        startAt,
        endAt,
        metadata: {
          title: `PERF K6 Assignment ${pad(i, 3)}`,
          difficulty: "MID",
          description: "Synthetic local assignment for k6 read tests",
          timeLimitMinutes: 60,
          learningGoals: ["Measure stable read path"],
          codeTemplates: [],
        },
        status: "PUBLISHED",
        createdAt: now,
        updatedAt: now,
        publishedAt: startAt,
        originAssignmentId: null,
        originCourseSlug: null,
        copyFingerprint: null,
      },
      { upsert: true }
    );

    db.assignment_requirements.replaceOne(
      { _id: `perf_requirement_${pad(i, 4)}_1` },
      {
        _id: `perf_requirement_${pad(i, 4)}_1`,
        _class: "assignmentRequirement",
        assignmentId,
        sortOrder: 1,
        requirementText: "Return the expected output for the synthetic case.",
        createdAt: now,
      },
      { upsert: true }
    );

    db.assignment_test_cases.replaceOne(
      { _id: `perf_testcase_${pad(i, 4)}_public` },
      {
        _id: `perf_testcase_${pad(i, 4)}_public`,
        _class: "assignmentTestCase",
        assignmentId,
        seq: 1,
        inputValues: [`PERF_INPUT_${pad(i, 3)}`],
        outputText: PUBLIC_MARKER,
        visibility: "PUBLIC",
        description: "Visible synthetic testcase",
        createdAt: now,
      },
      { upsert: true }
    );

    db.assignment_test_cases.replaceOne(
      { _id: `perf_testcase_${pad(i, 4)}_public_2` },
      {
        _id: `perf_testcase_${pad(i, 4)}_public_2`,
        _class: "assignmentTestCase",
        assignmentId,
        seq: 2,
        inputValues: [`PERF_INPUT_EXTRA_${pad(i, 3)}`],
        outputText: `${PUBLIC_MARKER}_EXTRA`,
        visibility: "PUBLIC",
        description: "Second visible synthetic testcase",
        createdAt: now,
      },
      { upsert: true }
    );

    db.assignment_test_cases.replaceOne(
      { _id: `perf_testcase_${pad(i, 4)}_private` },
      {
        _id: `perf_testcase_${pad(i, 4)}_private`,
        _class: "assignmentTestCase",
        assignmentId,
        seq: PRIVATE_SEQ,
        inputValues: [`PERF_PRIVATE_INPUT_${pad(i, 3)}`],
        outputText: PRIVATE_MARKER,
        visibility: "HIDDEN",
        description: "Hidden synthetic testcase that must not leak",
        createdAt: now,
      },
      { upsert: true }
    );
  }
}

function seedSubmissionStatuses(count) {
  for (let i = 1; i <= count; i += 1) {
    db.assignment_submission_statuses.replaceOne(
      { _id: `perf_submission_status_${pad(i, 4)}` },
      {
        _id: `perf_submission_status_${pad(i, 4)}`,
        _class: "assignmentSubmissionStatusProjection",
        assignmentId: TARGET_ASSIGNMENT_ID,
        publicCode: publicCodeAt(i),
        submitted: true,
        firstCompletedAt: now,
        lastCompletedAt: now,
        latestScore: 100,
        latestPassedCases: 1,
        latestTotalCases: 1,
        lastEventTimestamp: now,
        createdAt: now,
        updatedAt: now,
        version: NumberLong("1"),
      },
      { upsert: true }
    );
  }
}

function weekIdAt(index) {
  return `perf_week_k6_${index}`;
}

function ensureIndexes() {
  db.courses.createIndex({ slug: 1 }, { unique: true, name: "slug" });
  db.course_weeks.createIndex({ courseId: 1, weekNo: 1 }, { unique: true, name: "ux_course_week" });
  db.course_enrollments.createIndex({ courseId: 1, userId: 1 }, { unique: true, name: "ux_course_enrollment" });
  db.assignments.createIndex({ courseId: 1, weekNo: 1, orderInWeek: 1 }, { unique: true, name: "ux_assignment_course_week_order" });
  db.assignment_requirements.createIndex({ assignmentId: 1, sortOrder: 1 }, { unique: true, name: "ux_assignment_requirement_sort" });
  db.assignment_test_cases.createIndex({ assignmentId: 1, seq: 1 }, { unique: true, name: "ux_assignment_test_case_seq" });
  db.assignment_submission_statuses.createIndex({ assignmentId: 1, publicCode: 1 }, { unique: true, name: "ux_assignment_submission_status_assignment_public_code" });
  db.assignment_submission_statuses.createIndex({ assignmentId: 1 }, { name: "assignmentId" });
  db.assignment_submission_statuses.createIndex({ publicCode: 1 }, { name: "publicCode" });
  db.users.createIndex({ publicCode: 1 }, { unique: true, name: "publicCode" });
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
    throw new Error(`Refusing to seed non-fixture database: ${dbName}`);
  }
  if (["localhost", "127.0.0.1", "mongodb"].indexOf(mongoHost) < 0) {
    throw new Error(`Refusing to seed non-local MongoDB host: ${mongoHost || "unknown"}`);
  }
}
