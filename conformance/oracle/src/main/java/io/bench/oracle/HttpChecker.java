package io.bench.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.bench.oracle.model.HttpCheck;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Evaluates {@link HttpCheck}s against the contestant's HTTP server, polling until they hold. */
public final class HttpChecker {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DEFAULT_WAIT_MILLIS = 10_000;
    private static final long POLL_MILLIS = 500;

    private final String baseUrl;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public HttpChecker(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public Result check(HttpCheck c) throws InterruptedException {
        long deadline = System.currentTimeMillis() + (c.waitMs() != null ? c.waitMs() : DEFAULT_WAIT_MILLIS);
        String last;
        do {
            last = attempt(c);
            if (last == null) {
                return new Result(c.id(), c.key(), true, "ok");
            }
            Thread.sleep(POLL_MILLIS);
        } while (System.currentTimeMillis() < deadline);
        return new Result(c.id(), c.key(), false, last);
    }

    /** One request; null if the check holds, otherwise what was seen. */
    private String attempt(HttpCheck c) throws InterruptedException {
        HttpResponse<String> resp;
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + c.path()))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return "no HTTP response (" + e.getClass().getSimpleName() + ")";
        }
        String seen = "status " + resp.statusCode() + " body " + resp.body();
        if (resp.statusCode() != c.status()) {
            return "expected status " + c.status() + " but got " + seen;
        }
        JsonNode actual;
        try {
            actual = MAPPER.readTree(resp.body());
        } catch (IOException e) {
            return "body is not JSON: " + seen;
        }
        if (c.errorField() != null) {
            JsonNode f = actual == null ? null : actual.get(c.errorField());
            return f != null && f.isTextual() && !f.asText().isBlank()
                    ? null : "expected a non-blank '" + c.errorField() + "' field but got " + seen;
        }
        if (c.body() != null && !JsonMatch.matches(c.body(), actual)) {
            return "expected " + c.body() + " but got " + seen;
        }
        return null;
    }
}
