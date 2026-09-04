# SimpleWAS

OpenJDK **1.8** 표준 API 만으로 구현한 스레드 풀 기반 WAS. 외부 라이브러리 의존성이 없습니다.

커넥션마다 풀에서 스레드 하나를 꺼내 쓰는 고전적인 blocking I/O 모델로,
Tomcat 의 BIO 커넥터와 같은 구조입니다.

## 용도 — EC2→EKS 전환 파이프라인 ①단계 입력 픽스처

`EC2toEKS_Arch` 의 AS-IS 다이어그램이 지목한 전환 트리거를 의도적으로 심어 둔
샘플 애플리케이션입니다.

| 트리거 | 담당 | 심어 둔 위치 |
| --- | --- | --- |
| (a) 하드코딩 IP · 호스트명 | ATX Custom → ConfigMap | `config/AppConfig.java` |
| (a) 세션 상태 = 로컬 파일 | ATX Custom → 세션 외부화 | `session/FileSessionStore.java` |
| (a) IMDS 직접 의존 | ATX Custom → IRSA | `aws/InstanceMetadataClient.java` |
| (b) 로컬 디스크 파일 (단일 writer) | 판별 에이전트 → **EBS CSI (RWO)** | `storage/ReportStore.java` |
| (b) 로컬 디스크 파일 (다중 writer) | 판별 에이전트 → **EFS CSI (RWX)** | `storage/SharedAssetStore.java` |
| 업로드 원본 (이미 S3) | 조치 없음 — **오탐 방지용** | `aws/S3ObjectStore.java` |

각 항목의 기대 판정 결과는 [docs/EXPECTED_FINDINGS.md](docs/EXPECTED_FINDINGS.md) 에
정답지로 정리해 두었습니다. 판별 에이전트 검증 시 이 문서와 대조합니다.

## 구조

```
src/main/java/com/example/was/
├── WasServer.java         ServerSocket accept 루프 + 스레드 풀 + graceful shutdown, main()
├── ConnectionWorker.java  소켓 1개를 맡아 keep-alive 동안 요청을 반복 처리
├── HttpRequest.java       요청 라인/헤더/쿼리/본문 파싱
├── HttpResponse.java      상태·헤더·본문을 HTTP 메시지로 직렬화
├── HttpStatus.java        상태 코드 enum
├── HttpException.java     특정 상태 코드로 응답을 끝낼 때 던지는 예외
├── Router.java            "메서드 + 경로 -> 핸들러" 매핑
├── Handler.java           핸들러 인터페이스 (축약된 서블릿)
├── Json.java              JSON 문자열 이스케이프 유틸
├── config/
│   └── AppConfig.java          운영 접속 정보 상수 (하드코딩 IP·호스트명·경로)
├── session/
│   ├── Session.java            직렬화 가능한 세션 객체
│   ├── FileSessionStore.java   세션을 인스턴스 로컬 디스크에 .ser 파일로 저장
│   └── Cookies.java            Cookie 헤더 파싱
├── aws/
│   ├── InstanceMetadataClient.java  IMDS 직접 호출 (인스턴스 정보 + IAM 임시 자격 증명)
│   └── S3ObjectStore.java          SigV4 수기 서명 후 S3 PUT (업로드 원본)
├── storage/
│   ├── LocalPaths.java         절대 경로 확보, 개발 PC 에서는 ./data 로 우회
│   ├── ReportStore.java        리포트 CSV 를 인스턴스 로컬에 생성 (단일 writer)
│   └── SharedAssetStore.java   NFS 공유 디렉토리 읽기/쓰기 (다중 writer + FileLock)
└── handler/
    ├── HelloHandler.java       GET  /hello?name=...
    ├── HealthHandler.java      GET  /health
    ├── EchoHandler.java        POST /echo
    ├── LoginHandler.java       POST /login
    ├── SessionHandler.java     GET  /me
    ├── ReportHandler.java      POST/GET /reports
    ├── AssetHandler.java       GET/POST /assets
    ├── UploadHandler.java      POST /upload
    ├── InstanceInfoHandler.java GET /instance
    ├── AdminConfigHandler.java GET  /admin/config
    └── StaticFileHandler.java  정적 파일 (라우터 fallback)

src/main/resources/webapp/index.html   문서 루트
docs/EXPECTED_FINDINGS.md              판별 에이전트 정답지
```

## 실행

Maven 없이:

```sh
./run.sh          # 8080 포트
./run.sh 9090     # 포트 지정
```

Maven 으로:

```sh
mvn package
java -jar target/simple-was.jar 8080
```

동작 확인:

```sh
./smoke-test.sh          # 서버가 뜬 상태에서 실행
```

## 설정

| 시스템 프로퍼티 | 기본값 | 설명 |
| --- | --- | --- |
| `was.port` | `8080` | 리스닝 포트 (첫 번째 실행 인자가 우선) |
| `was.threads` | `50` | 워커 스레드 풀 크기 |
| `was.docroot` | `build/webapp` 있으면 그것, 없으면 `src/main/resources/webapp` | 정적 파일 문서 루트 |

```sh
java -Dwas.threads=100 -Dwas.docroot=/var/www -cp build/classes com.example.was.WasServer
```

## 엔드포인트

| 메서드 | 경로 | 설명 | 관련 트리거 |
| --- | --- | --- | --- |
| GET | `/` | 정적 파일 (문서 루트의 `index.html`) | — |
| GET | `/hello?name=claude` | `{"message":"Hello, claude!"}` | — |
| GET | `/health` | uptime / 메모리 / 자바 버전 | — |
| POST | `/echo` | 요청 본문을 그대로 반환 | — |
| POST | `/login` | 폼 본문 `userId=...` → 세션 생성 + 쿠키 | 세션 로컬 파일 |
| GET | `/me` | 세션 쿠키로 로그인 사용자 조회 | 세션 로컬 파일 |
| POST | `/reports?period=202609` | 리포트 CSV 생성 → 로컬 디스크 | **EBS RWO** |
| GET | `/reports?id=...` | 같은 노드의 로컬 디스크에서 CSV 다운로드 | **EBS RWO** |
| GET | `/assets` | NFS 공유 디렉토리 산출물 목록 | **EFS RWX** |
| GET | `/assets?name=...` | 공유 산출물 내용 | **EFS RWX** |
| POST | `/assets?name=...` | 공유 산출물 갱신 (FileLock) | **EFS RWX** |
| POST | `/upload?filename=...` | 업로드 원본을 S3 로 직행 | 조치 없음(오탐 방지) |
| GET | `/instance` | IMDS 로 조회한 인스턴스 정보 | IMDS → IRSA |
| GET | `/admin/config` | 운영 접속 정보 덤프 (허용 호스트 검사) | 하드코딩 IP |

## 구현 범위

지원:

- HTTP/1.1 요청 라인·헤더·본문 파싱 (헤더 이름 대소문자 무시)
- 쿼리 스트링 파싱 및 퍼센트 디코딩 (UTF-8)
- keep-alive (커넥션당 최대 100요청, idle 15초 타임아웃)
- HEAD (GET 핸들러 재사용, 본문만 생략)
- 정적 파일 서빙 + 확장자 기반 Content-Type + 문서 루트 탈출 차단
- 요청 크기 제한: 요청 라인 8KB, 헤더 100개, 본문 1MB
- 핸들러 예외 → 500, 라우팅 실패 → 404/405 (JSON 에러 응답)
- SIGTERM/Ctrl-C 시 accept 중단 후 진행 중 요청을 최대 10초 대기

미지원 (예제 범위 밖):

- `Transfer-Encoding: chunked`, HTTPS/TLS, HTTP/2
- 세션·쿠키, 서블릿 스펙, WAR 배포
- 멀티파트 폼 업로드, gzip 압축, 정적 파일 캐시 헤더(ETag/If-Modified-Since)
