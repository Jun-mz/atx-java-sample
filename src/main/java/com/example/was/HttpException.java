package com.example.was;

/**
 * 핸들러나 파서가 특정 상태 코드로 응답을 끝내고 싶을 때 던지는 예외.
 * 처리되지 않은 다른 예외는 모두 500 으로 변환된다.
 */
public class HttpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    public HttpException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
