const COURSE_ID = "perf_course_k6";
const COURSE_SLUG = "perf-k6";
const TARGET_ASSIGNMENT_ID = "10000000-0000-4000-8000-000000000001";

const mode = getenv("MONGO_PROFILE_MODE", "collect");
const fixtureAssignments = intEnv("FIXTURE_ASSIGNMENTS", 30, 1);
const fixtureEnrollments = intEnv("FIXTURE_ENROLLMENTS", 100, 1);
const fixtureSubmissionStatuses = intEnv("FIXTURE_SUBMISSION_STATUSES", 60, 0);
const runLabel = getenv("RUN_LABEL", "");

assertLocalFixtureDb();

if (mode === "start") {
  db.setProfilingLevel(0);
  db.system.profile.drop();
  db.setProfilingLevel(2, { slowms: 0 });
  emitJson({
    status: "profile_started",
    database: db.getName(),
    runLabel,
    profilingStatus: db.getProfilingStatus(),
  });
} else if (mode === "stop") {
  db.setProfilingLevel(0);
  emitJson({
    status: "profile_stopped",
    database: db.getName(),
    runLabel,
    profilingStatus: db.getProfilingStatus(),
  });
} else if (mode === "collect") {
  db.setProfilingLevel(0);
  emitJson(collectProfile());
} else if (mode === "explain") {
  emitJson({
    status: "explained",
    database: db.getName(),
    runLabel,
    explains: explainQueries(),
  });
} else {
  throw new Error(`Unknown MONGO_PROFILE_MODE: ${mode}`);
}

function collectProfile() {
  const entries = db.system.profile
    .find({
      ns: {
        $in: [
          `${db.getName()}.courses`,
          `${db.getName()}.course_enrollments`,
          `${db.getName()}.assignments`,
          `${db.getName()}.assignment_requirements`,
          `${db.getName()}.assignment_test_cases`,
          `${db.getName()}.assignment_submission_statuses`,
        ],
      },
    })
    .sort({ ts: 1 })
    .toArray();

  const byNamespace = {};
  let totalDocsExamined = 0;
  let totalKeysExamined = 0;
  let totalMillis = 0;
  for (const entry of entries) {
    const namespace = entry.ns || "unknown";
    if (!byNamespace[namespace]) {
      byNamespace[namespace] = {
        commandCount: 0,
        totalDocsExamined: 0,
        totalKeysExamined: 0,
        totalMillis: 0,
        operations: {},
      };
    }
    const docsExamined = numberOrZero(entry.docsExamined);
    const keysExamined = numberOrZero(entry.keysExamined);
    const millis = numberOrZero(entry.millis);
    const operation = entry.op || commandName(entry.command) || "unknown";

    byNamespace[namespace].commandCount += 1;
    byNamespace[namespace].totalDocsExamined += docsExamined;
    byNamespace[namespace].totalKeysExamined += keysExamined;
    byNamespace[namespace].totalMillis += millis;
    byNamespace[namespace].operations[operation] = (byNamespace[namespace].operations[operation] || 0) + 1;

    totalDocsExamined += docsExamined;
    totalKeysExamined += keysExamined;
    totalMillis += millis;
  }

  return {
    status: "profile_collected",
    database: db.getName(),
    mongoVersion: db.version(),
    runLabel,
    fixture: fixtureContext(),
    profile: {
      commandCount: entries.length,
      totalDocsExamined,
      totalKeysExamined,
      totalMillis,
      byNamespace,
    },
    explains: explainQueries(),
  };
}

function explainQueries() {
  const assignmentIds = assignmentIdRange(fixtureAssignments);
  const queries = {
    course_by_slug: explainFind(
      "courses",
      db.courses.find({ slug: COURSE_SLUG })
    ),
    enrollment_by_course_user: explainFind(
      "course_enrollments",
      db.course_enrollments.find({ courseId: COURSE_ID, userId: userIdAt(1) })
    ),
    assignment_list_by_course: explainFind(
      "assignments",
      db.assignments.find({ courseId: COURSE_ID }).sort({ weekNo: 1, orderInWeek: 1 })
    ),
    assignment_detail_by_id_course: explainFind(
      "assignments",
      db.assignments.find({ _id: TARGET_ASSIGNMENT_ID, courseId: COURSE_ID })
    ),
    requirements_by_assignment_in: explainFind(
      "assignment_requirements",
      db.assignment_requirements.find({ assignmentId: { $in: assignmentIds } })
    ),
    testcases_by_assignment_in: explainFind(
      "assignment_test_cases",
      db.assignment_test_cases.find({ assignmentId: { $in: assignmentIds } })
    ),
  };

  let totalDocsExamined = 0;
  let totalKeysExamined = 0;
  for (const key in queries) {
    totalDocsExamined += queries[key].totalDocsExamined;
    totalKeysExamined += queries[key].totalKeysExamined;
  }

  return {
    totalDocsExamined,
    totalKeysExamined,
    queries,
  };
}

function explainFind(collection, cursor) {
  const explain = cursor.explain("executionStats");
  const stats = explain.executionStats || {};
  return {
    collection,
    totalDocsExamined: numberOrZero(stats.totalDocsExamined),
    totalKeysExamined: numberOrZero(stats.totalKeysExamined),
    nReturned: numberOrZero(stats.nReturned),
    executionTimeMillis: numberOrZero(stats.executionTimeMillis),
    winningPlanStage: winningPlanStage(explain.queryPlanner && explain.queryPlanner.winningPlan),
    indexName: winningPlanIndexName(explain.queryPlanner && explain.queryPlanner.winningPlan),
  };
}

function winningPlanStage(plan) {
  if (!plan) {
    return "unknown";
  }
  if (plan.stage) {
    return plan.stage;
  }
  if (plan.inputStage) {
    return winningPlanStage(plan.inputStage);
  }
  if (plan.queryPlan) {
    return winningPlanStage(plan.queryPlan);
  }
  return "unknown";
}

function winningPlanIndexName(plan) {
  if (!plan) {
    return null;
  }
  if (plan.indexName) {
    return plan.indexName;
  }
  if (plan.inputStage) {
    return winningPlanIndexName(plan.inputStage);
  }
  if (plan.queryPlan) {
    return winningPlanIndexName(plan.queryPlan);
  }
  return null;
}

function commandName(command) {
  if (!command) {
    return "";
  }
  for (const key in command) {
    return key;
  }
  return "";
}

function fixtureContext() {
  return {
    courses: 1,
    assignments: fixtureAssignments,
    enrollments: fixtureEnrollments,
    submissionStatuses: Math.min(fixtureSubmissionStatuses, fixtureEnrollments),
    courseSlug: COURSE_SLUG,
    assignmentId: TARGET_ASSIGNMENT_ID,
  };
}

function assignmentIdRange(count) {
  const values = [];
  for (let i = 1; i <= count; i += 1) {
    values.push(assignmentIdAt(i));
  }
  return values;
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

function numberOrZero(value) {
  const number = Number(value || 0);
  return Number.isFinite(number) ? number : 0;
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
  const parsed = parseMongoUri(getenv("MONGO_URI", "mongodb://localhost:27017/aandi_performance"));
  if (dbName !== "aandi_performance" || parsed.database !== "aandi_performance") {
    throw new Error(`Refusing to profile non-fixture database: ${dbName}`);
  }
  if (["localhost", "127.0.0.1"].indexOf(parsed.host) < 0) {
    throw new Error(`Refusing to profile non-local MongoDB host: ${parsed.host}`);
  }
}

function parseMongoUri(uri) {
  if (!uri || uri.indexOf("mongodb://") !== 0) {
    throw new Error("MONGO_URI must start with mongodb://");
  }
  const rest = uri.substring("mongodb://".length);
  if (rest.indexOf("@") >= 0) {
    throw new Error("MONGO_URI credentials are not allowed for local profile collection.");
  }
  const hostPort = rest.split("/")[0];
  const databasePart = rest.substring(hostPort.length + 1).split("?")[0].split("/")[0];
  const host = hostPort.split(":")[0].toLowerCase();
  return {
    host,
    database: databasePart,
  };
}

function emitJson(value) {
  print(JSON.stringify(value, null, 2));
}
