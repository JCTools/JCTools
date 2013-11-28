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
package io.jaq.mpsc;

import io.jaq.spsc.FFBuffer;
import io.jaq.spsc.FFBufferWithOfferBatch;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Use an SPSC per producer.
 */
abstract class MPSCQueue35L0Pad {
	public long p00, p01, p02, p03, p04, p05, p06, p07;
	public long p30, p31, p32, p33, p34, p35, p36, p37;
}

@SuppressWarnings("unchecked")
abstract class MPSCQueue35Fields<E> extends MPSCQueue35L0Pad {
	private final Queue<E>[] queues = new Queue[Integer.getInteger("producers", 4)];
	protected final ThreadLocal<Queue<E>> producerQueue;
	protected int tail;
	protected Queue<E> currentQ;

	public MPSCQueue35Fields(final int capacity) {
		producerQueue = new ThreadLocal<Queue<E>>(){
		    final AtomicInteger index = new AtomicInteger();
			@Override
			protected Queue<E> initialValue() {
				return queues[index.getAndIncrement()];
			}
		};
		for(int i=0;i<queues.length;i++){
		    queues[i] = new FFBufferWithOfferBatch<E>(capacity);
		}
		currentQ = queues[0];
	}

	protected final Queue<E>[] getQueues() {
	    return queues;
    }
}

abstract class MPSCQueue35L3Pad<E> extends MPSCQueue35Fields<E> {
	public long p40, p41, p42, p43, p44, p45, p46;
	public long p30, p31, p32, p33, p34, p35, p36, p37;

	public MPSCQueue35L3Pad(int capacity) {
		super(capacity);
	}
}

public final class MPSCOnSPSCQueue<E> extends MPSCQueue35L3Pad<E> implements Queue<E> {

	public MPSCOnSPSCQueue(final int capacity) {
		super(capacity);
	}

	public boolean add(final E e) {
		if (offer(e)) {
			return true;
		}
		throw new IllegalStateException("Queue is full");
	}

	public boolean offer(final E e) {
		return producerQueue.get().offer(e);
	}

	public E poll() {
//		int pCount = getProducerCount();
//		if(pCount == 0){
//			return null;
//		}
		Queue<E>[] qs = getQueues();
//		int start = ThreadLocalRandom.current().nextInt(pCount);
//		tail++;
		
		for (int i = 0; i < qs.length; i++) {
			E e = qs[i].poll();
			if (e != null)
				return e;
		}
		return null;
	}

	public E remove() {
		final E e = poll();
		if (null == e) {
			throw new NoSuchElementException("Queue is empty");
		}

		return e;
	}

	public E element() {
		final E e = peek();
		if (null == e) {
			throw new NoSuchElementException("Queue is empty");
		}

		return e;
	}

	public E peek() {
		throw new UnsupportedOperationException();
	}

	public int size() {
		throw new UnsupportedOperationException();
	}

	public boolean isEmpty() {
		throw new UnsupportedOperationException();
	}

	public boolean contains(final Object o) {
		throw new UnsupportedOperationException();
	}

	public Iterator<E> iterator() {
		throw new UnsupportedOperationException();
	}

	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	public <T> T[] toArray(final T[] a) {
		throw new UnsupportedOperationException();
	}

	public boolean remove(final Object o) {
		throw new UnsupportedOperationException();
	}

	public boolean containsAll(final Collection<?> c) {
		for (final Object o : c) {
			if (!contains(o)) {
				return false;
			}
		}

		return true;
	}

	public boolean addAll(final Collection<? extends E> c) {
		for (final E e : c) {
			add(e);
		}

		return true;
	}

	public boolean removeAll(final Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	public boolean retainAll(final Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	public void clear() {
		Object value;
		do {
			value = poll();
		} while (null != value);
	}
}
