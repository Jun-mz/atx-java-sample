package com.example.was.session;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 로그인 세션. 파일로 직렬화해 두기 위해 Serializable 이다.
 */
public final class Session implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final long createdAt;
    private long lastAccessedAt;
    private final Map<String, String> attributes = new LinkedHashMap<String, String>();

    public Session(String id) {
        this.id = id;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
    }

    public String id() {
        return id;
    }

    public long createdAt() {
        return createdAt;
    }

    public long lastAccessedAt() {
        return lastAccessedAt;
    }

    public void touch() {
        this.lastAccessedAt = System.currentTimeMillis();
    }

    public void put(String key, String value) {
        attributes.put(key, value);
    }

    public String get(String key) {
        return attributes.get(key);
    }

    public Map<String, String> attributes() {
        return attributes;
    }
}
