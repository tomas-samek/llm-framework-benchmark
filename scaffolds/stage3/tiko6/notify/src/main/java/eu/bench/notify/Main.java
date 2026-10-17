package eu.bench.notify;
import io.tiko.config.ConfigSources;
import io.tiko.runtime.Tiko;
public final class Main {
    public static void main(String[] args) {
        Tiko.daemon(ConfigSources.classpath("application.yml")).awaitShutdown();
    }
}
