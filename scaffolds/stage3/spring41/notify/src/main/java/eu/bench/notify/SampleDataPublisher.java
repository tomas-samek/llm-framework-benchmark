package eu.bench.notify;

import java.time.Instant;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;
import static eu.bench.notify.Messages.*;

@Configuration
@Profile("sample")
public class SampleDataPublisher {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(NotifyApplication.class);
        app.setAdditionalProfiles("sample");
        try (var context = app.run(args)) { }
    }

    @Bean
    ApplicationRunner publishSamples(KafkaTemplate<String, String> kafka, ObjectMapper json) {
        return args -> {
            kafka.send("product-updates", "p1", json.writeValueAsString(
                    new ProductUpdate("p1", "Mechanical Keyboard", "electronics"))).get();
            kafka.send("user-updates", "u1", json.writeValueAsString(
                    new UserUpdate("u1", "Alice", "alice@example.com"))).get();
            kafka.send("price-updates", "p1", json.writeValueAsString(
                    new PriceUpdate("p1", 7999L, "USD"))).get();
            // Give the independently running consumer time to process reference updates for the demo.
            Thread.sleep(2000);
            kafka.send("purchases", "buy1", json.writeValueAsString(
                    new Purchase("buy1", "u1", "p1", 2, Instant.now().toString()))).get();
            kafka.flush();
        };
    }
}
