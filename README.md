# JennyNet
    Java transport layer on the TCP/IP network
    License:   GNU AGPL 3.0
    Author: Wolfgang Keller
    Published Software Version:  1.0.0 of 30. Jan 2025

_JennyNet_ eases the way in which Java objects and files can be transmitted over the Internet by Java applications in the context of a client/server or peer-to-peer communication architecture. _JennyNet_ is designed to work efficiently on multi-core computers and enables parallel processing of sending and receiving streams. Atomic communication units are objects (graphs of objects) of serialisable Java classes and files of the file-system. Communication is divided into channels which are described by the transport data-types and transmit-priority attributes. Each channel ensures that the sequence of posting objects or files is replicated at the remote end.

## Main Features

-    multi-threaded design optimises execution on multi-core machines and relieves applications from most blocking considerations.
-    automated serialisation/de-serialisation of Java objects and object graphs of registered classes.
-    two standard serialisation methods: a) Java Serialization framework, b) **Kryo** serialisation, plus a void slot for custom serialisations.
-    comfortable "background" file transfers (won't affect object communication).
-    5 priority channels for object and file transmissions; sending actions are performed with priority attributes. This allows data to be instantly preferred to ongoing transmissions of a lower priority channel.
-    event-driven communication of application and layer, optional polling service.
-    highly reactive interface; does not block application when large or multiple objects or files are being transmitted.
-    extra services like PING signals, ALIVE signaling and IDLE connection alarming.
-    thread impact is 2-3 per connection, plus 2-4 on the static level.
-    well documented interfaces and Javadoc-API

##  Release and Requirements

-  Java Standard Library (jennynet-[version].jar) for standard Java serialisation, size of 150 KiB.
-  Java Full Library (jennynet-all-[version].jar) for Java and Kryo serialisation, size of 600 KiB.
-  Java Virtual Machine (JRE) of version 1.8 or higher.
-  "all"-package includes external software (Kryo-4.0) for the optional use of Kryo-serialisation.

### Maven Central
pkg:maven/io.github.kse2/jennynet@1.0.0
<br>pkg:maven/io.github.kse2/jennynet-all@1.0.0

    <dependency>
        <groupId>io.github.kse2</groupId>
        <artifactId>jennynet</artifactId>
        <version>1.0.0</version>
    </dependency>

    <dependency>
        <groupId>io.github.kse2</groupId>
        <artifactId>jennynet-all</artifactId>
        <version>1.0.0</version>
    </dependency>

## Documentation
- User Manual: https://github.com/KSE2/JennyNet/wiki/User-Manual
- Kryo: https://github.com/EsotericSoftware/kryo/wiki/Kryo-v4

