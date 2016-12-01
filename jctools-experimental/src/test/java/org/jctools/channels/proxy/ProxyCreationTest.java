package org.jctools.channels.proxy;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

import org.jctools.channels.WaitStrategy;
import org.jctools.channels.mpsc.MpscOffHeapFixedSizeRingBuffer;
import org.jctools.channels.proxy.DemoIFace.CustomType;
import org.jctools.channels.spsc.SpscOffHeapFixedSizeRingBuffer;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ProxyCreationTest {
    private static final class ThrowExceptionOnFullQueue implements WaitStrategy {
        private static final String MESSAGE = "queue is full";

        @Override
        public int idle(int idleCounter) {
            throw new RuntimeException(MESSAGE);
        }

    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testGeneratedProxyInstance() {
        ProxyChannel<DemoIFace> proxyChannel =
                ProxyChannelFactory.createSpscProxy(10, DemoIFace.class, (idleCounter) -> 0);
        DemoIFace proxy = proxyChannel.proxy();
        /*
         * Not sure what the proper behaviour is here but I can see from the
         * types it should at least be a DemoIFace
         */
        assertThat(proxyChannel.proxyInstance(proxy), instanceOf(DemoIFace.class));
    }

    @Test
    public void givenGeneratedProxyUsingSpscReferenceChannel_whenCallMethods_expectAllCallsAreProxied() throws Exception {
        util_givenGeneratedProxyUsingReferenceChannel_whenCallMethods_expectAllCallsAreProxied(SpscOffHeapFixedSizeRingBuffer.class);
    }

    @Test
    public void givenGeneratedProxyUsingMpscReferenceChannel_whenCallMethods_expectAllCallsAreProxied() throws Exception {
        util_givenGeneratedProxyUsingReferenceChannel_whenCallMethods_expectAllCallsAreProxied(MpscOffHeapFixedSizeRingBuffer.class);
    }

    private static void util_givenGeneratedProxyUsingReferenceChannel_whenCallMethods_expectAllCallsAreProxied(
            Class<? extends ProxyChannelRingBuffer> backend) {
        ProxyChannel<DemoIFace> proxyChannel =
                ProxyChannelFactory.createProxy(10, DemoIFace.class, (idleCounter) -> 0, backend);

        DemoIFace proxy = proxyChannel.proxy();
        CustomType obj1 = new CustomType();
        CustomType obj2 = new CustomType();
        CustomType[] objArray = new CustomType[] { obj2, obj1 };
        proxy.call1(1, 2);
        proxy.call2(1, 2L, false);
        proxy.call3();
        proxy.call4(obj1, obj2);
        proxy.call5(obj1, 1, obj2);
        proxy.call6(6, objArray, obj1, obj2);

        DemoIFace implAssertions = new DemoIFace() {

            @Override
            public void call1(int x, int y) {
                Assert.assertEquals(1, x);
                Assert.assertEquals(2, y);
            }

            @Override
            public void call2(float x, double y, boolean z) {
                Assert.assertEquals(1, x, 0.000000001);
                Assert.assertEquals(2, y, 0.000000001);
                Assert.assertEquals(false, z);
            }

            @Override
            public void call3() {
                throw new RuntimeException();
            }

            @Override
            public void call4(Object x, CustomType y) {
                Assert.assertSame(obj1, x);
                Assert.assertSame(obj2, y);
            }

            @Override
            public void call5(CustomType x, int y, CustomType z) {
                Assert.assertSame(obj1, x);
                Assert.assertEquals(1, y);
                Assert.assertSame(obj2, z);
            }

            @Override
            public void call6(int x, CustomType[] y, CustomType... z) {
                Assert.assertEquals(6, x);
                Assert.assertSame(objArray, y);
                Assert.assertArrayEquals(new Object[] { obj1, obj2 }, z);
            }
        };
        proxyChannel.process(implAssertions, 1);
        proxyChannel.process(implAssertions, 1);
        try {
            proxyChannel.process(implAssertions, 1);
            Assert.fail();
        } catch (RuntimeException e) {
            // Happy
        }
        proxyChannel.process(implAssertions, 1);
        proxyChannel.process(implAssertions, 1);
        proxyChannel.process(implAssertions, 1);
    }

    @Test
    public void givenGeneratedProxy_andQueueIsFull_whenCallAgain_expectRuntimeException() throws Exception {
        ProxyChannel<DemoIFace> proxyChannel =
                ProxyChannelFactory.createSpscProxy(10, DemoIFace.class, new ThrowExceptionOnFullQueue());
        // capacity of 10 results in 16 slots in the queue
        util_givenProxyChannel_andQueueIsFull_whenCallAgain_expectRuntimeException(16, proxyChannel);
    }

    @Test
    public void givenDemoProxy_andQueueIsFull_whenCallAgain_expectRuntimeException() throws Exception {
        ProxyChannel<DemoIFace> proxyChannel = new DemoProxyResult(10, new ThrowExceptionOnFullQueue());
        // capacity of 10 results in 16 slots in the queue
        util_givenProxyChannel_andQueueIsFull_whenCallAgain_expectRuntimeException(16, proxyChannel);
    }

    private void util_givenProxyChannel_andQueueIsFull_whenCallAgain_expectRuntimeException(
            int capacity,
            ProxyChannel<DemoIFace> proxyChannel) {
        DemoIFace proxy = proxyChannel.proxy();
        for (int i = 0; i < capacity; i++) {
            proxy.call3();
        }

        // Then
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(ThrowExceptionOnFullQueue.MESSAGE);

        // When
        proxy.call3();
    }

    @Test
    public void givenDemoProxyUsingSpscReferenceChannel_whenCallMethods_expectAllCallsAreProxied() throws Exception {

        ProxyChannel<DemoIFace> proxyChannel = new DemoProxyResult(10, (idleCounter) -> 0);

        DemoIFace proxy = proxyChannel.proxy();
        CustomType obj1 = new CustomType();
        CustomType obj2 = new CustomType();
        CustomType[] objArray = new CustomType[] { obj2, obj1 };
        proxy.call1(1, 2);
        proxy.call2(1, 2L, false);
        proxy.call3();
        proxy.call4(obj1, obj2);
        proxy.call5(obj1, 1, obj2);
        proxy.call6(6, objArray, obj1, obj2);

        DemoIFace implAssertions = new DemoIFace() {

            @Override
            public void call1(int x, int y) {
                Assert.assertEquals(1, x);
                Assert.assertEquals(2, y);
            }

            @Override
            public void call2(float x, double y, boolean z) {
                Assert.assertEquals(1, x, 0.000000001);
                Assert.assertEquals(2, y, 0.000000001);
                Assert.assertEquals(false, z);
            }

            @Override
            public void call3() {
                throw new RuntimeException();
            }

            @Override
            public void call4(Object x, CustomType y) {
                Assert.assertSame(obj1, x);
                Assert.assertSame(obj2, y);
            }

            @Override
            public void call5(CustomType x, int y, CustomType z) {
                Assert.assertSame(obj1, x);
                Assert.assertEquals(1, y);
                Assert.assertSame(obj2, z);
            }

            @Override
            public void call6(int x, CustomType[] y, CustomType... z) {
                Assert.assertEquals(6, x);
                Assert.assertSame(objArray, y);
                Assert.assertArrayEquals(new Object[] { obj1, obj2 }, z);
            }
        };
        proxyChannel.process(implAssertions, 1);
        proxyChannel.process(implAssertions, 1);
        try {
            proxyChannel.process(implAssertions, 1);
            Assert.fail();
        } catch (RuntimeException e) {
            // Happy
        }
        proxyChannel.process(implAssertions, 1);
        proxyChannel.process(implAssertions, 1);
        proxyChannel.process(implAssertions, 1);
    }
}
