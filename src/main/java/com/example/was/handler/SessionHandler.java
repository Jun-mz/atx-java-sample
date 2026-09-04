package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;
import com.example.was.HttpStatus;
import com.example.was.Json;
import com.example.was.aws.InstanceMetadataClient;
import com.example.was.session.Cookies;
import com.example.was.session.FileSessionStore;
import com.example.was.session.Session;

/**
 * GET /me — 세션 쿠키로 현재 로그인 사용자를 조회한다.
 *
 * <p>세션 파일이 이 인스턴스에만 있으므로, 다른 인스턴스로 요청이 넘어가면
 * 같은 쿠키를 들고 있어도 세션을 찾지 못한다.
 */
public final class SessionHandler implements Handler {

    @Override
    public void handle(HttpRequest request, HttpResponse response) {
        String sessionId = Cookies.sessionId(request);
        Session session = FileSessionStore.load(sessionId);

        if (session == null) {
            response.status(HttpStatus.UNAUTHORIZED).json(
                    "{\"error\":\"no_session\""
                            + ",\"message\":\"이 인스턴스에 세션이 없습니다\""
                            + ",\"servedBy\":\"" + InstanceMetadataClient.instanceId() + "\"}");
            return;
        }

        response.json("{\"userId\":\"" + Json.escape(session.get("userId"))
                + "\",\"sessionId\":\"" + session.id()
                + "\",\"createdAt\":" + session.createdAt()
                + ",\"servedBy\":\"" + InstanceMetadataClient.instanceId() + "\"}");
    }
}
