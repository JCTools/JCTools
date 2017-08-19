package org.jctools.queues;

import java.util.Queue;

public class MpscLinkedQueueRemoveTest extends ScQueueRemoveTest {
    @Override
    protected Queue<Integer> newQueue() {
        return MpscLinkedQueue.newMpscLinkedQueue();
    }
}
