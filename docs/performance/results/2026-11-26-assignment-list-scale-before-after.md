# Assignment List Scale Before/After (2026-11-26 재측정)

로컬 MongoDB `aandi_performance`와 localhost API만 대상으로 한 과제 목록 조회 before/after 고정 부하 재측정입니다. 운영 최대 처리량으로 해석하지 않습니다.

- 측정일: `2026-11-26 KST`
- Target API: `GET /v2/courses/{courseSlug}/assignments?status=PUBLISHED`
- Before server commit: `9b678b6`
- After server commit: `642cfd3`
- 이전 기록: [2026-07-09 Assignment List Before/After](./2026-07-09-assignment-list-before-after.md)
- 표의 값은 표시용 반올림이며, 반올림하지 않은 값은 [`2026-11-26-assignment-list-scale-before-after.json`](./2026-11-26-assignment-list-scale-before-after.json)에 있습니다.

## Environment

| 항목 | 값 |
| :--- | :--- |
| 머신 | Apple M5 Pro, 메모리 48.00 GiB, 논리 CPU 15, macOS 26.6.2 (25G83), AC 전원 |
| 서버 JVM (bootRun) | `openjdk version "21.0.6" 2025-01-21` (Homebrew), -XX:TieredStopAtLevel=1 (Spring Boot bootRun default) |
| k6 | `k6 v0.52.0 (commit/20f8febb5b, go1.22.4, darwin/arm64)` (공식 릴리스 바이너리, zip SHA-256 체크섬 일치) |
| MongoDB | 7.0.16 (`mongo:7.0.16` 컨테이너, localhost:27018, 새 볼륨) |
| Docker | 29.5.3 (VM cpus=15 memBytes=8320815104) |
| k6 러너 저장소 커밋 | `d4cd644` (A-AND-I-WEB-SERVER main, gitDirty=false) |
| 측정 시각 | before k6 2026-11-26 11:37–12:00 UTC (예비 실행 포함), after k6 12:00–12:20 UTC (KST = UTC+9) |

## Conditions

- API: `GET /v2/courses/{courseSlug}/assignments?status=PUBLISHED` (목록 100%, 상세 0%)
- before 서버 `9b678b6`, after 서버 `642cfd3`. 각각 `git worktree add --detach` 후 `./gradlew bootRun`
- constant-arrival-rate, 2분 × 3회, preAllocatedVUs 30, maxVUs 200, sleep 0
- 표의 값은 3회 중앙값이고, 괄호 안은 3회 최소~최대입니다. 단위는 ms입니다.
- 실패율 > 0, checks < 100%, dropped > 0 중 하나라도 해당하면 **무효**로 표시합니다.

### 예비 실행 (before 서버, 30초, 100 RPS)

| 조건 | 처리량 (req/s) | P50 | P95 | dropped | HTTP 실패율 | 판정 |
| :--- | ---: | ---: | ---: | ---: | ---: | :--- |
| precheck-list-only-1000 | 10.00 | 19872.798 | 20565.773 | 2536 | 0.00% | 불통과 → 50 RPS로 측정 |
| precheck-list-only-300 | 34.57 | 5620.687 | 6017.544 | 1790 | 0.00% | 불통과 → 50 RPS로 측정 |

### 과제 30개 · 100 RPS

- fixture fingerprint: before `005fc5cdb43a402413b263ae38b5565a73634b25bc7532cef346320ad295cf1a` / after `005fc5cdb43a402413b263ae38b5565a73634b25bc7532cef346320ad295cf1a` (동일)
- 판정: before **유효**, after **유효**

| 지표 | Before `9b678b6` | After `642cfd3` | 변화 |
| :--- | ---: | ---: | :--- |
| 목록 P50 | 8.222 (8.154~8.283) | 8.394 (8.371~8.439) | 2.09% 증가 |
| 목록 P90 | 9.536 (9.128~9.672) | 9.791 (9.729~9.816) | 2.67% 증가 |
| 목록 P95 | 10.160 (9.571~10.351) | 10.373 (10.342~10.397) | 2.10% 증가 |
| 목록 P99 | 13.088 (10.841~13.917) | 12.380 (12.211~12.439) | 5.41% 감소 |
| 목록 성공 처리량 (req/s) | 100.001 (99.999~100.001) | 100.000 (99.999~100.000) | |
| HTTP 실패율 | 0.0000% (최대 0.0000%) | 0.0000% (최대 0.0000%) | |
| checks 성공률 | 100.0000% (최소 100.0000%) | 100.0000% (최소 100.0000%) | |
| dropped iterations | 0 (0~0) | 0 (0~0) | |

### 과제 300개 · 50 RPS

- fixture fingerprint: before `7ec90312e9aa43aa2aa28760809a2c9cdfb558ec70a4f892413f6b390c6972ee` / after `7ec90312e9aa43aa2aa28760809a2c9cdfb558ec70a4f892413f6b390c6972ee` (동일)
- 판정: before **무효**, after **유효**
  - before 무효 사유: run 1: dropped iterations 2074; run 2: dropped iterations 2291; run 3: dropped iterations 2143

| 지표 | Before `9b678b6` | After `642cfd3` | 변화 |
| :--- | ---: | ---: | :--- |
| 목록 P50 | 6398.542 (6262.320~6533.176) | 33.873 (33.279~34.385) | 99.47% 감소 (참고, 무효 조건 포함) |
| 목록 P90 | 6895.439 (6725.946~7178.376) | 39.978 (38.978~40.344) | 99.42% 감소 (참고, 무효 조건 포함) |
| 목록 P95 | 7021.340 (6864.758~7330.067) | 42.334 (40.905~42.786) | 99.40% 감소 (참고, 무효 조건 포함) |
| 목록 P99 | 7650.725 (7594.853~7759.763) | 46.947 (46.519~46.963) | 99.39% 감소 (참고, 무효 조건 포함) |
| 목록 성공 처리량 (req/s) | 30.818 (29.503~31.216) | 49.994 (49.994~49.995) | |
| HTTP 실패율 | 0.0000% (최대 0.0000%) | 0.0000% (최대 0.0000%) | |
| checks 성공률 | 100.0000% (최소 100.0000%) | 100.0000% (최소 100.0000%) | |
| dropped iterations | 2143 (2074~2291) | 0 (0~0) | |

### 과제 1000개 · 50 RPS

- fixture fingerprint: before `606ea27d61f599fb07d394be94ba7fcc513cca0937b07db8f4be76a8cb52714a` / after `606ea27d61f599fb07d394be94ba7fcc513cca0937b07db8f4be76a8cb52714a` (동일)
- 판정: before **무효**, after **유효**
  - before 무효 사유: run 1: HTTP failure rate 0.0008598452278589854; run 1: checks 0.9992015722884167; run 1: dropped iterations 4838; run 2: dropped iterations 4833; run 3: dropped iterations 4814

| 지표 | Before `9b678b6` | After `642cfd3` | 변화 |
| :--- | ---: | ---: | :--- |
| 목록 P50 | 23308.177 (23035.895~23591.618) | 190.139 (171.549~191.785) | 99.18% 감소 (참고, 무효 조건 포함) |
| 목록 P90 | 24816.959 (23858.742~27398.048) | 226.178 (197.944~226.967) | 99.09% 감소 (참고, 무효 조건 포함) |
| 목록 P95 | 24938.367 (24473.255~27937.422) | 237.767 (205.859~242.438) | 99.05% 감소 (참고, 무효 조건 포함) |
| 목록 P99 | 25522.224 (25430.543~29743.097) | 270.964 (220.036~293.095) | 98.94% 감소 (참고, 무효 조건 포함) |
| 목록 성공 처리량 (req/s) | 8.609 (8.501~8.666) | 49.940 (49.932~49.947) | |
| HTTP 실패율 | 0.0000% (최대 0.0860%) | 0.0000% (최대 0.0000%) | |
| checks 성공률 | 100.0000% (최소 99.9202%) | 100.0000% (최소 100.0000%) | |
| dropped iterations | 4833 (4814~4838) | 0 (0~0) | |

- 과제 30개 조건은 2026-07-09 문서와 fixture fingerprint(`005fc5cd…`)가 같아 동일 데이터셋을 재현했습니다. 이번 측정에서는 before/after 차이가 P50 +2.09%, P95 +2.10%, P99 -5.41%(after가 P50·P95는 약간 느리고 P99는 약간 빠름)로, 7월 문서의 P95 18.63% 감소는 재현되지 않았습니다. 30개 조건에서는 두 서버 모두 P50 8 ms대, P99 12~14 ms대이고, before의 P95·P99 run 간 범위(9.571~10.351 ms, 10.841~13.917 ms)가 차이보다 넓습니다.
- 7월 기록(before P95 9.297 ms / after 7.565 ms)과 비교해 이번 절대값(before 10.160 ms / after 10.373 ms)이 더 높습니다. 같은 머신이지만 측정 시점과 MongoDB 컨테이너 구성(새 컨테이너, 포트 27018)이 달라 7월 값과 이번 값을 섞어 비교하면 안 됩니다.
- 과제 300·1000개는 before 서버 30초 예비 실행(100 RPS)에서 dropped iterations가 발생해 규칙대로 50 RPS로 측정했습니다. 50 RPS에서도 before는 처리량이 300개 30.818 req/s, 1000개 8.609 req/s에 그쳐 dropped가 발생했으므로 before는 '무효'입니다. 표의 before 지연 시간은 포화(대기열 적체) 상태 값이라 지연 개선율(99%대)은 이력서 근거로 쓰지 않는 것을 권합니다.
- 같은 fixture·같은 50 RPS 조건에서 after(642cfd3)는 300개·1000개 모두 3회 전부 dropped 0, HTTP 실패 0, checks 100%로 목표 부하를 유지했습니다(목록 성공 처리량 300개 49.994 req/s, 1000개 49.940 req/s). 이 부분은 저장소 자체 집계 스크립트(summarize_runs.py)도 accepted=true로 판정했습니다.
- before 1000개 run 1에서 요청 1건이 k6 기본 60초 timeout으로 실패했습니다(HTTP 실패율 0.0860%, checks 99.92%).
- MongoDB는 지시된 `docker compose up -d mongodb` 대신 `docker run mongo:7.0.16`(포트 27018)로 띄웠습니다. 저장소 compose 파일의 이미지 태그가 `mongo:7`(로컬 캐시 7.0.37)이라 7.0.16을 맞추기 위해서이고, 기존 27017 컨테이너(aandi-report-mongodb-local)를 멈추는 것이 허용되지 않아 다른 포트를 사용했습니다. 서버와 fixture/k6 모두 같은 27018 URI를 사용했습니다.
- k6 러너는 A-AND-I-WEB-SERVER main `d4cd644`의 스크립트를 사용했습니다. 7월 측정의 러너 커밋 `6682802`와 비교해 performance/ 아래 차이는 results/README.md와 .gitkeep뿐입니다.
- before 300·1000 조건은 저장소 집계 스크립트가 dropped 때문에 aggregate JSON을 만들지 않아(accepted=false), 3회 중앙값·범위는 각 run의 summary JSON(`2026-11-26-raw/`)에서 같은 지표(assignment_list_duration med/p(90)/p(95)/p(99))로 직접 계산했습니다. aggregate가 있는 조건은 두 계산 결과가 일치함을 확인했습니다.
- 서버는 `./gradlew bootRun`(Spring Boot 기본 -XX:TieredStopAtLevel=1)으로 실행했습니다. 측정 중에는 다른 무거운 작업을 돌리지 않았습니다.

## Raw Files

- `2026-11-26-raw/{before,after}/list-only-{30,300,1000}/*.summary.json`: k6 run별 summary 원본 (`setup_data.accessToken` 값만 `[REDACTED]`로 바꿈)
- `2026-11-26-raw/{before,after}/list-only-{30,300,1000}/*.aggregate*.json`: 저장소 집계 스크립트(`performance/aggregate/summarize_runs.py`) 결과, accepted 여부 포함
- `2026-11-26-raw/{before,after}/list-only-{30,300,1000}/fixture-verify.json`: fixture 검증 결과와 fingerprint
- `2026-11-26-raw/precheck/`: before 서버 30초 예비 실행 summary
- `2026-11-26-raw/scenarios/*.env`: 측정에 사용한 시나리오 env (토큰 없음)
