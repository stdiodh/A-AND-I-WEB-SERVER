# Query Tuning

> 메인 README로 돌아가기: [README](../README.md)

본 문서는 MongoDB 쿼리 튜닝 후보와 실제 측정 가능 여부를 기록합니다. 이번 단계에서는 대표 데이터셋과 `executionStats`가 없어 인덱스나 운영 코드를 변경하지 않았습니다.

## 현재 측정 상태

| 항목 | 결과 |
| :--- | :--- |
| 로컬 MongoDB | `localhost:27017` 실행 확인 |
| 대상 DB | `aandi` |
| 컬렉션 상태 | `db.getCollectionNames()` 결과 `[]` |
| `mongosh` | `/opt/homebrew/bin/mongosh` 사용 가능 |
| before/after query tuning | 현재 before/after 측정값은 없습니다 |
| 튜닝 적용 여부 | 적용하지 않음 |

측정 명령:

```bash
mongosh 'mongodb://localhost:27017/aandi' --quiet --eval '
const names = db.getCollectionNames().sort();
printjson(names.map(name => ({
  collection: name,
  count: db.getCollection(name).countDocuments(),
  indexes: db.getCollection(name).getIndexes()
})));
'
```

현재 로컬 DB에 컬렉션이 없어 `explain("executionStats")`를 실행해도 실제 운영 쿼리 비용을 판단할 근거가 되지 않습니다.

## 주요 쿼리 후보

| 영역 | Repository method | 예상 사용처 | 현재 인덱스 근거 |
| :--- | :--- | :--- | :--- |
| 과제 슬롯 조회 | `AssignmentRepository.findByCourseIdAndWeekNoAndOrderInWeek` | 관리자 과제 생성/수정 중 중복 슬롯 확인 | `ux_assignment_course_week_order` unique compound index |
| 과제 목록 조회 | `AssignmentRepository.findAllByCourseId`, `findAllByCourseIdAndWeekNo`, status 포함 variant | 사용자/관리자 과제 목록 조회 | `courseId, weekNo, orderInWeek` compound index가 일부 조건에 활용될 수 있으나 status 포함 조회는 실측 필요 |
| 과제 복사 중복 확인 | `findByCourseIdAndOriginAssignmentId`, `findByCourseIdAndCopyFingerprint` | 과제 복사 중복 방지 | partial unique compound index 2개 |
| 요구사항/테스트케이스 조회 | `findAllByAssignmentIdOrderBySortOrder`, `findAllByAssignmentIdOrderBySeq` | 과제 상세 조회, problem sync snapshot 생성 | `assignmentId, sortOrder`, `assignmentId, seq` unique compound index |
| 제출 현황 projection | `findByAssignmentIdAndPublicCode`, `findAllByAssignmentId` | judge completed upsert, 관리자 제출 현황 조회 | `assignmentId, publicCode` unique compound index, `assignmentId` single-field index |
| 수강생 조회 | `CourseEnrollmentRepository.findAllByCourseId`, status/userId variant | 코스 수강생 목록, 사용자 코스 목록 | `courseId, userId` unique compound index. status 포함 조회는 실측 필요 |
| 사용자 표시 정보 | `ReportUserRepository.findByPublicCode` | 제출 현황 publicCode join | `publicCode` unique index |

## 이번 단계 판단

- `assignment_submission_statuses`, `assignments`, `course_enrollments`는 성능 후보가 될 수 있지만, 현재 로컬 DB에 데이터가 없어 `totalDocsExamined`, `totalKeysExamined`, `executionTimeMillis`를 측정할 수 없습니다.
- 대표 데이터셋 없이 인덱스를 추가하면 읽기 성능 개선을 증명할 수 없고, 쓰기 비용과 unique 제약 충돌 가능성을 평가할 수 없습니다.
- 따라서 이번 단계에서는 쿼리/인덱스 변경을 하지 않았습니다.

## 측정 절차

1. staging 또는 local fixture DB에 대표 데이터를 준비합니다.
   - courses: 최소 10개
   - assignments: 코스별 100개 이상
   - assignment_test_cases: 과제별 5개 이상
   - course_enrollments: 코스별 100명 이상
   - assignment_submission_statuses: 과제별 제출자 100명 이상
2. 실제 API가 사용하는 repository method와 동일한 filter/sort를 MongoDB query로 재현합니다.
3. 변경 전 `explain("executionStats")`를 기록합니다.
4. 인덱스 또는 쿼리 변경 후보를 하나만 적용합니다.
5. 같은 데이터셋으로 `explain("executionStats")`를 다시 기록합니다.
6. 쓰기 비용이 늘어나는 collection은 insert/update 경로 테스트도 함께 실행합니다.

## explain 기록 템플릿

| 후보 | Collection | Filter | Sort | Index | totalDocsExamined | totalKeysExamined | executionTimeMillis | 판단 |
| :--- | :--- | :--- | :--- | :--- | ---: | ---: | ---: | :--- |
| 관리자 제출 현황 | `assignment_submission_statuses` | `{ assignmentId: "<id>" }` | 없음 | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] |
| 코스 수강생 조회 | `course_enrollments` | `{ courseId: "<id>", status: "ENABLED" }` | 없음 | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] |
| 과제 주차 조회 | `assignments` | `{ courseId: "<id>", weekNo: 1, status: "PUBLISHED" }` | `{ orderInWeek: 1 }` | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] | [확인 필요] |

## 이력서 사용 기준

현재 쓰면 안 되는 문장:

- MongoDB 쿼리 성능을 N% 개선했습니다.
- 관리자 제출 현황 조회 latency를 N ms 단축했습니다.
- 인덱스 튜닝으로 처리량을 N배 개선했습니다.

쓸 수 있는 문장:

- MongoDB 쿼리 튜닝 후보를 repository method와 기존 index 기준으로 분류하고, 대표 데이터셋 부재로 인한 측정 보류 사유를 문서화했습니다.
