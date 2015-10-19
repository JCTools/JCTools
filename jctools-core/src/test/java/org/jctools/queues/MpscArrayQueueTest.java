package org.jctools.queues;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests those methods that belong to {@link MpscArrayQueue} only and are therefore
 * not part of the standard {@link java.util.Queue} API.
 *
 * @author Ivan Valeriani
 */
public class MpscArrayQueueTest {

    private MpscArrayQueue<Integer> queue;

    @Before
    public void setUp() throws Exception {
        this.queue = new MpscArrayQueue<Integer>(16);
    }

    @After
    public void tearDown() throws Exception
    {
    }

    @Test
    public void testOfferWithThreshold() {
        int i;
        for (i = 0; i < 8; ++i) {
            //Offers succeed because current size is below the HWM.
            Assert.assertTrue(this.queue.offerIfBelowTheshold(Integer.valueOf(i), 8));
        }
        //Not anymore, our offer got rejected.
        Assert.assertFalse(this.queue.offerIfBelowTheshold(Integer.valueOf(i),
            8));
        //Also, the threshold is dynamic and different levels can be set for
        //different task priorities.
        Assert.assertTrue(this.queue.offerIfBelowTheshold(Integer.valueOf(i),
            16));
    }

}
