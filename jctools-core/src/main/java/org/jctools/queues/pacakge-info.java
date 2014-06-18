
/**
 * Memory layout controls and False Sharing:<br>
 * The classes in this package use what is considered at the moment the most reliable method of controlling
 * class field layout, namely inheritance. The method is described in this <a
 * href="http://psy-lob-saw.blogspot.com/2013/05/know-thy-java-object-memory-layout.html">post</a> which also
 * covers why other methods are currently suspect.<br>
 * <p>
 * Use of sun.misc.Unsafe:<br>
 * A choice is made in this library to utilize sun.misc.Unsafe for performance reasons. In this package we have two
 * use cases:
 * <ol>
 * <li>The queue counters in the queues are all inlined (i.e. are primitive fields of the queue classes). To allow
 * lazySet/CAS semantics to these fields we could use {@link AtomicLongFieldUpdater} but choose not to.
 * <li>We use Unsafe to gain volatile/lazySet access to array elements. We could use {@link AtomicReferenceArray} but
 * the extra reference chase and redundant boundary checks are considered too high a price to pay at this time.
 * <ol/>
 * 
 * @author nitsanw
 */
package org.jctools.queues;
