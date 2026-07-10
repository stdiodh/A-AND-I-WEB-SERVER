# 2026-07 레거시 정리 기록

## 범위

- 기준 commit: `c2c542d`
- 작업 branch: `refactor/legacy-cleanup`
- 상태: 구현·검증 완료
- Docker·배포 파일: 변경 없음

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

Line coverage 상승은 새 테스트 추가가 아니라 미사용·미검증 `report` 코드가 configured scope에서 제거되어 분모가 줄어든 결과입니다. Branch coverage와 테스트 수는 유지됐습니다.
