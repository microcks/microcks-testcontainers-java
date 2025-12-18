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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * Microcks AMQP async mocking functionality.
 * @author laurent
 */
class MicrocksContainersEnsembleAMQPTest {

   @ParameterizedTest(name = "Mocking with {0} and {1})")
   @CsvSource({
         TestConstants.LATEST_IMAGE  + "," + TestConstants.ASYNC_LATEST_IMAGE,
         TestConstants.LATEST_NATIVE_IMAGE  + "," + TestConstants.ASYNC_LATEST_NATIVE_IMAGE,
         TestConstants.NIGHTLY_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_IMAGE,
         TestConstants.NIGHTLY_NATIVE_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_NATIVE_IMAGE,
   })
   void testAsyncFeatureAMQPMockingFunctionality(String microcksImage, String asyncMinionImage) throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(microcksImage)
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature(asyncMinionImage)
                  .withAMQPConnection(new GenericConnection("rabbitmq:5672", "test", "test"));

            RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.9.13-management-alpine"))
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("rabbitmq");
      ) {
         rabbitmq.start();
         rabbitmq.execInContainer("rabbitmqctl", "add_user", "test", "test");
         rabbitmq.execInContainer("rabbitmqctl", "set_permissions", "-p", "/", "test", ".*", ".*", ".*");

         ensemble.start();

         testMicrocksAsyncAMQPMocking(ensemble, rabbitmq);
      }
   }

   private void testMicrocksAsyncAMQPMocking(MicrocksContainersEnsemble ensemble, RabbitMQContainer rabbitmq) {
      // PastryordersAPI-0.1.0-pastry/orders
      String amqpDestination = ensemble.getAsyncMinionContainer().getAMQPMockDestination("Pastry orders API", "0.1.0", "SUBSCRIBE pastry/orders");
      String expectedMessage = "{\"id\":\"4dab240d-7847-4e25-8ef3-1530687650c8\",\"customerId\":\"fe1088b3-9f30-4dc1-a93d-7b74f0a072b9\",\"status\":\"VALIDATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      // Initialize RabbitMQ consumer to receive 1 message.
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost(rabbitmq.getHost());
      factory.setPort(rabbitmq.getAmqpPort());

      AtomicReference<String> message = new AtomicReference<>();

      try (Connection connection = factory.newConnection()) {
         Channel channel = connection.createChannel();

         channel.exchangeDeclare(amqpDestination, "topic", false);
         String queueName = channel.queueDeclare().getQueue();
         channel.queueBind(queueName, amqpDestination, "#");

         String consumerTag = channel.basicConsume(queueName, false, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
                  throws IOException {
               message.set(new String(body, StandardCharsets.UTF_8));
               channel.basicAck(envelope.getDeliveryTag(), false);
            }
         });

         Thread.sleep(4000L);

         channel.close();
      } catch (Exception e) {
         fail("Exception while connecting to AMQP broker", e);
      }

      // Compare messages.
      assertNotNull(message.get());
      assertTrue(message.get().length() > 1);
      assertEquals(expectedMessage, message.get());
   }
}
