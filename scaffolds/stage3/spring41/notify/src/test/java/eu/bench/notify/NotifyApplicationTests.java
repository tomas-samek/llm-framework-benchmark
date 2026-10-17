package eu.bench.notify;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
import static eu.bench.notify.Messages.*;
import static org.junit.jupiter.api.Assertions.*;

class NotifyApplicationTests {

    @Test
    void malformedJsonIsSkipped() {
        var consumer = new InputConsumer(JsonMapper.builder().build(), null, null);
        assertDoesNotThrow(() -> consumer.receive(new org.apache.kafka.clients.consumer.ConsumerRecord<>(
                "price-updates", 0, 0, "p1", "not-json")));
    }

	@Test
	void mergesMissingAndUpdatedReferencesAndSerializesNulls() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:unit;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);
        var store = new ReferenceStore(new JdbcTemplate(ds));
        var purchase = new Purchase("buy1", "u1", "p1", 2, "2026-06-07T12:00:00.000Z");
        var json = JsonMapper.builder().build();
        var absent = json.readTree(json.writeValueAsString(store.merge(purchase)));
        for (String field : new String[]{"userName", "productName", "priceCents", "currency", "totalCents"}) {
            assertTrue(absent.has(field));
            assertTrue(absent.get(field).isNull());
        }
        store.upsert(new PriceUpdate("p1", 500L, "EUR"));
        assertEquals(1000L, store.merge(purchase).totalCents());
        assertNull(store.merge(purchase).productName());
        store.upsert(new ProductUpdate("p1", "Keyboard", "electronics"));
        store.upsert(new ProductUpdate("p1", "Mechanical Keyboard", "electronics"));
        store.upsert(new UserUpdate("u1", "Alice", "alice@example.com"));
        store.upsert(new PriceUpdate("p1", 7999L, "USD"));
        assertEquals(new Notification("buy1", "u1", "Alice", "p1", "Mechanical Keyboard",
                7999L, "USD", 2, 15998L, purchase.occurredAt()), store.merge(purchase));
        store.upsert(new PriceUpdate("p1", 8999L, "USD"));
        assertEquals(17998L, store.merge(purchase).totalCents());
	}

}
