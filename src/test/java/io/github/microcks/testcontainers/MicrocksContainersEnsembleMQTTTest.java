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

import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.hivemq.HiveMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
