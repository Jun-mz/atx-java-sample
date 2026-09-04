package com.example.was;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * 응답 한 건. 핸들러가 상태/헤더/본문을 채우면 {@link #writeTo(OutputStream)} 이
 * HTTP 메시지로 직렬화한다.
 */
public final class HttpResponse {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String CRLF = "\r\n";

    private HttpStatus status = HttpStatus.OK;
    private final Map<String, String> headers = new LinkedHashMap<String, String>();
    private byte[] body = new byte[0];

    public HttpResponse status(HttpStatus status) {
        this.status = status;
        return this;
    }

    public HttpStatus status() {
        return status;
    }

    public HttpResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    public HttpResponse body(byte[] body) {
        this.body = (body != null) ? body : new byte[0];
        return this;
    }

    public HttpResponse text(String text) {
        return contentType("text/plain; charset=UTF-8").body(text.getBytes(UTF_8));
    }

    public HttpResponse html(String html) {
        return contentType("text/html; charset=UTF-8").body(html.getBytes(UTF_8));
    }

    public HttpResponse json(String json) {
        return contentType("application/json; charset=UTF-8").body(json.getBytes(UTF_8));
    }

    public HttpResponse contentType(String contentType) {
        return header("Content-Type", contentType);
    }

    public int bodyLength() {
        return body.length;
    }

    /**
     * 상태 라인 + 헤더 + 본문을 스트림에 쓴다. Content-Length 와 Date 는 여기서 채운다.
     *
     * @param includeBody HEAD 요청이면 false — 헤더는 그대로 두고 본문만 생략한다.
     */
    public void writeTo(OutputStream out, boolean includeBody) throws IOException {
        headers.put("Content-Length", String.valueOf(body.length));
        headers.put("Date", httpDate());
        if (!headers.containsKey("Content-Type") && body.length > 0) {
            headers.put("Content-Type", "application/octet-stream");
        }

        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status.code()).append(' ').append(status.reasonPhrase()).append(CRLF);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            head.append(entry.getKey()).append(": ").append(entry.getValue()).append(CRLF);
        }
        head.append(CRLF);

        out.write(head.toString().getBytes(UTF_8));
        if (includeBody && body.length > 0) {
            out.write(body);
        }
        out.flush();
    }

    public void writeTo(OutputStream out) throws IOException {
        writeTo(out, true);
    }

    private static String httpDate() {
        // SimpleDateFormat 은 스레드 세이프하지 않으므로 호출마다 새로 만든다.
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        return format.format(new Date());
    }
}
