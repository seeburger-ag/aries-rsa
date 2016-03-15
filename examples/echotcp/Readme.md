# Example echo TCP

Implements a simple echo service and exposes it using the Aries RSA TCP provider.

# Install

Download Apache Karaf 4.0.5

## Service
Extract to container_a and start. In the shell execute the commands below:

```
feature:repo-add aries-rsa 1.8-SNAPSHOT
feature:install scr aries-rsa-provider-tcp aries-rsa-discovery-zookeeper aries-rsa-discovery-zookeeper-server
install -s mvn:org.apache.aries.rsa.examples.echotcp/org.apache.aries.rsa.examples.echotcp.api
install -s mvn:org.apache.aries.rsa.examples.echotcp/org.apache.aries.rsa.examples.echotcp.service
```

The log should show that the service is exported using the tcp provider and published to zookeeper.
It should look similar to this:

```
2016-03-14 11:59:53,548 | INFO  | pool-50-thread-5 | TopologyManagerExport            | 57 - org.apache.aries.rsa.topology-manager - 1.8.0.SNAPSHOT | TopologyManager: export successful for [org.apache.aries.rsa.examples.echotcp.api.EchoService], endpoints: [{component.id=1, component.name=org.apache.aries.rsa.examples.echotcp.service.EchoServiceImpl, endpoint.framework.uuid=2b242970-0d54-49c4-a321-b0c323809c24, endpoint.id=tcp://192.168.0.129:36384, endpoint.package.version.org.apache.aries.rsa.examples.echotcp.api=1.0.0, endpoint.service.id=138, objectClass=[org.apache.aries.rsa.examples.echotcp.api.EchoService], service.bundleid=64, service.imported=true, service.imported.configs=[aries.tcp], service.scope=bundle}]
2016-03-14 11:59:53,549 | INFO  | pool-50-thread-5 | PublishingEndpointListener       | 54 - org.apache.aries.rsa.discovery.zookeeper - 1.8.0.SNAPSHOT | Local EndpointDescription added: {component.id=1, component.name=org.apache.aries.rsa.examples.echotcp.service.EchoServiceImpl, endpoint.framework.uuid=2b242970-0d54-49c4-a321-b0c323809c24, endpoint.id=tcp://192.168.0.129:36384, endpoint.package.version.org.apache.aries.rsa.examples.echotcp.api=1.0.0, endpoint.service.id=138, objectClass=[org.apache.aries.rsa.examples.echotcp.api.EchoService], service.bundleid=64, service.imported=true, service.imported.configs=[aries.tcp], service.scope=bundle}
```

## Consumer
Extract to container_b and start. In the shell execute the commands below:

```
feature:repo-add aries-rsa 1.8-SNAPSHOT
feature:install scr aries-rsa-provider-tcp aries-rsa-discovery-zookeeper
install -s mvn:org.apache.aries.rsa.examples.echotcp/org.apache.aries.rsa.examples.echotcp.api
install -s mvn:org.apache.aries.rsa.examples.echotcp/org.apache.aries.rsa.examples.echotcp.consumer
```

The consumer should start and show:
Sending to echo service
Good morning

The log should show that the discovery picks up the endpoint from zookeeper and that the RemoteServiceAdmin imports the service.

```
2016-03-14 12:03:30,518 | INFO  | er])-EventThread | InterfaceMonitor                 | 54 - org.apache.aries.rsa.discovery.zookeeper - 1.8.0.SNAPSHOT | found new node /osgi/service_registry/org/apache/aries/rsa/examples/echotcp/api/EchoService/[192.168.0.129#36384#]   ( []->child )  props: [1, org.apache.aries.rsa.examples.echotcp.service.EchoServiceImpl, 2b242970-0d54-49c4-a321-b0c323809c24, tcp://192.168.0.129:36384, 1.0.0, 138, [Ljava.lang.String;@69a6817f, 64, true, [Ljava.lang.String;@8514b3a, bundle]
2016-03-14 12:03:30,520 | INFO  | er])-EventThread | InterfaceMonitorManager          | 54 - org.apache.aries.rsa.discovery.zookeeper - 1.8.0.SNAPSHOT | calling EndpointListener.endpointAdded: org.apache.aries.rsa.topologymanager.importer.TopologyManagerImport@2366e9c8 from bundle org.apache.aries.rsa.topology-manager for endpoint: {component.id=1, component.name=org.apache.aries.rsa.examples.echotcp.service.EchoServiceImpl, endpoint.framework.uuid=2b242970-0d54-49c4-a321-b0c323809c24, endpoint.id=tcp://192.168.0.129:36384, endpoint.package.version.org.apache.aries.rsa.examples.echotcp.api=1.0.0, endpoint.service.id=138, objectClass=[org.apache.aries.rsa.examples.echotcp.api.EchoService], service.bundleid=64, service.imported=true, service.imported.configs=[aries.tcp], service.scope=bundle}
2016-03-14 12:03:30,522 | INFO  | pool-41-thread-1 | RemoteServiceAdminCore           | 52 - org.apache.aries.rsa.core - 1.8.0.SNAPSHOT | Importing service tcp://192.168.0.129:36384 with interfaces [org.apache.aries.rsa.examples.echotcp.api.EchoService] using handler class org.apache.aries.rsa.provider.tcp.TCPProvider.
```

