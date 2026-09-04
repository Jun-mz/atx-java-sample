package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;
import com.example.was.Json;

/**
 * GET /hello?name=... — 쿼리 파라미터를 읽어 JSON 으로 인사한다.
 */
public final class HelloHandler implements Handler {

    @Override
    public void handle(HttpRequest request, HttpResponse response) {
        String name = request.param("name", "world");
        response.json("{\"message\":\"Hello, " + Json.escape(name) + "!\"}");
    }
}
