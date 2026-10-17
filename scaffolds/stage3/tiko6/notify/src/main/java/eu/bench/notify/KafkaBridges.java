package eu.bench.notify;
import io.tiko.Scope;
import io.tiko.annotations.Component;
import io.tiko.annotations.EventTrigger;
import io.tiko.kafka.annotations.KafkaSource;
import io.tiko.kafka.annotations.KafkaSink;
import eu.bench.notify.Messages.*;
@Component(scope = Scope.SINGLETON)
public class KafkaBridges {
    @KafkaSource(topic = "product-updates") @EventTrigger
    public ProductUpdate product(ProductUpdate value) { return value; }
    @KafkaSource(topic = "user-updates") @EventTrigger
    public UserUpdate user(UserUpdate value) { return value; }
    @KafkaSource(topic = "price-updates") @EventTrigger
    public PriceUpdate price(PriceUpdate value) { return value; }
    @KafkaSource(topic = "purchases") @EventTrigger
    public Purchase purchase(Purchase value) { return value; }
    @KafkaSink(topic = "notifications", partitionKey = "purchaseId")
    public Notification notification(Notification value) { return value; }
}
