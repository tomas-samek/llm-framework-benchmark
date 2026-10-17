package eu.bench.notify;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import static eu.bench.notify.Messages.*;

@Component
@Profile("!sample")
public class InputConsumer {
    private static final Logger log = LoggerFactory.getLogger(InputConsumer.class);
    private final ObjectMapper json;
    private final ReferenceStore store;
    private final KafkaTemplate<String, String> kafka;

    public InputConsumer(ObjectMapper json, ReferenceStore store, KafkaTemplate<String, String> kafka) {
        this.json = json;
        this.store = store;
        this.kafka = kafka;
    }

    @KafkaListener(topics = {"product-updates", "user-updates", "price-updates", "purchases"})
    public void receive(ConsumerRecord<String, String> record) throws Exception {
        Object message;
        try {
            message = switch (record.topic()) {
                case "product-updates" -> json.readValue(record.value(), ProductUpdate.class);
                case "user-updates" -> json.readValue(record.value(), UserUpdate.class);
                case "price-updates" -> json.readValue(record.value(), PriceUpdate.class);
                case "purchases" -> json.readValue(record.value(), Purchase.class);
                default -> throw new IllegalArgumentException("Unknown topic");
            };
            if (message == null) throw new IllegalArgumentException("Null message");
        } catch (RuntimeException e) {
            log.warn("Skipping invalid JSON at {}-{} offset {}", record.topic(), record.partition(), record.offset(), e);
            return;
        }
        switch (message) {
            case ProductUpdate p -> store.upsert(p);
            case UserUpdate u -> store.upsert(u);
            case PriceUpdate p -> store.upsert(p);
            case Purchase p -> kafka.send("notifications", p.purchaseId(),
                    json.writeValueAsString(store.merge(p))).get();
            default -> throw new IllegalArgumentException("Unknown message type");
        }
    }
}
