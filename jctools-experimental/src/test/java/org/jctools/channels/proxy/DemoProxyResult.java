package org.jctools.channels.proxy;

import org.jctools.channels.spsc.SpscOffHeapFixedSizeWithReferenceSupportRingBuffer;
import org.jctools.util.UnsafeAccess;

/**
 * Generated code. This is a mockup for methods passing primitives only.
 *
 * @author yak
 */
public class DemoProxyResult extends SpscOffHeapFixedSizeWithReferenceSupportRingBuffer implements ProxyChannel<DemoIFace>, DemoIFace {

    public DemoProxyResult(int capacity) {
        super(capacity, 13, 2);
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

            @Override
            public void call4(Object x, CustomType y) {
                // TODO: What to do here?
            }

            @Override
            public void call5(CustomType x, int y, CustomType z) {
                // TODO: What to do here?
            }

            @Override
            public void call6(int x, CustomType[] y, CustomType... z) {
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
                    long referenceArrayIndex = this.consumerReferenceArrayIndex();
                    Object x = this.readReference(referenceArrayIndex);
                    Object y = this.readReference(referenceArrayIndex + 1);
                    this.readRelease(rOffset);
                    impl.call4(x, (CustomType) y);
                    break;
                }
                case 5: {
                    long referenceArrayIndex = this.consumerReferenceArrayIndex();
                    Object x = this.readReference(referenceArrayIndex);
                    int y = UnsafeAccess.UNSAFE.getInt(rOffset + 4);
                    Object z = this.readReference(referenceArrayIndex + 1);
                    this.readRelease(rOffset);
                    impl.call5((CustomType) x, y, (CustomType) z);
                    break;
                }
                case 6: {
                    long referenceArrayIndex = this.consumerReferenceArrayIndex();
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

    @Override
    public void call4(Object x, CustomType y) {
        long wOffset = this.writeAcquire();
        long arrayReferenceBaseIndex = this.producerReferenceArrayIndex();
        this.writeReference(arrayReferenceBaseIndex, x);
        this.writeReference(arrayReferenceBaseIndex + 1, y);
        this.writeRelease(wOffset, 4);
    }

    @Override
    public void call5(CustomType x, int y, CustomType z) {
        long wOffset = this.writeAcquire();
        long arrayReferenceBaseIndex = this.producerReferenceArrayIndex();
        this.writeReference(arrayReferenceBaseIndex, x);
        UnsafeAccess.UNSAFE.putInt(wOffset + 4, y);
        this.writeReference(arrayReferenceBaseIndex + 1, z);
        this.writeRelease(wOffset, 5);
    }

    @Override
    public void call6(int x, CustomType[] y, CustomType... z) {
        long wOffset = this.writeAcquire();
        long arrayReferenceBaseIndex = this.producerReferenceArrayIndex();
        UnsafeAccess.UNSAFE.putInt(wOffset + 4, x);
        this.writeReference(arrayReferenceBaseIndex, y);
        this.writeReference(arrayReferenceBaseIndex + 1, z);
        this.writeRelease(wOffset, 6);
    }
}
