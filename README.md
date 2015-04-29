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

And so on...

Build
==========
JCTools is maven built and requires an existing Maven installation and JDK8 (only for building, runtime is 1.6 compliant).

With 'MAVEN_HOME/bin' on the path and JDK8 set to your 'JAVA_HOME' you should be able to run "mvn install" from this
directory.

Install
==========

Add as a dependency using Maven:
```xml
        <dependency>
            <groupId>org.jctools</groupId>
            <artifactId>jctools-core</artifactId>
            <version>1.0</version>
        </dependency>
```
Or using Jitpack:
```xml
	<repository>
	    <id>jitpack.io</id>
	    <url>https://jitpack.io</url>
	</repository>
```
```xml
	<dependency>
	    <groupId>com.github.JCTools</groupId>
	    <artifactId>JCTools</artifactId>
	    <version>1.0</version>
	</dependency>
```
Latest code will be released via Jitpack with more official releases going through the Maven Central path too.

Benchmarks
==========
JCTools is benchmarked using both JMH benchmarks and handrolled harnesses. The benchmarks and related instructions can be
found in the jctools-benchmarks module. Instructions included in the module README.

Experimental
==========
Experimental work is available under the jctools-experimental module. Most of the stuff is developed with an eye to eventually
porting it to the core where it will be stabilized and released, but some implementations are kept purely for reference and
some may never graduate.


