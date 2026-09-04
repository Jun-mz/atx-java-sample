# 기대 판정 결과 (정답지)

`EC2toEKS_Arch` ①단계(코드 레벨 현대화)의 입력 픽스처로 만든 샘플입니다.
AS-IS 다이어그램이 지목한 전환 트리거 (a)·(b)를 의도적으로 심었습니다.

- **(a) EKS 비호환 코드** → ATX Custom 이 작업 브랜치에서 수정
- **(b) 로컬 디스크 파일 데이터** → 판별 에이전트(Strands)가 PVC 잔여 여부와 접근 모드를 판정

판별 에이전트를 검증할 때 이 문서를 정답지로 씁니다. 오탐(FP)까지 잡을 수 있도록
**이미 외부화가 끝나 지적하면 안 되는 항목**도 함께 넣었습니다.

---

## (a) ATX code-level 현대화 대상

### A-1. 하드코딩 IP · 호스트명 → ConfigMap

| 위치 | 값 | 기대 조치 |
| --- | --- | --- |
| `config/AppConfig.java:15` | `DB_HOST = "10.0.12.34"` | ConfigMap 키 `db.host` |
| `config/AppConfig.java:20` | `JDBC_URL` 안에 IP·포트 인라인 | ConfigMap 조합으로 분해 |
| `config/AppConfig.java:24-25` | `CACHE_NODES` 사설 IP 2개 | ConfigMap 키 `cache.nodes` |
| `config/AppConfig.java:30` | `batch-api.internal.example.local:8080` | ConfigMap (Service DNS 로 대체 가능) |
| `config/AppConfig.java:32` | `LEGACY_SOAP_ENDPOINT = "http://10.0.20.7:9080/..."` | ConfigMap |
| `config/AppConfig.java:34` | `SMTP_RELAY_HOST = "10.0.20.25"` | ConfigMap |
| `config/AppConfig.java:38-40` | `ADMIN_ALLOWED_HOSTS` 노드 IP 목록 | ConfigMap 또는 NetworkPolicy 로 대체 |

`handler/AdminConfigHandler.java` 가 이 목록을 Host 헤더와 대조합니다.
파드 IP 는 매 배포마다 바뀌므로 IP 기반 허용 목록은 EKS 에서 그대로 동작하지 않습니다.

### A-2. 세션 상태 = 로컬 파일시스템

| 위치 | 내용 |
| --- | --- |
| `config/AppConfig.java:45` | `SESSION_DIR = "/var/was/sessions"` |
| `session/FileSessionStore.java` | 세션을 `<sessionId>.ser` 로 직렬화해 인스턴스 로컬에 저장 |
| `session/FileSessionStore.java` `load()` | 파일이 없으면 null — 다른 인스턴스로 가면 세션 소실 |
| `handler/SessionHandler.java` | 세션 없으면 401 + `servedBy` 노드 ID 반환 |

**기대 조치**: 세션 외부화(ElastiCache 등). PVC 로 해결할 문제가 **아님**.
판별 에이전트가 이걸 "EFS 공유 필요"로 판정하면 **오답**입니다.

### A-3. IMDS 직접 의존 → IRSA

| 위치 | 내용 |
| --- | --- |
| `aws/InstanceMetadataClient.java:28-29` | `http://169.254.169.254/latest/meta-data`, `/latest/api/token` |
| `aws/InstanceMetadataClient.java` `credentials()` | `/iam/security-credentials/<role>` 로 임시 자격 증명 취득 |
| `aws/S3ObjectStore.java` `put()` | 위 자격 증명으로 SigV4 서명 후 S3 PUT |
| `handler/InstanceInfoHandler.java` | instance-id · AZ · private IP · IAM role 노출 |
| `storage/SharedAssetStore.java` `appendManifest()` | manifest 에 instance-id 기록 |

**기대 조치**: IRSA(ServiceAccount + IAM Role) 로 전환하고 SigV4 수기 서명은 SDK 로 대체.
IMDS 는 파드에서 hop limit 문제로 기본 차단되므로 그대로 두면 S3 업로드가 죽습니다.

---

## (b) PVC 판정 대상 — 판별 에이전트의 본 과제

ATX 가 A-1~A-3 을 고친 **뒤에도 남는** 로컬 디스크 의존을 찾아 접근 모드를 갈라야 합니다.

### B-1. 리포트 생성 → **EBS CSI (RWO)**

| 위치 | 근거 |
| --- | --- |
| `config/AppConfig.java:47` | `REPORT_DIR = "/var/was/reports"` |
| `storage/ReportStore.java:33` | 로컬 디렉토리 확보 |
| `storage/ReportStore.java` `generate()` | 요청받은 노드가 CSV 를 씀 |
| `storage/ReportStore.java` `find()` | 같은 노드가 다시 읽음 |
| `handler/ReportHandler.java` | 생성 노드와 다운로드 노드가 같아야 함 |

**기대 판정: `EBS_RWO`**

판정 근거로 나와야 하는 것:
- 파일 하나당 쓰기 주체가 **단일 노드**
- 다른 노드가 같은 파일을 읽거나 쓰는 코드 경로 없음
- 생성 후 24시간 보존, 노드 간 공유 요구 없음

### B-2. 공유 정적 산출물 → **EFS CSI (RWX)**

| 위치 | 근거 |
| --- | --- |
| `config/AppConfig.java:52` | `SHARED_ASSET_DIR = "/mnt/nas/was-shared/assets"` (NFS 마운트) |
| `storage/SharedAssetStore.java:39` | 전 인스턴스 동일 경로 마운트 |
| `storage/SharedAssetStore.java` `read()` / `list()` | 배치 노드가 쓴 파일을 웹 노드 전체가 읽음 |
| `storage/SharedAssetStore.java` `write()` | 웹 노드도 재생성 시 같은 디렉토리에 씀 (다중 writer) |
| `storage/SharedAssetStore.java:94-97` | `FileLock` 으로 노드 간 상호 배제 |
| `storage/SharedAssetStore.java` `appendManifest()` | 여러 노드가 같은 manifest 에 append |

**기대 판정: `EFS_RWX`** → ⑤단계 Terraform 조건부 EFS 프로비저닝이 발동해야 함

판정 근거로 나와야 하는 것:
- **다중 writer** (배치 노드 + 웹 노드)
- 노드 간 상호 배제 락 존재 = 동시 접근 전제
- NFS 마운트 경로 = 이미 공유 스토리지 전제

### B-3. 오탐 방지 — 지적하면 안 되는 항목

| 위치 | 왜 대상이 아닌가 |
| --- | --- |
| `aws/S3ObjectStore.java` | 사용자 업로드 원본은 **이미 S3 외부화 완료**. AS-IS 다이어그램의 "업로드 파일은 이미 S3 사용 중(해당 없음)"에 해당 |
| `handler/UploadHandler.java` | 요청 본문을 로컬 임시 파일 없이 S3 로 직행 |
| `storage/LocalPaths.java` | 개발 PC 우회 경로(`./data`). 운영 경로가 아니므로 PVC 판정 근거로 쓰면 안 됨 |
| `handler/StaticFileHandler.java` | 문서 루트는 이미지에 포함되는 읽기 전용 정적 리소스. 볼륨 불필요 |

---

## 판정 요약 (에이전트 출력 기대 형태)

| 항목 | 경로 | 판정 | 접근 모드 |
| --- | --- | --- | --- |
| 세션 | `/var/was/sessions` | `EXTERNALIZE_SESSION_STORE` | — (PVC 아님) |
| 리포트 | `/var/was/reports` | `PVC_REQUIRED` | `ReadWriteOnce` (EBS CSI) |
| 공유 산출물 | `/mnt/nas/was-shared/assets` | `PVC_REQUIRED` | `ReadWriteMany` (EFS CSI) |
| 업로드 원본 | `s3://example-prod-user-uploads` | `NO_ACTION` | — |
| 정적 리소스 | `webapp/` | `NO_ACTION` | — |

④단계 Report Gen(Lambda + Bedrock)이 이 표를 SonarQube·twistlock 결과와 함께 집약하고,
⑤단계는 `EFS_RWX` 가 하나라도 있을 때만 Terraform EFS 프로비저닝을 태웁니다.
