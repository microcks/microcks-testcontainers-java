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
package io.github.microcks.testcontainers.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple bean representing an unidirectional exchange as an event message.
 * @author laurent
 */
@JsonIgnoreProperties(ignoreUnknown=true)
public class UnidirectionalEvent {

   private EventMessage eventMessage;

   @JsonCreator
   public UnidirectionalEvent(@JsonProperty("eventMessage") EventMessage eventMessage) {
      this.eventMessage = eventMessage;
   }

   public EventMessage getEventMessage() {
      return eventMessage;
   }

   public void setEventMessage(EventMessage eventMessage) {
      this.eventMessage = eventMessage;
   }
}
