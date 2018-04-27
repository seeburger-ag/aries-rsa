# Aries Remote Service Admin (RSA)

The [Aries Remote Service Admin (RSA)](http://aries.apache.org/modules/rsa.html) project is the reference implementation of [Remote Services](https://osgi.org/specification/osgi.cmpn/7.0.0/service.remoteservices.html
) and [Remote Service Admin](https://osgi.org/specification/osgi.cmpn/7.0.0/service.remoteserviceadmin.html) from the OSGi R7 specs.

It allows to transparently use OSGi services for remote communication. OSGi services can be marked for export by adding a service property service.exported.interfaces=*. Various other  properties can be used to customize how the service is to be exposed.

## Distribution Provider

Aries Remote Service Admin provides two different transport layers out of the box and can be extended with custom transports.

 * [aries.tcp](provider/tcp/Readme.md) - A very lightweight TCP based transport that is ideal to get a first demo running and to serve as template for custom distribution providers
 * [aries.fastbin](provider/fastbin) - A fast binary transport that uses multiplexing on a pool of java nio channels. Fastbin supports both sync and long running async calls (via Future/Promise)

## Discovery Provider

The discovery providers are responsible for finding the available endpoint descriptions of remote services. Aries RSA provides three different implementations and can be extended with custom discovery providers. The three available implementations are

 * [zookeeper](discovery/zookeeper/Readme.md) - Manages endpoint descriptions as zookeeper nodes.
 * [local](discovery/local/Readme.md) - Scans bundles for endpoint descriptions
 * [config](discovery/config/Readme.md) - Reads endpoint descriptions from ConfigAdmin service