# Fixture Guide

Use synthetic local data only. Do not seed a remote database and do not run these k6 scripts against production or real user data.

## Local Database

- Database: `aandi_performance`
- Default URI: `mongodb://localhost:27017/aandi_performance`
- Allowed Mongo hosts: `localhost`, `127.0.0.1`, `mongodb`
- Cleanup deletes only deterministic performance fixture IDs, not whole collections.

## Collections

- `courses`
- `course_weeks`
- `users`
- `course_enrollments`
- `assignments`
- `assignment_requirements`
- `assignment_test_cases`
- `assignment_submission_statuses`

The fixture follows the actual Mongo `@Document` collections and `_class` aliases used by the application entities.

## Default Data

- Course: `COURSE_SLUG=perf-k6`
- USER subject: `00000000-0000-4000-8000-000000000001`
- ADMIN subject: `00000000-0000-4000-8000-999999999999`
- Detail assignment: `ASSIGNMENT_ID=10000000-0000-4000-8000-000000000001`
- Course weeks: 3
- Published assignments: `FIXTURE_ASSIGNMENTS=30`
- Enabled enrollments: `FIXTURE_ENROLLMENTS=100`
- Submitted projections: `FIXTURE_SUBMISSION_STATUSES=60`
- PUBLIC testcase marker: `PERF_PUBLIC_VISIBLE_001` (2 public cases per assignment)
- hidden testcase marker: `PERF_PRIVATE_MUST_NOT_LEAK_001`

## Commands

```bash
performance/fixtures/run-fixture.sh seed
performance/fixtures/run-fixture.sh verify
performance/fixtures/run-fixture.sh cleanup
```

Generate local JWTs into an ignored env file:

```bash
python3 performance/k6/tools/generate_test_jwt.py --output performance/k6/env.local
```

Run the full local sequence only after MongoDB and the application are pointed at `aandi_performance`:

```bash
MONGO_DB_URL=mongodb://localhost:27017/aandi_performance ./gradlew bootRun
performance/k6/run-local.sh all performance/k6/env.local
```

## Safety

- `run-fixture.sh` refuses any database name except `aandi_performance`.
- Mongo credentials in `MONGO_URI` are rejected.
- Remote fixture seed is not supported.
- Existing data outside deterministic performance IDs is not deleted.
