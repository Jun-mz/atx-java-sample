package com.example.was.aws;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * EC2 인스턴스 메타데이터 서비스(IMDS)를 직접 호출한다.
 *
 * <p>인스턴스 식별 정보와 인스턴스 프로파일에 붙은 IAM 역할의 임시 자격 증명을
 * 여기서 가져와 S3 호출 등에 사용한다. AWS SDK 를 쓰지 않고 링크로컬 주소
 * 169.254.169.254 로 직접 HTTP 요청을 보내는 방식이다.
 */
public final class InstanceMetadataClient {

    private static final Logger LOG = Logger.getLogger(InstanceMetadataClient.class.getName());
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** IMDS 링크로컬 엔드포인트. EC2 안에서만 응답한다. */
    private static final String IMDS_BASE = "http://169.254.169.254/latest/meta-data";
    private static final String IMDS_TOKEN_URL = "http://169.254.169.254/latest/api/token";
    private static final String CREDENTIALS_PATH = "/iam/security-credentials/";

    private static final int CONNECT_TIMEOUT_MS = 1000;
    private static final int READ_TIMEOUT_MS = 1000;

    /** 자격 증명은 매 요청마다 받아오지 않고 프로세스 메모리에 캐시한다. */
    private static volatile Map<String, String> cachedCredentials;
    private static volatile long credentialsFetchedAt;
    private static final long CREDENTIALS_TTL_MS = 5 * 60 * 1000L;

    /**
     * IMDS 에 닿지 못한 직후 매 요청마다 다시 타임아웃을 기다리면 응답이 느려진다.
     * 한 번 실패하면 잠시 호출을 건너뛴다.
     */
    private static final long UNAVAILABLE_BACKOFF_MS = 30 * 1000L;
    private static volatile long unavailableUntil;

    private InstanceMetadataClient() {
    }

    /** 인스턴스 ID. IMDS 에 닿지 못하면 "unknown". */
    public static String instanceId() {
        return get("/instance-id", "unknown");
    }

    /** 가용영역. 로그와 헬스 응답에 찍는다. */
    public static String availabilityZone() {
        return get("/placement/availability-zone", "unknown");
    }

    public static String privateIpv4() {
        return get("/local-ipv4", "unknown");
    }

    /** 인스턴스 프로파일에 연결된 IAM 역할 이름. */
    public static String iamRoleName() {
        return get(CREDENTIALS_PATH, "");
    }

    /**
     * 인스턴스 프로파일의 임시 자격 증명(AccessKeyId/SecretAccessKey/Token)을 가져온다.
     * S3 업로드 서명에 사용한다.
     */
    public static Map<String, String> credentials() {
        long now = System.currentTimeMillis();
        Map<String, String> cached = cachedCredentials;
        if (cached != null && (now - credentialsFetchedAt) < CREDENTIALS_TTL_MS) {
            return cached;
        }

        String role = iamRoleName();
        if (role.isEmpty()) {
            LOG.warning("인스턴스 프로파일에 연결된 IAM 역할을 찾지 못했습니다");
            return new HashMap<String, String>();
        }

        String body = get(CREDENTIALS_PATH + role.trim(), "");
        Map<String, String> parsed = parseCredentialJson(body);
        cachedCredentials = parsed;
        credentialsFetchedAt = now;
        return parsed;
    }

    private static String get(String path, String defaultValue) {
        if (System.currentTimeMillis() < unavailableUntil) {
            return defaultValue;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(IMDS_BASE + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);

            // IMDSv2 가 강제된 인스턴스에서는 토큰이 필요하다. 실패하면 v1 으로 그냥 진행한다.
            String token = fetchTokenQuietly();
            if (token != null) {
                connection.setRequestProperty("X-aws-ec2-metadata-token", token);
            }

            if (connection.getResponseCode() != 200) {
                return defaultValue;
            }
            return readBody(connection.getInputStream());
        } catch (IOException e) {
            LOG.log(Level.FINE, "IMDS 호출 실패: " + path, e);
            unavailableUntil = System.currentTimeMillis() + UNAVAILABLE_BACKOFF_MS;
            return defaultValue;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String fetchTokenQuietly() {
        if (System.currentTimeMillis() < unavailableUntil) {
            return null;
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(IMDS_TOKEN_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("X-aws-ec2-metadata-token-ttl-seconds", "21600");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            return readBody(connection.getInputStream());
        } catch (IOException e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBody(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, UTF_8));
        try {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString().trim();
        } finally {
            reader.close();
        }
    }

    /** IMDS 자격 증명 응답에서 필요한 필드만 문자열로 긁어낸다. */
    private static Map<String, String> parseCredentialJson(String json) {
        Map<String, String> result = new HashMap<String, String>();
        putIfPresent(result, json, "AccessKeyId");
        putIfPresent(result, json, "SecretAccessKey");
        putIfPresent(result, json, "Token");
        putIfPresent(result, json, "Expiration");
        return result;
    }

    private static void putIfPresent(Map<String, String> target, String json, String field) {
        String marker = "\"" + field + "\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return;
        }
        int colon = json.indexOf(':', start + marker.length());
        int open = json.indexOf('"', colon + 1);
        int close = json.indexOf('"', open + 1);
        if (colon < 0 || open < 0 || close < 0) {
            return;
        }
        target.put(field, json.substring(open + 1, close));
    }
}
