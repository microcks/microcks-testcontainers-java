# Microcks Testcontainers Java

Java library for Testcontainers that enables embedding Microcks into your JUnit tests with lightweight, throwaway instance thanks to containers

[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/microcks/microcks-testcontainers-java/build-verify.yml?logo=github&style=for-the-badge)](https://github.com/microcks/microcks-testcontainers-java/actions)
[![Version](https://img.shields.io/maven-central/v/io.github.microcks/microcks-testcontainers?color=blue&style=for-the-badge)]((https://search.maven.org/artifact/io.github.microcks/microcks-testcontainers-java))
[![License](https://img.shields.io/github/license/microcks/microcks-testcontainers-java?style=for-the-badge&logo=apache)](https://www.apache.org/licenses/LICENSE-2.0)
[![Project Chat](https://img.shields.io/badge/discord-microcks-pink.svg?color=7289da&style=for-the-badge&logo=discord)](https://microcks.io/discord-invite/)

## Build Status

Latest released version is `0.2.5`.

Current development version is `0.2.6-SNAPSHOT`.

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
  <version>0.2.5</version>
</dependency>
```

or if you're using Gradle:

```groovy
dependencies {
    testImplementation 'io.github.microcks:microcks-testcontainers:0.2.5'
}
```

### Startup the container

You just have to specify the container image you'd like to use. This library requires a Microcks `uber` distribution (with no MongoDB dependency).

Version `0.2.1` and above require version `1.8.1` of Microcks container images.

```java
MicrocksContainer microcks = new MicrocksContainer(
      DockerImageName.parse("quay.io/microcks/microcks-uber:1.8.1"));
microcks.start();
```

### Import content in Microcks

To use Microcks mocks or contract-testing features, you first need to import OpenAPI, Postman Collection, GraphQL or gRPC artifacts. 
Artifacts can be imported as main/Primary ones or as secondary ones. See [Multi-artifacts support](https://microcks.io/documentation/using/importers/#multi-artifacts-support) for details.

You can do it before starting the container using simple paths:

```java
MicrocksContainer microcks = new MicrocksContainer(DockerImageName.parse("quay.io/microcks/microcks-uber:1.8.1"))
    .withMainArtifacts("apipastries-openapi.yaml")
    .withSecondaryArtifacts("apipastries-postman-collection.json");
microcks.start();
```

or once the container started using `File` arguments:

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

### Using authentication Secrets

It's a common need to authenticate to external systems like Http/Git repositories or external brokers. For that, the `MicrocksContainer`
provides the `withSecret()` method to register authentication secrets at startup:

```java
microcks.withSecret(new Secret.Builder()
      .name("localstack secret")
      .username(localstack.getAccessKey())
      .password(localstack.getSecretKey())
      .build());
microcks.start();
```

You may reuse this secret using its name later on during a test like this:

```java
TestRequest testRequest = new TestRequest.Builder()
      .serviceId("Pastry orders API:0.1.0")
      .runnerType(TestRunnerType.ASYNC_API_SCHEMA.name())
      .testEndpoint("sqs://eu-east-1/pastry-orders?overrideUrl=http://localstack:45566")
      .secretName("localstack secret")
      .timeout(5000L)
      .build();
```

### Advanced features with MicrocksContainersEnsemble

The `MicrocksContainer` referenced above supports essential features of Microcks provided by the main Microcks container.
The list of supported features is the following:

* Mocking of REST APIs using different kinds of artifacts,
* Contract-testing of REST APIs using `OPEN_API_SCHEMA` runner/strategy,
* Mocking and contract-testing of SOAP WebServices,
* Mocking and contract-testing of GraphQL APIs,
* Mocking and contract-testing of gRPC APIs.

To support features like Asynchronous API and `POSTMAN` contract-testing, we introduced `MicrocksContainersEnsemble` that allows managing
additional Microcks services. `MicrocksContainersEnsemble` allow you to implement
[Different levels of API contract testing](https://medium.com/@lbroudoux/different-levels-of-api-contract-testing-with-microcks-ccc0847f8c97)
in the Inner Loop with Testcontainers!

A `MicrocksContainersEnsemble` conforms to Testcontainers lifecycle methods and presents roughly the same interface
as a `MicrocksContainer`. You can create and build an ensemble that way:

```java
MicrocksContainersEnsemble ensemble = new MicrocksContainersEnsemble(IMAGE)
    .withMainArtifacts("apipastries-openapi.yaml")
    .withSecondaryArtifacts("apipastries-postman-collection.json")
    .withAccessToHost(true);
ensemble.start();
```

A `MicrocksContainer` is wrapped by an ensemble and is still available to import artifacts and execute test methods.
You have to access it using:

```java
MicrocksContainer microcks = ensemble.getMicrocksContainer();
microcks.importAsMainArtifact(...);
microcks.getLogs();
```

Please refer to our [MicrocksContainerTest](https://github.com/microcks/microcks-testcontainers-java/blob/main/src/test/java/io/github/microcks/testcontainers/MicrocksContainersEnsembleTest.java) for comprehensive example on how to use it.

#### Postman contract-testing

On this `ensemble` you may want to enable additional features such as Postman contract-testing:

```java
ensemble.withPostman();
ensemble.start();
```

You can execute a `POSTMAN` test using an ensemble that way:

```java
TestRequest testRequest = new TestRequest.Builder()
    .serviceId("API Pastries:0.0.1")
    .runnerType(TestRunnerType.POSTMAN.name())
    .testEndpoint("http://good-impl:3003")
    .timeout(2500L)
    .build();

TestResult testResult = ensemble.getMicrocksContainer().testEndpoint(testRequest);
```

#### Asynchronous API support

Asynchronous API feature need to be explicitly enabled as well. In the case you want to use it for mocking purposes,
you'll have to specify additional connection details to the broker of your choice. See an example below with connection
to a Kafka broker:

```java
ensemble.withAsyncFeature()
      .withKafkaConnection(new KafkaConnection("kafka:9092"));
ensemble.start();
```

##### Using mock endpoints for your dependencies

Once started, the `ensemble.getAsyncMinionContainer()` provides methods for retrieving mock endpoint names for the different
supported protocols (WebSocket, Kafka, SQS and SNS).

```java
String kafkaTopic = ensemble.getAsyncMinionContainer()
      .getKafkaMockTopic("Pastry orders API", "0.1.0", "SUBSCRIBE pastry/orders");
```

##### Launching new contract-tests

Using contract-testing techniques on Asynchronous endpoints may require a different style of interacting with the Microcks
container. For example, you may need to:
1. Start the test making Microcks listen to the target async endpoint,
2. Activate your System Under Tests so that it produces an event,
3. Finalize the Microcks tests and actually ensure you received one or many well-formed events.

For that the `MicrocksContainer` now provides a `testEndpointAsync(TestRequest request)` method that actually returns a `CompletableFuture`.
Once invoked, you may trigger your application events and then `get()` the future result to assert like this:

```java
// Start the test, making Microcks listen the endpoint provided in testRequest
CompletableFuture<TestResult> testResultFuture = ensemble.getMicrocksContainer().testEndpointAsync(testRequest);

// Here below: activate your app to make it produce events on this endpoint.
// myapp.invokeBusinessMethodThatTriggerEvents();
      
// Now retrieve the final test result and assert.
TestResult testResult = testResultFuture.get();
assertTrue(testResult.isSuccess());
```