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
package io.github.microcks.testcontainers.util.jackson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonParser;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.DeserializationContext;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonMappingException;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This is a test case for ArrayToStringDeserializer class.
 */
class ArrayToStringDeserializerTest {

   public static Stream<Arguments> parametersForShouldDeserializeArrayOfStringToString() {
      return Stream.of(
            Arguments.of("null", null),
            Arguments.of("[]", ""),
            Arguments.of("[\"singleValue\"]", "singleValue"),
            Arguments.of("[\"value1\", \"value2\"]", "value1,value2"),
            Arguments.of("[1, 2]", "1,2"),
            Arguments.of("[true, false]", "true,false"),
            Arguments.of("[\"value\", 1, false]", "value,1,false")
      );
   }

   @ParameterizedTest
   @MethodSource("parametersForShouldDeserializeArrayOfStringToString")
   void shouldDeserializeArrayOfStringToString(String array, String expectedString) throws Exception {
      ArrayToStringDeserializer arrayToStringDeserializer = new ArrayToStringDeserializer();
      String json = format("{ \"array\": %s }", array);
      String deserializedArray = deserializeArray(arrayToStringDeserializer, json);
      assertEquals(expectedString, deserializedArray);
   }

   @Test
   void shouldThrowAnExceptionWhenArrayContainObjects() throws Exception {
      ArrayToStringDeserializer arrayToStringDeserializer = new ArrayToStringDeserializer();
      String json = "{ \"array\": [{\"key\": \"value1\"}]";
      JsonMappingException exception = assertThrows(JsonMappingException.class, () -> deserializeArray(arrayToStringDeserializer, json));
      assertEquals("Expected array of primitive types, got an object", exception.getMessage());
   }

   private String deserializeArray(ArrayToStringDeserializer deserializer, String json) throws Exception {
      InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
      ObjectMapper objectMapper = new ObjectMapper();
      JsonParser parser = objectMapper.getFactory().createParser(stream);
      DeserializationContext context = objectMapper.getDeserializationContext();
      parser.nextToken();
      parser.nextToken();
      parser.nextToken();
      return deserializer.deserialize(parser, context);
   }
}