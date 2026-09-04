package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpException;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;
import com.example.was.HttpStatus;
import com.example.was.Json;
import com.example.was.storage.SharedAssetStore;

import java.io.IOException;
import java.util.List;

/**
 * 공유 정적 산출물 조회/갱신.
 *
 * <ul>
 *   <li>{@code GET /assets} — 공유 디렉토리의 산출물 목록 (배치 노드가 쓴 것 포함)</li>
 *   <li>{@code GET /assets?name=catalog.json} — 산출물 내용</li>
 *   <li>{@code POST /assets?name=catalog.json} — 관리자 요청으로 즉시 재생성</li>
 * </ul>
 *
 * <p>대상 디렉토리는 전 인스턴스가 같은 경로로 마운트한 NFS 볼륨이다.
 */
public final class AssetHandler implements Handler {

    @Override
    public void handle(HttpRequest request, HttpResponse response) throws IOException {
        String name = request.param("name");

        if ("POST".equals(request.method())) {
            if (name == null) {
                throw new HttpException(HttpStatus.BAD_REQUEST, "name 파라미터가 필요합니다");
            }
            SharedAssetStore.write(name, request.body());
            response.status(HttpStatus.CREATED).json(
                    "{\"name\":\"" + Json.escape(name) + "\",\"bytes\":" + request.body().length + "}");
            return;
        }

        if (name == null) {
            List<String> names = SharedAssetStore.list();
            StringBuilder json = new StringBuilder("{\"assets\":[");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append('"').append(Json.escape(names.get(i))).append('"');
            }
            json.append("]}");
            response.json(json.toString());
            return;
        }

        byte[] content = SharedAssetStore.read(name);
        if (content == null) {
            throw new HttpException(HttpStatus.NOT_FOUND, "공유 산출물이 없습니다: " + name);
        }
        response.contentType("application/octet-stream").body(content);
    }
}
