package com.example.was;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 소켓 하나를 맡아 keep-alive 가 유지되는 동안 요청을 반복 처리한다.
 * 스레드 풀의 작업 단위.
 */
final class ConnectionWorker implements Runnable {

    private static final Logger LOG = Logger.getLogger(ConnectionWorker.class.getName());

    /** 하나의 커넥션에서 처리할 최대 요청 수. */
    private static final int MAX_REQUESTS_PER_CONNECTION = 100;

    private final Socket socket;
    private final Router router;
    private final int keepAliveTimeoutMillis;

    ConnectionWorker(Socket socket, Router router, int keepAliveTimeoutMillis) {
        this.socket = socket;
        this.router = router;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
    }

    @Override
    public void run() {
        String peer = socket.getRemoteSocketAddress().toString();
        try {
            socket.setSoTimeout(keepAliveTimeoutMillis);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());

            for (int i = 0; i < MAX_REQUESTS_PER_CONNECTION; i++) {
                if (!handleOne(in, out, peer)) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            // idle keep-alive 커넥션 정리. 정상 동작이라 로그를 남기지 않는다.
        } catch (IOException e) {
            LOG.log(Level.FINE, "I/O error on " + peer, e);
        } finally {
            closeQuietly();
        }
    }

    /** @return keep-alive 로 다음 요청을 계속 받을지 여부 */
    private boolean handleOne(InputStream in, OutputStream out, String peer) throws IOException {
        HttpRequest request;
        try {
            request = HttpRequest.parse(in);
        } catch (SocketTimeoutException e) {
            return false;
        } catch (HttpException e) {
            LOG.log(Level.FINE, "Bad request from " + peer + ": " + e.getMessage());
            writeError(out, e.status(), e.getMessage(), false);
            return false; // 스트림 상태를 신뢰할 수 없으므로 연결을 닫는다
        }

        if (request == null) {
            return false; // 클라이언트가 연결을 닫았다
        }

        long startedAt = System.currentTimeMillis();
        HttpResponse response = new HttpResponse();
        boolean keepAlive = request.isKeepAlive();

        try {
            Handler handler = router.route(request);
            handler.handle(request, response);
        } catch (HttpException e) {
            response = errorResponse(e.status(), e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Handler failed for " + request, e);
            response = errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
            keepAlive = false;
        }

        response.header("Connection", keepAlive ? "keep-alive" : "close");
        response.header("Server", "SimpleWAS/1.0");
        response.writeTo(out, !"HEAD".equals(request.method()));

        LOG.info(String.format("%s %s -> %d (%d bytes, %d ms)",
                request.method(), request.path(), response.status().code(),
                response.bodyLength(), System.currentTimeMillis() - startedAt));

        return keepAlive;
    }

    private void writeError(OutputStream out, HttpStatus status, String message, boolean keepAlive)
            throws IOException {
        HttpResponse response = errorResponse(status, message);
        response.header("Connection", keepAlive ? "keep-alive" : "close");
        response.writeTo(out);
    }

    private static HttpResponse errorResponse(HttpStatus status, String message) {
        String json = "{\"status\":" + status.code()
                + ",\"error\":\"" + Json.escape(status.reasonPhrase())
                + "\",\"message\":\"" + Json.escape(message == null ? "" : message) + "\"}";
        return new HttpResponse().status(status).json(json);
    }

    private void closeQuietly() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 닫는 중 실패는 알릴 대상이 없다
        }
    }
}
