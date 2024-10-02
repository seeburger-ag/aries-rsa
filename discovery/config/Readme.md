# Config Discovery

Reads endpoint descriptions from ConfigAdmin configurations factory pid **org.apache.aries.rsa.discovery.config**.

The config discovery module will notify all interested EndpointListeners of each described endpoint.
This will cause the TopologyManager to let the RemoteServiceAdmin create local proxy services for
the remote endpoints.

## Example

Configuration properties in org.apache.aries.rsa.discovery.config-test.cfg file

```
endpoint.id = fooservice
service.imported = true
service.imported.configs = org.apache.cxf.rs
objectClass = org.acme.foo.rest.api.FooService
org.apache.cxf.rs.address = http://localhost:9100/
```
