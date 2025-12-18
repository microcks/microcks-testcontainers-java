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

import io.github.microcks.testcontainers.connection.GooglePubSubConnection;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;
import io.github.microcks.testcontainers.model.TestStepResult;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * Microcks Google PubSub async mocking and testing functionalities.
 * @author laurent
 */
class MicrocksContainersEnsembleGooglePubSubTest {

   @ParameterizedTest(name = "Mocking with {0} and {1})")
   @CsvSource({
         TestConstants.NIGHTLY_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_IMAGE,
         TestConstants.NIGHTLY_NATIVE_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_NATIVE_IMAGE,
   })
   void testAsyncFeatureGooglePubSubMocking(String microcksImage, String asyncMinionImage) {
      Network network = null;
      PubSubEmulatorContainer emulator = null;
      MicrocksContainersEnsemble ensemble = null;
      try {
         network = Network.newNetwork();
         emulator = new PubSubEmulatorContainer(DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:549.0.0-emulators"))
               .withNetwork(network)
               .withNetworkAliases("pubsub-emulator");
         emulator.start();

         ensemble = new MicrocksContainersEnsemble(network, microcksImage)
               .withMainArtifacts("pastry-orders-asyncapi.yml")
               .withAsyncFeature(asyncMinionImage)
               .withGooglePubSubConnection(new GooglePubSubConnection("my-custom-project", "pubsub-emulator:8085"));
         ensemble.start();

         testMicrocksAsyncGooglePubSubMocking(ensemble, emulator);
      } finally {
         emulator.stop();
         ensemble.stop();
         network.close();
      }
   }

   @ParameterizedTest(name = "Testing with {0} and {1})")
   @CsvSource({
         TestConstants.NIGHTLY_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_IMAGE,
         TestConstants.NIGHTLY_NATIVE_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_NATIVE_IMAGE,
   })
   void testAsyncFeatureGooglePubSubTesting(String microcksImage, String asyncMinionImage) throws Exception {
      Network network = null;
      PubSubEmulatorContainer emulator = null;
      MicrocksContainersEnsemble ensemble = null;
      try {
         network = Network.newNetwork();
         emulator = new PubSubEmulatorContainer(DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:549.0.0-emulators"))
               .withNetwork(network)
               .withNetworkAliases("pubsub-emulator");
         emulator.start();

         ensemble = new MicrocksContainersEnsemble(network, microcksImage)
               .withMainArtifacts("pastry-orders-asyncapi.yml")
               .withAsyncFeature(asyncMinionImage);
         ensemble.start();

         testMicrocksAsyncGooglePubSubContractTesting(ensemble, emulator);
      } finally {
         emulator.stop();
         ensemble.stop();
         network.close();
      }
   }

   private void testMicrocksAsyncGooglePubSubMocking(MicrocksContainersEnsemble ensemble, PubSubEmulatorContainer emulator) {
      String projectId = "my-custom-project";
      String subscriptionId = "my-subscription-id";

      // PastryordersAPI-010-pastry-orders
      String pubSubTopic = ensemble.getAsyncMinionContainer().getGooglePubSubMockTopic("Pastry orders API", "0.1.0", "SUBSCRIBE pastry/orders");
      String expectedMessage = "{\"id\":\"4dab240d-7847-4e25-8ef3-1530687650c8\",\"customerId\":\"fe1088b3-9f30-4dc1-a93d-7b74f0a072b9\",\"status\":\"VALIDATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      // Prepare to receive messages.
      List<String> messages = new ArrayList<>();

      String hostport = emulator.getEmulatorEndpoint();
      ManagedChannel channel = ManagedChannelBuilder.forTarget(hostport).usePlaintext().build();
      try {
         TransportChannelProvider channelProvider = FixedTransportChannelProvider.create(
               GrpcTransportChannel.create(channel)
         );
         NoCredentialsProvider credentialsProvider = NoCredentialsProvider.create();

         // Wait a moment to be sure that minion has created the PubSub topic.
         Thread.sleep(2500L);

         // Ensure connection is possible and subscription exists.
         SubscriptionAdminSettings subscriptionAdminSettings = SubscriptionAdminSettings.newBuilder()
               .setTransportChannelProvider(channelProvider)
               .setCredentialsProvider(credentialsProvider)
               .build();
         SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(subscriptionAdminSettings);
         SubscriptionName subscriptionName = SubscriptionName.of(projectId, subscriptionId);
         subscriptionAdminClient.createSubscription(
               subscriptionName,
               TopicName.of(projectId, pubSubTopic),
               PushConfig.getDefaultInstance(),
               10
         );

         // Subscribe to PubSub.
         MessageReceiver receiver = (message, consumer) -> {
            messages.add(message.getData().toString(StandardCharsets.UTF_8));
            consumer.ack();
         };

         Subscriber.Builder subBuilder = Subscriber
               .newBuilder(ProjectSubscriptionName.of(subscriptionName.getProject(), subscriptionName.getSubscription()),
                     receiver)
               .setChannelProvider(channelProvider)
               .setCredentialsProvider(credentialsProvider);

         // Create a new subscriber for subscription.
         Subscriber subscriber = subBuilder.build();
         subscriber.startAsync().awaitRunning();

         // Wait and stop async receiver.
         Thread.sleep(4000L);
         subscriber.stopAsync();

      } catch (Exception e) {
         fail("Exception while connecting to Emulator PubSub topic", e);
      } finally {
         channel.shutdown();
      }

      // Check consumed messages.
      assertFalse(messages.isEmpty());
      for (String message : messages) {
         assertEquals(expectedMessage, message);
      }
   }

   private void testMicrocksAsyncGooglePubSubContractTesting(MicrocksContainersEnsemble ensemble, PubSubEmulatorContainer emulator) throws Exception {
      String projectId = "my-custom-project";
      String topicId = "pastry-orders";

      // Bad message has no status, good message has one.
      String badMessage = "{\"id\":\"abcd\",\"customerId\":\"efgh\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";
      String goodMessage = "{\"id\":\"abcd\",\"customerId\":\"efgh\",\"status\":\"CREATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      // Produce a new test request.
      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("Pastry orders API:0.1.0");
      testRequest.setRunnerType(TestRunnerType.ASYNC_API_SCHEMA.name());
      testRequest.setTestEndpoint("googlepubsub://my-custom-project/pastry-orders?emulatorHost=pubsub-emulator:8085");
      testRequest.setSecretName("localstack secret");
      testRequest.setTimeout(5000L);

      Publisher publisher = null;
      CompletableFuture<TestResult> testResultFuture = null;
      String hostport = emulator.getEmulatorEndpoint();
      ManagedChannel channel = ManagedChannelBuilder.forTarget(hostport).usePlaintext().build();
      try {
         TransportChannelProvider channelProvider = FixedTransportChannelProvider.create(
               GrpcTransportChannel.create(channel)
         );
         NoCredentialsProvider credentialsProvider = NoCredentialsProvider.create();

         // Create the topic.
         TopicAdminSettings topicAdminSettings = TopicAdminSettings.newBuilder()
               .setTransportChannelProvider(channelProvider)
               .setCredentialsProvider(credentialsProvider)
               .build();
         try (TopicAdminClient topicAdminClient = TopicAdminClient.create(topicAdminSettings)) {
            TopicName topicName = TopicName.of(projectId, topicId);
            topicAdminClient.createTopic(topicName);
         }

         // Create the publisher.
         publisher = Publisher.newBuilder(TopicName.of(projectId, topicId))
               .setChannelProvider(channelProvider)
               .setCredentialsProvider(credentialsProvider)
               .build();

         // First test should fail with validation failure messages.
         testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(testRequest);

         for (int i=0; i<5; i++) {
            PubsubMessage message = PubsubMessage.newBuilder()
                  .setData(ByteString.copyFromUtf8(badMessage))
                  .build();
            publisher.publish(message);
            System.err.println("Sending bad message " + i + " on Google PubSub topic");
            await().pollDelay(500, TimeUnit.MILLISECONDS)
                  .untilAsserted(() -> assertTrue(true));
         }
      } catch (Exception e) {
         channel.shutdown();
         fail("Exception while connecting to Emulator PubSub topic", e);
      }

      TestResult testResult = null;
      try {
         testResult = testResultFuture.get();
      } catch (Exception e) {
         channel.shutdown();
         fail("Got an exception while waiting for test completion", e);
      }

      assertFalse(testResult.isSuccess());
      assertEquals("googlepubsub://my-custom-project/pastry-orders?emulatorHost=pubsub-emulator:8085", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      TestStepResult testStepResult = testResult.getTestCaseResults().get(0).getTestStepResults().get(0);
      assertTrue(testStepResult.getMessage().contains("required property 'status' not found"));


      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("Pastry orders API:0.1.0")
            .runnerType(TestRunnerType.ASYNC_API_SCHEMA.name())
            .testEndpoint("googlepubsub://my-custom-project/pastry-orders?emulatorHost=pubsub-emulator:8085")
            .secretName("localstack secret")
            .timeout(5000L)
            .build();

      try {
         // Second test should succeed without validation failure messages.
         testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(otherTestRequestDTO);

         for (int i=0; i<5; i++) {
            PubsubMessage message = PubsubMessage.newBuilder()
                  .setData(ByteString.copyFromUtf8(goodMessage))
                  .build();
            publisher.publish(message);
            System.err.println("Sending good message " + i + " on Google PubSub topic");
            await().pollDelay(500, TimeUnit.MILLISECONDS)
                  .untilAsserted(() -> assertTrue(true));
         }
      } catch (Exception e) {
         fail("Exception while connecting to Emulator PubSub topic", e);
      } finally {
         channel.shutdown();
      }

      try {
         testResult = testResultFuture.get();
      } catch (Exception e) {
         channel.shutdown();
         fail("Got an exception while waiting for test completion", e);
      }

      assertTrue(testResult.isSuccess());
      assertEquals("googlepubsub://my-custom-project/pastry-orders?emulatorHost=pubsub-emulator:8085", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      assertNull(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
   }
}
