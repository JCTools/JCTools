package org.jctools.counters;

import org.jctools.util.UnsafeAccess;

/**
 * @author Tolstopyatov Vsevolod
 */
public final class CountersFactory {

    private CountersFactory() {
    }

    public static Counter createFixedSizeStripedCounter(int stripesCount) {
        // Assuming that if Unsafe has getAndSet(Object, Long, Object) then it has
        // all JDK 8+ methods like getAndAddX, getAndSetX etc.
        if (UnsafeAccess.SUPPORTS_GET_AND_SET) {
            return new FixedSizeStripedLongCounterV8(stripesCount);
        } else {
            return new FixedSizeStripedLongCounterV6(stripesCount);
        }
    }
}
