/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.testcontainers;

import io.github.microcks.testcontainers.connection.KafkaConnection;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;
import io.github.microcks.testcontainers.model.TestStepResult;
import io.github.microcks.testcontainers.model.UnidirectionalEvent;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * Microcks mocking and testing features with Kafka broker.
 * @author laurent
 */
class MicrocksContainersEnsembleKafkaTest {

   @ParameterizedTest(name = "Mocking with {0} and {1})")
   @CsvSource({
         TestConstants.LATEST_IMAGE  + "," + TestConstants.ASYNC_LATEST_IMAGE,
         TestConstants.LATEST_NATIVE_IMAGE  + "," + TestConstants.ASYNC_LATEST_NATIVE_IMAGE,
         TestConstants.NIGHTLY_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_IMAGE,
         TestConstants.NIGHTLY_NATIVE_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_NATIVE_IMAGE,
   })
   void testAsyncFeatureKafkaMocking(String microcksImage, String asyncMinionImage) {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(microcksImage)
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature(DockerImageName.parse(asyncMinionImage))
                  .withKafkaConnection(new KafkaConnection("kafka:19092"));

            KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("kafka")
                  .withListener(() -> "kafka:19092");
      ) {
         kafka.start();
         ensemble.start();

         testMicrocksAsyncKafkaMocking(ensemble, kafka);
      }
   }

   @ParameterizedTest(name = "Testing with {0} and {1})")
   @CsvSource({
         TestConstants.LATEST_IMAGE  + "," + TestConstants.ASYNC_LATEST_IMAGE,
         TestConstants.LATEST_NATIVE_IMAGE  + "," + TestConstants.ASYNC_LATEST_NATIVE_IMAGE,
         TestConstants.NIGHTLY_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_IMAGE,
         TestConstants.NIGHTLY_NATIVE_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_NATIVE_IMAGE,
   })
   void testAsyncFeatureKafkaTesting(String microcksImage, String asyncMinionImage) throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(microcksImage)
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature(asyncMinionImage);

            KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("kafka")
                  .withListener(() -> "kafka:19092");
      ) {
         kafka.start();
         ensemble.start();
         ensemble.getAsyncMinionContainer().followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("MINION")));

         testMicrocksAsyncKafkaContractTesting(ensemble, kafka);
      }
   }

   private void testMicrocksAsyncKafkaMocking(MicrocksContainersEnsemble ensemble, KafkaContainer kafka) {
      // PastryordersAPI-0.1.0-pastry-orders
      String kafkaTopic = ensemble.getAsyncMinionContainer().getKafkaMockTopic("Pastry orders API", "0.1.0", "SUBSCRIBE pastry/orders");
      String expectedMessage = "{\"id\":\"4dab240d-7847-4e25-8ef3-1530687650c8\",\"customerId\":\"fe1088b3-9f30-4dc1-a93d-7b74f0a072b9\",\"status\":\"VALIDATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      // Initialize Kafka consumer to receive 1 message.
      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers().replace("PLAINTEXT://", ""));
      props.put(ConsumerConfig.GROUP_ID_CONFIG, "random-" + System.currentTimeMillis());
      props.put(ConsumerConfig.CLIENT_ID_CONFIG, "random-" + System.currentTimeMillis());
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

      // Only retrieve incoming messages and do not persist offset.
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
      KafkaConsumer consumer = new KafkaConsumer<>(props);
      String message = null;

      try {
         // Subscribe Kafka consumer and receive 1 message.
         consumer.subscribe(Arrays.asList(kafkaTopic), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            }
            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
               partitions.forEach(p -> consumer.seek(p, 0));
            }
         });
         ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(4000));
         if (!records.isEmpty()) {
            message = records.iterator().next().value();
         }

      } catch (Exception e) {
         fail("Exception while connecting to Kafka broker", e);
      } finally {
         consumer.close();
      }

      // Compare messages.
      assertNotNull(message);
      assertTrue(message.length() > 1);
      assertEquals(expectedMessage, message);
   }

   private void testMicrocksAsyncKafkaContractTesting(MicrocksContainersEnsemble ensemble, KafkaContainer kafka) throws Exception {
      // Bad message has no status, good message has one.
      String badMessage = "{\"id\":\"abcd\",\"customerId\":\"efgh\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";
      String goodMessage = "{\"id\":\"abcd\",\"customerId\":\"efgh\",\"status\":\"CREATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      // Produce a new test request.
      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("Pastry orders API:0.1.0");
      testRequest.setRunnerType(TestRunnerType.ASYNC_API_SCHEMA.name());
      testRequest.setTestEndpoint("kafka://kafka:19092/pastry-orders"); //?startOffset=0");
      testRequest.setTimeout(4000l);

      // First test should fail with validation failure messages.
      CompletableFuture<TestResult> testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(testRequest);
      await().pollDelay(750, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(true));

      // Initialize Kafka producer and send 4 messages.
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers().replace("PLAINTEXT://", ""));
      props.put(ProducerConfig.CLIENT_ID_CONFIG, "random-" + System.currentTimeMillis());
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

      try {
         // Send 4 messages on Kafka.
         try (final Producer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i=0; i<5; i++) {
               ProducerRecord<String, String> record = new ProducerRecord<>("pastry-orders",
                     String.valueOf(System.currentTimeMillis()), badMessage);
               System.err.println(System.currentTimeMillis() + " Sending bad message " + i + " on Kafka broker");
               producer.send(record);
               producer.flush();
               await().pollDelay(500, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(true));
            }
         }
      } catch (Exception e) {
         fail("Exception while connecting to Kafka broker", e);
      }

      TestResult testResult = null;
      try {
         testResult = testResultFuture.get();
      } catch (Exception e) {
         fail("Got an exception while waiting for test completion", e);
      }

      /*
      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      */

      assertFalse(testResult.isSuccess());
      assertEquals("kafka://kafka:19092/pastry-orders", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      TestStepResult testStepResult = testResult.getTestCaseResults().get(0).getTestStepResults().get(0);
      assertTrue(testStepResult.getMessage().contains("required property 'status' not found"));

      // Retrieve event messages for the failing test case.
      List<UnidirectionalEvent> events = ensemble.getMicrocksContainer().getEventMessagesForTestCase(testResult,
            "SUBSCRIBE pastry/orders");
      // We should have at least 4 events.
      assertTrue(events.size() >= 4);
      for (UnidirectionalEvent event : events) {
         assertNotNull(event.getEventMessage());
         // Check these are the correct message.
         assertEquals(badMessage, event.getEventMessage().getContent());
      }

      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("Pastry orders API:0.1.0")
            .runnerType(TestRunnerType.ASYNC_API_SCHEMA)
            .testEndpoint("kafka://kafka:19092/pastry-orders")
            .timeout(Duration.ofSeconds(3))
            .build();

      // Second test should succeed without validation failure messages.
      testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(otherTestRequestDTO);
      await().pollDelay(750, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(true));

      try {
         // Send 4 messages on Kafka.
         try (final Producer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i=0; i<5; i++) {
               ProducerRecord<String, String> record = new ProducerRecord<>("pastry-orders",
                     String.valueOf(System.currentTimeMillis()), goodMessage);
               System.err.println("Sending good message " + i + " on Kafka broker");
               producer.send(record);
               producer.flush();
               await().pollDelay(500, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(true));
            }
         }
      } catch (Exception e) {
         fail("Exception while connecting to Kafka broker", e);
      }

      try {
         testResult = testResultFuture.get();
      } catch (Exception e) {
         fail("Got an exception while waiting for test completion", e);
      }

      /*
      mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      */

      assertTrue(testResult.isSuccess());
      assertEquals("kafka://kafka:19092/pastry-orders", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      assertNull(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
   }
}
