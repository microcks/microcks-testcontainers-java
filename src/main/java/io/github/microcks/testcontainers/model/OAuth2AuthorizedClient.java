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
 * Represent persisted information for an OAuth2 authorization/authentication done before launching a test.
 * @author laurent
 */
public class OAuth2AuthorizedClient {

   private OAuth2GrantType grantType;
   private String principalName;
   private String tokenUri;
   private String scopes;

   public OAuth2GrantType getGrantType() {
      return grantType;
   }

   public void setGrantType(OAuth2GrantType grantType) {
      this.grantType = grantType;
   }

   public String getPrincipalName() {
      return principalName;
   }

   public void setPrincipalName(String principalName) {
      this.principalName = principalName;
   }

   public String getTokenUri() {
      return tokenUri;
   }

   public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
   }

   public String getScopes() {
      return scopes;
   }

   public void setScopes(String scopes) {
      this.scopes = scopes;
   }
}
