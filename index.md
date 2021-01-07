JCTools - Java Concurrency Tools for the JVM
==========
This project aims to offer some concurrent data structures currently missing from the JDK:
- SPSC/MPSC/SPMC/MPMC Bounded lock free queues
- SPSC/MPSC Unbounded lock free queues
- Alternative interfaces for queues (experimental)
- Offheap concurrent ring buffer for ITC/IPC purposes (experimental)
- Single Writer Map/Set implementations (planned)
- Low contention stats counters (planned)
- Executor (planned)


Benchmarks
==========
JCTools is benchmarked using both JMH benchmarks and handrolled harnesses. The benchmarks and related instructions can be found in the jctools-benchmarks module. Instructions included in the module README.

Experimental
==========
Experimental work is available under the jctools-experimental module. Most of the stuff is developed with an eye to eventually porting it to the core where it will be stabilized and released, but some implementations are kept purely for reference and some may never graduate.

