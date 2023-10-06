package io.github.microcks.testcontainers.util.jackson;

import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonParser;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;
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
   public String deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException {
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
