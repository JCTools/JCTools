/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.queues;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Use an SPSC per producer.
 */
abstract class MpscOnSpscL0Pad<E> extends AbstractQueue<E> {
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

abstract class MpscOnSpscFields<E> extends MpscOnSpscL0Pad<E> {
	private final static long QUEUES_OFFSET = fieldOffset(MpscOnSpscFields.class, "queues");

	protected final ThreadLocal<Queue<E>> producerQueue;
	ReferenceQueue<Thread> refQ = new ReferenceQueue<Thread>();
	protected volatile Queue<E>[] queues;
	private static class ThreadWeakRef extends WeakReference<Thread> {
		final Object r;
		public ThreadWeakRef(ReferenceQueue<? super Thread> q, Object r) {
			super(Thread.currentThread(), q);
			this.r = r;
		}
	}
	private List<ThreadWeakRef> weakRefHolder = Collections.synchronizedList(new ArrayList<ThreadWeakRef>());

	@SuppressWarnings("unchecked")
	public MpscOnSpscFields(final int capacity) {
		producerQueue = new ThreadLocal<Queue<E>>() {
			@Override
			protected Queue<E> initialValue() {
				Queue<E> q = new SpscArrayQueue<E>(capacity);
				addQueue(q);
				return q;
			}
		};
		Thread refProcessor = new Thread() {
			@Override
			public void run() {
				while(true) {
					try {
						ThreadWeakRef remove = (ThreadWeakRef)refQ.remove();
						removeQueue(remove.r);
						weakRefHolder.remove(remove);
					} catch (InterruptedException e) {
						if (Thread.interrupted()) {
							return;
						}
					}
				}
			}
		};
		refProcessor.setDaemon(false);
		refProcessor.start();
		queues = new Queue[0];
	}

	@SuppressWarnings("rawtypes")
	protected final void addQueue(Queue<E> q) {
		Queue[] oldQs;
		Queue[] newQs;
		do {
			oldQs = queues;
			newQs = new Queue[oldQs.length + 1];
			System.arraycopy(oldQs, 0, newQs, 0, oldQs.length);
			newQs[oldQs.length] = q;
		} while (!UNSAFE.compareAndSwapObject(this, QUEUES_OFFSET, oldQs, newQs));
		weakRefHolder.add(new ThreadWeakRef(refQ, q));
	}

	@SuppressWarnings("rawtypes")
	protected final void removeQueue(Object q) {
		Queue[] oldQs;
		Queue[] newQs;
		do {
			oldQs = queues;
			int i = 0;
			final int length = oldQs.length;
			for (; i < length; i++) {
				if (q == oldQs[i]) {
					break;
				}
			}
			// not here...
			if (i == length) {
				return;
			}
			// copy over all but that element
			newQs = new Queue[length - 1];
			System.arraycopy(oldQs, 0, newQs, 0, i);
			System.arraycopy(oldQs, i + 1, newQs, i, length - i - 1);
		} while (!UNSAFE.compareAndSwapObject(this, QUEUES_OFFSET, oldQs, newQs));
	}

	int numberOfQueues(){
		return queues.length;
	}
}

public final class MpscOnSpscQueue<E> extends MpscOnSpscFields<E>implements Queue<E> {
	long p40, p41, p42, p43, p44, p45, p46;
	long p30, p31, p32, p33, p34, p35, p36, p37;

	public MpscOnSpscQueue(final int capacity) {
		super(capacity);
	}

	@Override
	public boolean offer(final E e) {
		return producerQueue.get().offer(e);
	}

	@Override
	public E poll() {
		Queue<E>[] qs = this.queues;
		final int start = ThreadLocalRandom.current().nextInt(qs.length);
		for (int i = start; i < qs.length; i++) {
			E e = qs[i].poll();
			if (e != null)
				return e;
		}
		for (int i = 0; i < start; i++) {
			E e = qs[i].poll();
			if (e != null)
				return e;
		}
		return null;
	}

	@Override
	public E peek() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		Queue<E>[] queues = this.queues;
		int sum = 0;
		for (Queue<E> q : queues) {
			sum += q.size();
		}
		return sum;
	}

	@Override
	public boolean isEmpty() {
		Queue<E>[] queues = this.queues;
		for (Queue<E> q : queues) {
			if (!q.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public Iterator<E> iterator() {
		throw new UnsupportedOperationException();
	}
}
