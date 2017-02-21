# Aries Remote Service Admin (RSA)

The [Aries Remote Service Admin (RSA)](http://aries.apache.org/modules/rsa.html) project allows to transparently use OSGi
services for remote communication. OSGi services can be marked for export by adding a service property
service.exported.interfaces=*. Various other  properties can be used to customize how the service is to be exposed.

For more information, check out section "13 Remote Services Version 1.0" in the "OSGi Service Platform
 Service Compendium, Release 4, Version 4.2" available for public download from the OSGi Alliance.

## Distribution Provider

Aries Remote Service Admin provides two different transport layers out of the box and can be extended with custom transports.
Please refer to their individual Readme.me on how to use them.

 * `aries.tcp` - A very lightweight TCP based transport that is ideal to get a first demo running and to serve as template for custom distribution providers
 * `aries.fastbin` - A fast binary transport that uses multiplexing on a pool of java nio channels. Fastbin supports both sync and long running async calls (via Future/Promise)

## Discovery Provider

The discovery providers are responsible for finding the available endpoint descriptions of remote services. Aries RSA provides three different implementations and can be extended with custom discovery providers. The three available implementations are

 * zookeeper - Manages endpoint descriptions as zookeeper nodes.
 * local - Scans bundles for endpoint descriptions
 * config - Reads endpoint descriptions from ConfigAdmin service