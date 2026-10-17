package eu.bench.notify;
import io.tiko.EventBus;
import io.tiko.Scope;
import io.tiko.annotations.Component;
import io.tiko.annotations.EventHandler;
import io.tiko.annotations.Inject;
import java.sql.SQLException;
import eu.bench.notify.Messages.*;
@Component(scope = Scope.SINGLETON)
public class NotificationService {
    private final ReferenceStore store;
    private final EventBus events;
    @Inject
    public NotificationService(ReferenceStore store, EventBus events) {
        this.store = store;
        this.events = events;
    }
    @EventHandler
    public void product(ProductUpdate p) throws SQLException { store.upsert(p); }
    @EventHandler
    public void user(UserUpdate u) throws SQLException { store.upsert(u); }
    @EventHandler
    public void price(PriceUpdate p) throws SQLException { store.upsert(p); }
    @EventHandler
    public void purchase(Purchase p) throws SQLException { events.publish(store.merge(p)); }
}
