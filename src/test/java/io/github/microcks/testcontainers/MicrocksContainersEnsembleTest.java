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
import io.github.microcks.testcontainers.connection.KafkaConnection;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;
import io.github.microcks.testcontainers.model.TestStepResult;

import io.restassured.RestAssured;
import io.restassured.response.Response;
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
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is a test case for MicrocksContainersEnsemble class.
 * @author laurent
 */
public class MicrocksContainersEnsembleTest {

   private static final String IMAGE = "quay.io/microcks/microcks-uber:1.8.0";

   private static final DockerImageName BAD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:02");
   private static final DockerImageName GOOD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:03");

   private static final DockerImageName BAD_PASTRY_ASYNC_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo-async:01");
   private static final DockerImageName GOOD_PASTRY_ASYNC_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo-async:02");

   @Test
   public void testMockingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(IMAGE)
                  .withMainArtifacts("apipastries-openapi.yaml")
                  .withSecondaryArtifacts("apipastries-postman-collection.json")
                  .withAccessToHost(true);
      ) {
         ensemble.start();
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         testMicrocksMockingFunctionality(ensemble.getMicrocksContainer());
      }
   }

   @Test
   public void testPostmanContractTestingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(IMAGE).withPostman();

            GenericContainer<?> badImpl = new GenericContainer<>(BAD_PASTRY_IMAGE)
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("bad-impl")
                  .waitingFor(Wait.forLogMessage(".*Example app listening on port 3002.*", 1));
            GenericContainer<?> goodImpl = new GenericContainer<>(GOOD_PASTRY_IMAGE)
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("good-impl")
                  .waitingFor(Wait.forLogMessage(".*Example app listening on port 3003.*", 1));
      ) {
         ensemble.start();
         badImpl.start();
         goodImpl.start();
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         ensemble.getMicrocksContainer().importAsMainArtifact(new File("target/test-classes/apipastries-openapi.yaml"));
         ensemble.getMicrocksContainer().importAsSecondaryArtifact(new File("target/test-classes/apipastries-postman-collection.json"));
         testMicrocksContractTestingFunctionality(ensemble.getMicrocksContainer(), badImpl, goodImpl);
      }
   }

   @Test
   public void testAsyncFeatureMockingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble("quay.io/microcks/microcks-uber:nightly")
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature();
      ) {
         ensemble.start();
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         testMicrocksAsyncMockingFunctionality(ensemble);
      }
   }

   @Test
   public void testAsyncFeatureKafkaMockingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble("quay.io/microcks/microcks-uber:nightly")
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature()
                  .withKafkaConnection(new KafkaConnection("redpanda:19092"));

            RedpandaContainer redpanda = new RedpandaContainer(DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v23.1.7"))
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("redpanda")
                  .withListener(() -> "redpanda:19092");
      ) {
         redpanda.start();
         ensemble.start();
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         testMicrocksAsyncKafkaMockingFunctionality(ensemble, redpanda);
      }
   }

   @Test
   public void testAsyncFeatureAmazonSQSMockingFunctionality() throws Exception {
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

         ensemble =  new MicrocksContainersEnsemble(network, "quay.io/microcks/microcks-uber:nightly")
               .withMainArtifacts("pastry-orders-asyncapi.yml")
               .withAsyncFeature()
               .withAmazonSQSConnection(new AmazonServiceConnection(localstack.getRegion(),
                     localstack.getAccessKey(),
                     localstack.getSecretKey(),
                     "http://localstack:" + localstack.getExposedPorts().get(0)));
         ensemble.start();
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         testMicrocksAsyncAmazonSQSMockingFunctionality(ensemble, localstack);
      } finally {
         localstack.stop();
         ensemble.stop();
      }
   }

   @Test
   public void testAsyncFeatureTestingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble("quay.io/microcks/microcks-uber:nightly")
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature();

            GenericContainer<?> badImpl = new GenericContainer<>(BAD_PASTRY_ASYNC_IMAGE)
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("bad-impl")
                  .waitingFor(Wait.forLogMessage(".*Starting WebSocket server on ws://localhost:4001/websocket.*", 1));
            GenericContainer<?> goodImpl = new GenericContainer<>(GOOD_PASTRY_ASYNC_IMAGE)
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("good-impl")
                  .waitingFor(Wait.forLogMessage(".*Starting WebSocket server on ws://localhost:4002/websocket.*", 1));
      ) {
         ensemble.start();
         ensemble.getAsyncMinionContainer().followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("MINION")));
         badImpl.start();
         goodImpl.start();
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         testMicrocksAsyncContractTestingFunctionality(ensemble);
      }
   }

   @Test
   public void testAsyncFeatureKafkaTestingFunctionality() throws Exception {
      try (
            MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble("quay.io/microcks/microcks-uber:nightly")
                  .withMainArtifacts("pastry-orders-asyncapi.yml")
                  .withAsyncFeature();

            RedpandaContainer redpanda = new RedpandaContainer(DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v23.1.7"))
                  .withNetwork(ensemble.getNetwork())
                  .withNetworkAliases("redpanda")
                  .withListener(() -> "redpanda:19092");
      ) {
         redpanda.start();
         ensemble.start();
         ensemble.getAsyncMinionContainer().followOutput(new Slf4jLogConsumer(LoggerFactory.getLogger("MINION")));
         testMicrocksConfigRetrieval(ensemble.getMicrocksContainer().getHttpEndpoint());

         testMicrocksAsyncKafkaContractTestingFunctionality(ensemble, redpanda);
      }
   }

   private void testMicrocksConfigRetrieval(String endpointUrl) {
      Response keycloakConfig = RestAssured.given().when()
            .get(endpointUrl + "/api/keycloak/config")
            .thenReturn();

      assertEquals(200, keycloakConfig.getStatusCode());
   }

   private void testMicrocksMockingFunctionality(MicrocksContainer microcks) {
      String baseApiUrl = microcks.getRestMockEndpoint("API Pastries", "0.0.1");

      // Check that mock from main/primary artifact has been loaded.
      Response millefeuille = RestAssured.given().when()
            .get(baseApiUrl + "/pastries/Millefeuille")
            .thenReturn();

      assertEquals(200, millefeuille.getStatusCode());
      assertEquals("Millefeuille", millefeuille.jsonPath().get("name"));
      //millefeuille.getBody().prettyPrint();

      // Check that mock from secondary artifact has been loaded.
      Response eclairChocolat = RestAssured.given().when()
            .get(baseApiUrl + "/pastries/Eclair Chocolat")
            .thenReturn();

      assertEquals(200, eclairChocolat.getStatusCode());
      assertEquals("Eclair Chocolat", eclairChocolat.jsonPath().get("name"));
      //eclairChocolat.getBody().prettyPrint();
   }

   private void testMicrocksContractTestingFunctionality(MicrocksContainer microcks, GenericContainer badImpl, GenericContainer goodImpl) throws Exception {
      // Produce a new test request.
      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("API Pastries:0.0.1");
      testRequest.setRunnerType(TestRunnerType.POSTMAN.name());
      testRequest.setTestEndpoint("http://bad-impl:3002");
      testRequest.setTimeout(5000l);

      // First test should fail with validation failure messages.
      TestResult testResult = microcks.testEndpoint(testRequest);

      /*
      System.err.println(microcks.getLogs());
      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
       */

      assertFalse(testResult.isSuccess());
      assertEquals("http://bad-impl:3002", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      // Postman runner stop at first failure so there's just 1 testStepResult per testCaseResult
      assertEquals(1, testResult.getTestCaseResults().get(0).getTestStepResults().size());
      // Order is not deterministic so it could be a matter of invalid size, invalid name or invalid price.
      assertTrue(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("Valid size in response pastries")
            || testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("Valid name in response pastry")
            || testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("Valid price in response pastry"));

      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("API Pastries:0.0.1")
            .runnerType(TestRunnerType.POSTMAN.name())
            .testEndpoint("http://good-impl:3003")
            .timeout(5000L)
            .build();

      testResult = microcks.testEndpoint(otherTestRequestDTO);

      /*
      mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
       */

      assertTrue(testResult.isSuccess());
      assertEquals("http://good-impl:3003", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      assertNull(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
   }

   private void testMicrocksAsyncMockingFunctionality(MicrocksContainersEnsemble ensemble) {
      String wsEndpoint = ensemble.getAsyncMinionContainer().getWSMockEndpoint("Pastry orders API", "0.1.0", "SUBSCRIBE pastry/orders");
      String expectedMessage = "{\"id\":\"4dab240d-7847-4e25-8ef3-1530687650c8\",\"customerId\":\"fe1088b3-9f30-4dc1-a93d-7b74f0a072b9\",\"status\":\"VALIDATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      List<String> messages = new ArrayList<>();
      try {
         // Open a WebSocket client.
         WebSocketClient wsClient = new WebSocketClient(new URI(wsEndpoint), new Draft_6455()) {
            @Override
            public void onMessage(String message) {
               messages.add(message);
            }
            @Override
            public void onOpen(ServerHandshake handshake) {
            }
            @Override
            public void onClose(int code, String reason, boolean remote) {
            }
            @Override
            public void onError(Exception e) {
            }
         };
         wsClient.connect();

         // Wait 7 seconds for messages from Async Minion WebSocket to get at least 2 messages.
         await().pollDelay(7, TimeUnit.SECONDS)
               .untilAsserted(() -> assertTrue(true));
      } catch (URISyntaxException ex) {
         fail("URISyntaxException exception: " + ex.getMessage());
      }

      assertFalse(messages.isEmpty());
      for (String message : messages) {
         assertEquals(expectedMessage, message);
      }
   }

   private void testMicrocksAsyncKafkaMockingFunctionality(MicrocksContainersEnsemble ensemble, RedpandaContainer redpanda) {
      // PastryordersAPI-0.1.0-pastry-orders
      String kafkaTopic = ensemble.getAsyncMinionContainer().getKafkaMockTopic("Pastry orders API", "0.1.0", "SUBSCRIBE pastry/orders");
      String expectedMessage = "{\"id\":\"4dab240d-7847-4e25-8ef3-1530687650c8\",\"customerId\":\"fe1088b3-9f30-4dc1-a93d-7b74f0a072b9\",\"status\":\"VALIDATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      // Initialize Kafka producer to receive 1 message.
      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers().replace("PLAINTEXT://", ""));
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

   private void testMicrocksAsyncAmazonSQSMockingFunctionality(MicrocksContainersEnsemble ensemble, LocalStackContainer localstack) {
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

         // Wait a moment to be sure that minion has created the SQS queue.
         await().pollDelay(1500, TimeUnit.MILLISECONDS)
               .untilAsserted(() -> assertTrue(true));

         // Retrieve this queue URL
         ListQueuesRequest listRequest = ListQueuesRequest.builder()
               .queueNamePrefix(sqsQueue).maxResults(1).build();
         ListQueuesResponse listResponse = sqsClient.listQueues(listRequest);
         String queueUrl = listResponse.queueUrls().get(0);

         // Now consumer the mock messages for 4 seconds.
         long startTime = System.currentTimeMillis();
         long timeoutTime = startTime + 4000;
         while (System.currentTimeMillis() - startTime < 4000) {
            // Start polling/receiving messages with a max wait time and a max number.
            ReceiveMessageRequest messageRequest = ReceiveMessageRequest.builder()
                  .queueUrl(queueUrl)
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
   private void testMicrocksAsyncContractTestingFunctionality(MicrocksContainersEnsemble ensemble) throws Exception {
      // Produce a new test request.
      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("Pastry orders API:0.1.0");
      testRequest.setRunnerType(TestRunnerType.ASYNC_API_SCHEMA.name());
      testRequest.setTestEndpoint("ws://bad-impl:4001/websocket");
      testRequest.setTimeout(7000l);

      // First test should fail with validation failure messages.
      // We're using a CompletableFuture here because in real test, you may want
      // to execute your application and send a bunch of messages.
      CompletableFuture<TestResult> testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(testRequest);
      long start = System.currentTimeMillis();

      TestResult testResult = null;
      try {
         testResult = testResultFuture.get();
      } catch (Exception e) {
         fail("Got an exception while waiting for test completion", e);
      }

      // Be sure the completion mechanism actually works and that you get the result only
      // after the timeout.
      long duration = System.currentTimeMillis() - start;
      assertTrue(duration > 7000);

      /*
      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      */

      assertFalse(testResult.isSuccess());
      assertEquals("ws://bad-impl:4001/websocket", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      TestStepResult testStepResult = testResult.getTestCaseResults().get(0).getTestStepResults().get(0);
      assertTrue(testStepResult.getMessage().contains("object has missing required properties ([\"status\"]"));

      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("Pastry orders API:0.1.0")
            .runnerType(TestRunnerType.ASYNC_API_SCHEMA.name())
            .testEndpoint("ws://good-impl:4002/websocket")
            .timeout(7000L)
            .build();

      try {
         testResult = ensemble.getMicrocksContainer().testEndpointAsync(otherTestRequestDTO).get();
      } catch (Exception e) {
         fail("Got an exception while waiting for test completion", e);
      }

      /*
      mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      */

      assertTrue(testResult.isSuccess());
      assertEquals("ws://good-impl:4002/websocket", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      assertNull(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
   }

   private void testMicrocksAsyncKafkaContractTestingFunctionality(MicrocksContainersEnsemble ensemble, RedpandaContainer redpanda) throws Exception {
      // Bad message has no status, good message has one.
      String badMessage = "{\"id\":\"abcd\",\"customerId\":\"efgh\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";
      String goodMessage = "{\"id\":\"abcd\",\"customerId\":\"efgh\",\"status\":\"CREATED\",\"productQuantities\":[{\"quantity\":2,\"pastryName\":\"Croissant\"},{\"quantity\":1,\"pastryName\":\"Millefeuille\"}]}";

      // Produce a new test request.
      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("Pastry orders API:0.1.0");
      testRequest.setRunnerType(TestRunnerType.ASYNC_API_SCHEMA.name());
      testRequest.setTestEndpoint("kafka://redpanda:19092/pastry-orders"); //?startOffset=0");
      testRequest.setTimeout(5000l);

      // First test should fail with validation failure messages.
      CompletableFuture<TestResult> testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(testRequest);

      // Initialize Kafka producer and send 4 messages.
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, redpanda.getBootstrapServers().replace("PLAINTEXT://", ""));
      props.put(ProducerConfig.CLIENT_ID_CONFIG, "random-" + System.currentTimeMillis());
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

      try {
         // Send 4 messages on Kafka.
         try (final Producer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i=0; i<5; i++) {
               ProducerRecord<String, String> record = new ProducerRecord<>("pastry-orders",
                     String.valueOf(System.currentTimeMillis()), badMessage);
               System.err.println("Sending bad message " + i + " on Kafka broker");
               producer.send(record);
               producer.flush();
               await().pollDelay(1000, TimeUnit.MILLISECONDS)
                     .untilAsserted(() -> assertTrue(true));
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
      assertEquals("kafka://redpanda:19092/pastry-orders", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      TestStepResult testStepResult = testResult.getTestCaseResults().get(0).getTestStepResults().get(0);
      assertTrue(testStepResult.getMessage().contains("object has missing required properties ([\"status\"]"));

      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("Pastry orders API:0.1.0")
            .runnerType(TestRunnerType.ASYNC_API_SCHEMA.name())
            .testEndpoint("kafka://redpanda:19092/pastry-orders")
            .timeout(6000L)
            .build();

      // Second test should succeed without validation failure messages.
      testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(otherTestRequestDTO);

      try {
         // Send 4 messages on Kafka.
         try (final Producer<String, String> producer = new KafkaProducer<>(props)) {
            for (int i=0; i<5; i++) {
               ProducerRecord<String, String> record = new ProducerRecord<>("pastry-orders",
                     String.valueOf(System.currentTimeMillis()), goodMessage);
               System.err.println("Sending good message " + i + " on Kafka broker");
               producer.send(record);
               producer.flush();
               await().pollDelay(1000, TimeUnit.MILLISECONDS)
                     .untilAsserted(() -> assertTrue(true));
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
      assertEquals("kafka://redpanda:19092/pastry-orders", testResult.getTestedEndpoint());

      // Ensure we had at least grab one message.
      assertFalse(testResult.getTestCaseResults().get(0).getTestStepResults().isEmpty());
      assertNull(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
   }
}
