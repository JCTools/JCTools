package org.jctools.queues.atomic;

import org.junit.Ignore;

import java.util.Queue;

@Ignore
public class SpscLinkedAtomicQueueTest extends ScLinkedAtomicQueueTest {
    @Override
    protected Queue<Integer> newQueue() {
        return new SpscLinkedAtomicQueue<>();
    }
}
