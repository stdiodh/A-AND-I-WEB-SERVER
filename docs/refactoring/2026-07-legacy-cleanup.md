# 2026-07 레거시 정리 기록

쓰이지 않는 구현과 테스트 호환 별칭을 지우고, 테스트 파일 경로를 패키지 선언에 맞췄습니다. 동작, 외부 계약, Docker·배포 파일은 바꾸지 않았습니다.

## 이전과 현재

| 항목 | 이전 | 현재 |
| --- | --- | --- |
| 외부 참조 없는 `report` 구현 | 8개 파일 | 0개 (Swagger 그룹과 진입 URL은 유지) |
| 테스트 전용 `AssignmentExample*` 별칭 | 있음 | 없음, `AssignmentTestCase*`로 통일 |
| 패키지와 경로가 다른 테스트 파일 | 27개 | 0개 |
| 테스트 | 306 | 306 |
| Line coverage | 85.67% | 89.47% (2,803 / 3,133) |
| Branch coverage | 63.37% | 63.37% (943 / 1,488) |

Line coverage가 오른 것은 테스트를 더해서가 아니라, 검증되지 않던 `report` 코드가 빠져 분모가 줄었기 때문입니다.

## 범위

동작과 외부 계약을 바꾸지 않는 정리만 포함합니다.

- 외부 참조가 없는 `report` 구현 8개 파일 제거
- Swagger의 `report-v1/v2`, `report-service` 그룹과 진입 URL 보존
- 테스트에서만 사용하던 `AssignmentExample*` 호환 typealias 제거
- 테스트 용어를 `AssignmentTestCase*`로 통일
- assignment application의 event infrastructure 직접 의존 제거 및 구조 가드 강화
- 테스트 파일 27개를 선언 package와 일치하는 경로로 이동
- 패키지 구조 가이드와 완료 문서 갱신

## 의도적으로 보존한 호환 코드

운영 데이터나 외부 소비자 확인이 필요한 아래 항목은 제거하지 않았습니다.

- MongoDB의 `ARCHIVED`, `inputText`, 누락 visibility 읽기 호환
- `#` 없는 publicCode 조회 fallback
- `AssignmentDelivery` 문서와 삭제 cascade
- `ReportUser` Mongo type alias
- 응답 DTO의 `examples` 소스 호환 property

## 검증

```text
306 tests, failures/errors/skipped 0
JaCoCo Line 89.47% (2803/3133)
JaCoCo Branch 63.37% (943/1488)
jacocoTestCoverageVerification passed
bootJar passed
git diff --check passed
```
