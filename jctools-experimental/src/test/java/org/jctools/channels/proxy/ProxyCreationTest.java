package org.jctools.channels.proxy;

import org.junit.Assert;
import org.junit.Test;

public class ProxyCreationTest {
    
    @Test
    public void testGenerated() throws Exception {

        ProxyChannel<DemoIFace> proxyChannel = ProxyChannelFactory.createSpscProxy(10, DemoIFace.class);

        DemoIFace proxy = proxyChannel.proxy();
        Object obj1 = new Object();
        Object obj2 = new Object();
        proxy.call1(1, 2);
        proxy.call2(1, 2L, false);
        proxy.call3();
        proxy.call4(obj1, obj2);
        proxy.call5(obj1, 1, obj2);

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
            public void call4(Object x, Object y) {
                Assert.assertSame(obj1, x);
                Assert.assertSame(obj2, y);
            }

            @Override
            public void call5(Object x, int y, Object z) {
                Assert.assertSame(obj1, x);
                Assert.assertEquals(1, y);
                Assert.assertSame(obj2, z);
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
    }

    @Test
    public void testDemo() throws Exception {

        ProxyChannel<DemoIFace> proxyChannel = new DemoProxyResult(10);

        DemoIFace proxy = proxyChannel.proxy();
        Object obj1 = new Object();
        Object obj2 = new Object();
        proxy.call1(1, 2);
        proxy.call2(1, 2L, false);
        proxy.call3();
        proxy.call4(obj1, obj2);
        proxy.call5(obj1, 1, obj2);

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
            public void call4(Object x, Object y) {
                Assert.assertSame(obj1, x);
                Assert.assertSame(obj2, y);
            }

            @Override
            public void call5(Object x, int y, Object z) {
                Assert.assertSame(obj1, x);
                Assert.assertEquals(1, y);
                Assert.assertSame(obj2, z);
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
    }
}
