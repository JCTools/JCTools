package org.jctools.channels.proxy;

import org.jctools.channels.WaitStrategy;
import org.jctools.channels.spsc.SpscOffHeapFixedSizeRingBuffer;
import org.jctools.util.UnsafeAccess;

/**
 * Generated code. This is a mockup for methods passing primitives only.
 *
 * @author yak
 */
public class DemoProxyResult extends SpscOffHeapFixedSizeRingBuffer implements ProxyChannel<DemoIFace>, DemoIFace {
    private final WaitStrategy waitStrategy;
    
    public DemoProxyResult(int capacity, WaitStrategy waitStrategy) {
        super(capacity, 13, 2);
        this.waitStrategy = waitStrategy;
    }

    @Override
    public DemoIFace proxyInstance(DemoIFace impl) {
        // What should we do here?
        return this;
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
            // Depending on the number of methods this could change for performance (needs testing)
            // Start off with a switch and see how we do. The compiler *should* be able to convert a large switch
            // to a lookup table and *should* be better equipped to make the call.
            switch (UnsafeAccess.UNSAFE.getInt(rOffset)) {
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
                case 4: {
                    long referenceArrayIndex = this.consumerReferenceArrayIndex(rOffset);
                    Object x = this.readReference(referenceArrayIndex);
                    Object y = this.readReference(referenceArrayIndex + 1);
                    this.readRelease(rOffset);
                    impl.call4(x, (CustomType) y);
                    break;
                }
                case 5: {
                    long referenceArrayIndex = this.consumerReferenceArrayIndex(rOffset);
                    Object x = this.readReference(referenceArrayIndex);
                    int y = UnsafeAccess.UNSAFE.getInt(rOffset + 4);
                    Object z = this.readReference(referenceArrayIndex + 1);
                    this.readRelease(rOffset);
                    impl.call5((CustomType) x, y, (CustomType) z);
                    break;
                }
                case 6: {
                    long referenceArrayIndex = this.consumerReferenceArrayIndex(rOffset);
                    int x = UnsafeAccess.UNSAFE.getInt(rOffset + 4);
                    Object y = this.readReference(referenceArrayIndex);
                    Object z = this.readReference(referenceArrayIndex + 1);
                    this.readRelease(rOffset);
                    impl.call6(x, (CustomType[]) y, (CustomType[]) z);
                    break;
                }
            }
        }

        return i;
    }

    @Override
    public void call1(int x, int y) {
        long wOffset = ProxyChannelFactory.writeAcquireWithWaitStrategy(this, waitStrategy);
        UnsafeAccess.UNSAFE.putInt(wOffset + 4, x);
        UnsafeAccess.UNSAFE.putInt(wOffset + 8, y);
        this.writeRelease(wOffset, 1);
    }

    @Override
    public void call2(float x, double y, boolean z) {
        long wOffset = ProxyChannelFactory.writeAcquireWithWaitStrategy(this, waitStrategy);
        UnsafeAccess.UNSAFE.putFloat(wOffset + 4, x);
        UnsafeAccess.UNSAFE.putDouble(wOffset + 8, y);
        UnsafeAccess.UNSAFE.putBoolean(null, wOffset + 16, z);
        this.writeRelease(wOffset, 2);
    }
    
    
    @Override
    public void call3() {
        long wOffset = ProxyChannelFactory.writeAcquireWithWaitStrategy(this, waitStrategy);
        this.writeRelease(wOffset, 3);
    }

    @Override
    public void call4(Object x, CustomType y) {
        long wOffset = ProxyChannelFactory.writeAcquireWithWaitStrategy(this, waitStrategy);
        long arrayReferenceBaseIndex = this.producerReferenceArrayIndex(wOffset);
        this.writeReference(arrayReferenceBaseIndex, x);
        this.writeReference(arrayReferenceBaseIndex + 1, y);
        this.writeRelease(wOffset, 4);
    }

    @Override
    public void call5(CustomType x, int y, CustomType z) {
        long wOffset = ProxyChannelFactory.writeAcquireWithWaitStrategy(this, waitStrategy);
        long arrayReferenceBaseIndex = this.producerReferenceArrayIndex(wOffset);
        this.writeReference(arrayReferenceBaseIndex, x);
        UnsafeAccess.UNSAFE.putInt(wOffset + 4, y);
        this.writeReference(arrayReferenceBaseIndex + 1, z);
        this.writeRelease(wOffset, 5);
    }

    @Override
    public void call6(int x, CustomType[] y, CustomType... z) {
        long wOffset = ProxyChannelFactory.writeAcquireWithWaitStrategy(this, waitStrategy);
        long arrayReferenceBaseIndex = this.producerReferenceArrayIndex(wOffset);
        UnsafeAccess.UNSAFE.putInt(wOffset + 4, x);
        this.writeReference(arrayReferenceBaseIndex, y);
        this.writeReference(arrayReferenceBaseIndex + 1, z);
        this.writeRelease(wOffset, 6);
    }
}
