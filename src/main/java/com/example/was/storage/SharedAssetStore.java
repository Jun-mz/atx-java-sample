package com.example.was.storage;

import com.example.was.config.AppConfig;
import com.example.was.aws.InstanceMetadataClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * 전 인스턴스가 함께 쓰는 정적 산출물 저장소.
 *
 * <p>{@code /mnt/nas/was-shared/assets} 는 모든 WAS 인스턴스에 동일하게 NFS 로 마운트되어
 * 있다. 야간 배치 노드가 상품 카탈로그·약관 PDF·프로모션 배너 같은 산출물을 여기에 써 넣고,
 * 웹 노드들은 그것을 그대로 읽어 사용자에게 내려준다.
 *
 * <p>웹 노드도 관리자 요청으로 카탈로그를 즉시 재생성할 때 같은 디렉토리에 쓴다.
 * 즉 여러 호스트가 같은 디렉토리에 동시에 쓰기 때문에, 재생성 중에는 다른 노드가
 * 반쯤 쓰인 파일을 읽지 않도록 {@code .lock} 파일로 상호 배제를 건다.
 * 어느 노드가 무엇을 언제 썼는지는 공용 manifest 에 함께 기록한다.
 */
public final class SharedAssetStore {

    private static final Logger LOG = Logger.getLogger(SharedAssetStore.class.getName());
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** 모든 인스턴스에 같은 경로로 마운트된 NFS 공유 디렉토리. */
    private static final File SHARED_DIR = LocalPaths.ensureDirectory(AppConfig.SHARED_ASSET_DIR);

    /** 어느 노드가 어떤 파일을 갱신했는지 전 노드가 함께 갱신하는 목록. */
    private static final String MANIFEST_NAME = "manifest.txt";
    private static final String LOCK_NAME = ".catalog.lock";

    private SharedAssetStore() {
    }

    /** 공유 디렉토리에 있는 산출물 목록. 다른 노드와 배치가 써 넣은 것까지 모두 보인다. */
    public static List<String> list() {
        String[] names = SHARED_DIR.list();
        if (names == null) {
            return new ArrayList<String>();
        }
        List<String> result = new ArrayList<String>(Arrays.asList(names));
        result.remove(MANIFEST_NAME);
        result.remove(LOCK_NAME);
        return result;
    }

    /** @return 공유 디렉토리에 없는 이름이면 null */
    public static byte[] read(String name) throws IOException {
        if (!isSafeName(name)) {
            return null;
        }
        File file = new File(SHARED_DIR, name);
        if (!file.isFile()) {
            return null;
        }

        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) Math.max(file.length(), 32L));
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
            }
            return buffer.toByteArray();
        } finally {
            in.close();
        }
    }

    /**
     * 공유 산출물을 갱신한다. 배치 노드와 웹 노드가 같은 파일을 건드릴 수 있어
     * 파일 락을 잡은 뒤에 쓴다.
     */
    public static void write(String name, byte[] content) throws IOException {
        if (!isSafeName(name)) {
            throw new IOException("허용되지 않는 산출물 이름: " + name);
        }

        RandomAccessFile lockFile = new RandomAccessFile(new File(SHARED_DIR, LOCK_NAME), "rw");
        FileLock lock = null;
        try {
            // 다른 인스턴스가 같은 디렉토리에 쓰는 중이면 여기서 대기한다.
            lock = lockFile.getChannel().lock();

            File target = new File(SHARED_DIR, name);
            OutputStream out = new FileOutputStream(target);
            try {
                out.write(content);
            } finally {
                out.close();
            }
            appendManifest(name, content.length);
            LOG.info("공유 산출물 갱신: " + target.getPath());
        } finally {
            if (lock != null) {
                lock.release();
            }
            lockFile.close();
        }
    }

    /** manifest 는 모든 노드가 이어서 덧붙인다. */
    private static void appendManifest(String name, int size) throws IOException {
        String line = System.currentTimeMillis()
                + "\t" + InstanceMetadataClient.instanceId()
                + "\t" + name
                + "\t" + size
                + System.getProperty("line.separator");

        OutputStream out = new FileOutputStream(new File(SHARED_DIR, MANIFEST_NAME), true);
        try {
            out.write(line.getBytes(UTF_8));
        } finally {
            out.close();
        }
    }

    private static boolean isSafeName(String name) {
        return name != null && name.matches("[A-Za-z0-9._-]{1,120}") && !name.contains("..");
    }
}
