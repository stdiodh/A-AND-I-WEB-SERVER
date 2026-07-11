"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");

const catalog = require("./v001-index-catalog.js");
const migration = require("./v001-index-lib.js");

function clone(value) {
    return JSON.parse(JSON.stringify(value));
}

function findBsonTypeField(value) {
    if (Array.isArray(value)) {
        return value.map(findBsonTypeField).find(Boolean);
    }
    if (value && typeof value === "object") {
        if (typeof value.$type === "string" && value.$type.startsWith("$")) {
            return value.$type.slice(1);
        }
        return Object.values(value).map(findBsonTypeField).find(Boolean);
    }
    return null;
}

class FakeCursor {
    constructor(values) {
        this.values = values;
    }

    limit(limit) {
        this.values = this.values.slice(0, limit);
        return this;
    }

    toArray() {
        return clone(this.values);
    }
}

class FakeCollection {
    constructor(database, name, state = {}) {
        this.database = database;
        this.name = name;
        this.indexes = clone(state.indexes || [{ name: "_id_", key: { _id: 1 } }]);
        this.duplicates = clone(state.duplicates || {});
        this.invalidTypeFields = new Set(state.invalidTypeFields || []);
        this.marker = state.marker || null;
        this.markerAfterUpdate = state.markerAfterUpdate;
        this.createError = state.createError || null;
    }

    getIndexes() {
        return clone(this.indexes);
    }

    aggregate(pipeline) {
        const groupStage = pipeline.find((stage) => stage.$group);
        const key = Object.keys(groupStage.$group._id).join(",");
        const counts = this.duplicates[key] || [];
        return new FakeCursor(counts.map((count) => ({ count })));
    }

    find(query) {
        const field = findBsonTypeField(query);
        return new FakeCursor(this.invalidTypeFields.has(field) ? [{ _id: "hidden" }] : []);
    }

    findOne(query) {
        return query._id === catalog.migrationId ? this.marker : null;
    }

    createIndex(key, options) {
        if (this.createError) {
            throw this.createError;
        }
        this.database.present.add(this.name);
        this.database.writes.push({ type: "createIndex", collection: this.name, key, options });
        this.indexes.push({ key: clone(key), ...clone(options) });
        return options.name;
    }

    updateOne(query, update, options) {
        this.database.present.add(this.name);
        this.database.writes.push({ type: "updateOne", collection: this.name, query, update, options });
        if (this.markerAfterUpdate !== undefined) {
            this.marker = clone(this.markerAfterUpdate);
        } else if (!this.marker) {
            this.marker = { _id: query._id, ...clone(update.$setOnInsert) };
        }
        return { acknowledged: true };
    }
}

class FakeDatabase {
    constructor(name = "aandi", collections = {}) {
        this.name = name;
        this.present = new Set(Object.keys(collections));
        this.collections = new Map();
        this.writes = [];
        Object.entries(collections).forEach(([collectionName, state]) => {
            this.collections.set(collectionName, new FakeCollection(this, collectionName, state));
        });
    }

    getName() {
        return this.name;
    }

    getCollectionNames() {
        return Array.from(this.present);
    }

    getCollection(name) {
        if (!this.collections.has(name)) {
            this.collections.set(name, new FakeCollection(this, name));
        }
        return this.collections.get(name);
    }
}

function expectedIndex(spec, name = spec.name) {
    return {
        name,
        key: clone(spec.key),
        ...(spec.unique ? { unique: true } : {}),
        ...(spec.partialFilterExpression
            ? { partialFilterExpression: clone(spec.partialFilterExpression) }
            : {}),
    };
}

function databaseWithAllIndexes({ marker = true, alias = null } = {}) {
    const states = {};
    catalog.indexes.forEach((spec) => {
        const state = states[spec.collection] || {
            indexes: [{ name: "_id_", key: { _id: 1 } }],
        };
        state.indexes.push(expectedIndex(spec, alias?.[spec.name] || spec.name));
        states[spec.collection] = state;
    });
    if (marker) {
        states[catalog.historyCollection] = {
            marker: {
                _id: catalog.migrationId,
                version: 1,
                indexNames: catalog.indexes.map((spec) => spec.name),
            },
        };
    }
    return new FakeDatabase("aandi", states);
}

const guardedEnv = { MONGO_INDEX_EXPECTED_DATABASE: "aandi" };
const applyEnv = { ...guardedEnv, MONGO_INDEX_APPLY: "YES" };

test("catalog defines the reviewed index contract", () => {
    const signature = (spec) => [
        spec.collection,
        spec.name,
        Object.entries(spec.key).map(([field, direction]) => `${field}:${direction}`).join(","),
        spec.unique ? "unique" : "nonunique",
        JSON.stringify(spec.partialFilterExpression || null),
        Object.entries(spec.keyTypes || {}).map(([field, type]) => `${field}:${type}`).join(","),
        (spec.nullableKeyFields || []).join(","),
    ].join("|");

    assert.deepEqual(catalog.indexes.map(signature), [
        "courses|ux_course_slug|slug:1|unique|null|slug:string|",
        "courses|ix_course_created_at_desc|createdAt:-1|nonunique|null||",
        "users|ux_user_public_code|publicCode:1|unique|null|publicCode:string|",
        "course_enrollments|ux_course_enrollment|courseId:1,userId:1|unique|null|courseId:string,userId:string|",
        "course_enrollments|ix_course_enrollment_user_status|userId:1,status:1|nonunique|null||",
        "course_weeks|ux_course_week|courseId:1,weekNo:1|unique|null|courseId:string,weekNo:int|",
        "assignments|ux_assignment_course_week_order|courseId:1,weekNo:1,orderInWeek:1|unique|null|courseId:string,weekNo:int,orderInWeek:int|",
        "assignments|ux_assignment_course_origin|courseId:1,originAssignmentId:1|unique|{\"originAssignmentId\":{\"$type\":\"string\"}}|courseId:string,originAssignmentId:string|originAssignmentId",
        "assignments|ux_assignment_course_copy_fingerprint|courseId:1,copyFingerprint:1|unique|{\"copyFingerprint\":{\"$type\":\"string\"}}|courseId:string,copyFingerprint:string|copyFingerprint",
        "assignment_requirements|ux_assignment_requirement_sort|assignmentId:1,sortOrder:1|unique|null|assignmentId:string,sortOrder:int|",
        "assignment_test_cases|ux_assignment_test_case_seq|assignmentId:1,seq:1|unique|null|assignmentId:string,seq:int|",
        "assignment_deliveries|ux_assignment_delivery_user|assignmentId:1,userId:1|unique|null|assignmentId:string,userId:string|",
        "assignment_submission_statuses|ux_assignment_submission_status_assignment_public_code|assignmentId:1,publicCode:1|unique|null|assignmentId:string,publicCode:string|",
    ]);
    assert.equal(new Set(catalog.indexes.map((spec) => spec.name)).size, 13);
    assert.equal(catalog.indexes.filter((spec) => spec.unique).length, 11);
    assert.equal(catalog.indexes.some((spec) =>
        spec.collection === "assignment_submission_statuses" &&
        Object.keys(spec.key).length === 1,
    ), false);
});

test("database and apply guards fail before any write", () => {
    const wrongDatabase = new FakeDatabase("wrong");
    assert.throws(
        () => migration.runPreflight(wrongDatabase, guardedEnv, catalog, () => {}),
        /database guard failed/,
    );
    assert.equal(wrongDatabase.writes.length, 0);

    const database = new FakeDatabase();
    assert.throws(
        () => migration.runApply(database, guardedEnv, catalog, () => {}),
        /MONGO_INDEX_APPLY=YES/,
    );
    assert.equal(database.writes.length, 0);
});

test("an incomplete unique-key type contract blocks an empty database", () => {
    const brokenCatalog = clone(catalog);
    delete brokenCatalog.indexes[0].keyTypes;
    const database = new FakeDatabase();

    assert.throws(
        () => migration.runPreflight(database, guardedEnv, brokenCatalog, () => {}),
        /preflight failed/,
    );
    assert.equal(database.writes.length, 0);
});

test("preflight reports duplicates without exposing values or writing", () => {
    const database = new FakeDatabase("aandi", {
        courses: { duplicates: { slug: [2, 4] } },
    });
    const outputs = [];

    assert.throws(
        () => migration.runPreflight(database, guardedEnv, catalog, (value) => outputs.push(value)),
        /preflight failed/,
    );
    assert.equal(database.writes.length, 0);

    const report = JSON.parse(outputs[0]);
    const issue = report.issues.find((candidate) => candidate.kind === "duplicate_values");
    assert.deepEqual(issue.sampleDocumentCounts, [2, 4]);
    assert.equal(outputs[0].includes("hidden"), false);
});

test("preflight rejects invalid BSON key types before multikey uniqueness checks", () => {
    const database = new FakeDatabase("aandi", {
        courses: { invalidTypeFields: ["slug"] },
        assignments: { invalidTypeFields: ["originAssignmentId"] },
    });
    const outputs = [];

    assert.throws(
        () => migration.runPreflight(database, guardedEnv, catalog, (value) => outputs.push(value)),
        /preflight failed/,
    );
    assert.equal(database.writes.length, 0);
    const issues = JSON.parse(outputs[0]).issues
        .filter((issue) => issue.kind === "invalid_bson_type");
    assert.deepEqual(issues.map((issue) => issue.field), ["slug", "originAssignmentId"]);
});

test("apply creates missing indexes, records history last, and reruns as a no-op", () => {
    const database = new FakeDatabase();

    const first = migration.runApply(database, applyEnv, catalog, () => {});
    assert.equal(first.status, "applied");
    assert.equal(first.created.length, 13);
    assert.equal(database.writes.filter((write) => write.type === "createIndex").length, 13);
    assert.equal(database.writes.at(-1).type, "updateOne");
    assert.equal(database.writes.at(-1).collection, catalog.historyCollection);

    const writeCount = database.writes.length;
    const second = migration.runApply(database, applyEnv, catalog, () => {});
    assert.equal(second.status, "already-applied");
    assert.equal(database.writes.length, writeCount);
});

test("apply refuses an incompatible same-key index before any write", () => {
    const database = new FakeDatabase("aandi", {
        courses: {
            indexes: [
                { name: "_id_", key: { _id: 1 } },
                { name: "legacy_slug", key: { slug: 1 } },
            ],
        },
    });

    assert.throws(
        () => migration.runApply(database, applyEnv, catalog, () => {}),
        /apply preflight failed/,
    );
    assert.equal(database.writes.length, 0);
});

test("apply leaves no marker on a mid-run failure and resumes safely", () => {
    const database = new FakeDatabase();
    const users = database.getCollection("users");
    users.createError = new Error("dup key: { publicCode: '#SECRET' }");
    const outputs = [];

    assert.throws(
        () => migration.runApply(database, applyEnv, catalog, (value) => outputs.push(value)),
        /index creation failed/,
    );
    assert.equal(database.writes.length, 2);
    assert.equal(database.writes[0].collection, "courses");
    assert.equal(database.writes[1].collection, "courses");
    assert.equal(database.present.has(catalog.historyCollection), false);
    assert.equal(outputs[0].includes("#SECRET"), false);
    assert.deepEqual(JSON.parse(outputs[0]).failed, {
        collection: "users",
        name: "ux_user_public_code",
        code: null,
        codeName: null,
    });

    users.createError = null;
    const resumed = migration.runApply(database, applyEnv, catalog, () => {});
    assert.equal(resumed.status, "applied");
    assert.equal(resumed.created.length, 11);
    assert.equal(database.writes.at(-1).collection, catalog.historyCollection);
});

test("verify accepts a compatible legacy name and remains read-only", () => {
    const database = databaseWithAllIndexes({
        alias: { ux_course_slug: "slug_1" },
    });

    const report = migration.runVerify(database, guardedEnv, catalog, () => {});
    assert.equal(report.status, "verified");
    assert.deepEqual(report.aliases, [{
        collection: "courses",
        expectedName: "ux_course_slug",
        actualName: "slug_1",
    }]);
    assert.equal(database.writes.length, 0);
});

test("verify requires both the marker and every reviewed index", () => {
    const database = databaseWithAllIndexes({ marker: false });
    database.getCollection("courses").indexes = [
        { name: "_id_", key: { _id: 1 } },
    ];

    assert.throws(
        () => migration.runVerify(database, guardedEnv, catalog, () => {}),
        /verification failed/,
    );
    assert.equal(database.writes.length, 0);
});

test("verify rejects an incompatible migration marker", () => {
    const database = databaseWithAllIndexes();
    database.getCollection(catalog.historyCollection).marker.version = 2;

    assert.throws(
        () => migration.runVerify(database, guardedEnv, catalog, () => {}),
        /verification failed/,
    );
    assert.equal(database.writes.length, 0);
});

test("apply re-reads and rejects a concurrently conflicting marker", () => {
    const database = new FakeDatabase();
    database.getCollection(catalog.historyCollection).markerAfterUpdate = {
        _id: catalog.migrationId,
        version: 2,
        indexNames: [],
    };

    assert.throws(
        () => migration.runApply(database, applyEnv, catalog, () => {}),
        /marker verification failed/,
    );
    assert.equal(database.writes.at(-1).type, "updateOne");
});
