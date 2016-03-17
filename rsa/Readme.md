# Remote Service Admin

Is called by the Topology Manager to expose local services as remote endpoints and create local proxy services as
clients for remote endpoints.

Aries RSA has a custom [SPI DistributionProvider](https://github.com/apache/aries-rsa/blob/master/spi/src/main/java/org/apache/aries/rsa/spi/DistributionProvider.java) that allows to easily create new transports and serializations. 
The RemoteServiceAdmin bundle tracks such provider services and relays the actual creation of endpoints and proxies to 
suitable providers.
