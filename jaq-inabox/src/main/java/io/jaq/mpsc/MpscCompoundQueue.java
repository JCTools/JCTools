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

import static io.jaq.util.Pow2.findNextPositivePowerOfTwo;
import static io.jaq.util.Pow2.isPowerOf2;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Use a set number of parallel MPSC queues to diffuse the contention on tail.
 */
abstract class MPSCQueue33L0Pad {
	public long p00, p01, p02, p03, p04, p05, p06, p07;
	public long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MPSCQueue33ColdFields<E> extends MPSCQueue33L0Pad {
	// must be power of 2
	private static final int CPUS = Runtime.getRuntime().availableProcessors();
	protected static final int PARALLEL_QUEUES = isPowerOf2(CPUS)?CPUS:
		findNextPositivePowerOfTwo(CPUS)/2;
	protected static final int PARALLEL_QUEUES_MASK = PARALLEL_QUEUES - 1;
	protected final MpscConcurrentQueue<E>[] queues;

	@SuppressWarnings("unchecked")
	public MPSCQueue33ColdFields(int capacity) {
		queues = new MpscConcurrentQueue[PARALLEL_QUEUES];
		for (int i = 0; i < PARALLEL_QUEUES; i++) {
			queues[i] = new MpscConcurrentQueue<E>(findNextPositivePowerOfTwo(capacity) / PARALLEL_QUEUES);
		}
	}
}

abstract class MPSCQueue33L3Pad<E> extends MPSCQueue33ColdFields<E> {
	public long p40, p41, p42, p43, p44, p45, p46;
	public long p30, p31, p32, p33, p34, p35, p36, p37;

	public MPSCQueue33L3Pad(int capacity) {
		super(capacity);
	}
}

public final class MpscCompoundQueue<E> extends MPSCQueue33L3Pad<E> implements Queue<E> {

	public MpscCompoundQueue(final int capacity) {
		super(capacity);
	}

	public boolean add(final E e) {
		if (offer(e)) {
			return true;
		}
		throw new IllegalStateException("Queue is full");
	}

	public boolean offer(final E e) {
		if (null == e) {
			throw new NullPointerException("Null is not a valid element");
		}
		int start = (int) (Thread.currentThread().getId() & PARALLEL_QUEUES_MASK);
		if (queues[start].offer(e)) {
			return true;
		} else {
			for (;;) {
				int status = 0;
				for (int i = start; i < start + PARALLEL_QUEUES; i++) {
					int s = queues[i & PARALLEL_QUEUES_MASK].offerStatus(e);
					if (s == 0) {
						return true;
					}
					status += s;
				}
				if (status == PARALLEL_QUEUES) {
					return false;
				}
			}
		}
	}

	public E poll() {
		int start = ThreadLocalRandom.current().nextInt(PARALLEL_QUEUES);
		for (int i = start; i < start + PARALLEL_QUEUES; i++) {
			E e = queues[i & PARALLEL_QUEUES_MASK].poll();
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
