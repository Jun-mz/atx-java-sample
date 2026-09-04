package com.example.was;

/**
 * 라우팅된 요청 하나를 처리하는 단위. 서블릿의 아주 축약된 형태라고 보면 된다.
 */
public interface Handler {

    void handle(HttpRequest request, HttpResponse response) throws Exception;
}
