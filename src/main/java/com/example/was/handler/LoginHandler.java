package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpException;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;
import com.example.was.HttpStatus;
import com.example.was.Json;
import com.example.was.session.Cookies;
import com.example.was.session.FileSessionStore;
import com.example.was.session.Session;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * POST /login — 폼 본문의 userId 로 세션을 만들고 세션 쿠키를 내려준다.
 *
 * <p>세션 실체는 이 인스턴스의 로컬 디스크에 파일로 저장된다.
 */
public final class LoginHandler implements Handler {

    @Override
    public void handle(HttpRequest request, HttpResponse response) {
        String userId = formValue(request.bodyAsString(), "userId");
        if (userId == null || userId.isEmpty()) {
            throw new HttpException(HttpStatus.BAD_REQUEST, "userId 가 필요합니다");
        }

        Session session = FileSessionStore.create(userId);

        response.header("Set-Cookie",
                Cookies.SESSION_COOKIE + "=" + session.id() + "; Path=/; HttpOnly");
        response.json("{\"sessionId\":\"" + session.id()
                + "\",\"userId\":\"" + Json.escape(userId) + "\"}");
    }

    private static String formValue(String body, String name) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            if (decode(pair.substring(0, eq)).equals(name)) {
                return decode(pair.substring(eq + 1));
            }
        }
        return null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 must be supported", e);
        }
    }
}
