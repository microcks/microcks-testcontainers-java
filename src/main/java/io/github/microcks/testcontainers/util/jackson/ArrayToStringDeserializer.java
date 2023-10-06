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

import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonParser;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonToken;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.DeserializationContext;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a custom deserializer to deserialize an array of primitive types to String where each value is separated by a comma.
 */
public class ArrayToStringDeserializer extends JsonDeserializer<String> {

   @Override
   public String deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
      if (jsonParser.currentToken() == JsonToken.START_ARRAY) {
         List<String> values = new ArrayList<>();
         jsonParser.nextToken();
         while (jsonParser.hasCurrentToken() && jsonParser.currentToken() != JsonToken.END_ARRAY) {
            if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
               throw deserializationContext.mappingException("Expected array of primitive types, got an object");
            }
            values.add(jsonParser.getValueAsString());
            jsonParser.nextToken();
         }
         return String.join(",", values);
      }
      return null;
   }
}
