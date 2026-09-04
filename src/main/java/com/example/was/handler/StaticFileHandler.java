package com.example.was.handler;

import com.example.was.Handler;
import com.example.was.HttpException;
import com.example.was.HttpRequest;
import com.example.was.HttpResponse;
import com.example.was.HttpStatus;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 문서 루트 아래의 정적 파일을 내려준다. 라우터의 fallback 으로 등록해서 쓴다.
 */
public final class StaticFileHandler implements Handler {

    private static final Map<String, String> CONTENT_TYPES = new HashMap<String, String>();

    static {
        CONTENT_TYPES.put("html", "text/html; charset=UTF-8");
        CONTENT_TYPES.put("htm", "text/html; charset=UTF-8");
        CONTENT_TYPES.put("css", "text/css; charset=UTF-8");
        CONTENT_TYPES.put("js", "application/javascript; charset=UTF-8");
        CONTENT_TYPES.put("json", "application/json; charset=UTF-8");
        CONTENT_TYPES.put("txt", "text/plain; charset=UTF-8");
        CONTENT_TYPES.put("svg", "image/svg+xml");
        CONTENT_TYPES.put("png", "image/png");
        CONTENT_TYPES.put("jpg", "image/jpeg");
        CONTENT_TYPES.put("jpeg", "image/jpeg");
        CONTENT_TYPES.put("gif", "image/gif");
        CONTENT_TYPES.put("ico", "image/x-icon");
    }

    private final File documentRoot;
    private final String welcomeFile;

    public StaticFileHandler(File documentRoot) {
        this(documentRoot, "index.html");
    }

    public StaticFileHandler(File documentRoot, String welcomeFile) {
        this.documentRoot = documentRoot;
        this.welcomeFile = welcomeFile;
    }

    @Override
    public void handle(HttpRequest request, HttpResponse response) throws IOException {
        if (!"GET".equals(request.method()) && !"HEAD".equals(request.method())) {
            throw new HttpException(HttpStatus.METHOD_NOT_ALLOWED,
                    request.method() + " not allowed for static resources");
        }

        String path = request.path();
        if (path.endsWith("/")) {
            path = path + welcomeFile;
        }

        File file = resolve(path);
        if (!file.isFile() || !file.canRead()) {
            throw new HttpException(HttpStatus.NOT_FOUND, "No such resource: " + request.path());
        }

        response.status(HttpStatus.OK)
                .contentType(contentTypeOf(file.getName()))
                .header("Last-Modified", String.valueOf(file.lastModified()))
                .body(readAll(file));
    }

    /**
     * 문서 루트 밖으로 나가는 경로(../ 등)를 차단한다.
     * canonical path 로 정규화한 뒤 루트 하위인지 확인하는 게 핵심.
     */
    private File resolve(String path) throws IOException {
        File candidate = new File(documentRoot, path).getCanonicalFile();
        String rootPath = documentRoot.getCanonicalPath();
        String candidatePath = candidate.getPath();
        if (!candidatePath.equals(rootPath) && !candidatePath.startsWith(rootPath + File.separator)) {
            throw new HttpException(HttpStatus.NOT_FOUND, "Path escapes document root: " + path);
        }
        return candidate;
    }

    private static String contentTypeOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "application/octet-stream";
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String contentType = CONTENT_TYPES.get(extension);
        return contentType != null ? contentType : "application/octet-stream";
    }

    private static byte[] readAll(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(file.length(), 32L));
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
