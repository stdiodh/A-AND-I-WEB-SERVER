"use strict";

(function exposeCatalog(root) {
    const catalog = {
        migrationId: "V001__baseline_indexes",
        historyCollection: "_aandi_schema_migrations",
        indexes: [
            {
                collection: "courses",
                name: "ux_course_slug",
                key: { slug: 1 },
                unique: true,
                keyTypes: { slug: "string" },
            },
            {
                collection: "courses",
                name: "ix_course_created_at_desc",
                key: { createdAt: -1 },
                unique: false,
            },
            {
                collection: "users",
                name: "ux_user_public_code",
                key: { publicCode: 1 },
                unique: true,
                keyTypes: { publicCode: "string" },
            },
            {
                collection: "course_enrollments",
                name: "ux_course_enrollment",
                key: { courseId: 1, userId: 1 },
                unique: true,
                keyTypes: { courseId: "string", userId: "string" },
            },
            {
                collection: "course_enrollments",
                name: "ix_course_enrollment_user_status",
                key: { userId: 1, status: 1 },
                unique: false,
            },
            {
                collection: "course_weeks",
                name: "ux_course_week",
                key: { courseId: 1, weekNo: 1 },
                unique: true,
                keyTypes: { courseId: "string", weekNo: "int" },
            },
            {
                collection: "assignments",
                name: "ux_assignment_course_week_order",
                key: { courseId: 1, weekNo: 1, orderInWeek: 1 },
                unique: true,
                keyTypes: { courseId: "string", weekNo: "int", orderInWeek: "int" },
            },
            {
                collection: "assignments",
                name: "ux_assignment_course_origin",
                key: { courseId: 1, originAssignmentId: 1 },
                unique: true,
                partialFilterExpression: { originAssignmentId: { $type: "string" } },
                keyTypes: { courseId: "string", originAssignmentId: "string" },
                nullableKeyFields: ["originAssignmentId"],
            },
            {
                collection: "assignments",
                name: "ux_assignment_course_copy_fingerprint",
                key: { courseId: 1, copyFingerprint: 1 },
                unique: true,
                partialFilterExpression: { copyFingerprint: { $type: "string" } },
                keyTypes: { courseId: "string", copyFingerprint: "string" },
                nullableKeyFields: ["copyFingerprint"],
            },
            {
                collection: "assignment_requirements",
                name: "ux_assignment_requirement_sort",
                key: { assignmentId: 1, sortOrder: 1 },
                unique: true,
                keyTypes: { assignmentId: "string", sortOrder: "int" },
            },
            {
                collection: "assignment_test_cases",
                name: "ux_assignment_test_case_seq",
                key: { assignmentId: 1, seq: 1 },
                unique: true,
                keyTypes: { assignmentId: "string", seq: "int" },
            },
            {
                collection: "assignment_deliveries",
                name: "ux_assignment_delivery_user",
                key: { assignmentId: 1, userId: 1 },
                unique: true,
                keyTypes: { assignmentId: "string", userId: "string" },
            },
            {
                collection: "assignment_submission_statuses",
                name: "ux_assignment_submission_status_assignment_public_code",
                key: { assignmentId: 1, publicCode: 1 },
                unique: true,
                keyTypes: { assignmentId: "string", publicCode: "string" },
            },
        ],
    };

    root.MongoIndexV001Catalog = catalog;
    if (typeof module !== "undefined" && module.exports) {
        module.exports = catalog;
    }
})(globalThis);
