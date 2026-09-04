package com.example.was.storage;

import com.example.was.config.AppConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 사용자가 요청한 주문 리포트를 인스턴스 로컬 디스크에 생성해 두고 다시 내려준다.
 *
 * <p>요청을 받은 인스턴스가 CSV 를 만들어 {@code /var/was/reports} 아래에 쓰고,
 * 같은 사용자가 잠시 뒤 다운로드를 요청하면 그 파일을 읽어 응답한다.
 * ALB 스티키 세션 덕분에 같은 사용자는 같은 인스턴스로 다시 들어오므로
 * 이 디렉토리를 인스턴스 간에 공유하지는 않는다.
 *
 * <p>한 파일을 쓰는 주체는 그 파일을 만든 인스턴스 하나뿐이고, 다른 인스턴스가
 * 같은 파일을 읽거나 쓰는 경우는 없다. 생성 후 24시간이 지나면 배치가 지운다.
 */
public final class ReportStore {

    private static final Logger LOG = Logger.getLogger(ReportStore.class.getName());
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** 리포트 CSV 가 쌓이는 인스턴스 로컬 디렉토리. */
    private static final File REPORT_DIR = LocalPaths.ensureDirectory(AppConfig.REPORT_DIR);

    private static final long RETENTION_MS = 24 * 60 * 60 * 1000L;

    private ReportStore() {
    }

    /**
     * 주문 리포트를 생성해 로컬 디스크에 쓴다.
     *
     * @return 생성된 리포트 ID
     */
    public static String generate(String userId, String period) throws IOException {
        String reportId = UUID.randomUUID().toString().replace("-", "");
        File file = fileOf(reportId);

        OutputStream out = new FileOutputStream(file);
        try {
            StringBuilder csv = new StringBuilder();
            csv.append("orderId,orderedAt,userId,amount\n");
            // 실제로는 여기서 Aurora 를 조회해 행을 채운다. 샘플에서는 고정 데이터를 쓴다.
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(new Date());
            for (int i = 1; i <= 3; i++) {
                csv.append("ORD-").append(period).append('-').append(i)
                        .append(',').append(today)
                        .append(',').append(userId)
                        .append(',').append(i * 12000)
                        .append('\n');
            }
            out.write(csv.toString().getBytes(UTF_8));
        } finally {
            out.close();
        }

        LOG.info("리포트 생성: " + file.getPath());
        return reportId;
    }

    /** @return 이 인스턴스에 해당 리포트 파일이 없으면 null */
    public static File find(String reportId) {
        if (reportId == null || !reportId.matches("[0-9a-f]{32}")) {
            return null;
        }
        File file = fileOf(reportId);
        return file.isFile() ? file : null;
    }

    /** 보존 기간이 지난 리포트를 지운다. */
    public static int purgeExpired() {
        File[] files = REPORT_DIR.listFiles();
        if (files == null) {
            return 0;
        }
        long threshold = System.currentTimeMillis() - RETENTION_MS;
        int purged = 0;
        for (File file : files) {
            if (file.isFile() && file.lastModified() < threshold && file.delete()) {
                purged++;
            }
        }
        return purged;
    }

    private static File fileOf(String reportId) {
        return new File(REPORT_DIR, "report-" + reportId + ".csv");
    }
}
