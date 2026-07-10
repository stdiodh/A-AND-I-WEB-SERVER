# 운영 배포

## 기준 경로

운영 태그 배포의 기준은 `.github/workflows/deploy-tag.yml`입니다. `vX.Y.Z` 형식의 태그가 다음 순서로 배포됩니다.

1. 태그 형식 검증
2. 테스트와 JaCoCo 기준 통과
3. 단일 bootJar 생성 및 `Dockerfile.runtime` 이미지 빌드
4. ECR push
5. EC2에서 포트 없는 candidate readiness 확인
6. 실제 컨테이너 교체 및 readiness 확인
7. 실패 시 직전 로컬 이미지로 복구

동시에 두 운영 태그가 배포되지 않도록 production concurrency group을 사용합니다.

## MongoDB 볼륨 주의

기존 운영 배포의 Compose 논리 키는 `mongo_data`입니다. 실제 Docker 볼륨 이름에는 Compose project name이 반영될 수 있습니다. 배포 workflow는 실행 중인 `aandi-report-mongodb`의 `/data/db` mount 이름을 읽어 명시적으로 재사용하며, GitHub variable `MONGO_VOLUME_NAME`과 다르면 배포를 중단합니다.

MongoDB 컨테이너가 없는 최초 배포나 복구 상황에는 `MONGO_VOLUME_NAME`을 반드시 설정해야 합니다. 이름을 자동 추정해 새 빈 볼륨을 만드는 동작은 허용하지 않습니다.

운영 호스트에서는 다음 작업을 금지합니다.

- `docker compose down -v`
- `mongo_data` 이름 변경 또는 새 볼륨으로의 무검증 전환
- 백업·복원 검증 없이 `docker-compose.prod.yml`로 교체

Compose 단일화는 먼저 `docker volume inspect`와 컨테이너 mount, 백업·복원 리허설로 실제 볼륨을 확인한 뒤 별도 변경으로 진행합니다.

## 비밀값

배포 workflow가 호스트에 기록하는 compose 파일에는 환경변수 이름만 남고 실제 secret 값은 기록하지 않습니다. secret은 SSH 세션 환경에서 Compose로 전달됩니다.

다만 Docker 컨테이너 환경 메타데이터는 호스트 관리자에게 보일 수 있습니다. 이를 제거하려면 AWS Secrets Manager 또는 파일 마운트와 애플리케이션의 `_FILE` 입력 지원이 필요합니다.

과거 배포에서 compose 파일에 값이 확장되었을 가능성이 있으므로 운영 반영 후 `AUTH_JWT_SECRET`과 `APP_REPORT_V2_SALT_SECRET` 회전을 권장합니다.

## 실패와 복구

- candidate는 포트를 열지 않고 이벤트 소비 기능을 끈 상태로 readiness를 확인합니다.
- candidate 전에 MongoDB는 `--no-recreate`로 기동하고 health를 확인합니다. 기존 MongoDB 컨테이너 구성은 교체하지 않습니다.
- candidate 실패 시 현재 애플리케이션 서비스는 교체하지 않습니다.
- 실제 컨테이너 교체 후 실패하면 배포 직전 image ID에 붙인 로컬 rollback tag를 현재 runtime 설정으로 재기동합니다.
- 이전 컨테이너가 없는 최초 배포에는 자동 rollback 대상이 없습니다.

자동 rollback은 애플리케이션 이미지 rollback입니다. GitHub variables/secrets의 이전 값까지 복원하지 않으므로 설정 변경 자체의 rollback은 별도 운영 절차가 필요합니다. candidate는 Mongo readiness와 애플리케이션 기동을 확인하지만 이벤트 소비를 끄므로 SNS/SQS ARN, IAM 권한, queue 접근성은 검증하지 않습니다.

실제 ECR·EC2 환경의 candidate 실패와 rollback 성공 여부는 staging에서 별도로 검증해야 합니다. 로컬 Docker 데몬 없이 확인 가능한 범위는 workflow YAML, 원격 셸 문법, Compose 렌더링, bootJar 구조까지입니다.

## 체크인된 Compose 파일

`docker-compose.prod.yml`은 현재 운영 배포의 기준 파일이 아닙니다. 인증·이벤트 환경 계약과 볼륨 이름이 태그 배포 경로와 다르므로 로컬 검토용 템플릿으로만 취급합니다. 두 정의를 합치는 작업은 운영 볼륨 확인 후 진행합니다.

호스트에 남는 `docker-compose.yml`에는 변수 참조만 있으므로 SSH 세션이 끝난 뒤 단독으로 `docker compose config/up`을 재현할 수 없습니다. 수동 재기동 대신 같은 태그 배포 workflow를 다시 실행하며, 수동 복구가 필요하면 승인된 runtime 변수와 secret을 다시 주입해야 합니다.
