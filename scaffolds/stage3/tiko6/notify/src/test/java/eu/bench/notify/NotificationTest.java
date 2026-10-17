package eu.bench.notify;

import eu.bench.notify.Messages.*;
import io.tiko.config.ConfigSources;
import io.tiko.kafka.KafkaTransport;
import io.tiko.kafka.serializer.JsonKafkaSerializer;
import io.tiko.kafka.test.FakeKafkaBroker;
import io.tiko.kafka.test.FakeKafkaTransport;
import io.tiko.runtime.Tiko;
import io.tiko.runtime.TikoOptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {
    @Test
    void skipsPoisonAndConsumesAllFourSources() throws Exception {
        var broker = new FakeKafkaBroker();
        var json = new JsonKafkaSerializer();
        try (var container = Tiko.create(TikoOptions.builder()
                .configSource(ConfigSources.classpath("application.yml"))
                .replaceTransport(KafkaTransport.class, t -> FakeKafkaTransport.over(t, broker))
                .build())) {
            var products = new java.util.concurrent.LinkedBlockingQueue<ProductUpdate>();
            var users = new java.util.concurrent.LinkedBlockingQueue<UserUpdate>();
            var prices = new java.util.concurrent.LinkedBlockingQueue<PriceUpdate>();
            var notifications = new java.util.concurrent.LinkedBlockingQueue<Notification>();
            container.getEventBus().subscribe(ProductUpdate.class, products::add);
            container.getEventBus().subscribe(UserUpdate.class, users::add);
            container.getEventBus().subscribe(PriceUpdate.class, prices::add);
            container.getEventBus().subscribe(Notification.class, notifications::add);
            broker.produce("price-updates", "not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            broker.produce("product-updates", json.serialize(new ProductUpdate("poison-p", "Mechanical Keyboard", "electronics")));
            broker.produce("user-updates", json.serialize(new UserUpdate("poison-u", "Alice", "alice@example.com")));
            broker.produce("price-updates", json.serialize(new PriceUpdate("poison-p", 7999, "USD")));
            assertNotNull(products.poll(10, java.util.concurrent.TimeUnit.SECONDS));
            assertNotNull(users.poll(10, java.util.concurrent.TimeUnit.SECONDS));
            assertNotNull(prices.poll(10, java.util.concurrent.TimeUnit.SECONDS));
            broker.produce("purchases", json.serialize(new Purchase("buy5", "poison-u", "poison-p", 1, "2026-06-07T12:00:00Z")));
            var notification = notifications.poll(10, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(notification);
            assertEquals(7999L, notification.totalCents());
            assertEquals("Alice", notification.userName());
            assertEquals("Mechanical Keyboard", notification.productName());
            assertEquals(1, broker.produced("notifications").size());
            assertEquals("buy5", broker.produced("notifications").getFirst().key());
        }
    }

    @Test
    void mergesReferencesAndPublishesOnceWithKeyAndExplicitNulls() {
        var broker = new FakeKafkaBroker();
        var json = new JsonKafkaSerializer();
        // Replace the transport before bootstrap: this test never contacts a real broker.
        try (var container = Tiko.create(TikoOptions.builder()
                .configSource(ConfigSources.classpath("application.yml"))
                .replaceTransport(KafkaTransport.class, t -> FakeKafkaTransport.over(t, broker))
                .build())) {
            var bus = container.getEventBus();
            var ts = "2026-06-07T12:00:00.123Z";
            bus.publish(new Purchase("buy2", "u-x", "p-x", 3, ts));
            var unknown = notification(broker, json, 0);
            assertNull(unknown.userName());
            assertNull(unknown.productName());
            assertNull(unknown.priceCents());
            assertNull(unknown.currency());
            assertNull(unknown.totalCents());
            assertEquals(ts, unknown.occurredAt());
            assertTrue(new String(broker.produced("notifications").get(0).value(),
                java.nio.charset.StandardCharsets.UTF_8).contains("\"userName\":null"));

            bus.publish(new PriceUpdate("p2", 500, "EUR"));
            bus.publish(new Purchase("buy3", "u-x", "p2", 4, ts));
            var partial = notification(broker, json, 1);
            assertNull(partial.userName());
            assertNull(partial.productName());
            assertEquals(2000L, partial.totalCents());
            assertEquals("EUR", partial.currency());

            bus.publish(new ProductUpdate("p1", "Keyboard", "electronics"));
            bus.publish(new ProductUpdate("p1", "Mechanical Keyboard", "electronics"));
            bus.publish(new UserUpdate("u1", "Alice", "alice@example.com"));
            bus.publish(new PriceUpdate("p1", 7999, "USD"));
            bus.publish(new Purchase("buy1", "u1", "p1", 2, ts));
            assertEquals(new Notification("buy1", "u1", "Alice", "p1",
                "Mechanical Keyboard", 7999L, "USD", 2, 15998L, ts), notification(broker, json, 2));
            bus.publish(new PriceUpdate("p1", 8999, "USD"));
            bus.publish(new Purchase("buyB", "u1", "p1", 1, ts));
            assertEquals(8999L, notification(broker, json, 3).totalCents());
            assertEquals(4, broker.produced("notifications").size());
            assertEquals("buy1", broker.produced("notifications").get(2).key());
        }
    }

    private Notification notification(FakeKafkaBroker broker, JsonKafkaSerializer json, int index) {
        return json.deserialize(broker.produced("notifications").get(index).value(), Notification.class);
    }
}
