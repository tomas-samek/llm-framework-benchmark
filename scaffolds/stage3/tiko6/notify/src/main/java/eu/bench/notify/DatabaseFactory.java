package eu.bench.notify;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.tiko.Scope;
import io.tiko.annotations.Component;
import io.tiko.annotations.Produces;
import io.tiko.annotations.PreDestroy;
import javax.sql.DataSource;
import java.sql.SQLException;
@Component(scope = Scope.SINGLETON)
public class DatabaseFactory {
    private HikariDataSource ownedPool;
    @Produces(scope = Scope.SINGLETON)
    public DataSource dataSource(DatabaseConfig config) throws SQLException {
        var pool = new HikariConfig();
        pool.setJdbcUrl(config.url());
        pool.setUsername(config.user());
        pool.setPassword(config.password());
        pool.setMaximumPoolSize(config.poolSize());
        var ds = new HikariDataSource(pool);
        try (var connection = ds.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS products (product_id VARCHAR PRIMARY KEY, name VARCHAR, category VARCHAR)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS users (user_id VARCHAR PRIMARY KEY, name VARCHAR, email VARCHAR)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS prices (product_id VARCHAR PRIMARY KEY, price_cents BIGINT, currency VARCHAR)");
        } catch (SQLException e) {
            ds.close();
            throw e;
        }
        ownedPool = ds;
        return ds;
    }
    @PreDestroy
    public void shutdown() {
        if (ownedPool != null) ownedPool.close();
    }
}
