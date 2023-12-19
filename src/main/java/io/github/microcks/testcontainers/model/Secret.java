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

/**
 * Domain class representing a Secret used for authenticating (to a remote Git repository or endpoint or broker).
 * @author laurent
 */
public class Secret {

   private String name;
   private String description;

   private String username;
   private String password;

   private String token;
   private String tokenHeader;
   private String caCertPem;

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String description) {
      this.description = description;
   }

   public String getUsername() {
      return username;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public String getToken() {
      return token;
   }

   public void setToken(String token) {
      this.token = token;
   }

   public String getTokenHeader() {
      return tokenHeader;
   }

   public void setTokenHeader(String tokenHeader) {
      this.tokenHeader = tokenHeader;
   }

   public String getCaCertPem() {
      return caCertPem;
   }

   public void setCaCertPem(String caCertPem) {
      this.caCertPem = caCertPem;
   }

   /**
    * Builder/Fluent API for creating Secret DTO instances.
    */
   public static class Builder {
      private String name;
      private String description;

      private String username;
      private String password;

      private String token;
      private String tokenHeader;
      private String caCertPem;

      public Builder name(String name) {
         this.name = name;
         return this;
      }

      public Builder description(String description) {
         this.description = description;
         return this;
      }

      public Builder username(String username) {
         this.username = username;
         return this;
      }

      public Builder password(String password) {
         this.password = password;
         return this;
      }

      public Builder token(String token) {
         this.token = token;
         return this;
      }

      public Builder tokenHeader(String tokenHeader) {
         this.tokenHeader = tokenHeader;
         return this;
      }

      public Builder caCertPem(String caCertPem) {
         this.caCertPem = caCertPem;
         return this;
      }

      /**
       * Build a new Secret instance after having initialized the different properties.
       * @return A new Secret instance
       */
      public Secret build() {
         // Build a request considering mandatory props are there.
         Secret secret = new Secret();
         secret.setName(name);
         // Now deal with optional properties.
         if (description != null) {
            secret.setDescription(description);
         }
         if (username != null) {
            secret.setUsername(username);
         }
         if (password != null) {
            secret.setPassword(password);
         }
         if (token != null) {
            secret.setToken(token);
         }
         if (tokenHeader != null) {
            secret.setTokenHeader(tokenHeader);
         }
         if (caCertPem != null) {
            secret.setCaCertPem(caCertPem);
         }
         return secret;
      }
   }
}
