Project JennyNet Network Transport Layer
Package-Version 1.0.0
Release: 30 Jan 2025
Author: (c) Wolfgang Keller, 2025
License: GNU GPL 3.0

== Status

With version 1.0.0 the project has reached a state of maturity and all
functions are implemented and well tested. While the basic conception is
the same, there are numerous design changes to the previously published
version (0.2.0). Projects which have used the old version will have to
modify their programs, possibly reconsider strategies. Fundamental 
methodical changes concern the use of threads in the layer, which is now
less extensive and more predictable in behaviour. It is guaranteed that
all event reporting, including communication results, occurs on layer-
owned threads. User threads are not involved in the working of the layer.
For a single connection all event reporting occurs sequentially; on the 
global level it is possible to have parallel event digestion on multiple
connections, while by default it all is occurring on the same thread.

From version 1.0 two serialisation methods are available: Java and Kryo.
"Java" implements the standard serialisation mechanics of Java and can
be used w/o external packages added. "Kryo" is an advanced method but
requires external packages, additional to JennyNet.

== Error Handling

The layer's policy on errors is that errors concerning the transmission
of OBJECTS lead to the closing of affected connections. This is
meaningful as the correct sequence of objects can be critical to 
applications. In contrast, errors concerning the transmission of FILES 
are limited to the abortion of the transmission and don't cause the 
closing of the connections in which they take place.

This behaviour is implemented if 'DefaultConnectionListener' is used
as super-class for custom connection-listeners. The behaviour can be
broken for more detailed reactions if event method 'objectAborted()' is
overwritten by application code. In this case serialisation based 
problems can be captured without the connection getting closed.

== External Software Packages

JennyNet optionally involves external software which is supplied ready
in the distribution package. Legal notices about this software are available
at the project wiki or in the document folder. It may not be assumed that
the license agreement for JennyNet stretches out over external software.
JennyNet can be used without external packages.

