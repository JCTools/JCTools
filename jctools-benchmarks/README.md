JCTools Benchmarks
-------
This project contains performance tests designed to stretch the queue implementations and expose contention as well
as providing some baseline quantities to consider when doing back of the envelope estimations. The benchmarks cover
some basic operation costs, latency (for a SPSC usecase) and throughput.

Building the benchmarks
-------
The benchmarks are maven built and involve some code generation for the JMH part. As such it is required that you
run 'mvn clean install' on changing the code. As the codebase is rather small it is recommended that you run this
command from the parent folder to avoid missed changes from other packages.

Running the benchmarks: General
-------
It is recommended that you consider some basic benchmarking practices before running benchmarks:

 1. Use a quiet machine with enough CPUs to run the number of threads you mean to run.
 2. Set the CPU freq to avoid variance due to turbo boost/heating.
 3. Use an OS tool such as taskset to pin the threads in the topology you mean to measure.

The benchmarks included are both JMH benchmarks(under org.jctools.jmh) and some handrolled benchmarks (under
org.jctools.handrolled). At the moment the queue type is set using a system property 'q.type' though in the near future
we should switch to using JMH parameters where possible for JMH.
Note that all SPSC benchmarks can be used to test MPMC/SPMC/MPSC queues as they cover a particular case for those.

Running the JMH Benchmarks
-----
To run all JMH benchmarks:

    java -jar target/microbenchmarks.jar -f <number-of-forks> -wi <number-of-warmup-iterations> -i <number-of-iterations>
To list available benchmarks:

    java -jar target/microbenchmarks.jar -l
Some JMH help:

    java -jar target/microbenchmarks.jar -h
Example:

To run the throughput benchmark for queue type 7 (queue type numbers are set in the QueueByTypeFactory class):

    java -Dq.type=7 -jar target/microbenchmarks.jar ".*.QueueThroughput.*"

This particular benchmark allows the testing of multiple consumers/producer threads by using thread groups:

    java -Dq.type=7 -jar target/microbenchmarks.jar ".*.QueueThroughput.*" -tg 4,4

The tg option will set 4 consumers and 4 producers to the benchmark. You can play with the other options as decribed
in the JMH help.

Running the handrolled benchmarks
-----
The handrolled benchmarks are currently only covering SPSC throughput. These can be run by directly invoking the class:

    java -Dq.type=7 -cp target/microbenchmarks.jar org.jctools.handrolled.throughput.spsc.QueuePerfTest


