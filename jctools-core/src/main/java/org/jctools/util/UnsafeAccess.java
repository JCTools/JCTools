package org.jctools.util;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Why should we resort to using Unsafe?<br>
 * <ol>
 * <li>To construct class fields which allow volatile/ordered/plain access: This requirement is covered by
 * {@link AtomicReferenceFieldUpdater} and similar but their performance is arguably worse than the DIY approach
 * (depending on JVM version) while Unsafe intrinsification is a far lesser challenge for JIT compilers.
 * <li>To construct flavors of {@link AtomicReferenceArray}.
 * <li>Other use cases exist but are not present in this library yet.
 * </ol>
 * 
 * @author nitsanw
 * 
 */
public class UnsafeAccess {
    public static final boolean SUPPORTS_GET_AND_SET;
    public static final Unsafe UNSAFE;
    static {
        try {
            /*
             * This is a bit of voodoo to force the unsafe object into visibility and acquire it. This is not playing
             * nice, but as an established back door it is not likely to be taken away.
             */
            final Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = (Unsafe) field.get(null);
        } catch (Exception e) {
            SUPPORTS_GET_AND_SET = false;
            throw new RuntimeException(e);
        }
        boolean getAndSetSupport = false;
        try {
            Unsafe.class.getMethod("getAndSetObject", Object.class, Long.TYPE,Object.class);
            getAndSetSupport = true;
        } catch (Exception e) {
        }
        SUPPORTS_GET_AND_SET = getAndSetSupport;
    }

}
