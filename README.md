# Microcks Testcontainers Java

Java library for Testcontainers that enables embedding Microcks into your JUnit tests with lightweight, throwaway instance thanks to containers

[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/microcks/microcks-testcontainers-java/build-verify.yml?logo=github&style=for-the-badge)](https://github.com/microcks/microcks-testcontainers-java/actions)
[![Version](https://img.shields.io/maven-central/v/io.github.microcks/microcks-testcontainers?color=blue&style=for-the-badge)]((https://search.maven.org/artifact/io.github.microcks/microcks-testcontainers-java))
[![License](https://img.shields.io/github/license/microcks/microcks-testcontainers-java?style=for-the-badge&logo=apache)](https://www.apache.org/licenses/LICENSE-2.0)
[![Project Chat](https://img.shields.io/badge/chat-on_zulip-pink.svg?color=ff69b4&style=for-the-badge&logo=zulip)](https://microcksio.zulipchat.com/)

## Build Status

Latest released version is `0.1.4`.

Current development version is `0.2.0-SNAPSHOT`.

#### Sonarcloud Quality metrics

[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=microcks_microcks-testcontainers-java&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=microcks_microcks-testcontainers-java)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=microcks_microcks-testcontainers-java&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=microcks_microcks-testcontainers-java)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=microcks_microcks-testcontainers-java&metric=bugs)](https://sonarcloud.io/summary/new_code?id=microcks_microcks-testcontainers-java)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=microcks_microcks-testcontainers-java&metric=coverage)](https://sonarcloud.io/summary/new_code?id=microcks_microcks-testcontainers-java)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=microcks_microcks-testcontainers-java&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=microcks_microcks-testcontainers-java)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=microcks_microcks-testcontainers-java&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=microcks_microcks-testcontainers-java)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=microcks_microcks-testcontainers-java&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=microcks_microcks-testcontainers-java)

## How to use it?

### Include it into your project dependencies

If you're using Maven:
```xml
<dependency>
  <groupId>io.github.microcks</groupId>
  <artifactId>microcks-testcontainers</artifactId>
  <version>0.1.4</version>
</dependency>
```

or if you're using Gradle:

```groovy
dependencies {
    testImplementation 'io.github.microcks:microcks-testcontainers:0.1.4'
}
```

### Startup the container

You just have to specify the container image you'd like to use. This library requires a Microcks `uber` distribution (with no MongoDB dependency).

```java
MicrocksContainer microcks = new MicrocksContainer(
      DockerImageName.parse("quay.io/microcks/microcks-uber:1.8.0"));
microcks.start();
```

### Import content in Microcks

To use Microcks mocks or contract-testing features, you first need to import OpenAPI, Postman Collection, GraphQL or gRPC artifacts. 
Artifacts can be imported as main/Primary ones or as secondary ones. See [Multi-artifacts support](https://microcks.io/documentation/using/importers/#multi-artifacts-support) for details.

```java
microcks.importAsMainArtifact(new File("target/test-classes/apipastries-openapi.yaml"));
microcks.importAsSecondaryArtifact(new File("target/test-classes/apipastries-postman-collection.json"));
```

Please refer to our [MicrocksContainerTest](https://github.com/microcks/microcks-testcontainers-java/blob/main/src/test/java/io/github/microcks/testcontainers/MicrocksContainerTest.java) for comprehensive example on how to use it.

### Using mock endpoints for your dependencies

During your test setup, you'd probably need to retrieve mock endpoints provided by Microcks containers to 
setup your base API url calls. You can do it like this:

```java
String baseApiUrl = microcks.getRestMockEndpoint("API Pastries", "0.0.1");
```

The container provides methods for different supported API styles/protocols (Soap, GraphQL, gRPC,...).

The container also provides `getHttpEndpoint()` for raw access to those API endpoints.

### Launching new contract-tests

If you want to ensure that your application under test is conformant to an OpenAPI contract (or other type of contract),
you can launch a Microcks contract/conformance test using the local server port you're actually running. This is
typically how it could be done for a Spring Boot application: 

```java
@LocalServerPort
private Integer port;

@BeforeEach
public void setupPort() {
  // Host port exposition should be done here.
  Testcontainers.exposeHostPorts(port);
}

@Test
public void testOpenAPIContract() throws Exception {
   // Ask for an Open API conformance to be launched.
   TestRequest testRequest = new TestRequest.Builder()
      .serviceId("API Pastries:0.0.1")
      .runnerType(TestRunnerType.OPEN_API_SCHEMA.name())
      .testEndpoint("http://host.testcontainers.internal:" + port)
      .timeout(2000L)
      .build();

   TestResult testResult = microcks.testEndpoint(testRequest);
   assertTrue(testResult.isSuccess());
}
```

The `TestResult` gives you access to all details regarding success of failure on different test cases.

A comprehensive Spring Boot demo application illustrating both usages is available here: [spring-boot-order-service](https://github.com/microcks/api-lifecycle/tree/master/shift-left-demo/spring-boot-order-service). 