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

import io.github.microcks.testcontainers.model.Header;
import io.github.microcks.testcontainers.model.OAuth2ClientContext;
import io.github.microcks.testcontainers.model.OAuth2GrantType;
import io.github.microcks.testcontainers.model.RequestResponsePair;
import io.github.microcks.testcontainers.model.Secret;
import io.github.microcks.testcontainers.model.TestRequest;
import io.github.microcks.testcontainers.model.TestResult;
import io.github.microcks.testcontainers.model.TestRunnerType;

import com.fasterxml.jackson.annotation.JsonInclude;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for MicrocksContainer class.
 * @author laurent
 */
public class MicrocksContainerTest {

   private static final String IMAGE = "quay.io/microcks/microcks-uber:1.12.0";
   private static final DockerImageName MICROCKS_IMAGE = DockerImageName.parse(IMAGE);

   private static final DockerImageName BAD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:01");
   private static final DockerImageName GOOD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:02");

   @Test
   public void testMockingFunctionality() throws Exception {
      try (
            MicrocksContainer microcks = new MicrocksContainer(IMAGE)
                  .withSnapshots("microcks-repository.json")
                  .withMainArtifacts("apipastries-openapi.yaml", "sub dir/weather-forecast-openapi.yaml")
                  .withMainRemoteArtifacts("https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastry-openapi.yaml")
                  .withSecondaryArtifacts("apipastries-postman-collection.json");
      ) {
         microcks.start();
         testMicrocksConfigRetrieval(microcks.getHttpEndpoint());
         testAvailableServices(microcks.getHttpEndpoint());

         testMockEndpoints(microcks);
         testMicrocksMockingFunctionality(microcks);
      }
   }

   @Test
   public void testContractTestingFunctionality() throws Exception {
      try (
            Network network = Network.newNetwork();
            MicrocksContainer microcks = new MicrocksContainer(MICROCKS_IMAGE)
                  .withNetwork(network);
            GenericContainer<?> badImpl = new GenericContainer<>(BAD_PASTRY_IMAGE)
                  .withNetwork(network)
                  .withNetworkAliases("bad-impl")
                  .waitingFor(Wait.forLogMessage(".*Example app listening on port 3001.*", 1));
            GenericContainer<?> goodImpl = new GenericContainer<>(GOOD_PASTRY_IMAGE)
                  .withNetwork(network)
                  .withNetworkAliases("good-impl")
                  .waitingFor(Wait.forLogMessage(".*Example app listening on port 3002.*", 1));

      ) {
         microcks.start();
         badImpl.start();
         goodImpl.start();
         testMicrocksConfigRetrieval(microcks.getHttpEndpoint());

         microcks.importAsMainArtifact(new File("target/test-classes/apipastries-openapi.yaml"));
         testMicrocksContractTestingFunctionality(microcks, badImpl, goodImpl);
      }
   }

   @Test
   public void testContractTestingFunctionalityWithOAuth2() throws Exception {
      try (
            Network network = Network.newNetwork();
            MicrocksContainer microcks = new MicrocksContainer(MICROCKS_IMAGE)
                  .withNetwork(network);
            KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.0")
                  .withNetwork(network)
                  .withNetworkAliases("keycloak")
                  .withRealmImportFile("myrealm-realm.json");
            GenericContainer<?> goodImpl = new GenericContainer<>(GOOD_PASTRY_IMAGE)
                  .withNetwork(network)
                  .withNetworkAliases("good-impl")
                  .waitingFor(Wait.forLogMessage(".*Example app listening on port 3002.*", 1));

      ) {
         microcks.start();
         keycloak.start();
         goodImpl.start();
         testMicrocksConfigRetrieval(microcks.getHttpEndpoint());

         microcks.importAsMainArtifact(new File("target/test-classes/apipastries-openapi.yaml"));

         // Issue a TestRequest with OAuth2 context.
         TestRequest testRequest = new TestRequest.Builder()
               .serviceId("API Pastries:0.0.1")
               .runnerType(TestRunnerType.OPEN_API_SCHEMA.name())
               .testEndpoint("http://good-impl:3002")
               .timeout(2000L)
               .oAuth2Context(new OAuth2ClientContext.Builder()
                     .clientId("myrealm-serviceaccount")
                     .clientSecret("ab54d329-e435-41ae-a900-ec6b3fe15c54")
                     .tokenUri("http://keycloak:8080/realms/myrealm/protocol/openid-connect/token")
                     .grantType(OAuth2GrantType.CLIENT_CREDENTIALS)
                     .build())
               .build();

         TestResult testResult = microcks.testEndpoint(testRequest);

         // Check test results.
         assertTrue(testResult.isSuccess());
         assertEquals("http://good-impl:3002", testResult.getTestedEndpoint());
         assertEquals(3, testResult.getTestCaseResults().size());
         assertEquals("", testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());

         // Ensure test has used a valid OAuth2 client.
         assertNotNull(testResult.getAuthorizedClient());
         assertEquals("myrealm-serviceaccount", testResult.getAuthorizedClient().getPrincipalName());
         assertEquals("http://keycloak:8080/realms/myrealm/protocol/openid-connect/token", testResult.getAuthorizedClient().getTokenUri());
         assertEquals("openid profile email", testResult.getAuthorizedClient().getScopes());
      }
   }

   @Test
   public void testSecretCreation() throws Exception {
      try (
            MicrocksContainer microcks = new MicrocksContainer(MICROCKS_IMAGE)
                  .withSecret(new Secret.Builder().name("my-secret").token("abc-123-xyz").tokenHeader("x-microcks").build())
                  .withMainRemoteArtifacts(
                        RemoteArtifact.of("https://raw.githubusercontent.com/microcks/microcks/master/samples/APIPastry-openapi.yaml",
                              "my-secret"));
      ) {
         microcks.start();
         testMicrocksConfigRetrieval(microcks.getHttpEndpoint());

         Response secrets = RestAssured.given().when()
               .get(microcks.getHttpEndpoint() + "/api/secrets")
               .thenReturn();

         assertEquals(200, secrets.getStatusCode());
         assertEquals(1, secrets.jsonPath().getList(".").size());
         assertEquals("my-secret", secrets.jsonPath().get("[0].name"));
         assertEquals("abc-123-xyz", secrets.jsonPath().get("[0].token"));
         assertEquals("x-microcks", secrets.jsonPath().get("[0].tokenHeader"));
      }
   }

   private void testMicrocksConfigRetrieval(String endpointUrl) {
      Response keycloakConfig = RestAssured.given().when()
            .get(endpointUrl + "/api/keycloak/config")
            .thenReturn();

      assertEquals(200, keycloakConfig.getStatusCode());
   }

   private void testAvailableServices(String endpointUrl) {
      Response services = RestAssured.given().when()
            .get(endpointUrl + "/api/services")
            .thenReturn();

      assertEquals(200, services.getStatusCode());
      assertEquals(7, services.jsonPath().getList(".").size());

      List<String> names = services.jsonPath().getList(".").stream()
            .map(service -> ((Map)service).get("name").toString())
            .collect(Collectors.toList());

      // Check the seven loaded services are correct.
      assertTrue(names.contains("API Pastries"));
      assertTrue(names.contains("WeatherForecast API"));
      assertTrue(names.contains("API Pastry - 2.0"));
      assertTrue(names.contains("HelloService Mock"));
      assertTrue(names.contains("Movie Graph API"));
      assertTrue(names.contains("Petstore API"));
      assertTrue(names.contains("io.github.microcks.grpc.hello.v1.HelloService"));
   }

   private void testMockEndpoints(MicrocksContainer microcks) {
      String baseWsUrl = microcks.getSoapMockEndpoint("Pastries Service", "1.0");
      assertEquals(microcks.getHttpEndpoint() + "/soap/Pastries Service/1.0", baseWsUrl);

      String baseApiUrl = microcks.getRestMockEndpoint("API Pastries", "0.0.1");
      assertEquals(microcks.getHttpEndpoint() + "/rest/API Pastries/0.0.1", baseApiUrl);

      String baseGraphUrl = microcks.getGraphQLMockEndpoint("Pastries Graph", "1");
      assertEquals(microcks.getHttpEndpoint() + "/graphql/Pastries Graph/1", baseGraphUrl);

      String baseGrpcUrl = microcks.getGrpcMockEndpoint();
      assertEquals("grpc://" + microcks.getHost() + ":" + microcks.getMappedPort(MicrocksContainer.MICROCKS_GRPC_PORT), baseGrpcUrl);

      String baseWsPath = microcks.getSoapMockEndpointPath("Pastries Service", "1.0");
      assertEquals("/soap/Pastries Service/1.0", baseWsPath);

      String baseApiPath = microcks.getRestMockEndpointPath("API Pastries", "0.0.1");
      assertEquals("/rest/API Pastries/0.0.1", baseApiPath);

      String baseGraphPath = microcks.getGraphQLMockEndpointPath("Pastries Graph", "1");
      assertEquals("/graphql/Pastries Graph/1", baseGraphPath);

      String validWsUrl = microcks.getValidatingSoapMockEndpoint("Pastries Service", "1.0");
      assertEquals(microcks.getHttpEndpoint() + "/soap/Pastries Service/1.0?validate=true", validWsUrl);

      String validApiUrl = microcks.getValidatingRestMockEndpoint("API Pastries", "0.0.1");
      assertEquals(microcks.getHttpEndpoint() + "/rest-valid/API Pastries/0.0.1", validApiUrl);

      String validWsPath = microcks.getValidatingSoapMockEndpointPath("Pastries Service", "1.0");
      assertEquals("/soap/Pastries Service/1.0?validate=true", validWsPath);

      String validApiPath = microcks.getValidatingRestMockEndpointPath("API Pastries", "0.0.1");
      assertEquals("/rest-valid/API Pastries/0.0.1", validApiPath);
   }

   private void testMicrocksMockingFunctionality(MicrocksContainer microcks) {
      String baseApiUrl = microcks.getRestMockEndpoint("API Pastries", "0.0.1");

      assertFalse(microcks.verify("API Pastries", "0.0.1"));
      assertEquals(0, microcks.getServiceInvocationsCount("API Pastries", "0.0.1"));

      // Check that mock from main/primary artifact has been loaded.
      Response millefeuille = RestAssured.given().when()
            .get(baseApiUrl + "/pastries/Millefeuille")
            .thenReturn();

      assertEquals(200, millefeuille.getStatusCode());
      assertEquals("Millefeuille", millefeuille.jsonPath().get("name"));
      //millefeuille.getBody().prettyPrint();

      testMicrocksInvocationsCheckingFunctionality(microcks, "API Pastries", "0.0.1", 1L);

      // Check that mock from secondary artifact has been loaded.
      Response eclairChocolat = RestAssured.given().when()
            .get(baseApiUrl + "/pastries/Eclair Chocolat")
            .thenReturn();

      assertEquals(200, eclairChocolat.getStatusCode());
      assertEquals("Eclair Chocolat", eclairChocolat.jsonPath().get("name"));
      //eclairChocolat.getBody().prettyPrint();

      testMicrocksInvocationsCheckingFunctionality(microcks, "API Pastries", "0.0.1", 2L);

      // Check that mock from main/primary remote artifact has been loaded.
      baseApiUrl = microcks.getRestMockEndpoint("API Pastry - 2.0", "2.0.0");

      millefeuille = RestAssured.given().when()
            .get(baseApiUrl + "/pastry/Millefeuille")
            .thenReturn();

      assertEquals(200, millefeuille.getStatusCode());
      assertEquals("Millefeuille", millefeuille.jsonPath().get("name"));
      //millefeuille.getBody().prettyPrint();

      testMicrocksInvocationsCheckingFunctionality(microcks, "API Pastry - 2.0", "2.0.0", 1L);
   }

   private static void testMicrocksInvocationsCheckingFunctionality(MicrocksContainer microcks, String serviceName, String serviceVersion, Long expectedOccurences) {
      Date invocationDay = new Date();
      Date incorrectInvocationDay = DateUtils.addDays(invocationDay, 1);

      assertTrue(microcks.verify(serviceName, serviceVersion));
      assertTrue(microcks.verify(serviceName, serviceVersion, (Date) null));
      assertTrue(microcks.verify(serviceName, serviceVersion, invocationDay));
      assertTrue(MicrocksContainer.verify(microcks.getHttpEndpoint(), serviceName, serviceVersion, invocationDay));
      assertFalse(microcks.verify(serviceName, serviceVersion, incorrectInvocationDay));
      assertFalse(MicrocksContainer.verify(microcks.getHttpEndpoint(), serviceName, serviceVersion, incorrectInvocationDay));

      assertEquals(expectedOccurences, microcks.getServiceInvocationsCount(serviceName, serviceVersion));
      assertEquals(expectedOccurences, microcks.getServiceInvocationsCount(serviceName, serviceVersion, (Date) null));
      assertEquals(expectedOccurences, microcks.getServiceInvocationsCount(serviceName, serviceVersion, invocationDay));
      assertEquals(expectedOccurences, MicrocksContainer.getServiceInvocationsCount(microcks.getHttpEndpoint(), serviceName, serviceVersion, invocationDay));
      assertEquals(0, microcks.getServiceInvocationsCount(serviceName, serviceVersion, incorrectInvocationDay));
      assertEquals(0, MicrocksContainer.getServiceInvocationsCount(microcks.getHttpEndpoint(), serviceName, serviceVersion, incorrectInvocationDay));
   }

   private void testMicrocksContractTestingFunctionality(MicrocksContainer microcks, GenericContainer badImpl, GenericContainer goodImpl) throws Exception {
      // Produce a new test request.
      TestRequest testRequest = new TestRequest();
      testRequest.setServiceId("API Pastries:0.0.1");
      testRequest.setRunnerType(TestRunnerType.OPEN_API_SCHEMA.name());
      testRequest.setTestEndpoint("http://bad-impl:3001");
      testRequest.setTimeout(2000l);

      // First test should fail with validation failure messages.
      TestResult testResult = microcks.testEndpoint(testRequest);

      System.err.println(microcks.getLogs());
      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
      System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));

      assertFalse(testResult.isSuccess());
      assertEquals("http://bad-impl:3001", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      assertTrue(testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage().contains("required property 'status' not found"));

      // Retrieve messages for the failing test case.
      List<RequestResponsePair> messages = microcks.getMessagesForTestCase(testResult, "GET /pastries");
      assertEquals(3, messages.size());
      for (RequestResponsePair message : messages) {
         // Check there is some response.
         assertNotNull(message.getRequest());
         assertNotNull(message.getResponse());
         assertNotNull(message.getResponse().getContent());
         // Check these are the correct requests.
         assertNotNull(message.getRequest().getQueryParameters());
         assertEquals(1, message.getRequest().getQueryParameters().size());
         assertEquals("size", message.getRequest().getQueryParameters().get(0).getName());
      }

      // Switch endpoint to the correct implementation.
      // Other way of doing things via builder and fluent api.
      TestRequest otherTestRequestDTO = new TestRequest.Builder()
            .serviceId("API Pastries:0.0.1")
            .runnerType(TestRunnerType.OPEN_API_SCHEMA.name())
            .testEndpoint("http://good-impl:3002")
            .timeout(2000L)
            .build();

      testResult = microcks.testEndpoint(otherTestRequestDTO);
      assertTrue(testResult.isSuccess());
      assertEquals("http://good-impl:3002", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      assertEquals("", testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());

      // Test request with operations headers
      List<Header> headers = new ArrayList<>();
      Header requestHeader = new Header();
      requestHeader.setName("X-Custom-Header-1");
      requestHeader.setValues("value1,value2,value3");
      headers.add(requestHeader);
      Map<String, List<Header>> operationsHeaders = new HashMap<>();
      operationsHeaders.put("GET /pastries", headers);
      TestRequest testRequestWithHeadersDTO = new TestRequest.Builder()
            .serviceId("API Pastries:0.0.1")
            .runnerType(TestRunnerType.OPEN_API_SCHEMA.name())
            .testEndpoint("http://good-impl:3002")
            .operationsHeaders(operationsHeaders)
            .timeout(Duration.ofSeconds(2))
            .build();

      testResult = microcks.testEndpoint(testRequestWithHeadersDTO);
      assertTrue(testResult.isSuccess());
      assertEquals("http://good-impl:3002", testResult.getTestedEndpoint());
      assertEquals(3, testResult.getTestCaseResults().size());
      assertEquals("", testResult.getTestCaseResults().get(0).getTestStepResults().get(0).getMessage());
      assertEquals(1, testResult.getOperationsHeaders().size());
      assertTrue(testResult.getOperationsHeaders().containsKey("GET /pastries"));
      assertEquals(1, testResult.getOperationsHeaders().get("GET /pastries").size());
      Header resultHeader = testResult.getOperationsHeaders().get("GET /pastries").iterator().next();
      assertEquals("X-Custom-Header-1", resultHeader.getName());
      List<String> expectedValues = Arrays.asList("value1", "value2", "value3");
      assertTrue(
            Arrays.asList(resultHeader.getValues().split(",")).containsAll(expectedValues),
            resultHeader.getValues() + " should contain in any order values : " + String.join(",", expectedValues)
      );
   }
}
