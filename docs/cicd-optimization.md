# CI/CD 최적화 측정

PR마다 같은 검사를 하던 CI를 검사 범위별로 나누고, 배포 이미지를 미리 만든 JAR로 빌드하도록 바꿨습니다. GitHub Actions 성공 실행 5회의 중앙값으로 비교했습니다.

## 이전과 현재

| 항목 | 이전 중앙값 | 현재 중앙값 | 개선율 |
| --- | ---: | ---: | ---: |
| 동일 범위 CI 전체 | 140.0초 | 109.0초 | 22.1% |
| 문서만 바뀐 push의 CI | 124.0초 | 28.0초 | 77.4% |
| 백엔드 테스트 | 121.0초 | 109.0초 | 9.9% |
| 성능 자산 검사 | 22.0초 | 20.0초 | 9.1% |
| CD dry-run 전체 경로 | 211.0초 | 82.0초 | 61.1% |
| CD 이미지 빌드만 | 161.0초 | 29.0초 | 82.0% |
| JAR 빌드 | 52.0초 | 63.0초 | 21.2% 느려짐 |

개선율은 `(이전 - 현재) / 이전 × 100`입니다. 항목마다 바꾼 범위가 달라 수치를 더하거나 누적해서 보지 않습니다. CD는 AWS, ECR, EC2 SSH, 운영 URL을 쓰지 않는 `push: false` dry-run이라 실제 배포 시간이 아닙니다.

## 바꾼 내용

- **동일 범위 CI**: 기존 `main` CI와 같은 검사(백엔드 테스트, JaCoCo 기준, 성능 자산 검사)만 묶어 비교했습니다. JAR 빌드는 PR 검사에서 빼서 따로 쟀습니다.
- **문서만 바뀐 push**: 바뀐 파일이 모두 문서면 빌드·테스트를 건너뛰는 분기를 추가했습니다. README만 바꾼 실제 push 5회로 비교했습니다([개선 전 실행](https://github.com/Team-AnI/A-AND-I-WEB-SERVER/actions/runs/29148359466), [개선 후 실행](https://github.com/Team-AnI/A-AND-I-WEB-SERVER/actions/runs/29149660905)).
- **CD 이미지**: 이전 `Dockerfile`은 Docker 안에서 Gradle 빌드를 다시 했습니다. 현재 `Dockerfile.runtime`은 앞 단계에서 만든 JAR만 받아 이미지를 만듭니다.

## 확인한 방법

- 측정 원본은 [`docs/metrics/web-cicd-remeasure.json`](./metrics/web-cicd-remeasure.json)에 있고, `scripts/validate-web-cicd-metrics.py` 검사를 통과했습니다.
- 실패, 취소, 건너뛴 실행은 성공 표본에 넣지 않았습니다. 표의 항목은 모두 이전·현재 각각 5회 성공입니다.

## 남은 한계

- JAR 빌드는 21.2% 느려져 개선으로 보지 않습니다.
- BuildKit 캐시를 설정했지만 캐시를 재사용한 실행은 측정하지 못해, 캐시 효과는 주장하지 않습니다.
