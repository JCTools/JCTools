package org.jctools.queues.varhandle;

import static org.jctools.util.TestUtil.makeParams;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class QueueSanityTestMpscBlockingConsumerVarHandle extends QueueSanityTest {
  public QueueSanityTestMpscBlockingConsumerVarHandle(ConcurrentQueueSpec spec, Queue<Integer> queue) {
    super(spec, queue);
  }

  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    ArrayList<Object[]> list = new ArrayList<>();
    list.add(makeParams(0, 1, 2, Ordering.FIFO, new MpscBlockingConsumerArrayQueueVarHandle<>(2)));
    list.add(
        makeParams(0, 1, SIZE, Ordering.FIFO, new MpscBlockingConsumerArrayQueueVarHandle<>(SIZE)));
    return list;
  }
}
