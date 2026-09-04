package com.example.was;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * "메서드 + 경로 -> 핸들러" 매핑. 정확히 일치하는 경로만 찾고,
 * 못 찾으면 fallback 핸들러(기본값: 정적 파일)로 넘긴다.
 */
public final class Router {

    /** key: "GET /hello" */
    private final Map<String, Handler> routes = new LinkedHashMap<String, Handler>();
    private final Map<String, Set<String>> methodsByPath = new LinkedHashMap<String, Set<String>>();
    private Handler fallback;

    public Router get(String path, Handler handler) {
        return add("GET", path, handler);
    }

    public Router post(String path, Handler handler) {
        return add("POST", path, handler);
    }

    public Router put(String path, Handler handler) {
        return add("PUT", path, handler);
    }

    public Router delete(String path, Handler handler) {
        return add("DELETE", path, handler);
    }

    public Router add(String method, String path, Handler handler) {
        routes.put(key(method, path), handler);
        Set<String> methods = methodsByPath.get(path);
        if (methods == null) {
            methods = new LinkedHashSet<String>();
            methodsByPath.put(path, methods);
        }
        methods.add(method);
        return this;
    }

    /** 등록된 라우트에 걸리지 않은 요청을 받을 핸들러. */
    public Router fallback(Handler handler) {
        this.fallback = handler;
        return this;
    }

    /**
     * @throws HttpException 404 또는 405
     */
    public Handler route(HttpRequest request) {
        // HEAD 는 GET 핸들러를 그대로 쓰고, 본문만 나중에 버린다.
        String method = "HEAD".equals(request.method()) ? "GET" : request.method();
        String path = normalize(request.path());

        Handler handler = routes.get(key(method, path));
        if (handler != null) {
            return handler;
        }

        Set<String> allowed = methodsByPath.get(path);
        if (allowed != null && !allowed.isEmpty()) {
            throw new HttpException(HttpStatus.METHOD_NOT_ALLOWED,
                    request.method() + " not allowed for " + path);
        }

        if (fallback != null) {
            return fallback;
        }
        throw new HttpException(HttpStatus.NOT_FOUND, "No route for " + request.method() + " " + path);
    }

    /** 뒤에 붙은 슬래시는 무시한다 (/hello/ 와 /hello 를 같게 취급). */
    private static String normalize(String path) {
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static String key(String method, String path) {
        return method + " " + normalize(path);
    }
}
