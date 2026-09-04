package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;

import java.lang.management.ManagementFactory;

/**
 * GET /health — 로드밸런서/모니터링이 찔러볼 헬스 체크.
 */
public final class HealthHandler implements Handler {

    @Override
    public void handle(HttpRequest request, HttpResponse response) {
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        response.json("{\"status\":\"UP\""
                + ",\"uptimeMillis\":" + uptimeMillis
                + ",\"usedMemoryMb\":" + usedMb
                + ",\"javaVersion\":\"" + System.getProperty("java.version") + "\"}");
    }
}
