# TCP Transport Provider

Allows transparent remoting using Java Serialization over TCP.
The TCP provider is very light-weight and
is ideal to get a first demo of remote services running.

## Endpoint Configuration Properties

| Key                      | Default     | Description                         |
|--------------------------|-------------|-------------------------------------|
| service.exported.configs |             | Must contain "aries.tcp"            |
| aries.rsa.port           | [free port] | Port to listen on                   |
| aries.rsa.id             | [random id] | Unique id string for endpoint       |
| aries.rsa.numThreads     | 10          | Number of listener threads to spawn |
