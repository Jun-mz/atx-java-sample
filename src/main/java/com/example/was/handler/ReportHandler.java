package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpException;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;
import com.example.was.HttpStatus;
import com.example.was.aws.InstanceMetadataClient;
import com.example.was.session.Cookies;
import com.example.was.session.FileSessionStore;
import com.example.was.session.Session;
import com.example.was.storage.ReportStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 주문 리포트 생성/다운로드.
 *
 * <ul>
 *   <li>{@code POST /reports?period=202609} — 리포트를 만들어 로컬 디스크에 쓰고 ID 를 반환</li>
 *   <li>{@code GET /reports?id=...} — 그 인스턴스의 로컬 디스크에서 읽어 CSV 로 응답</li>
 * </ul>
 *
 * <p>생성과 다운로드가 같은 인스턴스에서 일어나야 한다. 스티키 세션이 풀리면
 * 방금 만든 리포트를 찾지 못한다.
 */
public final class ReportHandler implements Handler {

    @Override
    public void handle(HttpRequest request, HttpResponse response) throws IOException {
        Session session = FileSessionStore.load(Cookies.sessionId(request));
        if (session == null) {
            throw new HttpException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
        }

        if ("POST".equals(request.method())) {
            create(request, response, session);
        } else {
            download(request, response);
        }
    }

    private void create(HttpRequest request, HttpResponse response, Session session) throws IOException {
        String period = request.param("period", "202609");
        String reportId = ReportStore.generate(session.get("userId"), period);

        response.status(HttpStatus.CREATED).json(
                "{\"reportId\":\"" + reportId + "\""
                        + ",\"downloadUrl\":\"/reports?id=" + reportId + "\""
                        + ",\"generatedBy\":\"" + InstanceMetadataClient.instanceId() + "\"}");
    }

    private void download(HttpRequest request, HttpResponse response) throws IOException {
        String reportId = request.param("id");
        File file = ReportStore.find(reportId);
        if (file == null) {
            throw new HttpException(HttpStatus.NOT_FOUND,
                    "이 인스턴스에 해당 리포트가 없습니다: " + reportId);
        }

        response.contentType("text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"")
                .body(readAll(file));
    }

    private static byte[] readAll(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(file.length(), 32L));
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
