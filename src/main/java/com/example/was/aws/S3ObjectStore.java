package com.example.was.aws;

import com.example.was.config.AppConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 사용자 업로드 파일 원본을 Amazon S3 에 저장한다.
 *
 * <p>업로드 경로는 예전에 이미 S3 로 옮겨 두었다. 로컬 디스크에는 아무것도 남기지 않고
 * 요청 본문을 그대로 S3 로 PUT 한다. 자격 증명은 인스턴스 프로파일에서 가져온다.
 */
public final class S3ObjectStore {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String SERVICE = "s3";
    private static final String ALGORITHM = "AWS4-HMAC-SHA256";

    private S3ObjectStore() {
    }

    /**
     * 객체 하나를 업로드한다.
     *
     * @param key         버킷 내 오브젝트 키 (예: uploads/2026/09/foo.png)
     * @param body        업로드할 바이트
     * @param contentType Content-Type 헤더 값
     * @return 업로드된 오브젝트의 s3:// URI
     */
    public static String put(String key, byte[] body, String contentType) throws IOException {
        Map<String, String> credentials = InstanceMetadataClient.credentials();
        String accessKey = credentials.get("AccessKeyId");
        String secretKey = credentials.get("SecretAccessKey");
        String sessionToken = credentials.get("Token");
        if (accessKey == null || secretKey == null) {
            throw new IOException("인스턴스 프로파일에서 S3 자격 증명을 가져오지 못했습니다");
        }

        String host = AppConfig.S3_UPLOAD_BUCKET + ".s3." + AppConfig.S3_REGION + ".amazonaws.com";
        String canonicalUri = "/" + key;
        Date now = new Date();
        String amzDate = format("yyyyMMdd'T'HHmmss'Z'", now);
        String dateStamp = format("yyyyMMdd", now);
        String payloadHash = hex(sha256(body));

        StringBuilder signedHeaders = new StringBuilder("content-type;host;x-amz-content-sha256;x-amz-date");
        StringBuilder canonicalHeaders = new StringBuilder()
                .append("content-type:").append(contentType).append('\n')
                .append("host:").append(host).append('\n')
                .append("x-amz-content-sha256:").append(payloadHash).append('\n')
                .append("x-amz-date:").append(amzDate).append('\n');
        if (sessionToken != null) {
            canonicalHeaders.append("x-amz-security-token:").append(sessionToken).append('\n');
            signedHeaders.append(";x-amz-security-token");
        }

        String canonicalRequest = "PUT\n" + canonicalUri + "\n\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String credentialScope = dateStamp + "/" + AppConfig.S3_REGION + "/" + SERVICE + "/aws4_request";
        String stringToSign = ALGORITHM + "\n" + amzDate + "\n" + credentialScope + "\n"
                + hex(sha256(canonicalRequest.getBytes(UTF_8)));
        String signature = hex(hmac(signingKey(secretKey, dateStamp), stringToSign.getBytes(UTF_8)));

        String authorization = ALGORITHM
                + " Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        HttpURLConnection connection = (HttpURLConnection) new URL("https://" + host + canonicalUri).openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("x-amz-content-sha256", payloadHash);
        connection.setRequestProperty("x-amz-date", amzDate);
        if (sessionToken != null) {
            connection.setRequestProperty("x-amz-security-token", sessionToken);
        }
        connection.setRequestProperty("Authorization", authorization);

        OutputStream out = connection.getOutputStream();
        try {
            out.write(body);
        } finally {
            out.close();
        }

        int status = connection.getResponseCode();
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new IOException("S3 업로드 실패: HTTP " + status);
        }
        return "s3://" + AppConfig.S3_UPLOAD_BUCKET + "/" + key;
    }

    private static byte[] signingKey(String secretKey, String dateStamp) {
        byte[] key = hmac(("AWS4" + secretKey).getBytes(UTF_8), dateStamp.getBytes(UTF_8));
        key = hmac(key, AppConfig.S3_REGION.getBytes(UTF_8));
        key = hmac(key, SERVICE.getBytes(UTF_8));
        return hmac(key, "aws4_request".getBytes(UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 서명 실패", e);
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String format(String pattern, Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }
}
