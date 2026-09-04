package com.example.was;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 소켓 InputStream 에서 HTTP/1.1 요청 하나를 읽어 들인 결과.
 *
 * <p>BufferedReader 를 쓰면 헤더를 읽는 과정에서 본문까지 미리 버퍼로 당겨오기 때문에
 * 바이트 본문을 정확히 잘라내기 어렵다. 그래서 헤더 구간은 raw stream 에서
 * 한 바이트씩 읽고, 본문은 Content-Length 만큼만 읽는다.
 */
public final class HttpRequest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** 요청 라인 최대 길이 (URI 폭탄 방지). */
    private static final int MAX_REQUEST_LINE_BYTES = 8 * 1024;
    /** 헤더 라인 최대 개수. */
    private static final int MAX_HEADER_COUNT = 100;
    /** 본문 최대 크기 (1MB). */
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private final String method;
    private final String path;
    private final String queryString;
    private final String protocol;
    private final Map<String, String> headers;
    private final Map<String, List<String>> queryParams;
    private final byte[] body;

    private HttpRequest(String method,
                        String path,
                        String queryString,
                        String protocol,
                        Map<String, String> headers,
                        Map<String, List<String>> queryParams,
                        byte[] body) {
        this.method = method;
        this.path = path;
        this.queryString = queryString;
        this.protocol = protocol;
        this.headers = headers;
        this.queryParams = queryParams;
        this.body = body;
    }

    /**
     * 스트림에서 요청 하나를 파싱한다.
     *
     * @return 파싱된 요청. 클라이언트가 아무것도 보내지 않고 연결을 끊으면 {@code null}.
     * @throws HttpException 요청 형식이 잘못되었거나 허용 크기를 넘은 경우
     */
    public static HttpRequest parse(InputStream in) throws IOException {
        String requestLine;
        try {
            requestLine = readLine(in, MAX_REQUEST_LINE_BYTES);
        } catch (EOFException e) {
            return null; // keep-alive 연결이 그냥 닫힌 정상 케이스
        }
        if (requestLine == null || requestLine.isEmpty()) {
            return null;
        }

        String[] tokens = requestLine.split(" ");
        if (tokens.length != 3) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "Malformed request line: " + requestLine);
        }
        String method = tokens[0].toUpperCase(Locale.ROOT);
        String rawTarget = tokens[1];
        String protocol = tokens[2];

        String path = rawTarget;
        String queryString = "";
        int questionMark = rawTarget.indexOf('?');
        if (questionMark >= 0) {
            path = rawTarget.substring(0, questionMark);
            queryString = rawTarget.substring(questionMark + 1);
        }

        Map<String, String> headers = readHeaders(in);
        byte[] body = readBody(in, headers);

        return new HttpRequest(
                method,
                decode(path),
                queryString,
                protocol,
                Collections.unmodifiableMap(headers),
                Collections.unmodifiableMap(parseQuery(queryString)),
                body);
    }

    private static Map<String, String> readHeaders(InputStream in) throws IOException {
        // 헤더 이름은 대소문자를 구분하지 않으므로 소문자로 정규화해서 담는다.
        Map<String, String> headers = new LinkedHashMap<String, String>();
        for (int i = 0; i < MAX_HEADER_COUNT; i++) {
            String line = readLine(in, MAX_REQUEST_LINE_BYTES);
            if (line == null || line.isEmpty()) {
                return headers;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new HttpException(HttpStatus.BAD_REQUEST, "Malformed header: " + line);
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            headers.put(name, value);
        }
        throw new HttpException(HttpStatus.BAD_REQUEST, "Too many headers");
    }

    private static byte[] readBody(InputStream in, Map<String, String> headers) throws IOException {
        String contentLength = headers.get("content-length");
        if (contentLength == null) {
            // chunked 전송은 이 예제 범위 밖이다.
            return new byte[0];
        }
        int length;
        try {
            length = Integer.parseInt(contentLength.trim());
        } catch (NumberFormatException e) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "Invalid Content-Length: " + contentLength);
        }
        if (length < 0) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "Negative Content-Length");
        }
        if (length > MAX_BODY_BYTES) {
            throw new HttpException(HttpStatus.PAYLOAD_TOO_LARGE, "Body exceeds " + MAX_BODY_BYTES + " bytes");
        }

        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n == -1) {
                throw new HttpException(HttpStatus.BAD_REQUEST, "Unexpected end of body");
            }
            read += n;
        }
        return body;
    }

    /** CRLF(또는 LF) 까지 한 줄을 읽는다. 첫 바이트부터 EOF 면 EOFException. */
    private static String readLine(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                byte[] bytes = buffer.toByteArray();
                int len = bytes.length;
                if (len > 0 && bytes[len - 1] == '\r') {
                    len--;
                }
                return new String(bytes, 0, len, UTF_8);
            }
            if (buffer.size() >= maxBytes) {
                throw new HttpException(HttpStatus.URI_TOO_LONG, "Line exceeds " + maxBytes + " bytes");
            }
            buffer.write(c);
        }
        if (buffer.size() == 0) {
            throw new EOFException("Connection closed by peer");
        }
        return new String(buffer.toByteArray(), UTF_8);
    }

    private static Map<String, List<String>> parseQuery(String queryString) {
        Map<String, List<String>> params = new LinkedHashMap<String, List<String>>();
        if (queryString == null || queryString.isEmpty()) {
            return params;
        }
        for (String pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? decode(pair.substring(0, eq)) : decode(pair);
            String value = eq >= 0 ? decode(pair.substring(eq + 1)) : "";
            List<String> values = params.get(key);
            if (values == null) {
                values = new ArrayList<String>();
                params.put(key, values);
            }
            values.add(value);
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 must be supported", e);
        } catch (IllegalArgumentException e) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "Invalid percent-encoding: " + value);
        }
    }

    public String method() {
        return method;
    }

    /** 퍼센트 디코딩된 경로. 예: {@code /hello} */
    public String path() {
        return path;
    }

    public String queryString() {
        return queryString;
    }

    public String protocol() {
        return protocol;
    }

    /** 헤더 이름은 대소문자를 가리지 않는다. */
    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, String> headers() {
        return headers;
    }

    /** 같은 이름이 여러 번 오면 첫 번째 값. */
    public String param(String name) {
        List<String> values = queryParams.get(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }

    public String param(String name, String defaultValue) {
        String value = param(name);
        return value != null ? value : defaultValue;
    }

    public Map<String, List<String>> params() {
        return queryParams;
    }

    public byte[] body() {
        return body;
    }

    public String bodyAsString() {
        return new String(body, UTF_8);
    }

    /** HTTP/1.1 은 기본 keep-alive, HTTP/1.0 은 기본 close. */
    public boolean isKeepAlive() {
        String connection = header("connection");
        if (connection != null) {
            return "keep-alive".equalsIgnoreCase(connection.trim());
        }
        return "HTTP/1.1".equalsIgnoreCase(protocol);
    }

    @Override
    public String toString() {
        return method + " " + path + (queryString.isEmpty() ? "" : "?" + queryString) + " " + protocol;
    }
}
