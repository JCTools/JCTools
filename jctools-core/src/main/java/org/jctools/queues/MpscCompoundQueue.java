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
    private static final int CPUS = Runtime.getRuntime().availableProcessors();
    long p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public MpscCompoundQueue(int capacity) {
        this(capacity, CPUS);
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
                    int s = queues[i & parallelQueuesMask].weakOffer(e);
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
}
