package com.example.was.config;

/**
 * 운영 환경 접속 정보. 배포 시점에 값이 고정되어 있어 환경별로 소스를 분기해 왔다.
 *
 * <p>온프레미스 시절부터 이어져 온 방식으로, VPC 내부 사설 IP 와 내부 DNS 이름이
 * 상수로 박혀 있다. 스테이징 배포 시에는 이 파일을 수정한 뒤 다시 빌드한다.
 */
public final class AppConfig {

    private AppConfig() {
    }

    // ── RDS Aurora PostgreSQL (운영) ─────────────────────────────────────
    public static final String DB_HOST = "10.0.12.34";
    public static final int DB_PORT = 5432;
    public static final String DB_NAME = "orderdb";
    public static final String DB_USER = "app_user";
    public static final String JDBC_URL =
            "jdbc:postgresql://10.0.12.34:5432/orderdb?ssl=true&sslmode=require";

    // ── 세션/캐시 노드 (Memcached) ───────────────────────────────────────
    public static final String[] CACHE_NODES = {
            "10.0.12.55:11211",
            "10.0.12.56:11211"
    };

    // ── 사내 연동 시스템 ─────────────────────────────────────────────────
    /** 야간 배치 API. 내부 DNS 이름에 의존한다. */
    public static final String BATCH_API_BASE_URL = "http://batch-api.internal.example.local:8080";
    /** 레거시 SOAP 게이트웨이. DNS 등록이 없어 IP 로 직접 호출한다. */
    public static final String LEGACY_SOAP_ENDPOINT = "http://10.0.20.7:9080/legacy/soap";
    /** 사내 SMTP 릴레이. */
    public static final String SMTP_RELAY_HOST = "10.0.20.25";

    /** 관리자 API 호출을 허용하는 운영 WAS 노드. 스케일아웃 때마다 손으로 추가해 왔다. */
    public static final String[] ADMIN_ALLOWED_HOSTS = {
            "10.0.30.11",
            "10.0.30.12",
            "was-admin-01.internal.example.local"
    };

    // ── 파일 저장 경로 ───────────────────────────────────────────────────
    /** 인스턴스 로컬 디스크. 세션 직렬화 파일이 쌓인다. */
    public static final String SESSION_DIR = "/var/was/sessions";
    /** 인스턴스 로컬 디스크. 사용자가 요청한 리포트를 생성해 두는 곳. */
    public static final String REPORT_DIR = "/var/was/reports";
    /**
     * 전 WAS 인스턴스가 함께 바라보는 NFS 마운트 지점.
     * 배치 노드가 써 넣은 정적 산출물을 웹 노드들이 동시에 읽는다.
     */
    public static final String SHARED_ASSET_DIR = "/mnt/nas/was-shared/assets";

    // ── S3 (사용자 업로드 원본은 이미 외부화 완료) ───────────────────────
    public static final String S3_REGION = "ap-northeast-2";
    public static final String S3_UPLOAD_BUCKET = "example-prod-user-uploads";

    /**
     * 로컬 개발 PC 에는 위 절대 경로를 만들 권한이 없어, 디렉토리 생성 실패 시
     * 프로젝트 하위로 우회한다. 운영에서는 항상 위의 절대 경로를 사용한다.
     */
    public static final String LOCAL_FALLBACK_ROOT = "./data";
}
