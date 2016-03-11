package org.jctools.channels.proxy;

import org.junit.Assert;
import org.junit.Test;

public class ProxyCreationTest {

    @Test
    public void testGenerated() throws Exception {

        ProxyChannel<DemoIFace> proxyChannel = ProxyChannelFactory.createSpscProxy(10, DemoIFace.class);

        DemoIFace proxy = proxyChannel.proxy();
        proxy.call1(1, 2);
        proxy.call2(1, 2L, false);
        proxy.call3();

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
        };
        proxyChannel.process(implAssertions, 1);
        proxyChannel.process(implAssertions, 1);
        try {
            proxyChannel.process(implAssertions, 1);
        } catch (RuntimeException e) {
            return;
        }
        Assert.fail();
    }

    @Test
    public void testDemo() throws Exception {

        ProxyChannel<DemoIFace> proxyChannel = new DemoProxyResult(10);

        DemoIFace proxy = proxyChannel.proxy();
        proxy.call1(1, 2);
        proxy.call2(1, 2L, false);
        proxy.call3();

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
        };
        proxyChannel.process(implAssertions, 1);
        proxyChannel.process(implAssertions, 1);
        try {
            proxyChannel.process(implAssertions, 1);
        } catch (RuntimeException e) {
            return;
        }
        Assert.fail();
    }
}
