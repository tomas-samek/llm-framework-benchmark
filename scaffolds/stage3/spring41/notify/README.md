# Purchase notifications

Build using JDK 21 and Maven: `mvn -DskipTests package`.
Start the local broker with `docker compose up -d`, then run
`java -jar target/notify-0.0.1-SNAPSHOT.jar`.

The separate sample publisher entry point can be run from the packaged jar:

```powershell
java -Dloader.main=eu.bench.notify.SampleDataPublisher -cp target/notify-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

Run the consumer first. The demo waits briefly between reference records and its
purchase; Kafka does not guarantee ordering between different topics.

Configure `KAFKA_BOOTSTRAP_SERVERS` (default `localhost:9092`), `KAFKA_GROUP_ID`,
and `H2_URL` through the environment. H2 defaults to an embedded in-memory store;
for persistence use `jdbc:h2:file:./data/references`.
Only reference rows are stored. Malformed JSON is logged and skipped, and handler
failures are logged without retries or dead-letter topics. Publishing waits for
Kafka acknowledgement before committing the purchase offset; crash recovery is
at-least-once, as exactly-once processing is outside the specification.
