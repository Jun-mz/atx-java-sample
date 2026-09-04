package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpException;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;
import com.example.was.HttpStatus;
import com.example.was.Json;
import com.example.was.aws.S3ObjectStore;
import com.example.was.session.Cookies;
import com.example.was.session.FileSessionStore;
import com.example.was.session.Session;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/**
 * POST /upload?filename=foo.png — 사용자 업로드 파일 원본을 S3 에 올린다.
 *
 * <p>업로드 경로는 이전 과제에서 이미 S3 로 외부화했다. 로컬 디스크에는
 * 임시 파일조차 만들지 않고 요청 본문을 그대로 S3 로 흘려보낸다.
 */
public final class UploadHandler implements Handler {

    @Override
    public void handle(HttpRequest request, HttpResponse response) throws IOException {
        Session session = FileSessionStore.load(Cookies.sessionId(request));
        if (session == null) {
            throw new HttpException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
        }
        if (request.body().length == 0) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "업로드할 본문이 비어 있습니다");
        }

        String filename = request.param("filename", "upload.bin");
        String datePath = new SimpleDateFormat("yyyy/MM", Locale.KOREA).format(new Date());
        String key = "uploads/" + datePath + "/"
                + UUID.randomUUID().toString().replace("-", "") + "-" + filename;

        String contentType = request.header("content-type");
        String uri = S3ObjectStore.put(key, request.body(),
                contentType != null ? contentType : "application/octet-stream");

        response.status(HttpStatus.CREATED).json(
                "{\"location\":\"" + Json.escape(uri) + "\",\"bytes\":" + request.body().length + "}");
    }
}
