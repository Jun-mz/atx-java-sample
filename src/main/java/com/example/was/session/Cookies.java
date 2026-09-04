package com.example.was.session;

import com.example.was.HttpRequest;

/**
 * Cookie 헤더에서 값 하나를 꺼내는 최소한의 헬퍼.
 */
public final class Cookies {

    public static final String SESSION_COOKIE = "WASSESSIONID";

    private Cookies() {
    }

    public static String value(HttpRequest request, String name) {
        String header = request.header("cookie");
        if (header == null) {
            return null;
        }
        for (String pair : header.split(";")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            if (pair.substring(0, eq).trim().equals(name)) {
                return pair.substring(eq + 1).trim();
            }
        }
        return null;
    }

    public static String sessionId(HttpRequest request) {
        return value(request, SESSION_COOKIE);
    }
}
