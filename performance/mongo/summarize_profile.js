#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");

function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.files.length === 0) {
    fail("At least one profile JSON file is required.");
  }

  const profiles = parsed.files.map((file) => loadProfile(file));
  const errors = validationErrors(profiles);
  if (errors.length > 0) {
    const rejected = { accepted: false, reasons: errors };
    process.stdout.write(`${JSON.stringify(rejected, null, 2)}\n`);
    process.exit(2);
  }

  const result = {
    accepted: true,
    context: context(profiles[0]),
    runs: profiles.map((profile) => runValues(profile)),
    summary: summarize(profiles),
  };
  result.markdown = renderMarkdown(result);

  const jsonText = `${JSON.stringify(result, null, 2)}\n`;
  if (parsed.jsonOutput) {
    fs.writeFileSync(parsed.jsonOutput, jsonText, "utf8");
  }
  if (parsed.markdownOutput) {
    fs.writeFileSync(parsed.markdownOutput, `${result.markdown}\n`, "utf8");
  }
  process.stdout.write(jsonText);
}

function parseArgs(argv) {
  const result = {
    files: [],
    jsonOutput: "",
    markdownOutput: "",
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--json-output") {
      result.jsonOutput = requiredValue(argv, i, arg);
      i += 1;
    } else if (arg === "--markdown-output") {
      result.markdownOutput = requiredValue(argv, i, arg);
      i += 1;
    } else if (arg.startsWith("--")) {
      fail(`Unknown argument: ${arg}`);
    } else {
      result.files.push(arg);
    }
  }
  return result;
}

function requiredValue(argv, index, flag) {
  const value = argv[index + 1];
  if (!value || value.startsWith("--")) {
    fail(`${flag} requires a value.`);
  }
  return value;
}

function loadProfile(file) {
  const payload = JSON.parse(fs.readFileSync(file, "utf8"));
  payload.file = path.basename(file);
  return payload;
}

function validationErrors(profiles) {
  const errors = [];
  const baseline = context(profiles[0]);
  profiles.forEach((profile, index) => {
    const label = `profile ${index + 1}`;
    if (profile.status !== "profile_collected") {
      errors.push(`${label} was not collected`);
    }
    if (profile.database !== "aandi_performance") {
      errors.push(`${label} database must be aandi_performance`);
    }
    const current = context(profile);
    for (const key of ["database", "mongoVersion", "fixture"]) {
      if (JSON.stringify(current[key]) !== JSON.stringify(baseline[key])) {
        errors.push(`${label} context mismatch: ${key}`);
      }
    }
  });
  return errors;
}

function context(profile) {
  return {
    database: profile.database,
    mongoVersion: profile.mongoVersion,
    fixture: profile.fixture,
  };
}

function runValues(profile) {
  return {
    file: profile.file,
    runLabel: profile.runLabel || "",
    commandCount: number(profile.profile && profile.profile.commandCount),
    totalDocsExamined: number(profile.profile && profile.profile.totalDocsExamined),
    totalKeysExamined: number(profile.profile && profile.profile.totalKeysExamined),
    totalMillis: number(profile.profile && profile.profile.totalMillis),
    explainTotalDocsExamined: number(profile.explains && profile.explains.totalDocsExamined),
    explainTotalKeysExamined: number(profile.explains && profile.explains.totalKeysExamined),
  };
}

function summarize(profiles) {
  const runs = profiles.map((profile) => runValues(profile));
  return {
    commandCount: stats(runs.map((run) => run.commandCount)),
    totalDocsExamined: stats(runs.map((run) => run.totalDocsExamined)),
    totalKeysExamined: stats(runs.map((run) => run.totalKeysExamined)),
    totalMillis: stats(runs.map((run) => run.totalMillis)),
    explainTotalDocsExamined: stats(runs.map((run) => run.explainTotalDocsExamined)),
    explainTotalKeysExamined: stats(runs.map((run) => run.explainTotalKeysExamined)),
    byNamespace: summarizeNamespaces(profiles),
    explains: summarizeExplains(profiles),
  };
}

function summarizeNamespaces(profiles) {
  const namespaces = new Set();
  profiles.forEach((profile) => {
    const byNamespace = (profile.profile && profile.profile.byNamespace) || {};
    Object.keys(byNamespace).forEach((namespace) => namespaces.add(namespace));
  });

  const result = {};
  Array.from(namespaces).sort().forEach((namespace) => {
    result[namespace] = {
      commandCount: stats(profiles.map((profile) => namespaceValue(profile, namespace, "commandCount"))),
      totalDocsExamined: stats(profiles.map((profile) => namespaceValue(profile, namespace, "totalDocsExamined"))),
      totalKeysExamined: stats(profiles.map((profile) => namespaceValue(profile, namespace, "totalKeysExamined"))),
      totalMillis: stats(profiles.map((profile) => namespaceValue(profile, namespace, "totalMillis"))),
    };
  });
  return result;
}

function summarizeExplains(profiles) {
  const queryNames = new Set();
  profiles.forEach((profile) => {
    const queries = (profile.explains && profile.explains.queries) || {};
    Object.keys(queries).forEach((queryName) => queryNames.add(queryName));
  });

  const result = {};
  Array.from(queryNames).sort().forEach((queryName) => {
    result[queryName] = {
      totalDocsExamined: stats(profiles.map((profile) => explainValue(profile, queryName, "totalDocsExamined"))),
      totalKeysExamined: stats(profiles.map((profile) => explainValue(profile, queryName, "totalKeysExamined"))),
      nReturned: stats(profiles.map((profile) => explainValue(profile, queryName, "nReturned"))),
      executionTimeMillis: stats(profiles.map((profile) => explainValue(profile, queryName, "executionTimeMillis"))),
      indexName: firstExplainValue(profiles, queryName, "indexName"),
      winningPlanStage: firstExplainValue(profiles, queryName, "winningPlanStage"),
    };
  });
  return result;
}

function namespaceValue(profile, namespace, key) {
  return number(profile.profile && profile.profile.byNamespace && profile.profile.byNamespace[namespace] && profile.profile.byNamespace[namespace][key]);
}

function explainValue(profile, queryName, key) {
  return number(profile.explains && profile.explains.queries && profile.explains.queries[queryName] && profile.explains.queries[queryName][key]);
}

function firstExplainValue(profiles, queryName, key) {
  for (const profile of profiles) {
    const value = profile.explains && profile.explains.queries && profile.explains.queries[queryName] && profile.explains.queries[queryName][key];
    if (value !== undefined && value !== null) {
      return value;
    }
  }
  return null;
}

function stats(values) {
  const sorted = values.map(number).sort((left, right) => left - right);
  return {
    median: median(sorted),
    min: sorted[0],
    max: sorted[sorted.length - 1],
  };
}

function median(sortedValues) {
  const middle = Math.floor(sortedValues.length / 2);
  if (sortedValues.length % 2 === 1) {
    return sortedValues[middle];
  }
  return (sortedValues[middle - 1] + sortedValues[middle]) / 2;
}

function renderMarkdown(result) {
  const lines = [
    "# MongoDB Profile Aggregate",
    "",
    "## Context",
    "",
    `- Database: ${result.context.database}`,
    `- MongoDB Version: ${result.context.mongoVersion}`,
    `- Fixture: ${JSON.stringify(result.context.fixture)}`,
    "",
    "## Profile Totals",
    "",
    "| Metric | Median | Min | Max |",
    "| :--- | ---: | ---: | ---: |",
  ];
  for (const key of ["commandCount", "totalDocsExamined", "totalKeysExamined", "totalMillis", "explainTotalDocsExamined", "explainTotalKeysExamined"]) {
    const value = result.summary[key];
    lines.push(`| ${key} | ${format(value.median)} | ${format(value.min)} | ${format(value.max)} |`);
  }
  lines.push("", "## Explain Queries", "", "| Query | Docs Examined | Keys Examined | Returned | Index | Stage |", "| :--- | ---: | ---: | ---: | :--- | :--- |");
  for (const queryName of Object.keys(result.summary.explains).sort()) {
    const query = result.summary.explains[queryName];
    lines.push(
      `| ${queryName} | ${format(query.totalDocsExamined.median)} | ${format(query.totalKeysExamined.median)} | ${format(query.nReturned.median)} | ${query.indexName || "n/a"} | ${query.winningPlanStage || "n/a"} |`
    );
  }
  return lines.join("\n");
}

function number(value) {
  const parsed = Number(value || 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function format(value) {
  const parsed = number(value);
  if (Number.isInteger(parsed)) {
    return String(parsed);
  }
  return parsed.toFixed(4).replace(/0+$/, "").replace(/\.$/, "");
}

function fail(message) {
  console.error(message);
  process.exit(2);
}

main();
