[![Total alerts](https://img.shields.io/lgtm/alerts/g/JCTools/JCTools.svg?logo=lgtm&logoWidth=18)](https://lgtm.com/projects/g/JCTools/JCTools/alerts/)
[![Coverage Status](https://coveralls.io/repos/github/JCTools/JCTools/badge.svg?branch=master)](https://coveralls.io/github/JCTools/JCTools?branch=master)
[![Build Status](https://travis-ci.org/JCTools/JCTools.svg?branch=master)](https://travis-ci.org/JCTools/JCTools)
[![Gitter](https://badges.gitter.im/JCTools/JCTools.svg)](https://gitter.im/JCTools/JCTools?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)

JCTools
==========
Java Concurrency Tools for the JVM. This project aims to offer some concurrent data structures currently missing from
the JDK:
- SPSC/MPSC/SPMC/MPMC variations for concurrent queues:
  * SPSC - Single Producer Single Consumer (Wait Free, bounded and unbounded)
  * MPSC - Multi Producer Single Consumer (Lock less, bounded and unbounded)
  * SPMC - Single Producer Multi Consumer (Lock less, bounded)
  * MPMC - Multi Producer Multi Consumer (Lock less, bounded)
  
- SPSC/MPSC linked array queues offer a balance between performance, allocation and footprint

- An expanded queue interface (MessagePassingQueue):
  * relaxedOffer/Peek/Poll: trade off conflated guarantee on full/empty queue state with improved performance.
  * drain/fill: batch read and write methods for increased throughput and reduced contention
  
There's more to come and contributions/suggestions are most welcome. JCTools has enjoyed support from the community
and contributions in the form of issues/tests/documentation/code have helped it grow.
JCTools offers excellent performance at a reasonable price (FREE! under the Apache 2.0 License). It's stable and in
use by such distinguished frameworks as Netty, RxJava and others. JCTools is also used by commercial products to great result.

Get it NOW!
==========
Add the latest version as a dependency using Maven:
```xml
        <dependency>
            <groupId>org.jctools</groupId>
            <artifactId>jctools-core</artifactId>
            <version>3.0.0</version>
        </dependency>
```

Or use the awesome, built from source, <https://jitpack.io/> version, you'll need to add the Jitpack repository:
```xml
        <repository>
          <id>jitpack.io</id>
           <url>https://jitpack.io</url>
        </repository>
```

And setup the following dependency:
```xml
        <dependency>
            <groupId>com.github.JCTools.JCTools</groupId>
            <artifactId>jctools-core</artifactId>
            <version>v3.0.0</version>
        </dependency>
```

You can also depend on latest snapshot from this repository (live on the edge) by setting the version to '3.0.1-SNAPSHOT'.


Build it from source
==========
JCTools is maven built and requires an existing Maven installation and JDK8 (only for building, runtime is 1.6 compliant).

With 'MAVEN_HOME/bin' on the path and JDK8 set to your 'JAVA_HOME' you should be able to run "mvn install" from this
directory.


But I have a zero-dependency/single-jar project
==========
While you are free to copy & extend JCTools, we would much prefer it if you have a versioned dependency on JCTools to
enable better support, upgrade paths and discussion. The shade plugin for Maven/Gradle is the preferred way to get
JCTools fused with your library. Examples are available in the [ShadeJCToolsSamples](https://github.com/JCTools/ShadeJCToolsSamples) project.


Benchmarks
==========
JCTools is benchmarked using both JMH benchmarks and handrolled harnesses. The benchmarks and related instructions can be
found in the jctools-benchmarks module [README](jctools-benchmarks/README.md). Go wild and please let us know how it did on your hardware.

Concurrency Testing
===========
```
mvn package
cd jctools-concurrency-test
java -jar target/concurrency-test.jar -v
```
Come up to the lab...
==========
Experimental work is available under the jctools-experimental module. Most of the stuff is developed with an eye to
eventually porting it to the core where it will be stabilized and released, but some implementations are kept purely for reference and some may never graduate. Beware the Jabberwock my child.

Have Questions? Suggestions?
==========
The best way to discuss JCTools is on the GitHub issues system. Any question is good, and GitHub provides a better
platform for knowledge sharing than twitter/mailing-list/gitter (or at least that's what we think).

Thanks!!!
=====
We have kindly been awarded [IntelliJ IDEA](https://www.jetbrains.com/idea/) licences by [JetBrains](https://www.jetbrains.com/) to aid in the development of JCTools. It's a great suite of tools which has benefited the developers and ultimately the community.

It's an awesome and inspiring company, [**BUY THEIR PRODUCTS NOW!!!**](https://www.jetbrains.com/store/#edition=commercial)

JCTools has enjoyed a steady stream of PRs, suggestions and user feedback. It's a community! Thank you all for getting involved!
