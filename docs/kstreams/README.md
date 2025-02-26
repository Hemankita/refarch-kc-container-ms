# Kafka Streams implementation of the container inventory management

In this chapter we are presenting how to sue the Kafka Streams API combined with Kafkat event sourcing to implement the container inventory management. The component can be represented in the figure below:

![](../images/kstreams-container.png)

The container topics includes all the event about container life cycle. The application is java based and deployed in Liberty packaged into a docker image deployable on kubernetes. The service exposes some RESTful APIs to get a container by ID. No CUD operations as all is done via events. The Streams implementation keeps data in table.

As a java based microservice we have two approaches to implement the service: springboot and microprofile. Knowing we will deploy on kubernetes cluster with Istio we will have a lot of the resiliency and scalability addressed for us. But Istio does not support implementing retries logic according to the business logic. Microprofile add a lot of nice capabilities like SSL, open API, JAXRS... Microprofile is supported by Open Liberty as well as many servers.

The [Apache Kafka Streams API](https://kafka.apache.org/documentation/streams/) is a client library for building applications and microservices, where the input and output data are stored in Kafka clusters. It simplifies the implementation of the stateless or stateful event processing to transform and enrich data. It supports time windowing processing.

We encourage to do [this Streams tutorial](https://kafka.apache.org/21/documentation/streams/tutorial). 

The features we want to illustrate in this implementation, using KStreams are:

* Listen to ContainerAddedToInventory event from the `containers` topic and maintains a stateful table of containers. 
* Listen to OrderCreated event from `orders` and assign a container from the inventory based on the pickup location and the container location and its characteristics.
* Implemented as JAXRS application deployed on Liberty and packaged with dockerfile.
* Deploy to kubernetes or run with docker-compose

## Start with maven

Kafka streams delivers a Maven archetype to create a squeleton project. The following command can be used to create the base code.
```sh
mvn archetype:generate -DarchetypeGroupId=org.apache.kafka -DarchetypeArtifactId=streams-quickstart-java     -DarchetypeVersion=2.1.0     -DgroupId=kc-container     -DartifactId=kc-container-streams    -Dversion=0.1     -Dpackage=containerManager
```

We added a `.project` file to develop the code in Eclipse, imported the code into Eclipse and modify the `.classpath` with the following lines:
```xml
    <classpathentry kind="con" path="org.eclipse.m2e.MAVEN2_CLASSPATH_CONTAINER">
		<attributes>
			<attribute name="maven.pomderived" value="true"/>
		</attributes>
	</classpathentry>
```

To access to serializer and testing framework we added the following dependencies in the pom.xml:

```xml
      <dependency>
    		<groupId>org.apache.kafka</groupId>
    		<artifactId>kafka-clients</artifactId>
    		<version>${kafka.version}</version>
		</dependency>
		<dependency>
		    <groupId>org.apache.kafka</groupId>
		    <artifactId>kafka-streams-test-utils</artifactId>
		    <version>${kafka.version}</version>
		    <scope>test</scope>
		</dependency>
```

Using this approach as the service runs in OpenLiberty and integrate JAXRS, microprofile, Open API,... we have to add a lot of depencies into the pom.xml file. Another approach is to use IBM Cloud starter kit.

## Start with IBM Cloud microprofile starter kit

Use the `Create resource` option and select the "Java microservice with microprofile and Java EE" starter kit as shown below:

![](mp-starter.png)

Then enter an application name (e.g. MP-ContainerMS) with a resource group and may be some tags. 

![](mp-app-details.png)

The next step is to select a kubernetes cluster instance:

![](mp-select-iks.png)

Configure the toolchain, and verify the application is created in the console:

![](mp-app-listed.png)

The application is accessible from github, a toolchain is ready to process the app and deploy it.

![](default-toolchain.png)

At the time of writting, and most likely in the future too, the steps and the documentations are not aligned. Code is release on a weekly basis and the documentation is often behind. We can download the source code from the github. The address was https://git.ng.bluemix.net/boyerje/MP-ContainerMS. We have to unprotect the master branch so we can push our update. 

We also have to modify the deployment configuration to change the target namespace. 

The generated code includes helm chart, Dockerfiles, and base JAXRS code. The code generated is very similar to the one created using the `ibmcloud dev` CLI. But we need to modify this generated project with some specific microprofile content.

Eclipse microprofile is now on version 2.2, so we also use the following code generator from [the Microprofile starter site](https://start.microprofile.io/) so we can get updated code with new capability like SSL, openAPI and JWT supports.

![](mp-eclipse-starter.png)

So now we need to integrate both code and then add Kafka streams. Here are some of the main updates we did:
* Add in the cli-config.yml the registry address and cluster name
* Change pom dependencies for microprofile 2.2, and change the image in Dockerfile to access websphere liberty 19.0.0.3 compatible with 2.2. (FROM websphere-liberty:19.0.0.3-microProfile2)
* Use the health class from the microprofile 2.2 generated code, as it uses microprofile annotation. Add also the configuration injection with properties file. Add new package names.
* Remove unnecessary files
* Modify the Values.yaml to reflect the target registry and add secret reference:
```
  repository: us.icr.io/ibmcaseeda/mpcontainerms
  tag: latest
  pullPolicy: Always
  pullSecret: browncompute-registry-secret
```

Some of those steps are pushed to the development kubernetes cluster using the command:


## Some useful Kafka streams APIs

The stream configuration looks similar to the Kafka consumer and producer configuration. 

```java
    props.put(StreamsConfig.APPLICATION_ID_CONFIG, "container-streams");
    props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");    
    props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
    props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
```
The StreamsConfig is a specific configuration for Streams app.  

One of the interesting class is the `KStream` to manage a stream of structured events. Kstreams represents unbounded collection of immutable events.

Two classes are supporting the order and container processing:

* [ContainerInventoryView](https://github.com/ibm-cloud-architecture/refarch-kc-container-ms/blob/master/kstreams/src/main/java/ibm/labs/kc/streams/containerManager/ContainerInventoryView.java)
* [ContainerOrderAssignment](https://github.com/ibm-cloud-architecture/refarch-kc-container-ms/blob/master/kstreams/src/main/java/ibm/labs/kc/streams/containerManager/ContainerOrderAssissgnment.java)

We are using the Streams DSL APIs to do the processing. Here is an example of terminal stream to print what is coming to the `orders` topic:
```java
   final StreamsBuilder builder = new StreamsBuilder();
    builder.stream("orders")
        .foreach((key,value) -> {
        			Order order = parser.fromJson((String)value, OrderEvent.class).getPayload();
        			// TODO do something to the order
        			System.out.println("received order " + key + " " + value);
        		});

    final Topology topology = builder.build();
    final KafkaStreams streams = new KafkaStreams(topology, props);
    streams.start();
```

We want now to implement the container inventory. We want to support the following events:

* ContainerAddedToInventory, ContainerRemovedFromInventory
* ContainerAtLocation
* ContainerOnMaintenance, ContainerOffMaintenance,
* ContainerAssignedToOrder, ContainerReleasedFromOrder
* ContainerGoodLoaded, ContainerGoodUnLoaded
* ContainerOnShip, ContainerOffShip
* ContainerOnTruck, ContainerOffTruck

We want the container event to keep a timestamp, a version, a type, and a payload representing the data describing a Reefer container. The Key is the containerID. The java class for the container event is [here](https://github.com/ibm-cloud-architecture/refarch-kc-container-ms/blob/master/kstreams/src/main/java/ibm/labs/kc/model/events/ContainerEvent.java).

Using a TDD approach we will start by the tests to implement the solution.

For more information on the Streams DSL API [keeep this page slose to you](https://kafka.apache.org/21/documentation/streams/developer-guide/dsl-api.html). 

## Test Driven Development

We want to document two major test suites. One for building the internal view of the container inventory, the other to support the container to order assignment.

### Container inventory

> When the service receives a ContainerAdded event it needs to add it to the table and be able to retreive it by ID

1. To support the Get By ID we are adding a Service class with the operation exposed as RESTful resource using JAXRS annotations. We already described this approach in the [fleetms project](https://github.com/ibm-cloud-architecture/refarch-kc-ms).

To test a stream application without Kafka backbone there is a test utility available [here](https://kafka.apache.org/documentation/streams/developer-guide/testing.html#unit-testing-processors). The settings are simple: get the properties, define the serialisation of the key and value of the event to get from kafka, define the stream process flow, named topology, send the input and get the output.

The [test TestContainerInventory](https://github.com/ibm-cloud-architecture/refarch-kc-container-ms/blob/master/kstreams/src/test/java/ut) is illustrating how to use the [TopologyTestDriver](https://kafka.apache.org/21/javadoc/org/apache/kafka/streams/TopologyTestDriver.html).

```java
	Properties props = ApplicationConfig.getStreamsProperties("test");
	props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
	TopologyTestDriver testDriver = new TopologyTestDriver(
				buildProcessFlow(), props);

	ConsumerRecordFactory<String, String> factory = new ConsumerRecordFactory<String, String>("containers",
				new StringSerializer(), new StringSerializer());
	ConsumerRecord<byte[],byte[]> record = factory.create("containers",ce.getContainerID(), parser.toJson(ce));
		
	testDriver.pipeInput(record);
```

We are using the String default serialization for the key and the ContainerEvent, and use Gson to serialize and deserialize the json.

So the test is to prepare a ContainerEvent with type = "ContainerAdded" and then get the payload, persist it in the table and access to the table via the concept of `store` and validate the data.

Below is the access to the store and compare the expected results

```java
	KeyValueStore<String, String> store = testDriver.getKeyValueStore("queryable-container-store");
	String containerStrg = store.get(ce.getContainerID());
	Assert.assertNotNull(containerStrg);
	Assert.assertTrue(containerStrg.contains(ce.getContainerID()));
	Assert.assertTrue(containerStrg.contains("atDock"));
```

Now the tricky part is in the Stream process flow. The idea is to process the ContainerEvent as streams (of String) and extract the payload (a Container), then generate the Container in a new stream, group by the key and then save to a table. We separate the code in a function so e can move it into the real application after.

```java
	public  Topology buildProcessFlow() {
		final StreamsBuilder builder = new StreamsBuilder();
	   // containerEvent is a string, map values help to change the type and data of the inpit values
	    builder.stream(CONTAINERS_TOPIC).mapValues((containerEvent) -> {
	    		 // the container payload is of interest to keep in table
	   			 Container c = jsonParser.fromJson((String)containerEvent, ContainerEvent.class).getPayload();
	   			 return jsonParser.toJson(c);
	   		 }).groupByKey()  // the keys are kept so we can group by key to prepare for the tabl
	   		 	.reduce((container,container1) -> {
	   		 		System.out.println("received container " + container1 );
	   		 		return container1;
	   		 	},
					 Materialized.as("queryable-container-store"));
	    return builder.build();
	}
```

The trick is to use the `reduce()` function that get the container and save it to the store that we can specify.

The unit test runs successfully with the command: `mvn -Dtest=TestContainerInventory test`.

This logic can be integrated in a View class. So we can refactor the test and add new class (see ContainerInventoryView class) to move the logic into the applciation. From a design point of view this class is a DAO. Now that we are not using the Testing tool, we need the real streams.

In class ContainerInventoryView:    
```java 
   private KafkaStreams streams;
   // ..
	Properties props = ApplicationConfig.getStreamsProperties("container-streams");
	props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
	streams = new KafkaStreams(buildProcessFlow(), props);
	try {
		streams.cleanUp(); 
		streams.start();
	} catch (Throwable e) {
		System.exit(1);
	}
```

As illustrated above, the streams API is a continuous running Thread, so it needs to be started only one time. We will address scaling separatly.  So we isolate the DAO as a Singleton, and start it when the deployed application starts, via a ServletContextListener. 

```java
public class EventLoop implements ServletContextListener{

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		// Initialize the Container consumer
		ContainerInventoryView cView = (ContainerInventoryView)ContainerInventoryView.instance();
		cView.start();
	}
```

 Now we can add the getById operation, package as a war, deploy it to Liberty. 

### Container to Order Assignment

The business logic we want to implement is to get an order with the source pickup city, the type of product, the quantity and the expected pickup date, manage the internal list of containers and search for a container located close to the pickup city from the order.

The test to validate this logic is under `kstreams/src/test/java/ut/TestContainerAssignment`. 

The story will not be completed if we do not talk about how th application get the order. As presented in the design and [order command microservice](https://github.com/ibm-cloud-architecture/refarch-kc-order-ms) implementation, when an order is created an event is generated to the `orders` topic. So we need to add another Streams processing and start the process flow in the context listener.

![](../images/container-to-order.png)

### Run tests

Recall with maven we can run all the unit tests, one test and skip integration tests.

```shell
# Test a unique test
$  mvn -Dtest=TestContainerInventory test
# Skip all tests
mvn install -DskipTests
# Skip integration test
mvn install -DskipITs
# Run everything
mvn install
```

To start the liberty server use the script: `./script/startLocalLiberty` or `mvn liberty:run-server`

## docker compose configuration

Replace existing springcontainerms declaration to the following

```yaml
    containerkstreams:
        image: ibmcase/kc-containerkstreams:latest
        hostname: containerkstreams
        ports:
            - "12080:9080"
        environment:
            KAFKA_ENV: ${KAFKA_ENV}
            KAFKA_BROKERS: ${KAFKA_BROKERS}
            KAFKA_APIKEY: ${KAFKA_APIKEY}
```
## How streams flows are resilient?


## How to scale?


