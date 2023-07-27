/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.testcontainers;

import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.TestResult;
import io.github.microcks.domain.TestRunnerType;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.utility.DockerImageName;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author laurent
 */
public class MicrocksContainerTest {

   private static final DockerImageName MICROCKS_IMAGE = DockerImageName.parse("quay.io/microcks/microcks-uber:nightly");

   private static final DockerImageName BAD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:01");
   private static final DockerImageName GOOD_PASTRY_IMAGE = DockerImageName.parse("quay.io/microcks/contract-testing-demo:02");

   @Test
   public void testMockingFunctionality() throws Exception {
      try (
            MicrocksContainer microcks = new MicrocksContainer(MICROCKS_IMAGE);
      ) {
         microcks.start();
         testMicrocksConfigRetrieval(microcks.getHttpEndpoint());

         microcks.importAsMainArtifact(new File("target/test-classes/apipastries-openapi.yaml"));
         testMicrocksMockingFunctionality(microcks);
      }
   }

   @Test
   public void testContractTestingFunctionality() throws Exception {
      try (
            MicrocksContainer microcks = new MicrocksContainer(MICROCKS_IMAGE);
            GenericContainer<?> badImpl = new GenericContainer<>(BAD_PASTRY_IMAGE)
                  .withExposedPorts(3001)
                  .waitingFor(Wait.forLogMessage(".*Example app listening on port 3001.*", 1));
            GenericContainer<?> goodImpl = new GenericContainer<>(GOOD_PASTRY_IMAGE)
                  .withExposedPorts(3002)
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

   private void testMicrocksConfigRetrieval(String endpointUrl) {
      Response keycloakConfig = RestAssured.given().when()
            .get(endpointUrl + "/api/keycloak/config")
            .thenReturn();

      assertEquals(200, keycloakConfig.getStatusCode());
   }

   private void testMicrocksMockingFunctionality(MicrocksContainer microcks) {
      String baseApiUrl = microcks.getMockEndpoint(ServiceType.REST, "API Pastries", "0.0.1");

      Response millefeuille = RestAssured.given().when()
            .get(baseApiUrl + "/pastries/Millefeuille")
            .thenReturn();

      assertEquals(200, millefeuille.getStatusCode());
      assertEquals("Millefeuille", millefeuille.jsonPath().get("name"));
      millefeuille.getBody().prettyPrint();
   }

   private void testMicrocksContractTestingFunctionality(MicrocksContainer microcks, GenericContainer badImpl, GenericContainer goodImpl) {
      // Produce a new test request.
      TestRequestDTO testRequest = new TestRequestDTO();
      testRequest.setServiceId("API Pastries:0.0.1");
      testRequest.setRunnerType(TestRunnerType.OPEN_API_SCHEMA.name());
      testRequest.setTestEndpoint("http://host.docker.internal:" + badImpl.getMappedPort(3001));
      testRequest.setTimeout(2000l);

      /*
      // Other way of doing things via builder and fluent api.
      TestRequestDTO testRequestDTO = new TestRequestDTO.Builder()
            .serviceId("API Pastries:0.0.1")
            .runnerType(TestRunnerType.OPEN_API_SCHEMA.name())
            .testEndpoint("http://host.docker.internal:" + badImpl.getMappedPort(3001))
            .timeout(2000L)
            .build();
       */

      ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

      try {
         TestResult testResult = microcks.testEndpoint(testRequest);

         assertFalse(testResult.isSuccess());
         System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      } catch (Exception e) {
         e.printStackTrace();
      }

      try {
         // Switch endpoint to the correct implementation.
         testRequest.setTestEndpoint("http://host.docker.internal:" + goodImpl.getMappedPort(3002));
         TestResult testResult = microcks.testEndpoint(testRequest);

         assertTrue(testResult.isSuccess());
         System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult));
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
}
