/**
 * This package aims to fill a gap in current JDK implementations in offering lock free (wait free where possible)
 * queues for inter-thread message passing with finer grained guarantees and an emphasis on performance.<br>
 * At the time of writing the only lock free queue available in the JDK is {@link java.util.concurrent.ConcurrentLinkedQueue}
 * which is an unbounded multi-producer, multi-consumer queue which is further encumbered by the need to implement
 * the full range of {@link java.util.Queue} methods. In this package we offer a range of implementations:
 * <ol>
 * <li> Bounded/Unbounded SPSC queues - Serving the Single Producer Single Consumer use case.
 * <li> Bounded/Unbounded MPSC queues - The Multi Producer Single Consumer case also had a multi lane implementation on
 * offer which trades the FIFO ordering for reduced contention.
 * <li> Bounded SPMC/MPMC queues
 * </ol> 
 * 
 * <p>
 * <b>Memory layout controls and False Sharing:</b><br>
 * The classes in this package use what is considered at the moment the most reliable method of controlling
 * class field layout, namely inheritance. The method is described in this <a
 * href="http://psy-lob-saw.blogspot.com/2013/05/know-thy-java-object-memory-layout.html">post</a> which also
 * covers why other methods are currently suspect.<br>
 * Note that we attempt to tackle both active (write/write) and passive(read/write) false sharing case:
 * <ol>
 * <li> Hot counters (or write locations) are padded.
 * <li> Read-Only shared fields are padded.
 * <li> Array edges are padded.
 * </ol> 
 * <p>
 * <b>Use of sun.misc.Unsafe:</b><br>
 * A choice is made in this library to utilize sun.misc.Unsafe for performance reasons. In this package we have two
 * use cases:
 * <ol>
 * <li>The queue counters in the queues are all inlined (i.e. are primitive fields of the queue classes). To allow
 * lazySet/CAS semantics to these fields we could use {@link AtomicLongFieldUpdater} but choose not to.
 * <li>We use Unsafe to gain volatile/lazySet access to array elements. We could use {@link AtomicReferenceArray} but
 * the extra reference chase and redundant boundary checks are considered too high a price to pay at this time.
 * </ol>
 * 
 * @author nitsanw
 */
package org.jctools.queues;

