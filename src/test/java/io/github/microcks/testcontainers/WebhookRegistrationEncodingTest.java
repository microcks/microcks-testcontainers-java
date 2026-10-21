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

import io.github.microcks.testcontainers.model.ServiceRef;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test checking the operation name encoding used when registering a webhook.
 * @author laurent
 */
class WebhookRegistrationEncodingTest {

   private static final String SERVICE_ID = "60f123456789abcdef012345";

   private HttpServer server;
   private final AtomicReference<String> receivedBody = new AtomicReference<>();

   @BeforeEach
   void startServer() throws Exception {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/api/webhooks", exchange -> {
         receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
         exchange.sendResponseHeaders(201, -1);
         exchange.close();
      });
      server.start();
   }

   @AfterEach
   void stopServer() {
      server.stop(0);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "newPet WEBHOOK|" + SERVICE_ID + "-newPet WEBHOOK",
         "my-operation WEBHOOK|" + SERVICE_ID + "-my-operation WEBHOOK",
         "POST /pets/new|" + SERVICE_ID + "-POST !pets!new",
         "POST /pets-store/new-pet|" + SERVICE_ID + "-POST !pets-store!new-pet"
   })
   void shouldEncodeOperationNameInRegistrationRequest(String operationName, String expectedOperationId) throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      ServiceRef ref = mapper.readValue(
            "{\"id\":\"" + SERVICE_ID + "\",\"name\":\"Petstore Webhooks\",\"version\":\"2.0.0\"}", ServiceRef.class);

      MicrocksContainer.registerWebhook("http://localhost:" + server.getAddress().getPort(),
            WebhookCoordinates.of("Petstore Webhooks:2.0.0", operationName, "http://localhost:8080/callback"),
            List.of(ref));

      JsonNode body = mapper.readTree(receivedBody.get());
      assertEquals(expectedOperationId, body.get("operationId").asText());
   }
}
