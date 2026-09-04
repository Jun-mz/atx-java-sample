package com.example.was.storage;

import com.example.was.config.AppConfig;

import java.io.File;
import java.util.logging.Logger;

/**
 * 설정에 박혀 있는 절대 경로를 실제 디렉토리로 만들어 준다.
 *
 * <p>운영 인스턴스에서는 절대 경로가 그대로 쓰인다. 개발 PC 에서는 해당 경로에
 * 쓰기 권한이 없어 디렉토리 생성이 실패하므로, 프로젝트 하위로 우회한다.
 */
public final class LocalPaths {

    private static final Logger LOG = Logger.getLogger(LocalPaths.class.getName());

    private LocalPaths() {
    }

    public static File ensureDirectory(String absolutePath) {
        File directory = new File(absolutePath);
        if (directory.isDirectory() || directory.mkdirs()) {
            return directory;
        }

        File fallback = new File(AppConfig.LOCAL_FALLBACK_ROOT, directory.getName());
        if (!fallback.isDirectory() && !fallback.mkdirs()) {
            throw new IllegalStateException("디렉토리를 만들 수 없습니다: " + absolutePath);
        }
        LOG.warning(absolutePath + " 생성 실패 — 개발 모드로 " + fallback.getPath() + " 를 사용합니다");
        return fallback;
    }
}
