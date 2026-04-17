# 📱 A&I : Android and iOS Development

<img src="https://github.com/user-attachments/assets/b98d32a0-7f19-4112-89d7-81cb4c9ec86a" width="300"/>


**인덕대학교 모바일 앱 개발 동아리 A&I에 오신 것을 환영합니다!** 😊  
저희는 모두가 즐겁고 Lean한 분위기에서 **더 나은 방향을 함께 연구하는 동아리**입니다. 🔥

## 👨‍🏫 프로젝트 소개

본 프로젝트는 A&I 3기 동아리원을 대상으로 한  
**레포트 공유 및 과제 운영 웹 서비스**입니다.

- 과제를 자동 공개하고,
- 과제 메타데이터와 테스트케이스를 관리하고,
- 공개 테스트케이스를 ONLINE-JUDGE-SERVER 와 동기화하며,
- 로그인을 기반으로 한 심화 기능을 제공합니다.

## ⏰ 개발 기간

- **2025년 03월 07일 ~ 진행 중**
- 이 저장소는 **Back-End Repository**입니다.


## 👥 프로젝트 팀원

| Front-End | Back-End |
|:--:|:--:|
| <img src="https://github.com/user-attachments/assets/3e22107e-3e30-44d5-8d4a-61cfbab8eac2" width="100"/> | <img src="https://github.com/user-attachments/assets/a51e908a-f9ca-4819-a36a-5f26da14a3aa" width="100"/> |
| [Han Sang Wook](https://github.com/SangWook16074) | [Hood](https://github.com/stdiodh) |


## ⚙️ 기술 스택

### 🛠 Back-End

| Kotlin | Spring Boot | MongoDB | AWS EC2 | Docker | GitHub Actions | Nignx |
|:--:|:--:|:--:|:--:|:--:|:--:|:---:|
| <img src="https://github.com/user-attachments/assets/80ae7152-6b52-477e-bee7-504e46119af2" width="50"/> | <img src="https://github.com/user-attachments/assets/f0a5c7a5-1ea5-486f-884e-f404e227f9d4" width="50"/> | <img src="https://github.com/user-attachments/assets/b1e27d13-222d-47f0-b25a-98d975283be3" width="50"/> | <img src="https://github.com/user-attachments/assets/1e5aaa79-0a47-4e20-9023-6ebd930d1716" width="50"/> | <img src="https://github.com/user-attachments/assets/8531285b-ac7a-43c5-a856-7dd15bbf1ed5" width="50"/> | <img src="https://simpleicons.org/icons/githubactions.svg" width="50"/> | <img src="https://github.com/user-attachments/assets/b09662b0-c4ed-4400-ab03-26e3a4515c12" width="50"/> |

## 📌 주요 기능

### ✅ A&I 레포트 공개
- 매주 **월요일 오전 9시**, 새로운 과제가 자동 공개됩니다.  
<img src="https://github.com/user-attachments/assets/df2c1775-4304-4e62-a109-3cf1e638d72c" width="600"/>

### 🔐 로그인 기반 심화 기능
- 로그인한 사용자만 접근 가능한 프로그래밍 학습 콘텐츠를 제공할 예정입니다.
<img src="https://github.com/user-attachments/assets/17a957a3-0d77-444c-b43c-8368b58b0db1" width="600"/>

### 🔄 ONLINE-JUDGE 연동
- 제출 생성, 결과 조회, 히스토리, SSE stream 책임은 ONLINE-JUDGE-SERVER 가 담당합니다.
- WEB-SERVER 는 과제와 테스트케이스를 관리하고, `EXCLUDED`를 제외한 테스트케이스 snapshot 을 OJ problem sync 이벤트로 발행합니다.
- 이벤트는 `PROBLEM_CREATED`, `PROBLEM_UPDATED`, `PROBLEM_DELETED` 형식을 사용합니다.
- `problemId` 는 항상 `assignmentId(UUID)` 이고, `testCases.input` 은 항상 배열이며 현재는 `[inputText]` 규칙으로 직렬화합니다.
- `submission-config` 조회 API는 현재 WEB-SERVER 에 제공되지 않습니다.
- 배포 시 과제 이벤트는 `APP_EVENTS_REPORT_TEST_CASE_SNS_TOPIC_ARN` 으로 지정한 SNS 토픽으로 발행되며, SQS 연결은 해당 토픽 구독으로 처리합니다.

### 📨 Judge Completion 이벤트 소비
- 최신 구현은 `OJ -> SNS FIFO topic -> SQS queue -> report server consumer` 경로를 기준으로 동작합니다.
- 런타임 소비자는 SQS queue URL 을 사용하고, SNS 구독 설정은 queue ARN 을 사용합니다.
- report server 는 `JUDGE_COMPLETED` 이벤트만 처리하고, 그 외 `eventType` 은 안전하게 로그 후 삭제합니다.
- SQS 메시지는 direct JSON body 와 SNS envelope body 를 모두 지원합니다.
- OJ 가 SNS FIFO topic 으로 publish 할 때 `MessageGroupId` 를 반드시 포함해야 합니다. 이 값이 없으면 SNS publish 자체가 실패하므로 SQS 에 메시지가 도착하지 않습니다.

#### Judge Submission Consumer 환경 변수

```properties
AWS_REGION=ap-northeast-2
REPORT_JUDGE_SUBMISSION_EVENTS_ENABLED=true
REPORT_JUDGE_SUBMISSION_EVENTS_QUEUE_URL=https://sqs.ap-northeast-2.amazonaws.com/<account-id>/online-judge-submission-events-queue
REPORT_JUDGE_SUBMISSION_EVENTS_WAIT_TIME_SECONDS=20
REPORT_JUDGE_SUBMISSION_EVENTS_MAX_MESSAGES=10
REPORT_JUDGE_SUBMISSION_EVENTS_VISIBILITY_TIMEOUT_SECONDS=60
```

- `REPORT_JUDGE_SUBMISSION_EVENTS_QUEUE_URL` 는 애플리케이션의 SQS polling 대상입니다.
- `AWS_REGION` 은 AWS SDK client region 으로 사용됩니다.
- queue ARN, topic ARN, account id 같은 인프라 식별자는 코드에 하드코딩하지 않고 배포 설정 또는 IaC 에서 주입해야 합니다.

#### SNS -> SQS 구독 예시

```bash
aws sns subscribe \
  --topic-arn "$OJ_SUBMISSION_EVENTS_TOPIC_ARN" \
  --protocol sqs \
  --notification-endpoint "$REPORT_JUDGE_SUBMISSION_EVENTS_QUEUE_ARN" \
  --attributes RawMessageDelivery=true
```

- `--notification-endpoint` 에는 queue URL 이 아니라 queue ARN 을 사용해야 합니다.
- `RawMessageDelivery=true` 이면 direct JSON body 로 전달되고, `false` 여도 report server 는 SNS envelope 을 파싱합니다.

#### SQS Queue Policy 예시

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowOnlineJudgeSubmissionTopic",
      "Effect": "Allow",
      "Principal": {
        "Service": "sns.amazonaws.com"
      },
      "Action": "sqs:SendMessage",
      "Resource": "$REPORT_JUDGE_SUBMISSION_EVENTS_QUEUE_ARN",
      "Condition": {
        "ArnEquals": {
          "aws:SourceArn": "$OJ_SUBMISSION_EVENTS_TOPIC_ARN"
        }
      }
    }
  ]
}
```

#### Runtime IAM Policy 예시

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
        "sqs:ChangeMessageVisibility"
      ],
      "Resource": "$REPORT_JUDGE_SUBMISSION_EVENTS_QUEUE_ARN"
    }
  ]
}
```

#### 검증 방법

1. 애플리케이션 시작 로그에서 `judge-submission SQS consumer started` 와 `queueUrl`, `region`, `visibilityTimeoutSeconds` 값을 확인합니다.
2. direct JSON 메시지를 넣고 projection 이 반영된 뒤 같은 메시지가 queue 에 남지 않는지 확인합니다.
3. SNS envelope 메시지를 넣고 동일하게 projection 반영과 메시지 삭제를 확인합니다.
4. 저장 로직을 강제로 실패시키거나 잘못된 DB 연결로 실행해 보고, 실패한 메시지가 삭제되지 않고 visibility timeout 이후 재수신되는지 확인합니다.

direct JSON 테스트 예시:

```bash
aws sqs send-message \
  --queue-url "$REPORT_JUDGE_SUBMISSION_EVENTS_QUEUE_URL" \
  --message-body '{"eventType":"JUDGE_COMPLETED","publicCode":"A00123","problemId":"quiz-101","score":80,"passedCases":8,"totalCases":10,"timestamp":"2026-04-09T02:15:30.123Z"}'
```

SNS envelope 테스트 예시:

```bash
aws sqs send-message \
  --queue-url "$REPORT_JUDGE_SUBMISSION_EVENTS_QUEUE_URL" \
  --message-body '{"Type":"Notification","Message":"{\"eventType\":\"JUDGE_COMPLETED\",\"publicCode\":\"A00123\",\"problemId\":\"quiz-101\",\"score\":80,\"passedCases\":8,\"totalCases\":10,\"timestamp\":\"2026-04-09T02:15:30.123Z\"}"}'
```

## 🗂️ ERD

<img src="https://github.com/user-attachments/assets/0ac35082-bce5-4b96-8915-35d0312b71d6" width="800"/>

## 💻 개발자 가이드 (Developer Guide)

이 문서는 **A&I 레포트 공유 및 제출 웹 서비스** 프로젝트에 참여하는 **개발자 및 협업자**를 위한 가이드입니다.

### 🛠 환경 설정 (Prerequisites)

이 프로젝트는 **Kotlin + Spring Boot (WebFlux)**로 개발되었습니다. 개발을 시작하기 위해 다음 도구들이 필요합니다.

*   **Java**: `21` (JDK 21)
*   **Kotlin**: `1.9.25` (Gradle 플러그인 포함)
*   **Spring Boot**: `3.4.3`
*   **MongoDB**: `latest` (Docker를 통해 실행)
*   **Docker & Docker Compose**: 로컬 개발 환경 구축용
*   **IDE**: IntelliJ IDEA (Kotlin 지원)

### 🚀 시작하기 (Getting Started)

#### 1. 프로젝트 클론 및 빌드

```bash
git clone <repository-url>
cd A-AND-I-REPORT-SERVER
./gradlew build
```

#### 2. 환경 변수 설정 (Environment Variables)

이 프로젝트는 `.env` 파일을 통해 환경 변수를 주입받습니다.  
로컬 개발 시 다음 환경 변수를 설정해야 정상적으로 동작합니다.

**필수 환경 변수:**
*   **MONGO_DB_URL**: MongoDB 연결 URI (예: `mongodb://localhost:27017/aandi`)
*   **SWAGGER_URL**: Swagger UI 접근 URL (예: `http://localhost:8080`)

`.env` 파일 예시:
```properties
MONGO_DB_URL=mongodb://localhost:27017/aandi
SWAGGER_URL=http://localhost:8080
```

> **Note**: 환경 변수를 설정하지 않으면 애플리케이션 실행 시 에러가 발생할 수 있습니다.

#### 3. 로컬 실행 (Running Locally)

**방법 1: Docker Compose 사용 (권장)**

```bash
# MongoDB와 함께 실행
docker-compose up -d mongodb
./gradlew bootRun
```

**방법 2: Gradle 직접 실행**

```bash
./gradlew bootRun
```

> **Note**: 방법 2를 사용할 경우, 별도로 MongoDB를 실행해야 합니다.

#### 4. Swagger UI 확인

애플리케이션 실행 후 다음 주소에서 API 문서를 확인할 수 있습니다.

```
http://localhost:8080/swagger-ui/index.html
```

### 📂 프로젝트 구조 (Project Structure)

이 프로젝트는 **Feature-first** 기반의 **Layered Architecture** 패턴을 따릅니다.

```
src/main/kotlin/com/example/aandi_post_web_server/
├── Application.kt              # 애플리케이션 진입점
├── common/                     # 공통 모듈
│   ├── annotation/            # 커스텀 어노테이션
│   ├── authority/             # JWT 인증/인가
│   ├── config/                # 설정 클래스 (Security, Swagger)
│   └── Token/                 # 인증 토큰 관련
├── member/                     # 회원 기능 모듈
│   ├── controller/            # REST API 컨트롤러
│   ├── dtos/                  # 데이터 전송 객체
│   ├── entity/                # 도메인 엔티티
│   ├── enum/                  # 열거형
│   ├── repository/            # 데이터 접근 계층
│   └── service/               # 비즈니스 로직 계층
└── report/                     # 레포트 기능 모듈
    ├── controller/
    ├── dtos/
    ├── entity/
    ├── enum/
    ├── repository/
    └── service/
```

### 📦 주요 라이브러리 (Tech Stack)

| 구분 | 라이브러리 | 용도 |
| --- | --- | --- |
| **Framework** | Spring Boot 3.4.3 | 백엔드 프레임워크 |
| **Language** | Kotlin 1.9.25 | 프로그래밍 언어 |
| **Reactive** | Spring WebFlux | 비동기/논블로킹 웹 스택 |
| **Database** | Spring Data MongoDB Reactive | MongoDB 리액티브 접근 |
| **Security** | Spring Security + JWT | 인증/인가 |
| **API Docs** | SpringDoc OpenAPI | Swagger UI 자동 생성 |
| **Build** | Gradle (Kotlin DSL) | 빌드 도구 |

### 🚢 배포 (Deployment)

이 프로젝트는 **Docker**를 사용하며, **GitHub Actions**를 통해 CI/CD가 구축되어 있습니다.

#### CI/CD 워크플로우

1.  **Trigger**: `main` 브랜치에 코드가 푸시되면 워크플로우가 시작됩니다.
2.  **Test**: 단위 테스트가 자동으로 실행됩니다.
3.  **Build**: Gradle을 통해 애플리케이션을 빌드합니다.
4.  **Docker**: Docker 이미지를 생성하고 Docker Hub에 푸시합니다.
5.  **Deploy**: AWS EC2 서버에 배포됩니다.

> **수동 배포 필요 시**: Docker 및 Docker Compose가 필요하며, `docker-compose up -d` 명령어를 사용합니다. (권장하지 않음, CI 사용 요망)

### 🤝 기여 가이드 (Contribution)

1.  이 저장소를 **Fork** 합니다.
2.  `feature/기능명` 또는 `fix/버그명` 브랜치를 생성하여 작업합니다.
3.  작업 완료 후 코드 품질을 확인합니다.
   *   IntelliJ IDEA의 코드 포맷팅 사용 (Ctrl+Alt+L / Cmd+Option+L)
   *   `./gradlew test` 실행하여 테스트 통과 확인
4.  **Pull Request**를 생성하고 리뷰를 요청합니다.


## 📬 문의

- 프로젝트 관련 문의는 팀원에게 직접 연락 부탁드립니다.
