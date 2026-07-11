# 문서 안내

문서는 현재 동작을 설명하는 운영 문서와 특정 시점의 측정 근거를 구분해 사용합니다. 과거 측정 문서는 재현 근거이므로 삭제하지 않지만, 현재 운영 설정의 기준으로 사용하지 않습니다.

## 현재 기준

| 문서 | 용도 |
| :--- | :--- |
| [프로젝트 README](../README.md) | 서비스 역할과 개발 환경 진입점 |
| [운영 배포](./DEPLOYMENT.md) | 태그 배포 기준, 볼륨 보존, 검증·복구 절차 |
| [MongoDB 인덱스 운영](./MONGODB_INDEX_RUNBOOK.md) | 인덱스 사전 점검, 적용, 검증과 안전한 재실행 절차 |
| [안정적 리팩터링 완료 기록](./refactoring/2026-07-stable-refactoring.md) | PR #68 완료 단계, 보존 계약, 별도 후속 작업 |
| [레거시 정리 기록](./refactoring/2026-07-legacy-cleanup.md) | 미사용 구현·테스트 호환 별칭·패키지 경로 정리와 검증 결과 |
| [과제 이벤트 일관성 ADR](./adr/0001-assignment-event-consistency.md) | MongoDB 변경과 SNS 발행의 일관성 개선 결정안 |
| [테스트와 성능 측정](./MEASUREMENT.md) | 테스트·성능 측정 방법과 과거 기준 |
| [성능 결과 재현](./performance/results/README.md) | 고정 부하 결과와 재현 절차 |

패키지 경계 원칙은 저장소 루트의 [PACKAGE_STRUCTURE_GUIDE](../PACKAGE_STRUCTURE_GUIDE.md)를 따릅니다.

## 진행 중 계획

| 문서 | 상태 |
| :--- | :--- |
| [과제 목록 지연시간 측정 계획](./ASSIGNMENT_LIST_LATENCY_PLAN.md) | 30개 fixture 측정 완료, 300/1000개 확장은 선택 작업 |
| [과제 이벤트 일관성 ADR](./adr/0001-assignment-event-consistency.md) | Proposed, 구현 전 MongoDB transaction 조건 확인 필요 |

## 과거 측정 근거

다음 문서는 완료된 시점의 증거이며 현재 배포 절차나 최신 테스트 수치의 기준이 아닙니다.

- `cicd-optimization.md`, `cicd-measurement-audit.md`: CI/CD 일회성 재측정 결과
- `resume-metrics.md`, `metrics/`: 이력서 수치와 생성 snapshot
- `docs/performance/results/` 및 `performance/results/`: 고정된 성능 실행 결과와 원시 산출물
- `.github/workflows/measure-web-cicd.yml`, `.github/workflows/cd-dry-run.yml`: 운영 배포가 아닌 측정 workflow

현재 운영 배포의 유일한 기준 workflow는 `.github/workflows/deploy-tag.yml`입니다.
