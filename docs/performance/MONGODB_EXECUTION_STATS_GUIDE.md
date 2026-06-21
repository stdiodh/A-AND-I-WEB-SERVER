# MongoDB ExecutionStats Guide

Use this only against the local `aandi_performance` fixture database. Do not run explain plans against production or real user data.

## Setup

```bash
mongosh mongodb://localhost:27017/aandi_performance
```

Default fixture values:

```javascript
const courseSlug = "perf-k6";
const courseId = "perf_course_k6";
const userId = "00000000-0000-4000-8000-000000000001";
const assignmentId = "10000000-0000-4000-8000-000000000001";
```

## Student Assignment List

Repository path:

- `CourseRepository.findBySlug(slug)`
- `CourseEnrollmentRepository.findByCourseIdAndUserId(courseId, userId)`
- `AssignmentRepository.findAllByCourseId(courseId)`
- `AssignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId)`
- `AssignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId)`

The Controller accepts `status=PUBLISHED`, but the current service validates that value and filters effective published status in application code. Do not add `{ status: "PUBLISHED" }` to the Mongo explain query unless the Repository query changes.

```javascript
db.courses.find({ slug: courseSlug }).explain("executionStats")
db.course_enrollments.find({ courseId, userId }).explain("executionStats")
db.assignments.find({ courseId }).sort({ weekNo: 1, orderInWeek: 1 }).explain("executionStats")
```

For a representative item returned by the list:

```javascript
db.assignment_requirements.find({ assignmentId }).sort({ sortOrder: 1 }).explain("executionStats")
db.assignment_test_cases.find({ assignmentId }).sort({ seq: 1 }).explain("executionStats")
```

## Student Assignment Detail

Repository path:

- `CourseRepository.findBySlug(slug)`
- `CourseEnrollmentRepository.findByCourseIdAndUserId(courseId, userId)`
- `AssignmentRepository.findByIdAndCourseId(id, courseId)`
- `AssignmentRequirementRepository.findAllByAssignmentIdOrderBySortOrder(assignmentId)`
- `AssignmentTestCaseRepository.findAllByAssignmentIdOrderBySeq(assignmentId)`

```javascript
db.courses.find({ slug: courseSlug }).explain("executionStats")
db.course_enrollments.find({ courseId, userId }).explain("executionStats")
db.assignments.find({ _id: assignmentId, courseId }).explain("executionStats")
db.assignment_requirements.find({ assignmentId }).sort({ sortOrder: 1 }).explain("executionStats")
db.assignment_test_cases.find({ assignmentId }).sort({ seq: 1 }).explain("executionStats")
```

## Admin Submission Statuses

Repository path:

- `CourseRepository.findBySlug(slug)`
- `AssignmentRepository.findByIdAndCourseId(id, courseId)`
- `CourseEnrollmentRepository.findAllByCourseId(courseId)`
- `AssignmentSubmissionStatusProjectionRepository.findAllByAssignmentId(assignmentId)`
- `ReportUserRepository.findAllById(enrollmentUserIds)`

```javascript
db.courses.find({ slug: courseSlug }).explain("executionStats")
db.assignments.find({ _id: assignmentId, courseId }).explain("executionStats")
db.course_enrollments.find({ courseId }).explain("executionStats")
db.assignment_submission_statuses.find({ assignmentId }).explain("executionStats")
db.users.find({ _id: { $in: db.course_enrollments.find({ courseId }).map(e => e.userId) } }).explain("executionStats")
```

## Record Fields

For every explain output, record:

- `executionStats.nReturned`
- `executionStats.totalDocsExamined`
- `executionStats.totalKeysExamined`
- `executionStats.executionTimeMillis`
- `queryPlanner.winningPlan`
- index name from the winning plan

Do not infer index usage from a different filter than the actual Repository path.
