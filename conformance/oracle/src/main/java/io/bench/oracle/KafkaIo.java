package io.bench.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Thin Kafka access for the oracle: publish inputs, drain the output topic. */
public final class KafkaIo implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String bootstrap;
    private final KafkaProducer<String, String> producer;

    public KafkaIo(String bootstrap) {
        this.bootstrap = bootstrap;
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(p);
    }

    /** Publish a JSON value (serialized compactly) to topic under key. */
    public void publishJson(String topic, String key, JsonNode value) {
        publishRaw(topic, key, value.toString());
    }

    /** Publish a raw string value (used for poison/non-JSON messages). */
    public void publishRaw(String topic, String key, String value) {
        try {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new RuntimeException("publish to " + topic + " failed", e);
        }
    }

    /**
     * Drain {@code topic} from the beginning, returning key -> latest value as a
     * parsed JsonNode (later offsets overwrite earlier ones for the same key).
     * Polls until {@code quietMillis} pass with no new records.
     */
    public Map<String, JsonNode> drain(String topic, long quietMillis) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "oracle-" + UUID.randomUUID());
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        p.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "15000");
        p.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "15000");

        Map<String, JsonNode> latest = new LinkedHashMap<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(p)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + 60_000;
            long lastSawRecord = System.currentTimeMillis();
            while (System.currentTimeMillis() - lastSawRecord < quietMillis
                    && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300));
                if (!records.isEmpty()) {
                    lastSawRecord = System.currentTimeMillis();
                    for (ConsumerRecord<String, String> r : records) {
                        latest.put(r.key(), parse(r.value()));
                    }
                }
            }
        }
        return latest;
    }

    private static JsonNode parse(String value) {
        try {
            return MAPPER.readTree(value);
        } catch (Exception e) {
            // A non-JSON record on the output topic is itself a failure signal;
            // represent it as a textual node so matching reports a mismatch.
            return MAPPER.getNodeFactory().textNode(value);
        }
    }

    @Override
    public void close() {
        producer.flush();
        producer.close();
    }

}
