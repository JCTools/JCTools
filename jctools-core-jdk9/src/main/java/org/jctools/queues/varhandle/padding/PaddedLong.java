package org.jctools.queues.varhandle.padding;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** Padded volatile long to prevent false sharing. */
public final class PaddedLong extends LongRhsPadding {

  public static final VarHandle VALUE_HANDLE;

  static {
    try {
      VALUE_HANDLE = MethodHandles.lookup().findVarHandle(PaddedLong.class, "value", long.class);
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }

  public PaddedLong() {
    this(0L);
  }

  /**
   * @param initialValue the initial value
   */
  public PaddedLong(long initialValue) {
    setPlain(initialValue);
  }

  /**
   * @return value with volatile semantics (full visibility)
   */
  public long getVolatile() {
    return (long) VALUE_HANDLE.getVolatile(this);
  }

  /**
   * @return value with acquire semantics (pairs with setRelease)
   */
  public long getAcquire() {
    return (long) VALUE_HANDLE.getAcquire(this);
  }

  /**
   * @return value with opaque semantics
   */
  public long getOpaque() {
    return (long) VALUE_HANDLE.getOpaque(this);
  }

  /**
   * @return value with plain semantics (no ordering)
   */
  public long getPlain() {
    return (long) VALUE_HANDLE.get(this);
  }

  /**
   * @param newValue value to store with volatile semantics (full visibility)
   */
  public void setVolatile(long newValue) {
    VALUE_HANDLE.setVolatile(this, newValue);
  }

  /**
   * @param newValue value to store with release semantics (pairs with getAcquire)
   */
  public void setRelease(long newValue) {
    VALUE_HANDLE.setRelease(this, newValue);
  }

  /**
   * @param newValue value to store with plain semantics (no ordering)
   */
  public void setPlain(long newValue) {
    VALUE_HANDLE.set(this, newValue);
  }

  /**
   * @param expected expected current value
   * @param newValue new value
   * @return true if successful
   */
  public boolean compareAndSet(long expected, long newValue) {
    return VALUE_HANDLE.compareAndSet(this, expected, newValue);
  }
}
