package eu.bench.notify;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler((record, exception) ->
                LoggerFactory.getLogger(KafkaConfiguration.class).error(
                        "Skipping failed message at {}-{} offset {}", record.topic(),
                        record.partition(), record.offset(), exception), new FixedBackOff(0L, 0L));
    }
}
