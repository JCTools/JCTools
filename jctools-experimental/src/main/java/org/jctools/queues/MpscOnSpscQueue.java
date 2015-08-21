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
	long p00, p01, p02, p03, p04, p05, p06, p07;
	long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscOnSpscFields<E> extends MpscOnSpscL0Pad<E> {
	private final static long QUEUES_OFFSET;

	static {
		try {
			QUEUES_OFFSET = UNSAFE.objectFieldOffset(MpscArrayQueueConsumerField.class.getDeclaredField("queues"));
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

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
