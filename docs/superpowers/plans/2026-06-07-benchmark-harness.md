# Benchmark Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the framework-independent benchmark harness — golden scaffolds, a pinned Kafka broker, an external conformance oracle that scores `fixtures/stage-1/scenarios.json`, and the run/metrics scaffolding — so each Spring/Tiko trial can be graded objectively.

**Architecture:** A standalone Java/Maven **conformance oracle** (no dependency on either contestant) brings nothing up itself; it talks to a Kafka broker started by `conformance/docker-compose.yml`. It loads the shared scenario fixtures, publishes their inputs in order against a single running contestant app, drains the `notifications` topic into a key→value map, and asserts each expectation with a numeric-tolerant deep-equals. A run script orchestrates broker + contestant app + oracle and appends timing to `results/metrics.csv`. Golden scaffolds are generated once via each framework's canonical command and committed untouched.

**Tech Stack:** Java 21, Maven 3.9, kafka-clients 3.7.1, Jackson 2.17.2, JUnit 5.10.2, AssertJ 3.25.3, Docker (apache/kafka:3.8.0). All versions per `docs/benchmark-protocol.md` §3.

**Reference docs:** `docs/benchmark-protocol.md`, `docs/specs/stage-1-spec.md`, `fixtures/stage-1/scenarios.json`.

**Conventions for this plan:**
- All paths are relative to the benchmark repo root `W:\workspace\spring-vs-tiko-benchmark`.
- `mvn` is not on PATH in this environment; commands assume a machine where `mvn`, `docker`, and `curl` are available (the protocol pins these). Where a command can't be run here, the step says so and the checkpoint is "runs on the harness machine."
- **Commits:** the repo is not yet a git repo. Task 0 initializes it. Each task ends with a commit.

---

## File Structure

```
.gitignore                                  # ignore target/, runs/*/, data/
conformance/
  docker-compose.yml                        # pinned single-node Kafka
  oracle/
    pom.xml                                  # standalone Maven project (io.bench:conformance-oracle)
    src/main/java/io/bench/oracle/
      model/Scenarios.java                   # root { List<Scenario> }
      model/Scenario.java                    # { id, title, steps[], expect[] }
      model/Step.java                        # { publish?, publishRaw?, settleMs? }
      model/Publish.java                     # { topic, key, JsonNode value }
      model/PublishRaw.java                  # { topic, key, String value }
      model/Expectation.java                 # { topic, key, JsonNode value }
      ScenarioLoader.java                    # Jackson load of scenarios.json
      JsonMatch.java                         # numeric-tolerant deep-equals (pure)
      KafkaIo.java                           # producer (JSON/raw) + drain consumer
      OracleRunner.java                      # run scenarios -> List<Result>
      Result.java                            # { scenarioId, key, passed, detail }
      Report.java                            # compliance %, console + JSON
      Main.java                              # CLI entry
    src/test/java/io/bench/oracle/
      JsonMatchTest.java                     # unit (pure)
      ScenarioLoaderTest.java                # unit against real fixtures
      OracleRunnerIT.java                    # docker integration (tagged, manual)
  stage-1/
    run-conformance.ps1                      # orchestrate broker+app+oracle (Windows)
    run-conformance.sh                       # same (POSIX)
scaffolds/
  spring/GENERATE.md                         # exact spring init command + the golden tree
  tiko/GENERATE.md                           # exact archetype command + the golden tree
results/
  metrics.csv                                # header + one row per trial
  reviews/RUBRIC.md                          # code-quality scoring rubric
```

---

## Task 0: Initialize the harness repo

**Files:**
- Create: `.gitignore`

- [ ] **Step 1: Create `.gitignore`**

Create `.gitignore`:

```gitignore
# Build output
target/
*.class

# Local data produced by contestant apps during trials
data/
**/data/

# Trial workspaces are generated per run, not source
runs/*/spring/trial-*/
runs/*/tiko/trial-*/

# OS / IDE
.DS_Store
.idea/
*.iml
```

- [ ] **Step 2: Initialize git and make the first commit**

Run:
```
git init
git add .
git commit -m "chore: benchmark harness skeleton, protocol, stage-1 spec and fixtures"
```
Expected: a repo with the README, `docs/`, `fixtures/`, and the empty skeleton dirs committed.

- [ ] **Step 3: Checkpoint** — `git log --oneline` shows one commit; `git status` clean.

---

## Task 1: Pinned Kafka broker

**Files:**
- Create: `conformance/docker-compose.yml`

- [ ] **Step 1: Create the compose file**

Create `conformance/docker-compose.yml`:

```yaml
# Single-node Kafka (KRaft) pinned for the benchmark. Auto-creates topics so the
# conformance oracle and contestant apps can publish/consume without admin steps.
services:
  kafka:
    image: apache/kafka:3.8.0
    container_name: bench-kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: "PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093"
      KAFKA_ADVERTISED_LISTENERS: "PLAINTEXT://localhost:9092"
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: "1@localhost:9093"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

- [ ] **Step 2: Smoke test (harness machine)**

Run:
```
docker compose -f conformance/docker-compose.yml up -d
docker compose -f conformance/docker-compose.yml ps
docker compose -f conformance/docker-compose.yml down
```
Expected: the `bench-kafka` container reaches "running"/healthy, then stops cleanly.

- [ ] **Step 3: Commit**

```
git add conformance/docker-compose.yml
git commit -m "feat(conformance): pinned single-node Kafka broker"
```

---

## Task 2: Oracle Maven project skeleton

**Files:**
- Create: `conformance/oracle/pom.xml`
- Create: `conformance/oracle/src/main/java/io/bench/oracle/Main.java`

- [ ] **Step 1: Create the POM**

Create `conformance/oracle/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>io.bench</groupId>
    <artifactId>conformance-oracle</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <kafka.version>3.7.1</kafka.version>
        <jackson.version>2.17.2</jackson.version>
        <junit.version>5.10.2</junit.version>
        <assertj.version>3.25.3</assertj.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.apache.kafka</groupId>
            <artifactId>kafka-clients</artifactId>
            <version>${kafka.version}</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
            <version>2.0.13</version>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>${assertj.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>conformance-oracle</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <excludedGroups>integration</excludedGroups>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.3</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>io.bench.oracle.Main</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create a compiling Main stub**

Create `conformance/oracle/src/main/java/io/bench/oracle/Main.java`:

```java
package io.bench.oracle;

/** CLI entry point for the conformance oracle. Filled in in Task 6. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        throw new UnsupportedOperationException("implemented in Task 6");
    }
}
```

- [ ] **Step 3: Verify it builds**

Run: `mvn -q -f conformance/oracle/pom.xml compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```
git add conformance/oracle/pom.xml conformance/oracle/src/main/java/io/bench/oracle/Main.java
git commit -m "build(oracle): standalone Maven project skeleton"
```

---

## Task 3: Scenario model + loader

A faithful object model of `fixtures/stage-1/scenarios.json` and a Jackson loader. The loader is tested against the **real** fixtures file so the model can't drift from the spec.

**Files:**
- Create: `conformance/oracle/src/main/java/io/bench/oracle/model/Publish.java`
- Create: `conformance/oracle/src/main/java/io/bench/oracle/model/PublishRaw.java`
- Create: `conformance/oracle/src/main/java/io/bench/oracle/model/Step.java`
- Create: `conformance/oracle/src/main/java/io/bench/oracle/model/Expectation.java`
- Create: `conformance/oracle/src/main/java/io/bench/oracle/model/Scenario.java`
- Create: `conformance/oracle/src/main/java/io/bench/oracle/model/Scenarios.java`
- Create: `conformance/oracle/src/main/java/io/bench/oracle/ScenarioLoader.java`
- Test: `conformance/oracle/src/test/java/io/bench/oracle/ScenarioLoaderTest.java`

- [ ] **Step 1: Write the failing test**

Create `conformance/oracle/src/test/java/io/bench/oracle/ScenarioLoaderTest.java`:

```java
package io.bench.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import io.bench.oracle.model.Scenario;
import io.bench.oracle.model.Scenarios;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScenarioLoaderTest {

    // From the oracle module dir up to repo root, then into fixtures.
    private static final Path FIXTURES =
            Path.of("..", "..", "fixtures", "stage-1", "scenarios.json");

    @Test
    void loadsAllSixScenariosInOrder() throws Exception {
        Scenarios scenarios = ScenarioLoader.load(FIXTURES);

        assertThat(scenarios.scenarios()).extracting(Scenario::id)
                .containsExactly("AC1", "AC2", "AC3", "AC4", "AC5", "AC6");
    }

    @Test
    void ac1HasThreeReferencePublishesASettleAndAPurchase() throws Exception {
        Scenario ac1 = ScenarioLoader.load(FIXTURES).scenarios().get(0);

        assertThat(ac1.steps()).hasSize(5);
        assertThat(ac1.steps().get(0).publish().topic()).isEqualTo("product-updates");
        assertThat(ac1.steps().get(3).settleMs()).isEqualTo(3000L);
        assertThat(ac1.steps().get(4).publish().topic()).isEqualTo("purchases");
        assertThat(ac1.expect()).hasSize(1);
        assertThat(ac1.expect().get(0).key()).isEqualTo("buy1");
        assertThat(ac1.expect().get(0).value().get("totalCents").asInt()).isEqualTo(15998);
    }

    @Test
    void ac5HasARawPoisonPublish() throws Exception {
        Scenario ac5 = ScenarioLoader.load(FIXTURES).scenarios().get(4);

        assertThat(ac5.steps().get(0).publishRaw()).isNotNull();
        assertThat(ac5.steps().get(0).publishRaw().value()).isEqualTo("this is not valid json");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -f conformance/oracle/pom.xml test -Dtest=ScenarioLoaderTest`
Expected: FAIL — `Scenarios`/`ScenarioLoader` do not exist (compilation error).

- [ ] **Step 3: Write the model records**

Create `conformance/oracle/src/main/java/io/bench/oracle/model/Publish.java`:

```java
package io.bench.oracle.model;

import com.fasterxml.jackson.databind.JsonNode;

/** Publish a JSON value to a topic under a key. */
public record Publish(String topic, String key, JsonNode value) {
}
```

Create `conformance/oracle/src/main/java/io/bench/oracle/model/PublishRaw.java`:

```java
package io.bench.oracle.model;

/** Publish a raw (possibly non-JSON) string value to a topic under a key. */
public record PublishRaw(String topic, String key, String value) {
}
```

Create `conformance/oracle/src/main/java/io/bench/oracle/model/Step.java`:

```java
package io.bench.oracle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One step in a scenario. Exactly one of the three is non-null:
 * a JSON publish, a raw publish, or a settle pause (milliseconds).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Step(Publish publish, PublishRaw publishRaw, Long settleMs) {
}
```

Create `conformance/oracle/src/main/java/io/bench/oracle/model/Expectation.java`:

```java
package io.bench.oracle.model;

import com.fasterxml.jackson.databind.JsonNode;

/** Expect topic[key] to deep-equal value after all steps have run. */
public record Expectation(String topic, String key, JsonNode value) {
}
```

Create `conformance/oracle/src/main/java/io/bench/oracle/model/Scenario.java`:

```java
package io.bench.oracle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Scenario(String id, String title, List<Step> steps, List<Expectation> expect) {
}
```

Create `conformance/oracle/src/main/java/io/bench/oracle/model/Scenarios.java`:

```java
package io.bench.oracle.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Root of scenarios.json. The leading "_format" object is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Scenarios(List<Scenario> scenarios) {
}
```

- [ ] **Step 4: Write the loader**

Create `conformance/oracle/src/main/java/io/bench/oracle/ScenarioLoader.java`:

```java
package io.bench.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.bench.oracle.model.Scenarios;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loads the shared scenario fixtures into the typed model. */
public final class ScenarioLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScenarioLoader() {
    }

    public static Scenarios load(Path file) throws IOException {
        try (var in = Files.newInputStream(file)) {
            return MAPPER.readValue(in, Scenarios.class);
        }
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `mvn -q -f conformance/oracle/pom.xml test -Dtest=ScenarioLoaderTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```
git add conformance/oracle/src/main/java/io/bench/oracle/model conformance/oracle/src/main/java/io/bench/oracle/ScenarioLoader.java conformance/oracle/src/test/java/io/bench/oracle/ScenarioLoaderTest.java
git commit -m "feat(oracle): scenario model and fixtures loader"
```

---

## Task 4: Numeric-tolerant deep-equals

`Notification` values come back as JSON; the same `ObjectMapper` parses both expected and actual, but a tolerant comparison (treat `7999` int and long alike, by decimal value) removes any node-type pitfalls. Nulls are significant: an expected `null` must match an actual `null`, and an object must have exactly the same field set.

**Files:**
- Create: `conformance/oracle/src/main/java/io/bench/oracle/JsonMatch.java`
- Test: `conformance/oracle/src/test/java/io/bench/oracle/JsonMatchTest.java`

- [ ] **Step 1: Write the failing test**

Create `conformance/oracle/src/test/java/io/bench/oracle/JsonMatchTest.java`:

```java
package io.bench.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonMatchTest {

    private final ObjectMapper m = new ObjectMapper();

    private boolean match(String expected, String actual) throws Exception {
        return JsonMatch.matches(m.readTree(expected), m.readTree(actual));
    }

    @Test
    void identicalObjectsMatch() throws Exception {
        assertThat(match("{\"a\":1,\"b\":\"x\"}", "{\"b\":\"x\",\"a\":1}")).isTrue();
    }

    @Test
    void intAndLongOfSameValueMatch() throws Exception {
        // 15998 vs 15998 written as a long-ish literal; same decimal value.
        assertThat(match("{\"t\":15998}", "{\"t\":15998}")).isTrue();
    }

    @Test
    void nullMatchesNullButNotAValue() throws Exception {
        assertThat(match("{\"p\":null}", "{\"p\":null}")).isTrue();
        assertThat(match("{\"p\":null}", "{\"p\":7999}")).isFalse();
        assertThat(match("{\"p\":7999}", "{\"p\":null}")).isFalse();
    }

    @Test
    void differentValuesDoNotMatch() throws Exception {
        assertThat(match("{\"name\":\"Alice\"}", "{\"name\":\"Bob\"}")).isFalse();
    }

    @Test
    void extraOrMissingFieldsDoNotMatch() throws Exception {
        assertThat(match("{\"a\":1}", "{\"a\":1,\"b\":2}")).isFalse();
        assertThat(match("{\"a\":1,\"b\":2}", "{\"a\":1}")).isFalse();
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -f conformance/oracle/pom.xml test -Dtest=JsonMatchTest`
Expected: FAIL — `JsonMatch` does not exist.

- [ ] **Step 3: Write the implementation**

Create `conformance/oracle/src/main/java/io/bench/oracle/JsonMatch.java`:

```java
package io.bench.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Map;

/** Deep, numeric-tolerant equality of two JSON values. Nulls are significant. */
public final class JsonMatch {

    private JsonMatch() {
    }

    public static boolean matches(JsonNode expected, JsonNode actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        if (expected.isNull() || actual.isNull()) {
            return expected.isNull() && actual.isNull();
        }
        if (expected.isNumber() && actual.isNumber()) {
            return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
        }
        if (expected.isObject() && actual.isObject()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                JsonNode a = actual.get(e.getKey());
                if (a == null || !matches(e.getValue(), a)) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isArray() && actual.isArray()) {
            if (expected.size() != actual.size()) {
                return false;
            }
            for (int i = 0; i < expected.size(); i++) {
                if (!matches(expected.get(i), actual.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (expected.isBoolean() && actual.isBoolean()) {
            return expected.asBoolean() == actual.asBoolean();
        }
        if (expected.isTextual() && actual.isTextual()) {
            return expected.asText().equals(actual.asText());
        }
        return false;
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q -f conformance/oracle/pom.xml test -Dtest=JsonMatchTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```
git add conformance/oracle/src/main/java/io/bench/oracle/JsonMatch.java conformance/oracle/src/test/java/io/bench/oracle/JsonMatchTest.java
git commit -m "feat(oracle): numeric-tolerant JSON deep-equals"
```

---

## Task 5: Kafka I/O (producer + drain consumer)

Publishes scenario inputs (JSON and raw) and drains `notifications` from the beginning into a `key -> latest JsonNode` map. Integration code; complete and self-contained. Unit-testing Kafka I/O directly needs a broker, so it is exercised by the tagged IT in Task 6 and the end-to-end run; there is no pure-unit step here.

**Files:**
- Create: `conformance/oracle/src/main/java/io/bench/oracle/KafkaIo.java`

- [ ] **Step 1: Write the implementation**

Create `conformance/oracle/src/main/java/io/bench/oracle/KafkaIo.java`:

```java
package io.bench.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/** Thin Kafka access for the oracle: publish inputs, drain the output topic. */
public final class KafkaIo implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String bootstrap;
    private final KafkaProducer<String, String> producer;

    public KafkaIo(String bootstrap) {
        this.bootstrap = bootstrap;
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        this.producer = new KafkaProducer<>(p);
    }

    /** Publish a JSON value (serialized compactly) to topic under key. */
    public void publishJson(String topic, String key, JsonNode value) {
        publishRaw(topic, key, value.toString());
    }

    /** Publish a raw string value (used for poison/non-JSON messages). */
    public void publishRaw(String topic, String key, String value) {
        try {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        } catch (Exception e) {
            throw new RuntimeException("publish to " + topic + " failed", e);
        }
    }

    /**
     * Drain {@code topic} from the beginning, returning key -> latest value as a
     * parsed JsonNode (later offsets overwrite earlier ones for the same key).
     * Polls until {@code quietMillis} pass with no new records.
     */
    public Map<String, JsonNode> drain(String topic, long quietMillis) {
        Properties p = new Properties();
        p.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "oracle-" + UUID.randomUUID());
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        Map<String, JsonNode> latest = new LinkedHashMap<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(p)) {
            consumer.subscribe(List.of(topic));
            long lastSawRecord = System.currentTimeMillis();
            while (System.currentTimeMillis() - lastSawRecord < quietMillis) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300));
                if (!records.isEmpty()) {
                    lastSawRecord = System.currentTimeMillis();
                    for (ConsumerRecord<String, String> r : records) {
                        latest.put(r.key(), parse(r.value()));
                    }
                }
            }
        }
        return latest;
    }

    private static JsonNode parse(String value) {
        try {
            return MAPPER.readTree(value);
        } catch (Exception e) {
            // A non-JSON record on the output topic is itself a failure signal;
            // represent it as a textual node so matching reports a mismatch.
            return MAPPER.getNodeFactory().textNode(value);
        }
    }

    @Override
    public void close() {
        producer.flush();
        producer.close();
    }

    public Map<String, String> info() {
        Map<String, String> m = new HashMap<>();
        m.put("bootstrap", bootstrap);
        return m;
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn -q -f conformance/oracle/pom.xml compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```
git add conformance/oracle/src/main/java/io/bench/oracle/KafkaIo.java
git commit -m "feat(oracle): kafka producer and drain consumer"
```

---

## Task 6: Runner, report, Main, and a docker integration test

Tie it together: execute every scenario's steps in order against a running app, drain `notifications`, evaluate each expectation, and emit a compliance report (console + JSON). Exit non-zero unless compliance is 100%.

**Files:**
- Create: `conformance/oracle/src/main/java/io/bench/oracle/Result.java`
- Create: `conformance/oracle/src/main/java/io/bench/oracle/OracleRunner.java`
- Create: `conformance/oracle/src/main/java/io/bench/oracle/Report.java`
- Modify: `conformance/oracle/src/main/java/io/bench/oracle/Main.java`
- Test: `conformance/oracle/src/test/java/io/bench/oracle/OracleRunnerIT.java`

- [ ] **Step 1: Create the Result record**

Create `conformance/oracle/src/main/java/io/bench/oracle/Result.java`:

```java
package io.bench.oracle;

/** Outcome of one expectation within a scenario. */
public record Result(String scenarioId, String key, boolean passed, String detail) {
}
```

- [ ] **Step 2: Create the runner**

Create `conformance/oracle/src/main/java/io/bench/oracle/OracleRunner.java`:

```java
package io.bench.oracle;

import com.fasterxml.jackson.databind.JsonNode;
import io.bench.oracle.model.Expectation;
import io.bench.oracle.model.Scenario;
import io.bench.oracle.model.Scenarios;
import io.bench.oracle.model.Step;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs all scenarios in order against a single running contestant app:
 * executes every step (publish/raw/settle), then drains the output topic once
 * and evaluates every expectation.
 */
public final class OracleRunner {

    private static final String OUTPUT_TOPIC = "notifications";
    private static final long DRAIN_QUIET_MILLIS = 5000;

    private final KafkaIo kafka;

    public OracleRunner(KafkaIo kafka) {
        this.kafka = kafka;
    }

    public List<Result> run(Scenarios scenarios) throws InterruptedException {
        for (Scenario s : scenarios.scenarios()) {
            for (Step step : s.steps()) {
                applyStep(step);
            }
        }
        Map<String, JsonNode> emitted = kafka.drain(OUTPUT_TOPIC, DRAIN_QUIET_MILLIS);

        List<Result> results = new ArrayList<>();
        for (Scenario s : scenarios.scenarios()) {
            for (Expectation e : s.expect()) {
                JsonNode actual = emitted.get(e.key());
                if (actual == null) {
                    results.add(new Result(s.id(), e.key(), false, "no notification with this key"));
                } else if (JsonMatch.matches(e.value(), actual)) {
                    results.add(new Result(s.id(), e.key(), true, "ok"));
                } else {
                    results.add(new Result(s.id(), e.key(), false,
                            "expected " + e.value() + " but got " + actual));
                }
            }
        }
        return results;
    }

    private void applyStep(Step step) throws InterruptedException {
        if (step.settleMs() != null) {
            Thread.sleep(step.settleMs());
        } else if (step.publish() != null) {
            kafka.publishJson(step.publish().topic(), step.publish().key(), step.publish().value());
        } else if (step.publishRaw() != null) {
            kafka.publishRaw(step.publishRaw().topic(), step.publishRaw().key(), step.publishRaw().value());
        }
    }
}
```

- [ ] **Step 3: Create the report**

Create `conformance/oracle/src/main/java/io/bench/oracle/Report.java`:

```java
package io.bench.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Renders results to the console and a JSON file; computes compliance. */
public final class Report {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Report() {
    }

    public static double compliance(List<Result> results) {
        if (results.isEmpty()) {
            return 0.0;
        }
        long passed = results.stream().filter(Result::passed).count();
        return (double) passed / results.size();
    }

    public static void printConsole(List<Result> results) {
        for (Result r : results) {
            System.out.printf("[%s] %-5s %s%s%n",
                    r.passed() ? "PASS" : "FAIL", r.scenarioId(), r.key(),
                    r.passed() ? "" : " -- " + r.detail());
        }
        System.out.printf("Compliance: %.0f%% (%d/%d)%n",
                compliance(results) * 100,
                results.stream().filter(Result::passed).count(), results.size());
    }

    public static void writeJson(Path out, List<Result> results) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("compliance", compliance(results));
        root.put("passed", results.stream().filter(Result::passed).count());
        root.put("total", results.size());
        ArrayNode arr = root.putArray("results");
        for (Result r : results) {
            ObjectNode n = arr.addObject();
            n.put("scenario", r.scenarioId());
            n.put("key", r.key());
            n.put("passed", r.passed());
            n.put("detail", r.detail());
        }
        if (out.getParent() != null) {
            Files.createDirectories(out.getParent());
        }
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);
    }
}
```

- [ ] **Step 4: Implement Main**

Replace `conformance/oracle/src/main/java/io/bench/oracle/Main.java` with:

```java
package io.bench.oracle;

import io.bench.oracle.model.Scenarios;
import java.nio.file.Path;
import java.util.List;

/**
 * CLI: java -jar conformance-oracle.jar <bootstrap> <scenarios.json> [out.json]
 * Exits 0 only when compliance is 100%; 1 otherwise (so run scripts can gate).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: <bootstrap> <scenarios.json> [out.json]");
            System.exit(2);
            return;
        }
        String bootstrap = args[0];
        Path scenariosFile = Path.of(args[1]);
        Path out = args.length >= 3 ? Path.of(args[2]) : null;

        Scenarios scenarios = ScenarioLoader.load(scenariosFile);
        List<Result> results;
        try (KafkaIo kafka = new KafkaIo(bootstrap)) {
            results = new OracleRunner(kafka).run(scenarios);
        }
        Report.printConsole(results);
        if (out != null) {
            Report.writeJson(out, results);
        }
        System.exit(Report.compliance(results) == 1.0 ? 0 : 1);
    }
}
```

- [ ] **Step 5: Write a tagged docker integration test (uses a fake producer-side app)**

This IT proves the runner wiring end-to-end without a contestant: it publishes the scenario inputs, then itself acts as a trivial "merge app" by echoing an expected notification, confirming drain+match. It is tagged `integration` (excluded from the default `mvn test`) and requires the broker from Task 1.

Create `conformance/oracle/src/test/java/io/bench/oracle/OracleRunnerIT.java`:

```java
package io.bench.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.bench.oracle.model.Scenarios;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Requires a broker on localhost:9092 (conformance/docker-compose.yml up).
 * Run with: mvn -f conformance/oracle/pom.xml test -Dgroups=integration
 */
@Tag("integration")
class OracleRunnerIT {

    private static final String BOOTSTRAP = "localhost:9092";
    private static final ObjectMapper M = new ObjectMapper();

    @Test
    void drainMatchesAPublishedNotification() throws Exception {
        // A one-scenario fixture: publish a notification directly and expect it back.
        String json = "{\"scenarios\":[{\"id\":\"SMOKE\",\"title\":\"smoke\","
                + "\"steps\":[{\"publish\":{\"topic\":\"notifications\",\"key\":\"smk1\","
                + "\"value\":{\"purchaseId\":\"smk1\",\"quantity\":1}}}],"
                + "\"expect\":[{\"topic\":\"notifications\",\"key\":\"smk1\","
                + "\"value\":{\"purchaseId\":\"smk1\",\"quantity\":1}}]}]}";
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"), "smoke-scenarios.json");
        java.nio.file.Files.writeString(tmp, json);

        Scenarios scenarios = ScenarioLoader.load(tmp);
        List<Result> results;
        try (KafkaIo kafka = new KafkaIo(BOOTSTRAP)) {
            results = new OracleRunner(kafka).run(scenarios);
        }
        assertThat(results).allMatch(Result::passed);
        assertThat(Report.compliance(results)).isEqualTo(1.0);
    }
}
```

- [ ] **Step 6: Run unit tests (no docker) to verify they pass**

Run: `mvn -q -f conformance/oracle/pom.xml test`
Expected: PASS; the `integration`-tagged IT is excluded by surefire config.

- [ ] **Step 7: Run the integration test against the broker (harness machine)**

Run:
```
docker compose -f conformance/docker-compose.yml up -d
mvn -f conformance/oracle/pom.xml test -Dgroups=integration
docker compose -f conformance/docker-compose.yml down
```
Expected: `OracleRunnerIT` passes (drain + match works end-to-end).

- [ ] **Step 8: Build the runnable jar**

Run: `mvn -q -f conformance/oracle/pom.xml package`
Expected: `conformance/oracle/target/conformance-oracle.jar` exists (shaded, runnable).

- [ ] **Step 9: Commit**

```
git add conformance/oracle/src/main/java/io/bench/oracle/Result.java conformance/oracle/src/main/java/io/bench/oracle/OracleRunner.java conformance/oracle/src/main/java/io/bench/oracle/Report.java conformance/oracle/src/main/java/io/bench/oracle/Main.java conformance/oracle/src/test/java/io/bench/oracle/OracleRunnerIT.java
git commit -m "feat(oracle): runner, report, CLI and docker integration test"
```

---

## Task 7: Stage-1 run scripts

Orchestrate a single trial's grading: bring up the broker, start the contestant app (caller supplies the start command, since Spring and Tiko launch differently), wait, run the oracle against `fixtures/stage-1/scenarios.json`, append a row to `results/metrics.csv`, tear down. The app's wall-clock-to-grade is recorded; token/turn counts are filled in from the agent transcript (columns exist for them).

**Files:**
- Create: `conformance/stage-1/run-conformance.ps1`
- Create: `conformance/stage-1/run-conformance.sh`

- [ ] **Step 1: Create the PowerShell script**

Create `conformance/stage-1/run-conformance.ps1`:

```powershell
# Grade one Stage-1 trial.
# Usage:
#   ./run-conformance.ps1 -Framework spring -Trial 01 -AppStartCmd "mvn -q -pl app spring-boot:run"
# The app start command is run from the trial working directory and must connect
# to localhost:9092. The script starts it in the background, grades, then stops it.
param(
    [Parameter(Mandatory = $true)][string]$Framework,
    [Parameter(Mandatory = $true)][string]$Trial,
    [Parameter(Mandatory = $true)][string]$AppStartCmd,
    [string]$TrialDir = ""
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path "$PSScriptRoot/../.."
if ($TrialDir -eq "") {
    $TrialDir = Join-Path $Root "runs/stage-1/$Framework/trial-$Trial"
}
$Scenarios = Join-Path $Root "fixtures/stage-1/scenarios.json"
$OracleJar = Join-Path $Root "conformance/oracle/target/conformance-oracle.jar"
$OutJson   = Join-Path $Root "results/stage-1-$Framework-$Trial.json"
$Compose   = Join-Path $Root "conformance/docker-compose.yml"

Write-Host "Bringing up broker..."
docker compose -f $Compose up -d
Start-Sleep -Seconds 8

Write-Host "Starting contestant app in $TrialDir ..."
$startedAt = Get-Date
$app = Start-Process -FilePath "powershell" -ArgumentList "-NoProfile","-Command",$AppStartCmd `
    -WorkingDirectory $TrialDir -PassThru
Start-Sleep -Seconds 20  # let consumers join before publishing

try {
    Write-Host "Running oracle..."
    & java -jar $OracleJar "localhost:9092" $Scenarios $OutJson
    $code = $LASTEXITCODE
} finally {
    if (-not $app.HasExited) { Stop-Process -Id $app.Id -Force }
    docker compose -f $Compose down
}

$elapsed = [int]((Get-Date) - $startedAt).TotalSeconds
$compliance = (Get-Content $OutJson | ConvertFrom-Json).compliance
$row = "{0},{1},stage-1,{2},{3},{4},,," -f (Get-Date -Format s), $Framework, $Trial, $compliance, $elapsed
Add-Content -Path (Join-Path $Root "results/metrics.csv") -Value $row
Write-Host "Compliance=$compliance elapsed=${elapsed}s (oracle exit $code)"
exit $code
```

- [ ] **Step 2: Create the POSIX script**

Create `conformance/stage-1/run-conformance.sh`:

```bash
#!/usr/bin/env bash
# Grade one Stage-1 trial.
# Usage: ./run-conformance.sh <framework> <trial> "<app-start-cmd>" [trial-dir]
set -euo pipefail

FRAMEWORK="$1"; TRIAL="$2"; APP_START_CMD="$3"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TRIAL_DIR="${4:-$ROOT/runs/stage-1/$FRAMEWORK/trial-$TRIAL}"
SCENARIOS="$ROOT/fixtures/stage-1/scenarios.json"
ORACLE_JAR="$ROOT/conformance/oracle/target/conformance-oracle.jar"
OUT_JSON="$ROOT/results/stage-1-$FRAMEWORK-$TRIAL.json"
COMPOSE="$ROOT/conformance/docker-compose.yml"

echo "Bringing up broker..."
docker compose -f "$COMPOSE" up -d
sleep 8

echo "Starting contestant app in $TRIAL_DIR ..."
STARTED_AT=$(date +%s)
( cd "$TRIAL_DIR" && eval "$APP_START_CMD" ) &
APP_PID=$!
sleep 20

cleanup() {
  kill "$APP_PID" 2>/dev/null || true
  docker compose -f "$COMPOSE" down
}
trap cleanup EXIT

echo "Running oracle..."
set +e
java -jar "$ORACLE_JAR" "localhost:9092" "$SCENARIOS" "$OUT_JSON"
CODE=$?
set -e

ELAPSED=$(( $(date +%s) - STARTED_AT ))
COMPLIANCE=$(grep -o '"compliance"[^,]*' "$OUT_JSON" | head -1 | grep -o '[0-9.]*')
echo "$(date +%FT%T),$FRAMEWORK,stage-1,$TRIAL,$COMPLIANCE,$ELAPSED,,," >> "$ROOT/results/metrics.csv"
echo "Compliance=$COMPLIANCE elapsed=${ELAPSED}s (oracle exit $CODE)"
exit $CODE
```

- [ ] **Step 3: Make the POSIX script executable and sanity-check syntax**

Run: `bash -n conformance/stage-1/run-conformance.sh && chmod +x conformance/stage-1/run-conformance.sh`
Expected: no syntax errors.

- [ ] **Step 4: Commit**

```
git add conformance/stage-1/run-conformance.ps1 conformance/stage-1/run-conformance.sh
git commit -m "feat(conformance): stage-1 trial run scripts"
```

---

## Task 8: Golden scaffolds + GENERATE.md

Generate each framework's clean starting point with its canonical command and commit the result untouched. These run on the harness machine (need `mvn`, and `curl` or the Spring CLI).

**Files:**
- Create: `scaffolds/tiko/GENERATE.md`
- Create: `scaffolds/spring/GENERATE.md`
- Create (generated): `scaffolds/tiko/<project>/...`, `scaffolds/spring/<project>/...`

- [ ] **Step 1: Write the Tiko GENERATE.md**

Create `scaffolds/tiko/GENERATE.md`:

```markdown
# Tiko golden scaffold

Generated once, committed untouched. Every trial copies this tree.

## Command (run from `scaffolds/tiko/`)

    mvn archetype:generate \
        -DarchetypeGroupId=io.github.tomas-samek \
        -DarchetypeArtifactId=tiko-archetype \
        -DarchetypeVersion=0.2.1 \
        -DgroupId=eu.bench.notify \
        -DartifactId=notify \
        -Dversion=1.0.0-SNAPSHOT \
        -DinteractiveMode=false

Verify `0.2.1` is the latest published archetype on Maven Central
(https://central.sonatype.com/artifact/io.github.tomas-samek/tiko-archetype).
If a newer version is the pinned Tiko version, use it and update
`docs/benchmark-protocol.md` §3 to match. Do NOT hand-edit the generated tree —
the archetype's bundled `CLAUDE.md`, `.ai-skills/`, and `.mcp.json` are part of
the as-shipped condition and must remain.

## After generation

    cd notify && mvn -q -DskipTests package   # confirm the scaffold builds

Then commit the whole `scaffolds/tiko/notify/` tree.
```

- [ ] **Step 2: Write the Spring GENERATE.md**

Create `scaffolds/spring/GENERATE.md`:

```markdown
# Spring Boot golden scaffold

Generated once, committed untouched. Every trial copies this tree.
Core only — the trial agent adds spring-kafka / data-jpa / web / h2 / lucene
itself, mirroring Tiko's opt-in model.

## Command (run from `scaffolds/spring/`)

Using curl against Spring Initializr (no Spring CLI required):

    curl -s https://start.spring.io/starter.zip \
        -d type=maven-project \
        -d language=java \
        -d javaVersion=21 \
        -d bootVersion=3.3.5 \
        -d groupId=eu.bench.notify \
        -d artifactId=notify \
        -d name=notify \
        -d packageName=eu.bench.notify \
        -d dependencies= \
        -o notify.zip
    unzip notify.zip -d notify && rm notify.zip

(Equivalent Spring CLI: `spring init --build=maven --java-version=21 \
 --boot-version=3.3.5 --group-id=eu.bench.notify --artifact-id=notify \
 --name=notify notify`.)

`dependencies=` (empty) yields core only: `spring-boot-starter` +
`spring-boot-starter-test`.

## After generation

    cd notify && mvn -q -DskipTests package   # confirm the scaffold builds

Then commit the whole `scaffolds/spring/notify/` tree.
```

- [ ] **Step 3: Generate both scaffolds (harness machine)**

Run the two commands above. Expected: `scaffolds/tiko/notify/` and
`scaffolds/spring/notify/` exist and each builds with `mvn -q -DskipTests package`.

- [ ] **Step 4: Commit the golden trees**

```
git add scaffolds/tiko scaffolds/spring
git commit -m "feat(scaffolds): golden Tiko archetype and Spring Initializr projects"
```

---

## Task 9: Results templates

**Files:**
- Create: `results/metrics.csv`
- Create: `results/reviews/RUBRIC.md`

- [ ] **Step 1: Create the metrics header**

Create `results/metrics.csv`:

```csv
timestamp,framework,stage,trial,compliance,wall_clock_seconds,output_tokens,agent_turns,tool_calls
```

- [ ] **Step 2: Create the review rubric**

Create `results/reviews/RUBRIC.md`:

```markdown
# Code-quality review rubric

Score each dimension 1-5 (5 best). One review per trial, stored as
`results/reviews/stage-<s>-<framework>-<trial>.md`. If using LLM judges, run >=3
and record the median per dimension. Framework is identifiable from imports;
score against the rubric, not against framework preference.

| Dimension | 1 | 3 | 5 |
|---|---|---|---|
| Idiomaticity | fights the framework | mostly idiomatic | clean, idiomatic use |
| Structure / cohesion | tangled, god-classes | reasonable split | focused, single-responsibility units |
| Defect risk | clear bugs / races | minor issues | none spotted |
| Error handling | crashes on bad input | partial | poison-safe, never stalls (per spec §6) |
| Test quality | none / trivial | covers happy path | covers merge + null + poison paths |

Only compare trials at equal external compliance (see protocol §8).
Record: dimension scores, total, and a 3-5 sentence justification.
```

- [ ] **Step 3: Commit**

```
git add results/metrics.csv results/reviews/RUBRIC.md
git commit -m "feat(results): metrics schema and code-quality rubric"
```

---

## Self-Review

- **Spec/protocol coverage:**
  - Pinned substrate (protocol §3) → Task 1 (Kafka image), Task 2 (kafka/jackson versions). ✓
  - External oracle graded compliance (protocol §8.1) → Tasks 3-6; runs from fixtures, independent of contestants. ✓
  - Stage-1 contracts & emit-with-nulls behavior (spec §3-5) → encoded in `fixtures/stage-1/scenarios.json` (already written); oracle asserts them (Tasks 4, 6). ✓
  - Poison-message survival (spec §6 / AC5) → fixture AC5 `publishRaw`; oracle `publishRaw` + drain (Tasks 5, 6). ✓
  - Equal start / canonical fresh-start (protocol §2) → Task 8 GENERATE.md for both. ✓
  - As-shipped Tiko guidance preserved (protocol §6) → Task 8 explicitly forbids editing the generated tree. ✓
  - Efficiency metrics (protocol §8.2) → Task 7 records wall-clock; Task 9 csv has token/turn/tool columns for transcript entry. ✓
  - Code-quality review (protocol §8.3) → Task 9 RUBRIC.md. ✓
  - Run procedure + stop rule (protocol §7) → Task 7 scripts (caps are operator-enforced during the agent run, out of harness scope). ✓
- **Placeholder scan:** no TBD/TODO; all code complete. The only deferred value is the archetype version, with an explicit verify-and-update instruction (Task 8 Step 1) — not a silent placeholder.
- **Type consistency:** `JsonMatch.matches(JsonNode, JsonNode)`, `ScenarioLoader.load(Path)→Scenarios`, `OracleRunner(KafkaIo).run(Scenarios)→List<Result>`, `Report.compliance(List<Result>)`, `KafkaIo.publishJson/publishRaw/drain` — names and signatures are used identically across Tasks 3-7. ✓
- **Note (not a gap):** the harness does not build the notification system itself — that is produced by the contestant agents during runs. The oracle treats each app as a black box over Kafka.
