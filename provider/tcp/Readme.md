# TCP transport provider

Allows transparent remoting using Java Serialization over TCP. The TCP provider is very light weight and
is ideal to get a first demo of remote services running.

## Endpoint Configuration

service.exported.configs: aries.tcp

| Key                      | Default       | Description                                  |
| -------------------------| ------------- | -------------------------------------------- |
| port                     |               | Port to listen on. By default a dynamic port is used |
| numThreads               | 10            | Number of listener threads to spawn          |

