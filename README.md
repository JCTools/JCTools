[![Build Status](https://travis-ci.org/JCTools/JCTools.svg?branch=master)](https://travis-ci.org/JCTools/JCTools)

JCTools
==========

Java Concurrency Tools for the JVM. This project aims to offer some concurrent data structures currently missing from
the JDK:
- Bounded lock free queues
- SPSC/MPSC/SPMC/MPMC variations for concurrent queues
- Alternative interfaces for queues (experimental)
- Offheap concurrent ring buffer for ITC/IPC purposes (experimental)
- Executor (planned)

JCTools offers excellent performance at a reasonable price (FREE! under the Apache 2.0 License). It's stable and in use by such distiguished frameworks as Netty, RxJava and others. JCTools is also used by commercial products to great result.

Get it NOW!
==========

Add the latest version as a dependency using Maven:
```xml
        <dependency>
            <groupId>org.jctools</groupId>
            <artifactId>jctools-core</artifactId>
            <version>1.1</version>
        </dependency>
```
Or use the awesome, built from source, <https://jitpack.io/> version:
```xml
        <dependency>
            <groupId>com.github.JCTools.JCTools</groupId>
            <artifactId>jctools-core</artifactId>
            <version>1.1</version>
        </dependency>
```
You'll need to add the Jitpack repository:
```xml
        <repository>
          <id>jitpack.io</id>
           <url>https://jitpack.io</url>
        </repository>
```



Build it from source
==========
JCTools is maven built and requires an existing Maven installation and JDK8 (only for building, runtime is 1.6 compliant).

With 'MAVEN_HOME/bin' on the path and JDK8 set to your 'JAVA_HOME' you should be able to run "mvn install" from this
directory.


Benchmarks
==========
JCTools is benchmarked using both JMH benchmarks and handrolled harnesses. The benchmarks and related instructions can be
found in the jctools-benchmarks module README. Go wild and please let us know how it did on your hardware.

Come up to the lab...
==========
Experimental work is available under the jctools-experimental module. Most of the stuff is developed with an eye to eventually
porting it to the core where it will be stabilized and released, but some implementations are kept purely for reference and
some may never graduate. Beware the Jabberwock my child.
