# Zookeeper Discovery

Manages endpoint descriptions as zookeeper nodes. 

* Local endpoints are published to zookeeper
* Interests for services in the form of EndpointListener scopes are looked up in zookeeper and the listeners are informed about any changes
on the matching endpoints

## Discovery Configuration

PID: org.apache.aries.rsa.discovery.zookeeper

| Key               | Default       | Description                                  |
| ------------------| ------------- | -------------------------------------------- |
| zookeeper.host    | localhost     | Hostname or ipadress of the zookeeper server |
| zookeeper.port    | 2181          | Client port of the zookeeper server          |
| zookeeper.timeout | 3000          | Session timeout in ms                        |

At least an empty config must be present to start the zookeeper discovery. The karaf feature will install such a config by default.

## Zookeeper Server Configuration

PID: org.apache.aries.rsa.discovery.zookeeper.server

| Key               | Default       | Description                                  
| ------------------| -------------:| -------------------------------------------- 
| clientPort        | 2181          | Port to listen on for client connections     
| tickTime          | 2000          |                                              
| initLimit         | 10            | 
| syncLimit         | 5             |
| dataDir           | zkdata        |

At least an empty config must be created manually to start the zookeeper server.

