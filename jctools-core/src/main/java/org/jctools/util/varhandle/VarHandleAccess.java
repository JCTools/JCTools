import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class VarHandleAccess<T> {

    private final MethodHandle mhGet;
    private final MethodHandle mhSet;

    private final MethodHandle mhGetOpaque;
    private final MethodHandle mhSetOpaque;

    private final MethodHandle mhGetVolatile;
    private final MethodHandle mhSetVolatile;

    private final MethodHandle mhGetAcquire;
    private final MethodHandle mhSetRelease;

    private final MethodHandle mhCompareAndSet;

    // --- static unbound MethodHandles ---
    private static final MethodHandle UB_MH_GET;
    private static final MethodHandle UB_MH_SET;

    private static final MethodHandle UB_MH_GET_OPAQUE;
    private static final MethodHandle UB_MH_SET_OPAQUE;

    private static final MethodHandle UB_MH_GET_VOLATILE;
    private static final MethodHandle UB_MH_SET_VOLATILE;

    private static final MethodHandle UB_MH_GET_ACQUIRE;
    private static final MethodHandle UB_MH_SET_RELEASE;

    private static final MethodHandle UB_MH_CAS;

    static {
        try {
            Class<?> vhClass = Class.forName("java.lang.invoke.VarHandle");
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            // unbound method handles: first argument is VarHandle itself
            UB_MH_GET          = lookup.findVirtual(vhClass, "get", MethodType.methodType(Object.class, Object.class));
            UB_MH_SET          = lookup.findVirtual(vhClass, "set", MethodType.methodType(void.class, Object.class, Object.class));

            UB_MH_GET_OPAQUE   = lookup.findVirtual(vhClass, "getOpaque", MethodType.methodType(Object.class, Object.class));
            UB_MH_SET_OPAQUE   = lookup.findVirtual(vhClass, "setOpaque", MethodType.methodType(void.class, Object.class, Object.class));

            UB_MH_GET_VOLATILE = lookup.findVirtual(vhClass, "getVolatile", MethodType.methodType(Object.class, Object.class));
            UB_MH_SET_VOLATILE = lookup.findVirtual(vhClass, "setVolatile", MethodType.methodType(void.class, Object.class, Object.class));

            UB_MH_GET_ACQUIRE  = lookup.findVirtual(vhClass, "getAcquire", MethodType.methodType(Object.class, Object.class));
            UB_MH_SET_RELEASE  = lookup.findVirtual(vhClass, "setRelease", MethodType.methodType(void.class, Object.class, Object.class));

            UB_MH_CAS          = lookup.findVirtual(vhClass, "compareAndSet", MethodType.methodType(boolean.class, Object.class, Object.class, Object.class));

        } catch (Throwable t) {
            throw new RuntimeException("Failed to initialize unbound VarHandle MethodHandles", t);
        }
    }

    // --- constructor binds to a specific VarHandle instance ---
    public VarHandleAccess(Object varHandle, Class<T> receiverType) {
        try {
      this.mhGet = UB_MH_GET.bindTo(varHandle);
            this.mhSet          = UB_MH_SET.bindTo(varHandle);

            this.mhGetOpaque    = UB_MH_GET_OPAQUE.bindTo(varHandle);
            this.mhSetOpaque    = UB_MH_SET_OPAQUE.bindTo(varHandle);

            this.mhGetVolatile  = UB_MH_GET_VOLATILE.bindTo(varHandle);
            this.mhSetVolatile  = UB_MH_SET_VOLATILE.bindTo(varHandle);

            this.mhGetAcquire   = UB_MH_GET_ACQUIRE.bindTo(varHandle);
            this.mhSetRelease   = UB_MH_SET_RELEASE.bindTo(varHandle);

            this.mhCompareAndSet= UB_MH_CAS.bindTo(varHandle);

        } catch (Throwable t) {
            throw new RuntimeException("Failed to bind VarHandle MethodHandles", t);
        }
    }

    // --- adapter API (hot path: zero reflection) ---

    public Object get(Object receiver) {
        try { return mhGet.invokeExact(receiver); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public void set(Object receiver, Object value) {
        try { mhSet.invokeExact(receiver, value); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public Object getOpaque(Object receiver) {
        try { return mhGetOpaque.invokeExact(receiver); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public void setOpaque(Object receiver, Object value) {
        try { mhSetOpaque.invokeExact(receiver, value); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public Object getVolatile(Object receiver) {
        try { return mhGetVolatile.invokeExact(receiver); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public void setVolatile(Object receiver, Object value) {
        try { mhSetVolatile.invokeExact(receiver, value); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public Object getAcquire(Object receiver) {
        try { return mhGetAcquire.invokeExact(receiver); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public void setRelease(Object receiver, Object value) {
        try { mhSetRelease.invokeExact(receiver, value); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }

    public boolean compareAndSet(Object receiver, Object expected, Object newValue) {
        try { return (boolean) mhCompareAndSet.invokeExact(receiver, expected, newValue); }
        catch (Throwable t) { throw new RuntimeException(t); }
    }
}
