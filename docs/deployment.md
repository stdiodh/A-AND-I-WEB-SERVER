# Deployment

> 메인 README로 돌아가기: [README](../README.md)

본 문서는 이 저장소에서 확인되는 Docker, GitHub Actions, CloudWatch Logs 배포 근거를 정리합니다.

## 로컬 실행

| 파일 | 역할 |
| :--- | :--- |
| `Dockerfile` | Gradle builder stage에서 `bootJar` 생성 후 JRE image로 실행 |
| `docker-compose.yml` | 로컬 MongoDB와 report-server 실행 |
| `src/main/resources/application.yml` | MongoDB, Swagger, JWT, event, logging 기본값 |

로컬 기본 실행:

```bash
docker compose up -d mongodb
./gradlew bootRun
```

서버까지 컨테이너로 실행:

```bash
docker compose up -d
```

## Dockerfile 기준

- builder image: `gradle:8.12.1-jdk21`
- runtime image: `eclipse-temurin:21-jre`
- bootJar build: `./gradlew clean bootJar -x test --no-daemon`
- readiness healthcheck: `/actuator/health/readiness`

## CI

`.github/workflows/ci-test.yml`:

| 항목 | 값 |
| :--- | :--- |
| trigger | `push` to `develop`, `main`, PR to `develop`, `main`, `workflow_dispatch` |
| JDK | Temurin 21 |
| task | `./gradlew test --no-daemon` |

CI workflow에는 `jacocoTestCoverageVerification` task가 직접 포함되어 있지 않습니다. `test` task는 Gradle 설정상 `jacocoTestReport`를 finalizedBy로 실행하지만, coverage verification은 CI 품질 게이트로 확인되지 않습니다.

## 태그 배포

`.github/workflows/deploy-tag.yml`:

| 단계 | 설명 |
| :--- | :--- |
| trigger | `v*.*.*` tag push |
| build | `./gradlew clean bootJar --no-daemon` |
| image | Docker build 후 ECR push |
| log group | CloudWatch log group 생성 및 retention policy 설정 |
| deploy | SSH로 EC2 접속 후 compose file 생성, `docker compose up -d` |

## Production compose

`docker-compose.prod.yml`에서 확인되는 운영 기준:

- MongoDB와 report-server 모두 Docker `awslogs` logging driver 사용
- 기본 log group: `/a-and-i/prod/report`, `/a-and-i/prod/report-mongodb`
- report-server healthcheck: `/actuator/health/readiness`
- v2 structured logging 관련 환경변수 설정

## Event 환경변수

| 영역 | 주요 환경변수 |
| :--- | :--- |
| Problem sync publish | `APP_EVENTS_REPORT_TEST_CASE_ENABLED`, `APP_EVENTS_REPORT_TEST_CASE_SNS_TOPIC_ARN`, `APP_EVENTS_REPORT_TEST_CASE_REGION` |
| Auth user sync consume | `APP_EVENTS_USER_SYNC_ENABLED`, `APP_EVENTS_USER_SYNC_QUEUE_URL`, `APP_EVENTS_USER_SYNC_REGION` |
| Judge completed consume | `REPORT_JUDGE_SUBMISSION_EVENTS_ENABLED`, `REPORT_JUDGE_SUBMISSION_EVENTS_QUEUE_URL`, `AWS_REGION` |

## 확인 필요

- 실제 운영 AWS account, IAM role, SNS/SQS ARN, queue policy 값은 이 문서에 노출하지 않습니다.
- 현재 운영 배포 URL의 live smoke test는 이번 작업에서 수행하지 않았습니다.

## 관련 문서

- [Deploy v2.0.8](./deploy-v2.0.8.md)
- [CloudWatch Report Server](./cloudwatch-report-server.md)
- [Test](./test.md)
