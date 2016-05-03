package org.jctools.channels.proxy;

import org.jctools.channels.spsc.SpscOffHeapFixedSizeRingBuffer;
import org.jctools.util.UnsafeAccess;

/**
 * Generated code. This is a mockup for methods passing primitives only.
 *
 * @author yak
 */
public class DemoProxyResult extends SpscOffHeapFixedSizeRingBuffer implements ProxyChannel<DemoIFace>, DemoIFace {

    public DemoProxyResult(int capacity) {
        super(capacity, 13);
    }

    @Override
    public DemoIFace proxyInstance(DemoIFace impl) {
        return new DemoIFace() {

            @Override
            public void call1(int x, int y) {
                // TODO: What to do here?
            }

            @Override
            public void call2(float x, double y, boolean z) {
                // TODO: What to do here?
            }

            @Override
            public void call3() {
                // TODO: What to do here?
            }
        };
    }

    @Override
    public DemoIFace proxy() {
        return this;
    }

    @Override
    public int process(DemoIFace impl, int limit) {
        int i = 0;
        for (; i < limit; i++) {
            long rOffset = this.readAcquire();
            if (rOffset == EOF)
                break;
            int type = UnsafeAccess.UNSAFE.getInt(rOffset);
            // Depending on the number of methods this could change for performance (needs testing)
            // Start off with a switch and see how we do. The compiler *should* be able to convert a large switch
            // to a lookup table and *should* be better equipped to make the call.
            switch (type) {
                case 1: {
                    int x = UnsafeAccess.UNSAFE.getInt(rOffset + 4);
                    int y = UnsafeAccess.UNSAFE.getInt(rOffset + 8);
                    this.readRelease(rOffset);
                    impl.call1(x, y);
                    break;
                }
                case 2: {
                    float x = UnsafeAccess.UNSAFE.getFloat(rOffset + 4);
                    double y = UnsafeAccess.UNSAFE.getDouble(rOffset + 8);
                    boolean z = UnsafeAccess.UNSAFE.getBoolean(null, rOffset + 16);
                    this.readRelease(rOffset);
                    impl.call2(x, y, z);
                    break;
                }
                case 3: {
                    this.readRelease(rOffset);
                    impl.call3();
                    break;
                }
            }
        }

        return i;
    }

    @Override
    public void call1(int x, int y) {
        // issue: with this interface we have no way of signaling the queue is full.
        // solution: have all calls return a boolean? a bit annoying, would work
        // solution: block? clean interface, but a bit hard to justify. Perhaps pass in a blocking strategy or somthing
        // solution: throw exception? argh
        long wOffset = this.writeAcquire();
        UnsafeAccess.UNSAFE.putInt(wOffset + 4, x);
        UnsafeAccess.UNSAFE.putInt(wOffset + 8, y);
        this.writeRelease(wOffset, 1);
    }

    @Override
    public void call2(float x, double y, boolean z) {
        long wOffset = this.writeAcquire();
        UnsafeAccess.UNSAFE.putFloat(wOffset + 4, x);
        UnsafeAccess.UNSAFE.putDouble(wOffset + 8, y);
        UnsafeAccess.UNSAFE.putBoolean(null, wOffset + 16, z);
        this.writeRelease(wOffset, 2);
    }

    @Override
    public void call3() {
        long wOffset = this.writeAcquire();
        this.writeRelease(wOffset, 3);
    }
}
