# Demo Assets Guide

이 문서는 README와 docs에 넣을 GIF/이미지 촬영 기준을 정리한다.
현재 작업에서는 실제 이미지나 GIF를 생성하지 않고, 필요한 위치와 촬영 기준만 남긴다.

## README에 필요한 대표 이미지

### 1. 전체 시스템 흐름 이미지

- 위치: `README.md` > `🏗️ 시스템 아키텍처`
- 파일명: `docs/assets/images/architecture.png`
- 형식: PNG 또는 SVG
- 내용: Client -> A&I Web Server -> MongoDB -> SNS/SQS -> Online Judge Server -> CloudWatch Logs
- 기준: 각 컴포넌트의 책임을 한 줄씩 표시하고, API 요청 흐름과 이벤트 흐름을 색으로 구분한다.

### 2. 과제 운영 동작 GIF

- 위치: `README.md` > `코스별 과제 조회와 공개 상태 계산`
- 파일명: `docs/assets/demo-assignment-flow.gif`
- 형식: GIF
- 내용: 코스 목록 조회 -> 과제 목록 조회 -> 과제 상세 확인 -> 공개 테스트케이스 확인
- 비고: 프론트엔드 화면이 없으면 Swagger UI 또는 API Client 흐름으로 대체한다.

### 3. 관리자 과제 운영 GIF

- 위치: `README.md` > `관리자 코스/수강/과제 운영`
- 파일명: `docs/assets/demo-admin-assignment-flow.gif`
- 형식: GIF
- 내용: 관리자 토큰 요청 -> 과제 생성 또는 복사 -> 응답 확인
- 비고: 중복 과제 복사 시 `409` 응답이 나오는 장면을 별도 보조 컷으로 남기면 좋다.

### 4. 테스트케이스 OJ 동기화 GIF

- 위치: `README.md` 및 `docs/api-flows/testcase-oj-sync-flow.md`
- 파일명: `docs/assets/demo-testcase-oj-sync.gif`
- 형식: GIF
- 내용: 테스트케이스 수정 -> 저장 -> problem sync 이벤트 로그 확인 -> SNS/SQS 전달 확인
- 비고: private/hidden testcase 원문은 노출하지 않는다.

### 5. Judge Completion 이벤트 소비 GIF

- 위치: `README.md` 및 `docs/api-flows/judge-completion-consumer-flow.md`
- 파일명: `docs/assets/demo-judge-completion-flow.gif`
- 형식: GIF
- 내용: SQS 메시지 주입 -> consumer 로그 확인 -> 관리자 제출 현황 조회
- 비고: `publicCode`, 점수, timestamp가 projection에 반영되는 장면을 보여준다.

## 파일명 규칙

- `docs/assets/demo-assignment-flow.gif`
- `docs/assets/demo-admin-assignment-flow.gif`
- `docs/assets/demo-testcase-oj-sync.gif`
- `docs/assets/demo-judge-completion-flow.gif`
- `docs/assets/images/architecture.png`

## 촬영 전 확인

- 실제 토큰, secret, queue URL, AWS account id는 화면에 노출하지 않는다.
- 테스트케이스 입력/출력 원문 중 private/hidden 성격의 데이터는 가린다.
- API Client를 사용할 때 request/response는 핵심 필드만 보이게 접는다.
