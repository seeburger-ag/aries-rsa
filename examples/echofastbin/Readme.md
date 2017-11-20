# Example echo fastbin

Implements a simple echo service and exposes it using the Aries RSA fastbin provider.
The echo services shows 

 * simple invocations
 * InputStreams as parameters and return values
 * Asynchronous Calls using Future/CompletableFuture as return value

# Install

Download Apache Karaf 4.1.3

## Service
Extract to container_a and start. In the shell execute the commands below:

```
feature:repo-add aries-rsa 1.11.0
feature:install scr aries-rsa-provider-fastbin aries-rsa-discovery-zookeeper aries-rsa-discovery-zookeeper-server
bundle:install -s mvn:org.apache.aries.rsa.examples.echofastbin/org.apache.aries.rsa.examples.echofastbin.fbapi
bundle:install -s mvn:org.apache.aries.rsa.examples.echofastbin/org.apache.aries.rsa.examples.echofastbin.fbservice
```

The log should show that the service is exported using the fastbin provider and published to zookeeper.
It should look similar to this:

```
2017-11-20 23:48:44,114 | INFO  | pool-29-thread-5 | TopologyManagerExport            | 59 - org.apache.aries.rsa.topology-manager - 1.11.0 | TopologyManager: export successful for [org.apache.aries.rsa.examples.fastbin.api.EchoService], endpoints: [{aries.fastbin.address=tcp://yourhostname:2544, component.id=2, component.name=org.apache.aries.rsa.examples.fastbin.service.EchoServiceImpl, endpoint.framework.uuid=8e247f92-5b51-4d66-8db5-7be03efe77a2, endpoint.id=698226447-37305-1511218094107-0-1, endpoint.package.version.org.apache.aries.rsa.examples.fastbin.api=1.0.0, endpoint.service.id=137, objectClass=[org.apache.aries.rsa.examples.fastbin.api.EchoService], service.bundleid=69, service.imported=true, service.imported.configs=[aries.fastbin], service.scope=bundle}]
```

## Consumer
Extract to container_b and start. If both containers are running on the same host you need to make sure that both are using different fastbin ports. To do that create a file `etc/org.apache.aries.rsa.provider.fastbin.cfg` with the following contents:

```
uri=tcp://0.0.0.0:5000
```

This will open the fastbin server on all interfaces on port 5000.
In the shell execute the commands below:

```
feature:repo-add aries-rsa 1.11.0
feature:install scr aries-rsa-provider-fastbin aries-rsa-discovery-zookeeper
bundle:install -s mvn:org.apache.aries.rsa.examples.echofastbin/org.apache.aries.rsa.examples.echofastbin.fbapi
bundle:install -s mvn:org.apache.aries.rsa.examples.echofastbin/org.apache.aries.rsa.examples.echofastbin.fbconsumer
```

```
The consumer should start and show:
karaf@root()> Sending to echo service: echo                                                                                                                                                                 
Good morning
Sending to echo service: async
Sending to echo service: stream
Good morning received as a stream
Sending to echo service: stream2
Good morning send as a stream
Good morning Async

The log should show that the discovery picks up the endpoint from zookeeper and that the RemoteServiceAdmin imports the service.
```

```
2017-11-20 23:55:51,415 | INFO  | er])-EventThread | InterfaceMonitor                 | 55 - org.apache.aries.rsa.discovery.zookeeper - 1.11.0 | Processing change on node: /osgi/service_registry/org/apache/aries/rsa/examples/fastbin/api/EchoService
2017-11-20 23:55:51,422 | INFO  | er])-EventThread | InterfaceMonitor                 | 55 - org.apache.aries.rsa.discovery.zookeeper - 1.11.0 | found new node /osgi/service_registry/org/apache/aries/rsa/examples/fastbin/api/EchoService/[null#-1#698226447-37305-1511218094107-0-1]   ( []->child )  props: [tcp://yourhostname:2544, 2, org.apache.aries.rsa.examples.fastbin.service.EchoServiceImpl, 8e247f92-5b51-4d66-8db5-7be03efe77a2, 698226447-37305-1511218094107-0-1, 1.0.0, 137, [Ljava.lang.String;@12f10073, 69, true, [Ljava.lang.String;@22e61067, bundle]
2017-11-20 23:55:51,423 | INFO  | er])-EventThread | InterfaceMonitorManager          | 55 - org.apache.aries.rsa.discovery.zookeeper - 1.11.0 | calling EndpointListener.endpointAdded: org.apache.aries.rsa.topologymanager.importer.TopologyManagerImport@4f6cbd90 from bundle org.apache.aries.rsa.topology-manager for endpoint: {aries.fastbin.address=tcp://yourhostname:2544, component.id=2, component.name=org.apache.aries.rsa.examples.fastbin.service.EchoServiceImpl, endpoint.framework.uuid=8e247f92-5b51-4d66-8db5-7be03efe77a2, endpoint.id=698226447-37305-1511218094107-0-1, endpoint.package.version.org.apache.aries.rsa.examples.fastbin.api=1.0.0, endpoint.service.id=137, objectClass=[org.apache.aries.rsa.examples.fastbin.api.EchoService], service.bundleid=69, service.imported=true, service.imported.configs=[aries.fastbin], service.scope=bundle}
2017-11-20 23:55:51,433 | INFO  | pool-30-thread-1 | RemoteServiceAdminCore           | 52 - org.apache.aries.rsa.core - 1.11.0 | Importing service 698226447-37305-1511218094107-0-1 with interfaces [org.apache.aries.rsa.examples.fastbin.api.EchoService] using handler class org.apache.aries.rsa.provider.fastbin.FastBinProvider.

```

