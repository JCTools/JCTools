package org.jctools.queues.varhandle;

import static org.jctools.queues.varhandle.utils.TestUtils.makeMpq;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import org.jctools.queues.QueueSanityTest;
import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class QueueSanityTestSpmcArrayVarHandle extends QueueSanityTest {
  public QueueSanityTestSpmcArrayVarHandle(ConcurrentQueueSpec spec, Queue<Integer> queue) {
    super(spec, queue);
  }

  @Parameterized.Parameters
  public static Collection<Object[]> parameters() {
    ArrayList<Object[]> list = new ArrayList<Object[]>();
    // need at least size 2 for this test
    list.add(makeMpq(1, 0, 1, Ordering.FIFO));
    list.add(makeMpq(1, 0, SIZE, Ordering.FIFO));
    return list;
  }
}
