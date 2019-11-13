# Topology Manager

Listens to local services and decides which to expose. It can also add properties to change the way services are exposed.
For the services to be exported it calls RemoteServiceAdmin.exportService to do the actual export. Then notifies
EndpointListeners about the new Endpoint.
Listens for service requests from consumers and creates EndpointListeners for these interests.

The TopologyManager by default exposes all suitably marked local services for export and imports all service interests
with matching remote Endpoints.

## ExportPolicy

This behaviour can be customized by exposing a service implementing the [ExportPolicy interface](https://github.com/apache/aries-rsa/blob/master/spi/src/main/java/org/apache/aries/rsa/spi/ExportPolicy.java).
It can be used to implement system wide governance rules.

Some examples what could be done:

* Enhancing all exposed remote endpoints with SSL, basic auth, logging
* Exporting OSGi services with annotations for JAX-WS or JAX-RS even when not specially marked for export

According to its role the TopologyManager does not directly implement the enhancements above. It simply enhances the
service properties and creates the necessary calls to a suitable RemoteServiceAdmin.
