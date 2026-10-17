package eu.bench.notify;

import io.tiko.kafka.serializer.JsonKafkaSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import java.time.Instant;
import java.util.Properties;
import eu.bench.notify.Messages.*;

/** Standalone demo producer; does not bootstrap the application's consumers. */
public final class SampleDataPublisher {
    public static void main(String[] args) throws Exception {
        var properties = new Properties();
        properties.put("bootstrap.servers", System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"));
        properties.put("acks", "all");
        var json = new JsonKafkaSerializer();
        try (var producer = new KafkaProducer<String, byte[]>(properties, new StringSerializer(), new ByteArraySerializer())) {
            producer.send(new ProducerRecord<>("product-updates", "p1",
                json.serialize(new ProductUpdate("p1", "Mechanical Keyboard", "electronics")))).get();
            producer.send(new ProducerRecord<>("user-updates", "u1",
                json.serialize(new UserUpdate("u1", "Alice", "alice@example.com")))).get();
            producer.send(new ProducerRecord<>("price-updates", "p1",
                json.serialize(new PriceUpdate("p1", 7999, "USD")))).get();
            // Cross-topic ordering is not guaranteed; allow a demo consumer time to process references.
            Thread.sleep(2000);
            producer.send(new ProducerRecord<>("purchases", "buy1",
                json.serialize(new Purchase("buy1", "u1", "p1", 2, Instant.now().toString())))).get();
        }
    }
}
