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
import io.github.microcks.testcontainers.model.Secret;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;
import io.github.microcks.testcontainers.model.TestStepResult;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * Microcks asynchronous minion Amazon SQS functionalities.
 * @author laurent
 */
class MicrocksContainersEnsembleAmazonTest {

   @ParameterizedTest(name = "Mocking with {0} and {1})")
   @CsvSource({
         TestConstants.LATEST_IMAGE  + "," + TestConstants.ASYNC_LATEST_IMAGE,
         TestConstants.LATEST_NATIVE_IMAGE  + "," + TestConstants.ASYNC_LATEST_NATIVE_IMAGE,
         TestConstants.NIGHTLY_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_IMAGE,
         TestConstants.NIGHTLY_NATIVE_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_NATIVE_IMAGE,
   })
   void testAsyncFeatureAmazonSQSMockingFunctionality(String microcksImage, String asyncMinionImage) {
      Network network = null;
      LocalStackContainer localstack = null;
      MicrocksContainersEnsemble ensemble = null;
      try {
         network = Network.newNetwork();
         localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
               .withNetwork(network)
               .withNetworkAliases("localstack")
               .withServices(LocalStackContainer.Service.SQS);
         localstack.start();

         ensemble = new MicrocksContainersEnsemble(network, microcksImage)
               .withMainArtifacts("pastry-orders-asyncapi.yml")
               .withAsyncFeature(asyncMinionImage)
               .withAmazonSQSConnection(new AmazonServiceConnection(localstack.getRegion(),
                     localstack.getAccessKey(),
                     localstack.getSecretKey(),
                     "http://localstack:" + localstack.getExposedPorts().get(0)));
         ensemble.start();

         testMicrocksAsyncAmazonSQSMocking(ensemble, localstack);
      } finally {
         localstack.stop();
         ensemble.stop();
         network.close();
      }
   }

   @ParameterizedTest(name = "Testing with {0} and {1})")
   @CsvSource({
         TestConstants.LATEST_IMAGE  + "," + TestConstants.ASYNC_LATEST_IMAGE,
         TestConstants.LATEST_NATIVE_IMAGE  + "," + TestConstants.ASYNC_LATEST_NATIVE_IMAGE,
         TestConstants.NIGHTLY_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_IMAGE,
         TestConstants.NIGHTLY_NATIVE_IMAGE  + "," + TestConstants.ASYNC_NIGHTLY_NATIVE_IMAGE,
   })
   void testAsyncFeatureAmazonSQSTestingFunctionality(String microcksImage, String asyncMinionImage) throws Exception {
      Network network = null;
      LocalStackContainer localstack = null;
      MicrocksContainersEnsemble ensemble = null;
      try {
         network = Network.newNetwork();
         localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
               .withNetwork(network)
               .withNetworkAliases("localstack")
               .withServices(LocalStackContainer.Service.SQS);
         localstack.start();

         ensemble =  new MicrocksContainersEnsemble(network, microcksImage)
               .withMainArtifacts("pastry-orders-asyncapi.yml")
               .withAsyncFeature(asyncMinionImage)
               .withSecret(new Secret.Builder()
                     .name("localstack secret")
                     .username(localstack.getAccessKey())
                     .password(localstack.getSecretKey())
                     .build());
         ensemble.start();

         testMicrocksAsyncAmazonSQSContractTesting(ensemble, localstack);
      } finally {
         localstack.stop();
         ensemble.stop();
         network.close();
      }
   }

   private void testMicrocksAsyncAmazonSQSMocking(MicrocksContainersEnsemble ensemble, LocalStackContainer localstack) {
      // PastryordersAPI-010-pastry-orders
      String sqsQueue = ensemble.getAsyncMinionContainer().getAmazonSQSMockQueue("Pastry orders API", "0.1.0", "SUBSCRIBE pastry/orders");
      String expectedMessage = "{\"id\":\"4dab240d-7847-4e25-8ef3-1530687650c8\",\"customerId\":\"fe1088b3-9f30-4dc1-a93d-7b74f0a072b9\",\"status\":\"VALIDATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      SqsClient sqsClient = null;
      List<String> messages = new ArrayList<>();
      try {
         sqsClient = SqsClient.builder()
               .endpointOverride(localstack.getEndpoint())
               .region(Region.of(localstack.getRegion()))
               .credentialsProvider(StaticCredentialsProvider.create(
                     AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
               ))
               .build();

         // Retrieve this queue URL
         ListQueuesRequest listRequest = ListQueuesRequest.builder()
               .queueNamePrefix(sqsQueue).maxResults(1).build();
         AtomicReference<String> queueUrl = new AtomicReference<>(null);

         // Wait a moment to be sure that minion has created the SQS queue.
         SqsClient finalSqsClient = sqsClient;
         await().atMost(3, TimeUnit.SECONDS)
               .pollDelay(500, TimeUnit.MILLISECONDS)
               .pollDelay(500, TimeUnit.MILLISECONDS)
               .until(() -> {
                  ListQueuesResponse listResponse = finalSqsClient.listQueues(listRequest);
                  if (!listResponse.queueUrls().isEmpty()) {
                     queueUrl.set(listResponse.queueUrls().get(0));
                     return true;
                  }
                  return false;
               });

         // Now consumer the mock messages for 4 seconds.
         long startTime = System.currentTimeMillis();
         long timeoutTime = startTime + 4000;
         while (System.currentTimeMillis() - startTime < 4000) {
            // Start polling/receiving messages with a max wait time and a max number.
            ReceiveMessageRequest messageRequest = ReceiveMessageRequest.builder()
                  .queueUrl(queueUrl.get())
                  .maxNumberOfMessages(10)
                  .waitTimeSeconds((int) (timeoutTime - System.currentTimeMillis()) / 1000)
                  .build();

            List<Message> receivedMessages = sqsClient.receiveMessage(messageRequest).messages();

            for (Message receivedMessage : receivedMessages) {
               messages.add(receivedMessage.body());
            }
         }
      } catch (Exception e) {
         fail("Exception while connecting to Localstack SQS queue", e);
      } finally {
         sqsClient.close();
      }

      // Check consumed messages.
      assertFalse(messages.isEmpty());
      for (String message : messages) {
         assertEquals(expectedMessage, message);
      }
   }

   private void testMicrocksAsyncAmazonSQSContractTesting(MicrocksContainersEnsemble ensemble, LocalStackContainer localstack) throws Exception {
      // Bad message has no status, good message has one.
      String badMessage = "{\"id\":\"abcd\",\"customerId\":\"efgh\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";
      String goodMessage = "{\"id\":\"abcd\",\"customerId\":\"efgh\",\"status\":\"CREATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      // Produce a new test request.
      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("Pastry orders API:0.1.0");
      testRequest.setRunnerType(TestRunnerType.ASYNC_API_SCHEMA.name());
      testRequest.setTestEndpoint("sqs://" + localstack.getRegion() + "/pastry-orders?overrideUrl=http://localstack:" + localstack.getExposedPorts().get(0));
      testRequest.setSecretName("localstack secret");
      testRequest.setTimeout(4000L);

      String queueUrl = null;
      SqsClient sqsClient = null;
      CompletableFuture<TestResult> testResultFuture = null;
      try {
         sqsClient = SqsClient.builder()
               .endpointOverride(localstack.getEndpoint())
               .region(Region.of(localstack.getRegion()))
               .credentialsProvider(StaticCredentialsProvider.create(
                     AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
               ))
               .build();

         CreateQueueRequest createQueueRequest = CreateQueueRequest.builder().queueName("pastry-orders").build();
         queueUrl = sqsClient.createQueue(createQueueRequest).queueUrl();

         // First test should fail with validation failure messages.
         testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(testRequest);

         final String finalQueueUrl = queueUrl;
         for (int i=0; i<5; i++) {
            sqsClient.sendMessage(mr -> mr.queueUrl(finalQueueUrl)
                  .messageBody(badMessage)
                  .build());
            System.err.println("Sending bad message " + i + " on SQS queue");
            await().pollDelay(500, TimeUnit.MILLISECONDS)
                  .untilAsserted(() -> assertTrue(true));
         }
      } catch (Exception e) {
         sqsClient.close();
         fail("Exception while connecting to SQS queue", e);
      }

      TestResult testResult = null;
      try {
         testResult = testResultFuture.get();
      } catch (Exception e) {
         sqsClient.close();
         fail("Got an exception while waiting for test completion", e);
      }

      /*
      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      */

      assertFalse(testResult.isSuccess());
      assertEquals("sqs://" + localstack.getRegion() + "/pastry-orders?overrideUrl=http://localstack:" + localstack.getExposedPorts().get(0), testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      TestStepResult testStepResult = testResult.getTestCaseResults().get(0).getTestStepResults().get(0);
      assertTrue(testStepResult.getMessage().contains("required property 'status' not found"));


      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("Pastry orders API:0.1.0")
            .runnerType(TestRunnerType.ASYNC_API_SCHEMA.name())
            .testEndpoint("sqs://" + localstack.getRegion() + "/pastry-orders?overrideUrl=http://localstack:" + localstack.getExposedPorts().get(0))
            .secretName("localstack secret")
            .timeout(4000L)
            .build();

      testResultFuture = null;
      try {
         // Second test should succeed without validation failure messages.
         testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(otherTestRequestDTO);

         final String finalQueueUrl = queueUrl;
         for (int i=0; i<5; i++) {
            sqsClient.sendMessage(mr -> mr.queueUrl(finalQueueUrl)
                  .messageBody(goodMessage)
                  .build());
            System.err.println("Sending good message " + i + " on SQS queue");
            await().pollDelay(500, TimeUnit.MILLISECONDS)
                  .untilAsserted(() -> assertTrue(true));
         }
      } catch (Exception e) {
         sqsClient.close();
         fail("Exception while connecting to SQS queue", e);
      }

      try {
         testResult = testResultFuture.get();
      } catch (Exception e) {
         sqsClient.close();
         fail("Got an exception while waiting for test completion", e);
      }
      // We no longer need the SQS client.
      sqsClient.close();

      /*
      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      */

      assertTrue(testResult.isSuccess());
      assertEquals("sqs://" + localstack.getRegion() + "/pastry-orders?overrideUrl=http://localstack:" + localstack.getExposedPorts().get(0), testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      assertNull(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
   }
}
