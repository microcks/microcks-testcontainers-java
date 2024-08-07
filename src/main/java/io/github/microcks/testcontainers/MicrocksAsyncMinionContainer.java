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

import io.github.microcks.testcontainers.connection.AmazonServiceConnection;
import io.github.microcks.testcontainers.connection.GenericConnection;
import io.github.microcks.testcontainers.connection.KafkaConnection;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers implementation for Async Minion container. Instances of this class are not meant to
 * be created directly but through a {@code MicrocksContainersEnsemble}.
 * @author laurent
 */
public class MicrocksAsyncMinionContainer extends GenericContainer<MicrocksAsyncMinionContainer> {

   private static final String MICROCKS_ASYNC_MINION_FULL_IMAGE_NAME = "quay.io/microcks/microcks-uber-async-minion";
   private static final DockerImageName MICROCKS_ASYNC_MINION_IMAGE = DockerImageName.parse(MICROCKS_ASYNC_MINION_FULL_IMAGE_NAME);

   private static final String ASYNC_PROTOCOLS_ENV_VAR = "ASYNC_PROTOCOLS";
   private static final String DESTINATION_PATTERN = "%s-%s-%s";

   public static final int MICROCKS_ASYNC_MINION_HTTP_PORT = 8081;

   private String extraProtocols = "";


   /**
    * Build a new MicrocksAsyncMinionContainer with its container image name as string. This image must
    * be compatible with quay.io/microcks/microcks-uber-async-minion image.
    * @param network The network to attach this container to.
    * @param image The name (with tag/version) of Microcks Async Minion Uber distribution to use.
    * @param microcks The microcks container this minion will be bound to.
    */
   public MicrocksAsyncMinionContainer(Network network, String image, MicrocksContainer microcks) {
      this(network, DockerImageName.parse(image), microcks);
   }

   /**
    * Build a new MicrocksAsyncMinionContainer with its container image name as string. This image must
    * be compatible with quay.io/microcks/microcks-uber-async-minion image.
    * @param network The network to attach this container to.
    * @param imageName The name of Microcks Async Minion Uber distribution to use.
    * @param microcks The microcks container this minion will be bound to.
    */
   public MicrocksAsyncMinionContainer(Network network, DockerImageName imageName, MicrocksContainer microcks) {
      super(imageName);
      imageName.assertCompatibleWith(MICROCKS_ASYNC_MINION_IMAGE);

      withNetwork(network);
      withNetworkAliases("microcks-async-minion");
      withEnv("MICROCKS_HOST_PORT", "microcks:" + MicrocksContainer.MICROCKS_HTTP_PORT);
      withExposedPorts(MICROCKS_ASYNC_MINION_HTTP_PORT);

      waitingFor(Wait.forLogMessage(".*Profile prod activated\\..*", 1));
      dependsOn(microcks);
   }

   /**
    * Build a new MicrocksAsyncMinionContainer with its container image name as string. This image must
    * be compatible with quay.io/microcks/microcks-uber-async-minion image.
    * @param network The network to attach this container to.
    * @param imageName The name of Microcks Async Minion Uber distribution to use.
    * @param microcksHost The microcks container hostname this minion will be bound to.
    */
   public MicrocksAsyncMinionContainer(Network network, DockerImageName imageName, String microcksHost) {
      super(imageName);
      imageName.assertCompatibleWith(MICROCKS_ASYNC_MINION_IMAGE);

      withNetwork(network);
      withNetworkAliases("microcks-async-minion");
      withEnv("MICROCKS_HOST_PORT", microcksHost + ":" + MicrocksContainer.MICROCKS_HTTP_PORT);
      withExposedPorts(MICROCKS_ASYNC_MINION_HTTP_PORT);

      waitingFor(Wait.forLogMessage(".*Profile prod activated\\..*", 1));
   }


   /**
    * Connect the MicrocksAsyncMinionContainer to a Kafka server to allow Kafka messages mocking.
    * @param connection Connection details to a Kafka broker.
    * @return self
    */
   public MicrocksAsyncMinionContainer withKafkaConnection(KafkaConnection connection) {
      if (!extraProtocols.contains(",KAFKA")) {
         extraProtocols += ",KAFKA";
      }
      withEnv(ASYNC_PROTOCOLS_ENV_VAR, extraProtocols);
      withEnv("KAFKA_BOOTSTRAP_SERVER", connection.getBootstrapServers());
      return this;
   }

   /**
    * Connect the MicrocksAsyncMinionContainer to a MQTT server to allow MQTT messages mocking.
    * @param connection Connection details to the MQTT server.
    * @return self
    */
   public MicrocksAsyncMinionContainer withMQTTConnection(GenericConnection connection) {
      if (!extraProtocols.contains(",MQTT")) {
         extraProtocols += ",MQTT";
      }
      withEnv(ASYNC_PROTOCOLS_ENV_VAR, extraProtocols);
      withEnv("MQTT_SERVER", connection.getServer());
      withEnv("MQTT_USERNAME", connection.getUsername());
      withEnv("MQTT_PASSWORD", connection.getPassword());
      return this;
   }

   /**
    * Connect the MicrocksAsyncMinionContainer to a RabbitMQ server to allow AMQP messages mocking.
    * @param connection Connection details to the RabbitMQ server.
    * @return self
    */
   public MicrocksAsyncMinionContainer withAMQPConnection(GenericConnection connection) {
      if (!extraProtocols.contains(",AMQP")) {
         extraProtocols += ",AMQP";
      }
      withEnv(ASYNC_PROTOCOLS_ENV_VAR, extraProtocols);
      withEnv("AMQP_SERVER", connection.getServer());
      withEnv("AMQP_USERNAME", connection.getUsername());
      withEnv("AMQP_PASSWORD", connection.getPassword());
      return this;
   }

   /**
    * Connect the MicrocksAsyncMinionContainer to an Amazon SQS service to allow SQS messages mocking.
    * @param connection Connection details to an Amazon SQS service.
    * @return self
    */
   public MicrocksAsyncMinionContainer withAmazonSQSConnection(AmazonServiceConnection connection) {
      if (!extraProtocols.contains(",SQS")) {
         extraProtocols += ",SQS";
      }
      withEnv(ASYNC_PROTOCOLS_ENV_VAR, extraProtocols);
      withEnv("AWS_SQS_REGION", connection.getRegion());
      withEnv("AWS_ACCESS_KEY_ID", connection.getAccessKey());
      withEnv("AWS_SECRET_ACCESS_KEY", connection.getSecretKey());
      if (connection.getEndpointOverride() != null) {
         withEnv("AWS_SQS_ENDPOINT", connection.getEndpointOverride());
      }
      return this;
   }

   /**
    * Connect the MicrocksAsyncMinionContainer to an Amazon SNS service to allow SNS messages mocking.
    * @param connection Connection details to an Amazon SNS service.
    * @return self
    */
   public MicrocksAsyncMinionContainer withAmazonSNSConnection(AmazonServiceConnection connection) {
      if (!extraProtocols.contains(",SNS")) {
         extraProtocols += ",SNS";
      }
      withEnv(ASYNC_PROTOCOLS_ENV_VAR, extraProtocols);
      withEnv("AWS_SNS_REGION", connection.getRegion());
      withEnv("AWS_ACCESS_KEY_ID", connection.getAccessKey());
      withEnv("AWS_SECRET_ACCESS_KEY", connection.getSecretKey());
      if (connection.getEndpointOverride() != null) {
         withEnv("AWS_SNS_ENDPOINT", connection.getEndpointOverride());
      }
      return this;
   }

   /**
    * Get the Http endpoint where Microcks can be accessed (you'd have to append '/api' to access APIs)
    * @return The Http endpoint for talking to container.
    */
   public String getHttpEndpoint() {
      return String.format("http://%s:%s", getHost(), getMappedPort(MICROCKS_ASYNC_MINION_HTTP_PORT));
   }

   /**
    * Get the exposed mock endpoints for a WebSocket Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @param operationName The name of operation to get the endpoint for
    * @return A usable endpoint to interact with Microcks mocks.
    */
   public String getWSMockEndpoint(String service, String version, String operationName) {
      // operationName may start with SUBSCRIBE or PUBLISH.
      if (operationName.contains(" ")) {
         operationName = operationName.split(" ")[1];
      }
      return String.format("ws://%s:%s/api/ws/%s/%s/%s", getHost(), getMappedPort(MICROCKS_ASYNC_MINION_HTTP_PORT),
            service.replace(" ", "+"),
            version.replace(" ", "+"),
            operationName);
   }

   /**
    * Get the exposed mock topic for a Kafka Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @param operationName The name of operation to get the topic for
    * @return A usable topic to interact with Microcks mocks.
    */
   public String getKafkaMockTopic(String service, String version, String operationName) {
      // operationName may start with SUBSCRIBE or PUBLISH.
      if (operationName.contains(" ")) {
         operationName = operationName.split(" ")[1];
      }
      return String.format(DESTINATION_PATTERN,
            service.replace(" ", "").replace("-", ""),
            version,
            operationName.replace("/", "-"));
   }

   /**
    * Get the exposed mock topic for a MQTT Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @param operationName The name of operation to get the topic for
    * @return A usable topic to interact with Microcks mocks.
    */
   public String getMQTTMockTopic(String service, String version, String operationName) {
      // operationName may start with SUBSCRIBE or PUBLISH.
      if (operationName.contains(" ")) {
         operationName = operationName.split(" ")[1];
      }
      return String.format(DESTINATION_PATTERN,
            service.replace(" ", "").replace("-", ""),
            version.replace(" ", ""),
            operationName);
   }

   /**
    * Get the exposed mock destination for a AMQP Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @param operationName The name of operation to get the topic for
    * @return A usable topic to interact with Microcks mocks.
    */
   public String getAMQPMockDestination(String service, String version, String operationName) {
      // operationName may start with SUBSCRIBE or PUBLISH.
      if (operationName.contains(" ")) {
         operationName = operationName.split(" ")[1];
      }
      return String.format(DESTINATION_PATTERN,
            service.replace(" ", "").replace("-", ""),
            version.replace(" ", ""),
            operationName);
   }

   /**
    * Get the exposed mock queue for an Amazon SQS Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @param operationName The name of operation to get the topic for
    * @return A usable queue to interact with Microcks mocks.
    */
   public String getAmazonSQSMockQueue(String service, String version, String operationName) {
      return getAmazonServiceMockDestination(service, version, operationName);
   }

   /**
    * Get the exposed mock topic for an Amazon SNS Service.
    * @param service The name of Service/API
    * @param version The version of Service/API
    * @param operationName The name of operation to get the topic for
    * @return A usable queue to interact with Microcks mocks.
    */
   public String getAmazonSNSMockTopic(String service, String version, String operationName) {
      return getAmazonServiceMockDestination(service, version, operationName);
   }

   private String getAmazonServiceMockDestination(String service, String version, String operationName) {
      // operationName may start with SUBSCRIBE or PUBLISH.
      if (operationName.contains(" ")) {
         operationName = operationName.split(" ")[1];
      }
      return String.format(DESTINATION_PATTERN,
            service.replace(" ", "").replace("-", ""),
            version.replace(".", ""),
            operationName.replace("/", "-"));
   }
}
