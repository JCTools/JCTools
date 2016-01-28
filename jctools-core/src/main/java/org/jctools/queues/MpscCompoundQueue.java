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

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.util.JvmInfo.CPUs;
import static org.jctools.util.Pow2.isPowerOfTwo;
import static org.jctools.util.Pow2.roundToPowerOfTwo;

/**
 * Use a set number of parallel MPSC queues to diffuse the contention on tail.
 */
abstract class MpscCompoundQueueL0Pad<E> extends AbstractQueue<E> implements MessagePassingQueue<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class MpscCompoundQueueColdFields<E> extends MpscCompoundQueueL0Pad<E> {
    // must be power of 2
    protected final int parallelQueues;
    protected final int parallelQueuesMask;
    protected final MpscArrayQueue<E>[] queues;

    @SuppressWarnings("unchecked")
    public MpscCompoundQueueColdFields(int capacity, int queueParallelism) {
        parallelQueues = isPowerOfTwo(queueParallelism) ? queueParallelism
                : roundToPowerOfTwo(queueParallelism) / 2;
        parallelQueuesMask = parallelQueues - 1;
        queues = new MpscArrayQueue[parallelQueues];
        int fullCapacity = roundToPowerOfTwo(capacity);
        if(fullCapacity < parallelQueues) {
            throw new IllegalArgumentException("Queue capacity must exceed parallelism");
        }
        for (int i = 0; i < parallelQueues; i++) {
            queues[i] = new MpscArrayQueue<E>(fullCapacity / parallelQueues);
        }
    }
}

abstract class MpscCompoundQueueMidPad<E> extends MpscCompoundQueueColdFields<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public MpscCompoundQueueMidPad(int capacity, int queueParallelism) {
        super(capacity, queueParallelism);
    }
}

abstract class MpscCompoundQueueConsumerQueueIndex<E> extends MpscCompoundQueueMidPad<E> {
    int consumerQueueIndex;

    public MpscCompoundQueueConsumerQueueIndex(int capacity, int queueParallelism) {
        super(capacity, queueParallelism);
    }
}

public class MpscCompoundQueue<E> extends MpscCompoundQueueConsumerQueueIndex<E> {
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public MpscCompoundQueue(int capacity) {
        this(capacity, CPUs);
    }

    public MpscCompoundQueue(int capacity, int queueParallelism) {
        super(capacity, queueParallelism);
    }

    @Override
    public boolean offer(final E e) {
        int start = (int) (Thread.currentThread().getId() & parallelQueuesMask);
        if (queues[start].offer(e)) {
            return true;
        } else {
            for (;;) {
                int status = 0;
                for (int i = start; i < start + parallelQueues; i++) {
                    int s = queues[i & parallelQueuesMask].failFastOffer(e);
                    if (s == 0) {
                        return true;
                    }
                    status += s;
                }
                if (status == parallelQueues) {
                    return false;
                }
            }
        }
    }

    @Override
    public E poll() {
        int qIndex = consumerQueueIndex & parallelQueuesMask;
        int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++) {
            e = queues[qIndex & parallelQueuesMask].poll();
            if (e != null) {
                break;
            }
        }
        consumerQueueIndex = qIndex;
        return e;
    }

    @Override
    public E peek() {
        int qIndex = consumerQueueIndex & parallelQueuesMask;
        int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++) {
            e = queues[qIndex & parallelQueuesMask].peek();
            if (e != null) {
                break;
            }
        }
        consumerQueueIndex = qIndex;
        return e;
    }

    @Override
    public int size() {
        int size = 0;
        for (MpscArrayQueue<E> lane : queues) {
            size += lane.size();
        }
        return size;
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }
    
	@Override
	public boolean relaxedOffer(E e) {
		final int parallelQueuesMask = this.parallelQueuesMask;
		int start = (int) (Thread.currentThread().getId() & parallelQueuesMask);
        final MpscArrayQueue<E>[] queues = this.queues;
        int status = 0;
		if ((status = queues[start].failFastOffer(e)) == 0) {
            return true;
        } else {
            for (;;) {
                
                final int parallelQueues = this.parallelQueues;
				for (int i = start + 1; i < start + parallelQueues; i++) {
                    int s = queues[i & parallelQueuesMask].failFastOffer(e);
                    if (s == 0) {
                        return true;
                    }
                    status += s;
                }
                if (status == parallelQueues) {
                    return false;
                }
                status = 0;
            }
        }
	}

	@Override
	public E relaxedPoll() {
		int qIndex = consumerQueueIndex & parallelQueuesMask;
        int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++) {
            e = queues[qIndex & parallelQueuesMask].relaxedPoll();
            if (e != null) {
                break;
            }
        }
        consumerQueueIndex = qIndex;
        return e;
	}

	@Override
	public E relaxedPeek() {
		int qIndex = consumerQueueIndex & parallelQueuesMask;
        int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++) {
            e = queues[qIndex & parallelQueuesMask].relaxedPeek();
            if (e != null) {
                break;
            }
        }
        consumerQueueIndex = qIndex;
        return e;
	}

	@Override
	public int capacity() {
		return queues.length * queues[0].capacity();
	}
	

    @Override
    public int drain(Consumer<E> c) {
        final int limit = capacity();
        return drain(c,limit);
    }

    @Override
    public int fill(Supplier<E> s) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int drain(Consumer<E> c, int limit) {
        for (int i=0;i<limit;i++) {
            E e = relaxedPoll();
            if(e==null){
                return i;
            }
            c.accept(e);
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void drain(Consumer<E> c,
            WaitStrategy wait,
            ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            E e = relaxedPoll();
            if(e==null){
                idleCounter = wait.idle(idleCounter);
                continue;
            }
            idleCounter = 0;
            c.accept(e);
        }
    }

    @Override
    public void fill(Supplier<E> s,
            WaitStrategy wait,
            ExitCondition exit) {
        int idleCounter = 0;
        while (exit.keepRunning()) {
            E e = s.get();
            while (!relaxedOffer(e)) {
                idleCounter = wait.idle(idleCounter);
                continue;
            }
            idleCounter = 0;    
        }
    }
}
