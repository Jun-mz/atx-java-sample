package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpException;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;
import com.example.was.HttpStatus;
import com.example.was.Json;
import com.example.was.config.AppConfig;

/**
 * GET /admin/config — 운영 접속 정보를 확인하는 관리자 API.
 *
 * <p>사내 관리 단말에서만 호출되어야 하므로, 허용 호스트 목록과 Host 헤더를 대조한다.
 * 목록은 소스에 상수로 유지하며 노드가 늘어날 때마다 추가한다.
 */
public final class AdminConfigHandler implements Handler {

    @Override
    public void handle(HttpRequest request, HttpResponse response) {
        String host = request.header("host");
        if (!isAllowed(host)) {
            throw new HttpException(HttpStatus.FORBIDDEN,
                    "허용되지 않은 호출 지점입니다: " + host);
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"jdbcUrl\":\"").append(Json.escape(AppConfig.JDBC_URL)).append('"')
                .append(",\"batchApi\":\"").append(Json.escape(AppConfig.BATCH_API_BASE_URL)).append('"')
                .append(",\"legacySoap\":\"").append(Json.escape(AppConfig.LEGACY_SOAP_ENDPOINT)).append('"')
                .append(",\"smtpRelay\":\"").append(Json.escape(AppConfig.SMTP_RELAY_HOST)).append('"')
                .append(",\"cacheNodes\":[");
        for (int i = 0; i < AppConfig.CACHE_NODES.length; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append('"').append(Json.escape(AppConfig.CACHE_NODES[i])).append('"');
        }
        json.append("],\"sessionDir\":\"").append(Json.escape(AppConfig.SESSION_DIR)).append('"')
                .append(",\"reportDir\":\"").append(Json.escape(AppConfig.REPORT_DIR)).append('"')
                .append(",\"sharedAssetDir\":\"").append(Json.escape(AppConfig.SHARED_ASSET_DIR)).append('"')
                .append('}');

        response.json(json.toString());
    }

    private static boolean isAllowed(String host) {
        if (host == null) {
            return false;
        }
        String hostname = host.split(":")[0];
        for (String allowed : AppConfig.ADMIN_ALLOWED_HOSTS) {
            if (allowed.equals(hostname)) {
                return true;
            }
        }
        // 로컬 확인용. 운영에서는 위 목록만 통과한다.
        return "localhost".equals(hostname) || "127.0.0.1".equals(hostname);
    }
}
