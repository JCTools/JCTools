JCTools
==========

Java Concurrency Tools for the JVM. This project aims to offer some concurrent data structures currently missing from
the JDK:
- Bounded lock free queues
- SPSC/MPSC/SPMC/MPMC variations for concurrent queues
- Alternative interfaces for queues (experimental)
- Offheap concurrent ring buffer for ITC/IPC purposes (experimental)
- Executor (planned)

And so on...

Build
==========
JCTools is maven built and requires an existing Maven installation and JDK8 (only for building, runtime is 1.6 compliant).

With 'MAVEN_HOME/bin' on the path and JDK8 set to your 'JAVA_HOME' you should be able to run "mvn install" from this
directory.


