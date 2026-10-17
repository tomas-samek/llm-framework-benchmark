package eu.bench.notify;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import static eu.bench.notify.Messages.*;

@Repository
public class ReferenceStore {
    private final JdbcTemplate jdbc;
    public ReferenceStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void upsert(ProductUpdate p) {
        jdbc.update("MERGE INTO products (product_id, name, category) KEY(product_id) VALUES (?, ?, ?)",
                p.productId(), p.name(), p.category());
    }
    public void upsert(UserUpdate u) {
        jdbc.update("MERGE INTO users (user_id, name, email) KEY(user_id) VALUES (?, ?, ?)",
                u.userId(), u.name(), u.email());
    }
    public void upsert(PriceUpdate p) {
        jdbc.update("MERGE INTO prices (product_id, price_cents, currency) KEY(product_id) VALUES (?, ?, ?)",
                p.productId(), p.priceCents(), p.currency());
    }
    public Notification merge(Purchase p) {
        var products = jdbc.query("SELECT name FROM products WHERE product_id = ?",
                (rs, row) -> rs.getString(1), p.productId());
        var users = jdbc.query("SELECT name FROM users WHERE user_id = ?",
                (rs, row) -> rs.getString(1), p.userId());
        var prices = jdbc.query("SELECT price_cents, currency FROM prices WHERE product_id = ?",
                (rs, row) -> new PriceUpdate(p.productId(), rs.getLong(1), rs.getString(2)), p.productId());
        PriceUpdate price = prices.isEmpty() ? null : prices.getFirst();
        return new Notification(p.purchaseId(), p.userId(), users.isEmpty() ? null : users.getFirst(),
                p.productId(), products.isEmpty() ? null : products.getFirst(),
                price == null ? null : price.priceCents(), price == null ? null : price.currency(),
                p.quantity(), price == null ? null : Math.multiplyExact(price.priceCents(), p.quantity().longValue()),
                p.occurredAt());
    }
}
