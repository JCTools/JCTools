package org.jctools.queues;

import java.util.Queue;

public class ScQueueRemoveTestMpscLinked extends ScQueueRemoveTest {
    @Override
    protected Queue<Integer> newQueue() {
        return new MpscLinkedQueue();
    }
}
