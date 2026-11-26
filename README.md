[![CI](https://github.com/JCTools/JCTools/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/JCTools/JCTools/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/JCTools/JCTools/badge.svg?branch=master)](https://coveralls.io/github/JCTools/JCTools?branch=master)

JCTools
==========
Java Concurrency Tools for the JVM. This project aims to offer some concurrent data structures currently missing from
the JDK:

- SPSC/MPSC/SPMC/MPMC variations for concurrent queues:
    * SPSC - Single Producer Single Consumer (Wait Free, bounded and unbounded)
    * MPSC - Multi Producer Single Consumer (Lock less, bounded and unbounded)
    * SPMC - Single Producer Multi Consumer (Lock less, bounded)
    * MPMC - Multi Producer Multi Consumer (Lock less, bounded)

- SPSC/MPSC linked array queues (bounded and unbounded) offer a balance between performance, allocation and footprint
- MPSC/MPMC XAdd based, unbounded linked array queues offer reduced contention costs for producers (using XADD instead
  of a CAS loop), and pooled queue chunks for reduced allocation.
- An expanded queue interface (MessagePassingQueue):
    * relaxedOffer/Peek/Poll: trade off conflated guarantee on full/empty queue state with improved performance.
    * drain/fill: batch read and write methods for increased throughput and reduced contention
- A blocking consumer MPSC queue (MpscBlockingConsumerArrayQueue) for when you want your consumer to park rather than
  spin, with blocking drain and offerIfBelowThreshold on offer
- Cliff Click's NonBlockingHashMap and friends (NonBlockingHashMapLong, NonBlockingIdentityHashMap, NonBlockingHashSet,
  NonBlockingSetInt) - lock free maps and sets.
- Concurrent counters (Counter/CountersFactory) - Padded atomic counter alternatives

Many queues are available in both `Unsafe` (default, uses `sun.misc.Unsafe`) and `Atomic` (relying
on `AtomicFieldUpdater`) variations, as well as `Unpadded` (lower footprint by removing false sharing avoiding field
padding). JDK11+ users can also get `VarHandle` based variants (padded and unpadded) from the `jctools-core-jdk11`
artifact - no `Unsafe` in sight, and faster than the `AtomicFieldUpdater` flavour.

Contributions/suggestions are most welcome. JCTools has enjoyed support from the community
and contributions in the form of issues/tests/documentation/code have helped it grow.
JCTools offers excellent performance at a reasonable price (FREE! under the [Apache 2.0 License](LICENSE)). It's stable
and in use by such distinguished frameworks as Netty, RxJava and others. JCTools is also used by commercial products to
great result.

Get it NOW!
==========
Add the latest version as a dependency using Maven:

```xml

<dependency>
    <groupId>org.jctools</groupId>
    <artifactId>jctools-core</artifactId>
    <version>4.0.6</version>
</dependency>
```

On JDK11+ you can swap in `jctools-core-jdk11` for the `VarHandle` queues. It depends on `jctools-core`, so you get
everything above plus the new variants:

```xml

<dependency>
    <groupId>org.jctools</groupId>
    <artifactId>jctools-core-jdk11</artifactId>
    <version>4.0.6</version>
</dependency>
```

A word of warning: **4.0.4 was published with incorrect bytecode version and is superseded by 4.0.5**, which carries
the same source. If you are pinned to 4.0.4, move up. 4.0.3 is likewise just a re-cut of 4.0.2 with no code changes.

You can use the built from source, <https://jitpack.io/> version, you'll need to add the Jitpack repository:

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
    <version>v4.0.6</version>
</dependency>
```

You can also depend on latest snapshot from this repository (live on the edge) by setting the version to
'5.0.0-SNAPSHOT' and adding the snapshots repository:

```xml

<repository>
    <id>maven-snapshots</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    <snapshots>
        <enabled>true</enabled>
    </snapshots>
</repository>
```

Docs and Release Notes
==========
The Javadoc is the reference and there's a fair amount of design rationale hiding in the package docs, so start there:
<https://javadoc.io/doc/org.jctools/jctools-core>

Release notes for every version live on the [GitHub Releases page](https://github.com/JCTools/JCTools/releases).

What's in the box
==========
- **jctools-core** - the queues, maps and counters. This is the artifact you want.
- **jctools-core-jdk11** - the `VarHandle` variants for JDK11+ users. Depends on core.
- **jctools-build** - the code generators. Queue variants (`Atomic`, `Unpadded`, `VarHandle`) are generated from the `Unsafe` implementations rather than hand written, which is why the build has a `generate-sources` step.
- **jctools-benchmarks** - JMH and handrolled benchmarks, see the module [README](jctools-benchmarks/README.md).
- **jctools-concurrency-test** - the jcstress based correctness harness.
- **jctools-channels** - off-heap channels for inter-thread messaging.
- **jctools-experimental** - see "Come up to the lab..." below.

Build it from source
==========
JCTools is maven built and requires an existing Maven installation (3.5 or newer) and JDK11 or newer. Note that this is
a *build* requirement - the `jctools-core` artifact itself targets Java 8. The build needs 11 because of the
`jctools-core-jdk11` module, which is where the `VarHandle` code lives.

With 'MAVEN_HOME/bin' on the path and JDK11+ set to your 'JAVA_HOME' you should be able to run "mvn install" from this
directory.

Two things to know before sending a patch:

- Formatting is enforced. Spotless runs at the 'verify' phase and will fail the build, so run "mvn spotless:apply".
- Many queues are generated, so edit the base implementation (e.g. `SpscArrayQueue`) and never the generated variant.
  A full "mvn clean install" regenerates everything - if you skip it your change will look like it did nothing.


Reusing test utilities from source
==========
The development build attaches a `tests` JAR to `jctools-core`, containing helpers such as `QueueSanityTest`.
These are internal test classes with no compatibility guarantee. The 4.0.6 release does not include this classifier;
install the development version from source as described above, then use the same version for both artifacts:

```xml
<dependencies>
    <dependency>
        <groupId>org.jctools</groupId>
        <artifactId>jctools-core</artifactId>
        <version>5.0.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>org.jctools</groupId>
        <artifactId>jctools-core</artifactId>
        <version>5.0.0-SNAPSHOT</version>
        <classifier>tests</classifier>
        <type>test-jar</type>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.13.2</version>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.hamcrest</groupId>
        <artifactId>hamcrest-all</artifactId>
        <version>1.3</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Test dependencies are not inherited from the test JAR. `QueueSanityTest` needs JUnit 4 and Hamcrest, as shown above;
other helpers may need additional dependencies. `-DskipTests` still compiles and packages test classes, whereas
`-Dmaven.test.skip=true` skips their compilation and can omit the test JAR.


But I have a zero-dependency/single-jar project
==========
While you are free to copy & extend JCTools, we would much prefer it if you have a versioned dependency on JCTools to
enable better support, upgrade paths and discussion. The shade plugin for Maven/Gradle is the preferred way to get
JCTools fused with your library. Examples are available in
the [ShadeJCToolsSamples](https://github.com/JCTools/ShadeJCToolsSamples) project.

For the module minded, the jars ship both OSGi and JPMS metadata - `jctools-core` is bundle and module
`org.jctools.core`, and takes an optional dependency on `jdk.unsupported` (that'll be the `Unsafe`).


Benchmarks
==========
JCTools is benchmarked using both JMH benchmarks and handrolled harnesses. The benchmarks and related instructions can
be
found in the jctools-benchmarks module [README](jctools-benchmarks/README.md). Go wild and please let us know how it did
on your hardware.

Concurrency Testing
==========

```bash
mvn package
cd jctools-concurrency-test
java -jar target/concurrency-test.jar -v
```

There's also a manually triggered `jcstress` GitHub Actions workflow if you'd rather let someone else's machine sweat.

Come up to the lab...
==========
Experimental work is available under the jctools-experimental module. Most of the stuff is developed with an eye to
eventually porting it to the core where it will be stabilized and released, but some implementations are kept purely for
reference and some may never graduate. Beware the Jabberwock my child.

Have Questions? Suggestions?
==========
The best way to discuss JCTools is on the GitHub issues system. Any question is good, and GitHub provides a better
platform for knowledge sharing than twitter/mailing-list/gitter (or at least that's what we think).

Thanks!!!
=====
We have kindly been awarded [IntelliJ IDEA](https://www.jetbrains.com/idea/) licences
by [JetBrains](https://www.jetbrains.com/) to aid in the development of JCTools. It's a great suite of tools which has
benefited the developers and ultimately the community.

It's an awesome and inspiring company, [**BUY THEIR PRODUCTS
NOW!!!**](https://www.jetbrains.com/store/#edition=commercial)

JCTools has enjoyed a steady stream of PRs, suggestions and user feedback. It's a community! Thank you all for getting
involved!
