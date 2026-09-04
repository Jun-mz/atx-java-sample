package com.example.was.session;

import com.example.was.config.AppConfig;
import com.example.was.storage.LocalPaths;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 로그인 세션을 인스턴스 로컬 디스크에 파일로 보관한다.
 *
 * <p>세션 하나가 {@code /var/was/sessions/<sessionId>.ser} 파일 하나에 대응한다.
 * ALB 의 스티키 세션(쿠키 기반)으로 같은 사용자가 같은 인스턴스로 다시 오도록 묶어 두었기
 * 때문에 인스턴스 간 세션 공유는 하지 않는다.
 *
 * <p>이 방식 때문에 배포나 스케일인으로 인스턴스가 교체되면 그 인스턴스에 붙어 있던
 * 사용자는 모두 로그아웃된다. 무중단 배포가 어려운 주된 이유다.
 */
public final class FileSessionStore {

    private static final Logger LOG = Logger.getLogger(FileSessionStore.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 세션 파일이 쌓이는 인스턴스 로컬 디렉토리. */
    private static final File SESSION_DIR = LocalPaths.ensureDirectory(AppConfig.SESSION_DIR);

    /** 30분간 접근이 없으면 만료로 본다. */
    private static final long SESSION_TTL_MS = 30 * 60 * 1000L;

    private FileSessionStore() {
    }

    public static Session create(String userId) {
        String id = UUID.nameUUIDFromBytes(newRandomBytes()).toString().replace("-", "");
        Session session = new Session(id);
        session.put("userId", userId);
        save(session);
        return session;
    }

    /** @return 세션 파일이 없거나 만료되었으면 null */
    public static Session load(String sessionId) {
        if (sessionId == null || !isSafeId(sessionId)) {
            return null;
        }
        File file = fileOf(sessionId);
        if (!file.isFile()) {
            // 다른 인스턴스에서 만들어진 세션이면 이 노드에는 파일이 없다.
            return null;
        }

        try {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
            try {
                Session session = (Session) in.readObject();
                if (System.currentTimeMillis() - session.lastAccessedAt() > SESSION_TTL_MS) {
                    invalidate(sessionId);
                    return null;
                }
                session.touch();
                save(session);
                return session;
            } finally {
                in.close();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "세션 파일을 읽지 못했습니다: " + file.getPath(), e);
            return null;
        } catch (ClassNotFoundException e) {
            LOG.log(Level.WARNING, "세션 역직렬화 실패: " + file.getPath(), e);
            return null;
        }
    }

    public static void save(Session session) {
        File file = fileOf(session.id());
        try {
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));
            try {
                out.writeObject(session);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "세션 파일 저장 실패: " + file.getPath(), e);
        }
    }

    public static void invalidate(String sessionId) {
        if (sessionId == null || !isSafeId(sessionId)) {
            return;
        }
        File file = fileOf(sessionId);
        if (file.isFile() && !file.delete()) {
            LOG.warning("세션 파일 삭제 실패: " + file.getPath());
        }
    }

    /** 만료된 세션 파일을 정리한다. 크론으로 주기 호출한다. */
    public static int purgeExpired() {
        File[] files = SESSION_DIR.listFiles();
        if (files == null) {
            return 0;
        }
        long threshold = System.currentTimeMillis() - SESSION_TTL_MS;
        int purged = 0;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < threshold && file.delete()) {
                purged++;
            }
        }
        return purged;
    }

    private static File fileOf(String sessionId) {
        return new File(SESSION_DIR, sessionId + ".ser");
    }

    private static boolean isSafeId(String sessionId) {
        return sessionId.matches("[0-9a-f]{32}");
    }

    private static byte[] newRandomBytes() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
