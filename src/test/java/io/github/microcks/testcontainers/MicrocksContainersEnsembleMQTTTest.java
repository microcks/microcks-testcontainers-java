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

import io.github.microcks.testcontainers.connection.GenericConnection;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;
import io.github.microcks.testcontainers.model.TestStepResult;
import io.github.microcks.testcontainers.model.UnidirectionalEvent;

import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * Microcks MQTT async mocking functionality.
 * @author laurent
 */
class MicrocksContainersEnsembleMQTTTest {

   @ParameterizedTest(name = "Mocking with {0} and {1})")
   @CsvSource({
         TestConstants.LATEST_IMAGE  + "," + TestConstants.ASYNC_LATEST_IMAGE,
         TestConstants.LATEST_NATIVE_IMAGE  + "," + TestConstants.ASYNC_LATEST_NATIVE_IMAGE,
         TestConstants.NIGHTLY_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_IMAGE,
         TestConstants.NIGHTLY_NATIVE_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_NATIVE_IMAGE,
   })
   void testAsyncFeatureMQTTMocking(String microcksImage, String asyncMinionImage) {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(microcksImage)
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature(asyncMinionImage)
                  .withMQTTConnection(new GenericConnection("hivemq:1883", "test", "test"));

            HiveMQContainer hivemq = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce:2024.3"))
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("hivemq");
      ) {
         hivemq.start();
         ensemble.start();

         testMicrocksAsyncMQTTMocking(ensemble, hivemq);
      }
   }

   // Native async minion images fail to connect to the MQTT broker when running tests: only JVM images are covered.
   @ParameterizedTest(name = "Testing with {0} and {1})")
   @CsvSource({
         TestConstants.LATEST_IMAGE  + "," + TestConstants.ASYNC_LATEST_IMAGE,
         TestConstants.NIGHTLY_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_IMAGE,
         TestConstants.NIGHTLY_NATIVE_IMAGE + "," + TestConstants.ASYNC_NIGHTLY_NATIVE_IMAGE
   })
   void testAsyncFeatureMQTTTesting(String microcksImage, String asyncMinionImage) throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(microcksImage)
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature(DockerImageName.parse(asyncMinionImage).asCompatibleSubstituteFor("quay.io/microcks/microcks-uber-async-minion"));

            HiveMQContainer hivemq = new HiveMQContainer(DockerImageName.parse("hivemq/hivemq-ce:2024.3"))
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("hivemq");
      ) {
         hivemq.start();
         ensemble.start();

         testMicrocksAsyncMQTTContractTesting(ensemble, hivemq);
      }
   }

   private void testMicrocksAsyncMQTTContractTesting(MicrocksContainersEnsemble ensemble, HiveMQContainer hivemq) throws Exception {
      // Bad message has no status, good message has one.
      String badMessage = "{\"id\":\"abcd\",\"customerId\":\"efgh\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";
      String goodMessage = "{\"id\":\"abcd\",\"customerId\":\"efgh\",\"status\":\"CREATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("Pastry orders API:0.1.0");
      testRequest.setRunnerType(TestRunnerType.ASYNC_API_SCHEMA.name());
      testRequest.setTestEndpoint("mqtt://hivemq:1883/pastry/orders");
      testRequest.setTimeout(4000L);

      // First test should fail with validation failure messages.
      CompletableFuture<TestResult> testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(testRequest);
      await().pollDelay(2, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(true));

      publishMessages(hivemq, "pastry/orders", badMessage);

      TestResult testResult = null;
      try {
         testResult = testResultFuture.get();
      } catch (Exception e) {
         fail("Got an exception while waiting for test completion", e);
      }

      assertFalse(testResult.isSuccess());
      assertEquals("mqtt://hivemq:1883/pastry/orders", testResult.getTestedEndpoint());

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
         assertEquals(badMessage, event.getEventMessage().getContent());
      }

      // Switch to the correct implementation, using the builder and fluent api.
      TestRequest otherTestRequest = new TestRequest.Builder()
            .serviceId("Pastry orders API:0.1.0")
            .runnerType(TestRunnerType.ASYNC_API_SCHEMA)
            .testEndpoint("mqtt://hivemq:1883/pastry/orders")
            .timeout(Duration.ofSeconds(4))
            .build();

      // Second test should succeed without validation failure messages.
      testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(otherTestRequest);
      await().pollDelay(2, TimeUnit.SECONDS).untilAsserted(() -> assertTrue(true));

      publishMessages(hivemq, "pastry/orders", goodMessage);

      try {
         testResult = testResultFuture.get();
      } catch (Exception e) {
         fail("Got an exception while waiting for test completion", e);
      }

      assertTrue(testResult.isSuccess());
      assertEquals("mqtt://hivemq:1883/pastry/orders", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      assertNull(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
   }

   private void publishMessages(HiveMQContainer hivemq, String topic, String message) {
      final Mqtt3BlockingClient client = Mqtt3Client.builder()
            .serverHost(hivemq.getHost())
            .serverPort(hivemq.getMqttPort())
            .buildBlocking();
      try {
         client.connect();
         for (int i = 0; i < 5; i++) {
            client.publishWith().topic(topic).payload(message.getBytes(StandardCharsets.UTF_8)).send();
            await().pollDelay(500, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(true));
         }
      } catch (Exception e) {
         fail("Exception while connecting to MQTT broker", e);
      } finally {
         client.disconnect();
      }
   }

   private void testMicrocksAsyncMQTTMocking(MicrocksContainersEnsemble ensemble, HiveMQContainer hivemq) {
      // PastryordersAPI-0.1.0-pastry/orders
      String mqttTopic = ensemble.getAsyncMinionContainer().getMQTTMockTopic("Pastry orders API", "0.1.0", "SUBSCRIBE pastry/orders");
      String expectedMessage = "{\"id\":\"4dab240d-7847-4e25-8ef3-1530687650c8\",\"customerId\":\"fe1088b3-9f30-4dc1-a93d-7b74f0a072b9\",\"status\":\"VALIDATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      // Initialize MQTT consumer to receive 1 message.
      final Mqtt3BlockingClient client = Mqtt3Client.builder()
            .serverHost(hivemq.getHost())
            .serverPort(hivemq.getMqttPort())
            .buildBlocking();

      AtomicReference<String> message = new AtomicReference<>();

      try {
         client.connect();
         client.toAsync().subscribeWith()
               .topicFilter(mqttTopic)
               .callback(publish -> {
                  message.set(new String(publish.getPayloadAsBytes(), StandardCharsets.UTF_8));
               })
               .send();

         Thread.sleep(4000L);
      } catch (Exception e) {
         fail("Exception while connecting to MQTT broker", e);
      } finally {
         client.disconnect();
      }

      // Compare messages.
      assertNotNull(message.get());
      assertTrue(message.get().length() > 1);
      assertEquals(expectedMessage, message.get());
   }
}
