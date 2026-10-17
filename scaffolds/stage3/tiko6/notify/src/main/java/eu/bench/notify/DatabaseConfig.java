package eu.bench.notify;
import io.tiko.annotations.Configuration;
@Configuration(prefix = "database")
public record DatabaseConfig(String url, String user, String password, int poolSize) {}
