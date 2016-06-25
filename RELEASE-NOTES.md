1.2.1
==========
- PERF: Fix GC nepotism issue in SpscUnboundedArrayQueue (Issue #95, PR #96, courtesy of @akarnokd)
- PERF: Unified base implementation to SpscUnbounded/Growable (Issue #103, PR #107, courtesy of @pcholakov)
- BUG: IllegalStateException during MpscChunkedArrayQueue.offer(....) (Issue #115)
- BUG: new MpscChunkedArrayQueue(1024, Integer.MAX_VALUE, ...) throws confusing exception (Issue #116)
- BUG: SpscGrowable/SpscUnbounded/SpscAtomic/SpscUnboundedAtomic !isEmpty() but poll() == null (Issue #119)
- BUG: MpscArrayQueue.offerIfBelowTheshold is broken: offering is still possible when queue is full (Issue #120)


1.2
==========
- Added MpscChunkedArrayQueue an MPSC bounded queue aiming to replace current usage of MpscLinkedQueue in usecases where low footprint AND low GC churn are desirable. This is acheived through usage of smaller buffers which are then linked to either bigger buffers or same sized buffers as queue size demands.
- Fixed a GC nepotism issue in linked queues. This is not a bug but an observable generational GC side effect causing false promotion of linked nodes because of a reference from a promoted dead node. See discussion here: https://github.com/akka/akka/issues/19216
- Fixed an inconsistently handled exception on offering null elements. This was a bug in MpmcArrayQueue.
- Formatting and refactoring

Contributions made by:
 - https://github.com/guidomedina
 - https://github.com/kay


1.1
==========
- New API offered under MessagePassingQueue
- New Queues
- Minor Bug fixes


1.0
==========
JCTools offers java.util.Queue implementations for concurrent use. The queues
support a limited subset of the interface focusing on message passing rather
than collection like semantics. In particular the queues do not support:
- The iterator() method
- remove()
- contains()

Enjoy!
