package org.jctools.queues;

import java.util.ArrayList;
import java.util.Collection;

import org.jctools.queues.spec.ConcurrentQueueSpec;
import org.jctools.queues.spec.Ordering;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SpscUnboundedMessagePassingQueueSanityTest extends MessagePassingQueueSanityTest {

   @Parameterized.Parameters
   public static Collection<Object[]> parameters() {
      ArrayList<Object[]> list = new ArrayList<Object[]>();
      list.add(makeMpq(0, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(2)));
      list.add(makeMpq(0, 1, 0, Ordering.FIFO, new SpscUnboundedArrayQueue<>(64)));
      return list;
   }

   public SpscUnboundedMessagePassingQueueSanityTest(ConcurrentQueueSpec spec, MessagePassingQueue<Integer> queue) {
      super(spec, queue);
   }
}
