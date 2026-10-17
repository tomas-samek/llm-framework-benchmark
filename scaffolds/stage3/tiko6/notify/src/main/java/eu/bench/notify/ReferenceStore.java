package eu.bench.notify;
import io.tiko.Scope;
import io.tiko.annotations.Component;
import io.tiko.annotations.Inject;
import javax.sql.DataSource;
import java.sql.SQLException;
import eu.bench.notify.Messages.*;
@Component(scope = Scope.SINGLETON)
public class ReferenceStore {
    private final DataSource dataSource;
    @Inject
    public ReferenceStore(DataSource dataSource) { this.dataSource = dataSource; }
    public void upsert(ProductUpdate p) throws SQLException {
        update("MERGE INTO products (product_id, name, category) KEY(product_id) VALUES (?, ?, ?)", p.productId(), p.name(), p.category());
    }
    public void upsert(UserUpdate u) throws SQLException {
        update("MERGE INTO users (user_id, name, email) KEY(user_id) VALUES (?, ?, ?)", u.userId(), u.name(), u.email());
    }
    public void upsert(PriceUpdate p) throws SQLException {
        update("MERGE INTO prices (product_id, price_cents, currency) KEY(product_id) VALUES (?, ?, ?)", p.productId(), p.priceCents(), p.currency());
    }
    private void update(String sql, Object... values) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }
    public Notification merge(Purchase p) throws SQLException {
        String sql = "SELECT u.name AS user_name, p.name AS product_name, r.price_cents, r.currency "
                + "FROM (VALUES (1)) seed(n) LEFT JOIN users u ON u.user_id = ? "
                + "LEFT JOIN products p ON p.product_id = ? LEFT JOIN prices r ON r.product_id = ?";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, p.userId());
            statement.setString(2, p.productId());
            statement.setString(3, p.productId());
            try (var row = statement.executeQuery()) {
                row.next();
                Long cents = row.getObject("price_cents", Long.class);
                return new Notification(p.purchaseId(), p.userId(), row.getString("user_name"),
                    p.productId(), row.getString("product_name"), cents, row.getString("currency"),
                    p.quantity(), cents == null ? null : cents * p.quantity(), p.occurredAt());
            }
        }
    }
}
