package com.example.was;

import com.example.was.handler.AdminConfigHandler;
import com.example.was.handler.AssetHandler;
import com.example.was.handler.EchoHandler;
import com.example.was.handler.HealthHandler;
import com.example.was.handler.HelloHandler;
import com.example.was.handler.InstanceInfoHandler;
import com.example.was.handler.LoginHandler;
import com.example.was.handler.ReportHandler;
import com.example.was.handler.SessionHandler;
import com.example.was.handler.StaticFileHandler;
import com.example.was.handler.UploadHandler;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 스레드 풀 기반의 아주 단순한 WAS.
 *
 * <p>커넥션마다 풀에서 스레드 하나를 꺼내 쓰는 고전적인 blocking I/O 모델이다
 * (Tomcat 의 BIO 커넥터와 같은 구조). JDK 1.8 표준 API 만 사용한다.
 *
 * <pre>
 *   java -cp build/classes com.example.was.WasServer 8080
 * </pre>
 */
public final class WasServer {

    private static final Logger LOG = Logger.getLogger(WasServer.class.getName());

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_THREADS = 50;
    private static final int DEFAULT_BACKLOG = 100;
    private static final int DEFAULT_KEEP_ALIVE_TIMEOUT_MS = 15_000;

    private final int port;
    private final int backlog;
    private final int keepAliveTimeoutMillis;
    private final Router router;
    private final ExecutorService workers;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ServerSocket serverSocket;

    public WasServer(int port, int threads, Router router) {
        this(port, threads, DEFAULT_BACKLOG, DEFAULT_KEEP_ALIVE_TIMEOUT_MS, router);
    }

    public WasServer(int port, int threads, int backlog, int keepAliveTimeoutMillis, Router router) {
        this.port = port;
        this.backlog = backlog;
        this.keepAliveTimeoutMillis = keepAliveTimeoutMillis;
        this.router = router;
        this.workers = Executors.newFixedThreadPool(threads, new NamedThreadFactory("was-worker-"));
    }

    /** accept 루프를 돌린다. {@link #stop()} 전까지 블로킹된다. */
    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Server is already running");
        }

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(port), backlog);

        LOG.info("SimpleWAS started on http://localhost:" + port);

        while (running.get()) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (running.get()) {
                    LOG.log(Level.WARNING, "accept() failed", e);
                    continue;
                }
                break; // stop() 이 소켓을 닫은 정상 종료 경로
            }

            try {
                workers.execute(new ConnectionWorker(socket, router, keepAliveTimeoutMillis));
            } catch (RejectedExecutionException e) {
                // 풀이 가득 찼거나 종료 중 — 커넥션을 흘려보내지 말고 바로 닫는다.
                LOG.log(Level.WARNING, "Connection rejected, closing socket");
                closeQuietly(socket);
            }
        }
    }

    /** accept 루프를 멈추고 진행 중인 요청을 최대 10초까지 기다린다. */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        LOG.info("Shutting down SimpleWAS...");

        ServerSocket socket = serverSocket;
        if (socket != null) {
            closeQuietly(socket);
        }

        workers.shutdown();
        try {
            if (!workers.awaitTermination(10, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("SimpleWAS stopped");
    }

    public static void main(String[] args) throws IOException {
        int port = intOf(args.length > 0 ? args[0] : System.getProperty("was.port"), DEFAULT_PORT);
        int threads = intOf(System.getProperty("was.threads"), DEFAULT_THREADS);
        File documentRoot = resolveDocumentRoot(System.getProperty("was.docroot"));

        ReportHandler reportHandler = new ReportHandler();
        AssetHandler assetHandler = new AssetHandler();

        Router router = new Router()
                .get("/hello", new HelloHandler())
                .get("/health", new HealthHandler())
                .post("/echo", new EchoHandler())
                // 로그인 세션 (인스턴스 로컬 파일)
                .post("/login", new LoginHandler())
                .get("/me", new SessionHandler())
                // 주문 리포트 (인스턴스 로컬 디스크에 생성 후 같은 노드에서 다운로드)
                .post("/reports", reportHandler)
                .get("/reports", reportHandler)
                // 공유 정적 산출물 (전 노드가 마운트한 NFS)
                .get("/assets", assetHandler)
                .post("/assets", assetHandler)
                // 사용자 업로드 원본 (S3 로 외부화 완료)
                .post("/upload", new UploadHandler())
                // 운영 확인용
                .get("/instance", new InstanceInfoHandler())
                .get("/admin/config", new AdminConfigHandler())
                .fallback(new StaticFileHandler(documentRoot));

        LOG.info("Document root: " + documentRoot.getAbsolutePath());

        final WasServer server = new WasServer(port, threads, router);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                server.stop();
            }
        }, "was-shutdown"));

        server.start();
    }

    private static File resolveDocumentRoot(String configured) {
        if (configured != null && !configured.trim().isEmpty()) {
            return new File(configured);
        }
        // 빌드 산출물에서 실행하든 소스 트리에서 실행하든 동작하도록 두 곳을 본다.
        File packaged = new File("build/webapp");
        if (packaged.isDirectory()) {
            return packaged;
        }
        return new File("src/main/resources/webapp");
    }

    private static int intOf(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOG.warning("Not a number: '" + value + "', using default " + defaultValue);
            return defaultValue;
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // 종료 경로라 더 할 수 있는 일이 없다
        }
    }

    /** 스레드 덤프에서 워커를 구분할 수 있게 이름을 붙인다. */
    private static final class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
