# Fastbin transport provider

Allows transparent remoting using Java Serialization over TCP. The fastbin provider uses a pool of nio tcp channels to transport data.
It can use either java object serialization or protobuf to serialize parameters and return values.
Sync remote calls have a default timeout of 5 minutes. For long running operations async calls should be used. This is indicated by having either

 * `Future`
 * `CompletableFuture`
 * `Promise`

as the return value of the remote method. The client will receive a proxy of that type that will be resolved async as soon as the server finished computation.


## Streaming Data

When large amount of data (e.g. files) need to be transfered remotely it is not advisable to use large byte arrays as this will allocate a lot of memory. Instead the fastbin transport allows to
use `InputStream` and `OutputStream` as parameter or return value. When a remote method contains such a parameter, the stream is replaced with a proxy implementation that pipes data remotely from/to the original stream.


## Transport configuration

Config PID: org.apache.aries.rsa.provider.fastbin

| Key                      | Default               | Description                                              |
| -------------------------| --------------------- | -------------------------------------------------------- |
| uri                      | tcp://0.0.0.0:2543    | The bind address to use                                  |
| exportAddress            | looks up the hostname | The ip/hostname how remote clients can reach this server |
| timeout                  | 300000                | The timeout for sync calls (default 5 minutes)           |


## Endpoint Configuration

Per service configuration using service properties.

service.exported.configs: aries.fastbin
