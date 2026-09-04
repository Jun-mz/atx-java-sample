package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;
import com.example.was.HttpStatus;
import com.example.was.Json;

/**
 * POST /echo — 받은 본문을 그대로 돌려준다. 요청 본문 파싱 확인용.
 */
public final class EchoHandler implements Handler {

    @Override
    public void handle(HttpRequest request, HttpResponse response) {
        String contentType = request.header("content-type");
        String json = "{\"contentType\":\"" + Json.escape(contentType == null ? "" : contentType)
                + "\",\"length\":" + request.body().length
                + ",\"body\":\"" + Json.escape(request.bodyAsString()) + "\"}";
        response.status(HttpStatus.OK).json(json);
    }
}
