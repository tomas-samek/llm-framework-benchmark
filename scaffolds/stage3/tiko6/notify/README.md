# Purchase notifications

Java 21 application using Tiko DI 0.6.0, tiko-config, tiko-kafka, and raw JDBC over HikariCP/H2.

Build: `mvn -DskipTests package`. Start a local broker with `docker compose up -d`,
then run `mvn exec:java`. Stop the application with Ctrl+C.
The four input topics are created automatically by the supplied broker.

Environment overrides: `KAFKA_BOOTSTRAP_SERVERS` (localhost:9092),
`KAFKA_CONSUMER_GROUP` (purchase-notifications), `H2_URL`
(jdbc:h2:mem:notifications;DB_CLOSE_DELAY=-1), `H2_USER` (sa), and
`H2_PASSWORD` (empty). Use an embedded file H2 URL to retain reference data
between restarts. Defaults are in src/main/resources/application.yml.

Run the standalone demo publisher with
`mvn exec:java -Dexec.mainClass=eu.bench.notify.SampleDataPublisher`.
It sends three reference records and a purchase. Its short demo delay is not
a cross-topic ordering guarantee: production purchases use whatever reference
data has already been processed.

Reference updates use primary-key MERGE statements. Purchases are never stored;
one synchronous handler reads independently nullable references and publishes
one Notification event. The Kafka sink uses purchaseId as the output key.
Money uses integer cents and timestamps are echoed unchanged.
Malformed input is logged and skipped by the Kafka transport's SKIP policy.
There are no retry queues or dead-letter topics; delivery is not exactly-once.
