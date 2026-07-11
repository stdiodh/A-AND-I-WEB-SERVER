"use strict";

load("scripts/mongo/indexes/v001-index-catalog.js");
load("scripts/mongo/indexes/v001-index-lib.js");

MongoIndexV001.runPreflight(db, process.env, MongoIndexV001Catalog, print);
