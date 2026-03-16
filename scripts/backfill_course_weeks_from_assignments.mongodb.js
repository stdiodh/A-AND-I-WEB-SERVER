const DB_NAME = typeof dbName !== "undefined" ? dbName : "aandi";
const COURSE_SLUG = typeof courseSlug !== "undefined" ? courseSlug : "3rd-cs";

function toUtcMidnight(dateValue) {
  const date = new Date(dateValue);
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
}

const database = db.getSiblingDB(DB_NAME);
const course = database.courses.findOne({ slug: COURSE_SLUG });

if (!course) {
  throw new Error(`course not found: ${COURSE_SLUG}`);
}

const courseId = String(course._id);

const weeks = database.assignments
  .aggregate([
    { $match: { courseSlug: COURSE_SLUG } },
    {
      $group: {
        _id: "$weekNo",
        minStartAt: { $min: "$startAt" },
        maxEndAt: { $max: "$endAt" },
      },
    },
    { $sort: { _id: 1 } },
  ])
  .toArray();

if (weeks.length === 0) {
  printjson({
    success: false,
    message: `no assignments found for courseSlug=${COURSE_SLUG}`,
  });
} else {
  let insertedOrUpdated = 0;

  weeks.forEach((week) => {
    const startDate = toUtcMidnight(week.minStartAt);
    const endDate = toUtcMidnight(week.maxEndAt);

    const result = database.course_weeks.updateOne(
      { courseId: courseId, weekNo: week._id },
      {
        $set: {
          courseId: courseId,
          title: `${week._id}주차`,
          startDate,
          endDate,
          updatedAt: new Date(),
        },
        $setOnInsert: {
          weekNo: week._id,
          createdAt: new Date(),
        },
      },
      { upsert: true }
    );

    insertedOrUpdated +=
      (result.modifiedCount || 0) + (result.upsertedCount || 0) + (result.matchedCount || 0);
  });

  printjson({
    success: true,
    courseId: courseId,
    courseSlug: COURSE_SLUG,
    weekCount: weeks.length,
    insertedOrUpdated,
  });

  print("generated course_weeks:");
  printjson(
    database.course_weeks
      .find({ courseId: courseId }, { courseId: 1, weekNo: 1, title: 1, startDate: 1, endDate: 1 })
      .sort({ weekNo: 1 })
      .toArray()
  );
}
