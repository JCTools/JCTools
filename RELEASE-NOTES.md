4.0.1
=========
This is a major release, following the removal of the `QueueFactory`, `AtomicQueueFactory` and `org.jctools.queues.spec`
package(deprecated since 3.0). These classes are still used for testing, but are not part of the release artifacts
anymore.

New unpadded queue variants are also included in this release.

Further changes included:
- Fix table-size long math on very large tables
- #355 Adher to size semantic for queue emptiness for MpscLinkedArrayQueue variants

- 3.3.0
=========
This is a minor release with new features and several bug fixes.

Bug fixes:
- #334 NBHMLong iterators do not remove NO_KEY (thanks @matteobertozzi)
- #335 NBHM/Long/Identity iterators only removes keys if values have not changed. Now follow JDK convention.
- #336 MpscBlockingConsumerArrayQueue: fix race writing to blocked field (thanks @philipa)
- #339 `fill` wakeup call can spin forever(or until a consumer is blocked again) on MpscBlockingConsumerArrayQueue

New features:
- #340 MpscBlockingConsumerArrayQueue provide a new blocking drain variant (thanks @franz1981)

3.2.0
=========
This is a minor release with new features and several bug fixes.

Bug fixes:
- #319 Relying on test cases provided by @alefedor (#328) we apply the fix used for #205 to `NonBlockingHashMapLong` and `NonBlockingIdentityHashMap` to provide `getAndSet` like semantics for put and remove.
- #330 Fix a `NonBlockingIdentityHashMap` `replace` bug. During this fix some further work was done to bring the code closer in line to the current state of `NonBlockingHashMap`

Enhancements:
- #326 Xadd queues consumers can help producers
- #323 Update to latest JCStress (thanks @shipilev )
- Further build and doc improvements (thanks @kay @Rjbeckwith55 @pveentjer )
 
New features:
- After long incubation and following a user request (see #321), we move counters (introduced in #93 by @qwwdfsad) into core!
- Merging some experimental utils and a #264 we add a `PaddedAtomicLong`, thanks @pveentjer 

3.1.0
=========
This is a minor release with one new feature and several bug fixes.

Bug fixes:
- Use byte fields for padding (avoid upcoming false sharing problem in JDK 15+ where field ordering has changed)
- #289 Add Automatic-Module-Name header to MANIFEST.MF (thanks @vy)
- #292 Fix inconsistent isEmpty/poll/peek/offer dynamics for SpscLinkedQueue : 5fd5772#diff-b17b0df9e15e7821411b77042876eb02 (thanks @hl845740757 and @franz1981)
- Fixed potential for negative queue size for indexed queues and similar issue with isEmpty : 5fd5772#diff-f32b0a7583f04b29affe3c5f0486df4f (thanks @hl845740757 and @franz1981)
- #296 Fix peek/relaxedPeek race with poll/offer in MC queues (thanks @hl845740757 and @franz1981)
- #297 Fix inconsistent size of FF based queues causing potential size() > capacity() (thanks @hl845740757)
- #316 Fix MpscBlockingConsumerArrayQueue::poll(TimeUnit,timeout) (thanks @philipa , @njhill and @franz1981)
- #310 Fix MpmcUnboundedXaddArrayQueue::peek/relaxedPeek can load "future" elements (thanks @franz1981)

New feature:
- #314 MpscBlockingConsumerArrayQueue::offerIfBelowThreshold is added (thanks @philipa)

3.0.0
=========
This is a major version as there are some minor API breaking changes which may effect users. Please apply with care and provide feedback. The breaking changes:

Removed MpscLinkedQueue7 and MpscLinkedQueue8 and consolidate into parent. This removes the need for the builder method on MpscLinkedQueue.
Deprecated QueueFactory and spec package classes. These are not used by any users AFAICT and are only used for testing internally.
Removed some internal classes and reduced visibility of internal utilities where practical. The @InternalAPI tagging annotation is also used more extensively to discourage dependency.
We also have some great new queues for y'all to try:
- #226: XADD unbounded mpsc/mpmc queue: highly scalable linked array queues
 (from @franz1981)
- New blocking consumer MPSC (with contributions and bug fixes from @njhill)

Bug fixes:
- #209: On Arm7, non-volatile long can have unaligned address leading to error
- #216: Size of SpscGrowableArrayQueue can exceeds max capacity (from @franz1981 PR #218)
- #241: Protect the producer index in case of OutOfMemoryError (from @franz1981)
- #244: Long NBHM AssertionError when replacing missing key (thanks @fvlad for reporting and @cliffclick for assistance and review)
- Fix argument checks on fill/drain methods
- Fix LGTM warning, potential int overflow bug b467d29, 15d944c, 6367951

Improvements:
- Don't mark generated linked atomic queues as final (from @kay 9db418c)
- #211: Implement batching methods on MpmcArrayQueue (from @franz1981)
- #228: Iterator for MpscArrayQueue and MpscUnboundedArrayQueue (PR #229 from @srdo)
- Iterator support also available for the *ArrayQueue classes
- #208: MpscLinkedAtomicQueue can be made not final
- #237: Add scale to exception message to help debug netty/netty#8916 (from @johnou)

Many other improvements to testing, javadoc, formatting were made with some contributions from @Hearen @JanStureNielsen @nastra thanks!

2.1.2
=========
- PR #202 : Fix NBHM bug in remove/replace where ref equality was used to report val match instead of equals (thanks @henri-tremblay)
- PR #206 : Improved javadoc (thanks @franz1981)
- Issue #205 : NBHM remove/put getAndSet semantics issue (thanks @fvlad for reporting and @cliffclick for review)
- Issue #208 : no need for queues to be final

Further improvements to testing and code style.

Not included in the release, but very much appreciated, are contributions from @franz1981 and @qwwdfsad to the experimental part of JCTools, which may one day get merged into core, and @maseev contribution to integrate build with Coveralls.io.


2.1.1
=========
- PR #193 : Fix API break on release in MpscLinkedAtomicQueue
- Issue #194 : Fix MpscCompoundQueue::relaxedOffer bug
- Issue #196 : Fix MpscLinkedArray::fill bug
- Issue #197 : Fix MpscChunkedQueue:fill bug

2.1.0
==========
Bug fixes:
- PR #188 JvmInfo called from Atomic queues invokes Unsafe methods (thanks @kay )

Features:
- PR #187 + #186 + #185  Atomic queues are now generated from source (thanks @kay )
- PR #184 + #190 MpscLinked supports a remove method (thanks @Scottmitch )
- PR #183   Make SpscLinkedArray queues support the MPQ interface (thanks @franz1981 )
- PR #181 Testing was expanded for NBHM, and minor issue fixed (thanks @qwwdfsad )

Some further improvements to formatting, javadoc and testing and general tending to the garden by @nitsanw

2.0.2
==========
- PR #168 from @maseev - unifying the approach to queue size range checks and exceptions
- PR #173 #174 #176 from @neomatrix369 - porting the SPSC linked array queues to non-unsafe versions
- PR #172 #175 from @chbatey - porting the MPSC linked array queues to non-unsafe versions
- Fix #179 - bug in MpmcArrayQueue and MpmcAtomicArrayQueue leading to false reporting of queue full on offer which races with a poll, reported by Sharath Gururaj (I think that is @flipsha)
- JCStress testing support added by @victorparmar 
- Code tidy up by @avalanche123 
- Experimental support for MPSC proxy channels by @kay 

2.0.1
==========
BUG: #143 - toString() didn't work for many of the queues because the default implemetation relied on iterators.
BUG: #151 fixed by @cliffclick and helped by @vyazelenko (PR #152) - bringing in some fixes and improvements to NHBM

2.0
==========
PR #94 : The NonBlockingHashMap by @cliffclick is now released as part of JCTools core. Also thanks to @pcholakov for help is converting and sanity testing some of the benchmark code (PR #108).
PR #129 : MPSC linked array queues code is tidied up and split into 3 implementations. The old Chunked is here, but constructor has changed. Growable is split from chunked and Unbounded is added.
Bug #135 : Bug fix for the growable MPSC case.
PR #127 : Releasing JCTools as an OSGi bundle, thanks @CodingFabian 

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
- Added MpscChunkedArrayQueue an MPSC bounded queue aiming to replace current usage of MpscLinkedQueue in usecases where low footprint AND low GC churn are desirable. This is achieved through usage of smaller buffers which are then linked to either bigger buffers or same sized buffers as queue size demands.
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
