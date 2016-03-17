# Local Discovery

Scans bundles for endpoint descriptions in the path `OSGI-INF/remote-service/*.xml`. The descriptions must
use the namespace http://www.osgi.org/xmlns/rsa/v1.0.0 defined in the Remote Service Admin Spec.  
Each endpoint-description record in the file represents a remote endpoint. 

The local discovery module will notify all interested EndpointListeners of each described endpoint. 
This will cause the TopologyManager to let the RemoteServiceAdmin create local proxy services for 
the remote endpoints.

## Example

```
<endpoint-descriptions xmlns="http://www.osgi.org/xmlns/rsa/v1.0.0">
  <endpoint-description>
    <property name="objectClass">
      <array>
        <value>org.apache.aries.rsa.examples.echotcp.api.EchoService</value>
      </array>
    </property>
    <property name="endpoint.id">tcp://localhost:3456</property>
    <property name="service.imported.configs">aries.tcp</property>
  </endpoint-description>
</endpoint-descriptions>
```

