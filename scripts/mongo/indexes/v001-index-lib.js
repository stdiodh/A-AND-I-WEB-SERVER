"use strict";

(function exposeLibrary(root, factory) {
    const library = factory();
    root.MongoIndexV001 = library;
    if (typeof module !== "undefined" && module.exports) {
        module.exports = library;
    }
})(globalThis, function createLibrary() {
    const DUPLICATE_SAMPLE_LIMIT = 20;

    function orderedDocumentEquals(left, right) {
        const leftEntries = Object.entries(left || {});
        const rightEntries = Object.entries(right || {});
        return leftEntries.length === rightEntries.length &&
            leftEntries.every(([key, value], index) => {
                const [rightKey, rightValue] = rightEntries[index] || [];
                return key === rightKey && value === rightValue;
            });
    }

    function stableValue(value) {
        if (Array.isArray(value)) {
            return value.map(stableValue);
        }
        if (value && typeof value === "object") {
            return Object.fromEntries(
                Object.keys(value)
                    .sort()
                    .map((key) => [key, stableValue(value[key])]),
            );
        }
        return value;
    }

    function deepEquals(left, right) {
        return JSON.stringify(stableValue(left)) === JSON.stringify(stableValue(right));
    }

    function semanticOptions(index) {
        return {
            unique: index.unique === true,
            sparse: index.sparse === true,
            partialFilterExpression: index.partialFilterExpression || null,
            collation: index.collation || null,
            expireAfterSeconds: index.expireAfterSeconds === undefined ? null : index.expireAfterSeconds,
            hidden: index.hidden === true,
        };
    }

    function expectedOptions(spec) {
        return {
            unique: spec.unique === true,
            sparse: false,
            partialFilterExpression: spec.partialFilterExpression || null,
            collation: null,
            expireAfterSeconds: null,
            hidden: false,
        };
    }

    function isCompatibleIndex(index, spec) {
        return orderedDocumentEquals(index.key, spec.key) &&
            deepEquals(semanticOptions(index), expectedOptions(spec));
    }

    function duplicatePipeline(spec) {
        const groupKey = {};
        Object.keys(spec.key).forEach((field) => {
            groupKey[field] = `$${field}`;
        });

        const pipeline = [];
        if (spec.partialFilterExpression) {
            pipeline.push({ $match: spec.partialFilterExpression });
        }
        pipeline.push(
            { $group: { _id: groupKey, count: { $sum: 1 } } },
            { $match: { count: { $gt: 1 } } },
            { $project: { _id: 0, count: 1 } },
            { $limit: DUPLICATE_SAMPLE_LIMIT },
        );
        return pipeline;
    }

    function unexpectedBsonTypeQuery(field, expectedType, nullable) {
        const bsonType = { $type: `$${field}` };
        if (!nullable) {
            return { $expr: { $ne: [bsonType, expectedType] } };
        }
        return {
            $expr: {
                $not: [{
                    $in: [bsonType, ["missing", "null", expectedType]],
                }],
            },
        };
    }

    function createOptions(spec) {
        const options = { name: spec.name };
        if (spec.unique) {
            options.unique = true;
        }
        if (spec.partialFilterExpression) {
            options.partialFilterExpression = spec.partialFilterExpression;
        }
        return options;
    }

    function describeIndex(index) {
        return {
            name: index.name,
            key: index.key,
            options: semanticOptions(index),
        };
    }

    function assertExpectedDatabase(database, env) {
        const expected = env.MONGO_INDEX_EXPECTED_DATABASE;
        const actual = database.getName();
        if (!expected) {
            throw new Error("MONGO_INDEX_EXPECTED_DATABASE is required");
        }
        if (expected !== actual) {
            throw new Error(`database guard failed: expected=${expected}, actual=${actual}`);
        }
        if (["admin", "config", "local"].includes(actual)) {
            throw new Error(`system database is not allowed: ${actual}`);
        }
        return actual;
    }

    function readMarker(database, catalog, collectionNames) {
        if (!collectionNames.has(catalog.historyCollection)) {
            return null;
        }
        return database
            .getCollection(catalog.historyCollection)
            .findOne({ _id: catalog.migrationId });
    }

    function isCompatibleMarker(marker, catalog) {
        return marker !== null &&
            marker.version === 1 &&
            deepEquals(marker.indexNames, catalog.indexes.map((spec) => spec.name));
    }

    function inspect(database, catalog) {
        const collectionNames = new Set(database.getCollectionNames());
        const issues = [];
        const missing = [];
        const existing = [];
        const aliases = [];
        const extras = [];
        const indexesByCollection = new Map();
        const checkedKeyTypes = new Set();

        const getIndexes = (collectionName) => {
            if (!collectionNames.has(collectionName)) {
                return [];
            }
            if (!indexesByCollection.has(collectionName)) {
                indexesByCollection.set(
                    collectionName,
                    database.getCollection(collectionName).getIndexes(),
                );
            }
            return indexesByCollection.get(collectionName);
        };

        catalog.indexes.forEach((spec) => {
            const indexes = getIndexes(spec.collection);
            const sameName = indexes.find((index) => index.name === spec.name);
            const sameKey = indexes.filter((index) => orderedDocumentEquals(index.key, spec.key));

            if (sameName && !isCompatibleIndex(sameName, spec)) {
                issues.push({
                    kind: "index_name_conflict",
                    collection: spec.collection,
                    expectedName: spec.name,
                    actual: describeIndex(sameName),
                });
            } else {
                const compatible = sameKey.find((index) => isCompatibleIndex(index, spec));
                if (compatible) {
                    existing.push({
                        collection: spec.collection,
                        expectedName: spec.name,
                        actualName: compatible.name,
                    });
                    if (compatible.name !== spec.name) {
                        aliases.push({
                            collection: spec.collection,
                            expectedName: spec.name,
                            actualName: compatible.name,
                        });
                    }
                } else if (sameKey.length > 0) {
                    issues.push({
                        kind: "index_key_conflict",
                        collection: spec.collection,
                        expectedName: spec.name,
                        actual: sameKey.map(describeIndex),
                    });
                } else if (!sameName) {
                    missing.push({ collection: spec.collection, name: spec.name });
                }

                sameKey
                    .filter((index) => !isCompatibleIndex(index, spec))
                    .forEach((index) => {
                        if (!issues.some((issue) =>
                            issue.kind === "index_key_conflict" &&
                            issue.collection === spec.collection &&
                            issue.expectedName === spec.name,
                        )) {
                            issues.push({
                                kind: "index_key_conflict",
                                collection: spec.collection,
                                expectedName: spec.name,
                                actual: [describeIndex(index)],
                            });
                        }
                    });
            }

            const keyFields = Object.keys(spec.key);
            const typeFields = Object.keys(spec.keyTypes || {});
            if (spec.unique && !deepEquals(keyFields, typeFields)) {
                issues.push({
                    kind: "catalog_key_type_contract_invalid",
                    collection: spec.collection,
                    index: spec.name,
                });
            }
            if (!collectionNames.has(spec.collection)) {
                return;
            }

            const collection = database.getCollection(spec.collection);
            typeFields.forEach((field) => {
                const expectedType = spec.keyTypes[field];
                const nullable = (spec.nullableKeyFields || []).includes(field);
                const typeCheckKey = `${spec.collection}/${field}/${expectedType}/${nullable}`;
                if (checkedKeyTypes.has(typeCheckKey)) {
                    return;
                }
                checkedKeyTypes.add(typeCheckKey);
                const invalid = collection
                    .find(unexpectedBsonTypeQuery(field, expectedType, nullable), { _id: 1 })
                    .limit(1)
                    .toArray();
                if (invalid.length > 0) {
                    issues.push({
                        kind: "invalid_bson_type",
                        collection: spec.collection,
                        index: spec.name,
                        field,
                        expectedType,
                        nullable,
                    });
                }
            });

            if (spec.unique) {
                const duplicateGroups = collection
                    .aggregate(duplicatePipeline(spec), { allowDiskUse: true })
                    .toArray();
                if (duplicateGroups.length > 0) {
                    issues.push({
                        kind: "duplicate_values",
                        collection: spec.collection,
                        index: spec.name,
                        sampledGroupCount: duplicateGroups.length,
                        sampleDocumentCounts: duplicateGroups.map((group) => group.count),
                        sampleLimit: DUPLICATE_SAMPLE_LIMIT,
                    });
                }
            }
        });

        const specsByCollection = new Map();
        catalog.indexes.forEach((spec) => {
            const specs = specsByCollection.get(spec.collection) || [];
            specs.push(spec);
            specsByCollection.set(spec.collection, specs);
        });
        specsByCollection.forEach((specs, collectionName) => {
            getIndexes(collectionName)
                .filter((index) => index.name !== "_id_")
                .filter((index) => !specs.some((spec) =>
                    index.name === spec.name || isCompatibleIndex(index, spec),
                ))
                .forEach((index) => {
                    extras.push({ collection: collectionName, name: index.name });
                });
        });

        const marker = readMarker(database, catalog, collectionNames);
        const hasIndexDrift = issues.length > 0 || missing.length > 0;
        if (marker && !isCompatibleMarker(marker, catalog)) {
            issues.push({
                kind: "migration_marker_conflict",
                migrationId: catalog.migrationId,
            });
        }
        if (marker && hasIndexDrift) {
            issues.push({
                kind: "migration_history_drift",
                migrationId: catalog.migrationId,
            });
        }

        return {
            migrationId: catalog.migrationId,
            database: database.getName(),
            markerPresent: marker !== null,
            existing,
            missing,
            aliases,
            extras,
            issues,
        };
    }

    function emit(output, report) {
        output(JSON.stringify(report));
    }

    function blockedError(message, report) {
        const error = new Error(message);
        error.report = report;
        return error;
    }

    function runPreflight(database, env, catalog, output = print) {
        assertExpectedDatabase(database, env);
        const report = inspect(database, catalog);
        report.phase = "preflight";
        report.status = report.issues.length === 0 ? "ready" : "blocked";
        emit(output, report);
        if (report.status === "blocked") {
            throw blockedError("MongoDB index preflight failed", report);
        }
        return report;
    }

    function runApply(database, env, catalog, output = print) {
        assertExpectedDatabase(database, env);
        if (env.MONGO_INDEX_APPLY !== "YES") {
            throw new Error("MONGO_INDEX_APPLY=YES is required for apply");
        }

        const before = inspect(database, catalog);
        before.phase = "apply";
        if (before.issues.length > 0) {
            before.status = "blocked";
            emit(output, before);
            throw blockedError("MongoDB index apply preflight failed", before);
        }
        if (before.markerPresent) {
            before.status = "already-applied";
            before.created = [];
            emit(output, before);
            return before;
        }

        const missingNames = new Set(before.missing.map(({ collection, name }) => `${collection}/${name}`));
        const created = [];
        const missingSpecs = catalog.indexes
            .filter((spec) => missingNames.has(`${spec.collection}/${spec.name}`));
        for (const spec of missingSpecs) {
            try {
                database
                    .getCollection(spec.collection)
                    .createIndex(spec.key, createOptions(spec));
                created.push({ collection: spec.collection, name: spec.name });
            } catch (error) {
                before.status = "incomplete";
                before.created = created;
                before.failed = {
                    collection: spec.collection,
                    name: spec.name,
                    code: error.code || null,
                    codeName: error.codeName || null,
                };
                emit(output, before);
                throw blockedError(
                    `MongoDB index creation failed at ${spec.collection}/${spec.name}`,
                    before,
                );
            }
        }

        const after = inspect(database, catalog);
        if (after.issues.length > 0 || after.missing.length > 0) {
            after.phase = "apply";
            after.status = "incomplete";
            after.created = created;
            emit(output, after);
            throw blockedError("MongoDB index apply verification failed", after);
        }

        const historyCollection = database.getCollection(catalog.historyCollection);
        try {
            historyCollection.updateOne(
                { _id: catalog.migrationId },
                {
                    $setOnInsert: {
                        version: 1,
                        database: database.getName(),
                        indexNames: catalog.indexes.map((spec) => spec.name),
                        appliedAt: new Date(),
                    },
                },
                { upsert: true },
            );
        } catch (error) {
            after.phase = "apply";
            after.status = "incomplete";
            after.created = created;
            after.failed = {
                collection: catalog.historyCollection,
                name: catalog.migrationId,
                code: error.code || null,
                codeName: error.codeName || null,
            };
            emit(output, after);
            throw blockedError("MongoDB index migration marker write failed", after);
        }

        const marker = historyCollection.findOne({ _id: catalog.migrationId });
        if (!isCompatibleMarker(marker, catalog)) {
            after.phase = "apply";
            after.status = "incomplete";
            after.markerPresent = marker !== null;
            after.created = created;
            after.issues.push({
                kind: "migration_marker_conflict",
                migrationId: catalog.migrationId,
            });
            emit(output, after);
            throw blockedError("MongoDB index migration marker verification failed", after);
        }

        after.phase = "apply";
        after.status = "applied";
        after.markerPresent = true;
        after.created = created;
        emit(output, after);
        return after;
    }

    function runVerify(database, env, catalog, output = print) {
        assertExpectedDatabase(database, env);
        const report = inspect(database, catalog);
        report.phase = "verify";
        if (!report.markerPresent) {
            report.issues.push({
                kind: "migration_marker_missing",
                migrationId: catalog.migrationId,
            });
        }
        if (report.missing.length > 0) {
            report.issues.push({ kind: "required_indexes_missing" });
        }
        report.status = report.issues.length === 0 ? "verified" : "blocked";
        emit(output, report);
        if (report.status === "blocked") {
            throw blockedError("MongoDB index verification failed", report);
        }
        return report;
    }

    return {
        createOptions,
        duplicatePipeline,
        inspect,
        isCompatibleIndex,
        isCompatibleMarker,
        orderedDocumentEquals,
        runApply,
        runPreflight,
        runVerify,
        unexpectedBsonTypeQuery,
    };
});
