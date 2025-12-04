package org.jctools.varhandle.padding;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** A padded, volatile Thread value designed to minimize false sharing. */
public final class PaddedThread extends ThreadRhsPadding {

  /** VarHandle providing atomic access to the {@code value} field. */
  public static final VarHandle VALUE_HANDLE;

  static {
    try {
      VALUE_HANDLE =
          MethodHandles.lookup().findVarHandle(PaddedThread.class, "value", Thread.class);
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }

  /**
   * Stores a value using an opaque write.
   *
   * <p>Opaque mode prevents the write from being reordered with respect to other opaque or stronger
   * operations on the same variable, but does not provide the ordering guarantees of release or
   * volatile stores.
   *
   * <p>Opaque writes are typically used when the value does not participate in a strict
   * happens-before relationship but still must not be fully reordered with respect to adjacent
   * accesses to the same field.
   *
   * @param newValue the value to store using opaque semantics
   */
  public void setOpaque(Thread newValue) {
    VALUE_HANDLE.setOpaque(this, newValue);
  }

  /**
   * Stores a new value using a volatile write.
   *
   * <p>This provides <strong>full write visibility</strong> and prevents reorderings of preceding
   * operations.
   *
   * @param newValue the value to store
   */
  public void setVolatile(Thread newValue) {
    VALUE_HANDLE.setVolatile(this, newValue);
  }

  /**
   * Atomically sets the value to {@code newValue} and returns the previous value.
   *
   * @param newValue the value to set
   * @return the previous value
   */
  public Thread getAndSet(Thread newValue) {
    return (Thread) VALUE_HANDLE.getAndSet(this, newValue);
  }
}
