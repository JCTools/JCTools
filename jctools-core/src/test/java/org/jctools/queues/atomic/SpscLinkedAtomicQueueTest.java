package org.jctools.queues.atomic;

import java.util.Queue;

public class SpscLinkedAtomicQueueTest extends ScLinkedAtomicQueueTest {
    @Override
    protected Queue<Integer> newQueue() {
        return new SpscLinkedAtomicQueue<>();
    }
}
