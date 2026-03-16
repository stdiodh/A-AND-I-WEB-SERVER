const DB_NAME = typeof dbName !== "undefined" ? dbName : "aandi";
const COURSE_SLUG = typeof courseSlug !== "undefined" ? courseSlug : "3rd-cs";
const DEFAULT_TIME_LIMIT_MINUTES =
  typeof defaultTimeLimitMinutes !== "undefined" ? defaultTimeLimitMinutes : 60;

const database = db.getSiblingDB(DB_NAME);
const course = database.courses.findOne({ slug: COURSE_SLUG });

if (!course) {
  throw new Error(`course not found: ${COURSE_SLUG}`);
}

const courseId = String(course._id);
const assignments = database.assignments.find({ courseSlug: COURSE_SLUG }).toArray();

if (assignments.length === 0) {
  printjson({
    success: false,
    message: `no assignments found for courseSlug=${COURSE_SLUG}`,
  });
} else {
  let updatedAssignments = 0;
  let upsertedRequirements = 0;
  let upsertedExamples = 0;

  assignments.forEach((assignment) => {
    const assignmentId = String(assignment._id);
    const metadata = assignment.metadata || {};
    const legacyRequirements = Array.isArray(metadata.requirements) ? metadata.requirements : [];
    const legacyExamples = Array.isArray(metadata.examples) ? metadata.examples : [];

    const learningGoals = Array.isArray(metadata.learningGoals)
      ? metadata.learningGoals
          .map((goal) => {
            if (typeof goal === "string") return goal.trim();
            if (goal && typeof goal.learningGoalText === "string") return goal.learningGoalText.trim();
            return null;
          })
          .filter(Boolean)
      : [];

    database.assignments.updateOne(
      { _id: assignment._id },
      {
        $set: {
          courseId: courseId,
          metadata: {
            title: typeof metadata.title === "string" ? metadata.title : "",
            difficulty: metadata.difficulty || "LOW",
            description: typeof metadata.description === "string" ? metadata.description : "",
            timeLimitMinutes:
              typeof metadata.timeLimitMinutes === "number"
                ? metadata.timeLimitMinutes
                : DEFAULT_TIME_LIMIT_MINUTES,
            learningGoals: learningGoals,
            problemDetail: metadata.problemDetail || null,
            submissionGuide: metadata.submissionGuide || null,
            codeTemplates: Array.isArray(metadata.codeTemplates) ? metadata.codeTemplates : [],
            hiddenTestCases: Array.isArray(metadata.hiddenTestCases) ? metadata.hiddenTestCases : [],
            attributes: metadata.attributes || {},
          },
        },
      }
    );
    updatedAssignments += 1;

    legacyRequirements.forEach((requirement) => {
      const sortOrder =
        typeof requirement.sortOrder === "number"
          ? requirement.sortOrder
          : typeof requirement.seq === "number"
            ? requirement.seq
            : 1;
      const requirementText =
        typeof requirement.requirementText === "string"
          ? requirement.requirementText.trim()
          : typeof requirement.content === "string"
            ? requirement.content.trim()
            : "";

      if (!requirementText) {
        return;
      }

      database.assignment_requirements.updateOne(
        { assignmentId: assignmentId, sortOrder: sortOrder },
        {
          $set: {
            assignmentId: assignmentId,
            sortOrder: sortOrder,
            requirementText: requirementText,
          },
          $setOnInsert: {
            createdAt: new Date(),
          },
        },
        { upsert: true }
      );
      upsertedRequirements += 1;
    });

    legacyExamples.forEach((example) => {
      const seq = typeof example.seq === "number" ? example.seq : 1;
      const inputText =
        typeof example.inputText === "string"
          ? example.inputText
          : typeof example.input === "string"
            ? example.input
            : "";
      const outputText =
        typeof example.outputText === "string"
          ? example.outputText
          : typeof example.output === "string"
            ? example.output
            : "";

      if (!inputText || !outputText) {
        return;
      }

      database.assignment_examples.updateOne(
        { assignmentId: assignmentId, seq: seq },
        {
          $set: {
            assignmentId: assignmentId,
            seq: seq,
            inputText: inputText,
            outputText: outputText,
            description: example.description || null,
          },
          $setOnInsert: {
            createdAt: new Date(),
          },
        },
        { upsert: true }
      );
      upsertedExamples += 1;
    });
  });

  printjson({
    success: true,
    courseSlug: COURSE_SLUG,
    courseId: courseId,
    assignments: assignments.length,
    updatedAssignments: updatedAssignments,
    upsertedRequirements: upsertedRequirements,
    upsertedExamples: upsertedExamples,
  });
}
