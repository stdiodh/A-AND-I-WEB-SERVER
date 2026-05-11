# v2.0.8 Report Server Deploy

이 문서는 Report 서버만 `v2.0.8`로 배포하는 절차다.
Gateway 리포지토리는 건드리지 않고, Gateway 새 태그도 만들지 않는다.

## Required GitHub Secrets

- `AWS_ROLE_TO_ASSUME`
- `AWS_HOST`
- `AWS_USER`
- `AWS_SSH_KEY`
- `AUTH_JWT_SECRET`
- `APP_REPORT_V2_SALT_SECRET`

## Required GitHub Vars

- `AWS_REGION=ap-northeast-2`
- `ECR_REPOSITORY=aandi-report-server`
- `APP_DIR=/opt/aandi-report-server`
- `MONGO_DB_NAME=aandi`
- `SWAGGER_URL=https://api.aandiclub.com`
- `AUTH_ISSUER_URI=https://auth.aandiclub.com`
- `AUTH_SERVICE_BASE_URL=https://api.aandiclub.com`
- `AUTH_AUDIENCE=aandiclub-api`
- `AUTH_JWT_CLOCK_SKEW_SECONDS=30`
- `APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://aandiclub.com,https://www.aandiclub.com,https://*.aandiclub.com`
- `ONLINE_JUDGE_BASE_URL=http://<ONLINE_JUDGE_PRIVATE_IP>:8080`
- `APP_ENV=prod`
- `APP_V2_LOG_SERVICE_NAME=report-service`
- `APP_V2_LOG_DOMAIN_CODE=4`
- `APP_V2_LOG_MAX_BODY_BYTES=8192`
- `APP_V2_LOG_INSTANCE_ID=aandi-report-server`
- `AWSLOGS_GROUP_REPORT=/a-and-i/prod/report`
- `AWSLOGS_GROUP_REPORT_MONGODB=/a-and-i/prod/report-mongodb`
- `CLOUDWATCH_LOG_RETENTION_DAYS=14`

## Optional Event Vars

- `APP_EVENTS_REPORT_TEST_CASE_ENABLED`
- `APP_EVENTS_REPORT_TEST_CASE_SNS_TOPIC_ARN`
- `APP_EVENTS_REPORT_TEST_CASE_REGION`
- `APP_EVENTS_USER_SYNC_ENABLED`
- `APP_EVENTS_USER_SYNC_QUEUE_URL`
- `APP_EVENTS_USER_SYNC_REGION`
- `APP_EVENTS_USER_SYNC_WAIT_TIME_SECONDS`
- `APP_EVENTS_USER_SYNC_MAX_NUMBER_OF_MESSAGES`
- `REPORT_JUDGE_SUBMISSION_EVENTS_ENABLED`
- `REPORT_JUDGE_SUBMISSION_EVENTS_QUEUE_URL`
- `REPORT_JUDGE_SUBMISSION_EVENTS_WAIT_TIME_SECONDS`
- `REPORT_JUDGE_SUBMISSION_EVENTS_MAX_MESSAGES`
- `REPORT_JUDGE_SUBMISSION_EVENTS_VISIBILITY_TIMEOUT_SECONDS`

## IAM

EC2 instance profile needs CloudWatch Logs write permission:

```text
logs:CreateLogGroup
logs:CreateLogStream
logs:PutLogEvents
logs:DescribeLogStreams
```

GitHub Actions deploy role needs retention setup permission:

```text
logs:CreateLogGroup
logs:PutRetentionPolicy
logs:DescribeLogGroups
```

## Verify PR #50

```bash
gh repo set-default Team-AnI/A-AND-I-WEB-SERVER
gh pr view 50
gh pr checkout 50
./gradlew clean test
./gradlew clean bootJar
```

Check that assignment copy tests pass, duplicate guards return `409`, and OJ problem sync create events use the new copied assignment id.

## Merge PR #50

```bash
gh pr merge 50 --merge --repo Team-AnI/A-AND-I-WEB-SERVER
git checkout main
git pull origin main
git fetch --tags
```

## Tag v2.0.8

```bash
git tag -l 'v*.*.*' --sort=-v:refname | head
git tag -a v2.0.8 -m "release: v2.0.8 - admin assignment copy and report log collection"
git push origin v2.0.8
```

The `Deploy Tagged Report Release` workflow runs on `v*.*.*` tag pushes.

## EC2 Checks

```bash
cd /opt/aandi-report-server
docker compose ps
docker logs --tail=100 aandi-report-server
docker inspect aandi-report-server \
  --format 'OOMKilled={{.State.OOMKilled}} RestartCount={{.RestartCount}} Health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}'
curl -i http://127.0.0.1:8080/actuator/health || true
curl -i http://127.0.0.1:8080/actuator/health/readiness || true
```

## CloudWatch Checks

```bash
aws logs describe-log-groups \
  --region ap-northeast-2 \
  --max-items 10

aws logs describe-log-streams \
  --region ap-northeast-2 \
  --log-group-name /a-and-i/prod/report \
  --max-items 10
```

## Gateway Smoke Test

Do not deploy a new Gateway tag. Confirm existing `REPORT_SERVICE_URI` points to the Report private URL, then call through Gateway:

```bash
curl -i -X POST "https://api.aandiclub.com/v2/admin/courses/<targetCourseSlug>/assignments/copy" \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceAssignmentId": "<SOURCE_ASSIGNMENT_UUID>",
    "targetWeekNo": 1,
    "targetOrderInWeek": 2,
    "targetStartAt": "2026-05-12T09:00:00+09:00",
    "targetEndAt": "2026-05-19T08:59:59+09:00"
  }'
```

Interpretation:

- `2xx`: success
- `401`: token problem
- `403`: admin role problem
- `404`: Gateway route or controller path problem
- `409`: duplicate guard may be working as expected
- `502/504`: Gateway to Report connectivity problem

## Rollback

Redeploy the previous stable tag, for example:

```bash
git push origin v2.0.7
```

If the tag already exists, rerun the previous `Deploy Tagged Report Release` workflow from GitHub Actions or push a new rollback tag pointing at the known-good commit.
