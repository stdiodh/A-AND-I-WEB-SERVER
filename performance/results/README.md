# 성능 결과 파일 인덱스

> 이 디렉터리는 성능 측정 raw output을 모두 보관하는 장소가 아니라, README와 문서에서 인용하는 근거 파일을 고정하는 장소입니다.

[README로 돌아가기](../../README.md)

## 추적 기준

`performance/results/*`는 기본적으로 `.gitignore` 대상입니다. 문서나 README에서 직접 인용하는 결과만 예외적으로 추적합니다.

| 분류 | 파일 |
| :--- | :--- |
| Repository call evidence | `assignment-read-query-evidence.json` |
| Before aggregate | `assignment-read-before.aggregate.json` |
| After aggregate | `assignment-read-after.aggregate.json` |
| Strict comparison | `assignment-read-comparison.json`, `assignment-read-comparison.md` |
| Before raw summaries | `assignment-read-2026-06-22T17-31-36-700Z-d787f67.summary.json`, `assignment-read-2026-06-22T17-33-54-832Z-d787f67.summary.json`, `assignment-read-2026-06-22T17-36-06-142Z-d787f67.summary.json` |
| After raw summaries | `assignment-read-2026-06-22T17-50-14-635Z-08a2a21.summary.json`, `assignment-read-2026-06-22T17-52-26-529Z-08a2a21.summary.json`, `assignment-read-2026-06-22T17-54-39-325Z-08a2a21.summary.json` |

## 해석 기준

- `assignment-read-query-evidence.json`의 `60 -> 2`는 service-level repository interaction 기준입니다. MongoDB command count로 표현하지 않습니다.
- aggregate와 comparison 파일은 local fixed-load, 100 RPS, 2분, 3회 조건의 결과입니다. 최대 처리량이나 운영 capacity로 해석하지 않습니다.
- raw summary JSON은 aggregate를 재계산하기 위한 근거입니다. README에는 aggregate나 comparison에 정리된 값만 인용합니다.
- `gitDirty=false`인 결과만 resume/README 근거로 사용합니다.

## 재계산 명령

```bash
python3 performance/aggregate/summarize_runs.py \
  performance/results/assignment-read-2026-06-22T17-31-36-700Z-d787f67.summary.json \
  performance/results/assignment-read-2026-06-22T17-33-54-832Z-d787f67.summary.json \
  performance/results/assignment-read-2026-06-22T17-36-06-142Z-d787f67.summary.json \
  > performance/results/assignment-read-before.aggregate.json
```

```bash
python3 performance/aggregate/summarize_runs.py \
  performance/results/assignment-read-2026-06-22T17-50-14-635Z-08a2a21.summary.json \
  performance/results/assignment-read-2026-06-22T17-52-26-529Z-08a2a21.summary.json \
  performance/results/assignment-read-2026-06-22T17-54-39-325Z-08a2a21.summary.json \
  > performance/results/assignment-read-after.aggregate.json
```

```bash
python3 performance/aggregate/compare_aggregates.py \
  performance/results/assignment-read-before.aggregate.json \
  performance/results/assignment-read-after.aggregate.json \
  --json-output performance/results/assignment-read-comparison.json \
  --markdown-output performance/results/assignment-read-comparison.md
```

## 로컬 산출물 정리 기준

로컬에 남아 있는 ignored raw files는 필요하면 보관할 수 있지만, 다음 기준을 만족하지 않으면 README나 resume 근거로 사용하지 않습니다.

- 측정 commit, dirty 여부, fixture fingerprint, k6 version, load condition이 남아 있어야 합니다.
- before/after 비교는 fixture와 부하 조건이 같고 comparison 결과가 `accepted=true`여야 합니다.
- warmup, preflight, smoke, server log는 본 측정 근거가 아니라 보조 진단 자료로만 봅니다.
- 결과 파일을 외부에 공유하기 전 summary context에 local token이나 환경값이 포함되어 있지 않은지 확인합니다.
