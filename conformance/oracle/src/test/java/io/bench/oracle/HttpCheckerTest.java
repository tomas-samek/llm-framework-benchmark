package io.bench.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.bench.oracle.model.HttpCheck;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpCheckerTest {

    private final ObjectMapper m = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private HttpServer server;
    private HttpChecker checker;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        // /later answers "old" twice, then "new": a check must poll until the state holds.
        server.createContext("/later", ex -> reply(ex, 200, calls.incrementAndGet() < 3 ? "{\"v\":\"old\"}" : "{\"v\":\"new\"}"));
        server.createContext("/bad", ex -> reply(ex, 400, "{\"error\":\"q is required\"}"));
        server.createContext("/blank", ex -> reply(ex, 400, "{\"error\":\" \"}"));
        server.start();
        checker = new HttpChecker("http://localhost:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    @Test
    void pollsUntilTheBodyMatches() throws Exception {
        Result r = checker.check(new HttpCheck("S", "k", "/later", 200, m.readTree("{\"v\":\"new\"}"), null, 5000L));
        assertThat(r.passed()).isTrue();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void failsWithWhatItSawAfterTheWait() throws Exception {
        Result r = checker.check(new HttpCheck("S", "k", "/later", 200, m.readTree("{\"v\":\"never\"}"), null, 1200L));
        assertThat(r.passed()).isFalse();
        assertThat(r.detail()).contains("\"v\":\"new\"");
    }

    @Test
    void wrongStatusFails() throws Exception {
        Result r = checker.check(new HttpCheck("S", "k", "/bad", 200, null, null, 600L));
        assertThat(r.passed()).isFalse();
        assertThat(r.detail()).startsWith("expected status 200");
    }

    @Test
    void errorFieldMustBeANonBlankString() throws Exception {
        assertThat(checker.check(new HttpCheck("S", "k", "/bad", 400, null, "error", 600L)).passed()).isTrue();
        assertThat(checker.check(new HttpCheck("S", "k", "/blank", 400, null, "error", 600L)).passed()).isFalse();
    }

    @Test
    void noServerIsAFailureNotAnException() throws Exception {
        HttpChecker nobody = new HttpChecker("http://localhost:1");
        Result r = nobody.check(new HttpCheck("S", "k", "/x", 200, null, null, 600L));
        assertThat(r.passed()).isFalse();
        assertThat(r.detail()).startsWith("no HTTP response");
    }
}
