package org.jctools.varhandle.padding;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** A padded, volatile long value designed to minimize false sharing. */
public final class PaddedLong extends LongRhsPadding {

  /** VarHandle providing atomic access to the {@code value} field. */
  public static final VarHandle VALUE_HANDLE;

  static {
    try {
      VALUE_HANDLE = MethodHandles.lookup().findVarHandle(PaddedLong.class, "value", long.class);
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }

  /** Creates a new {@code PaddedLong} with initial value {@code 0}. */
  public PaddedLong() {
    this(0L);
  }

  /**
   * Creates a new {@code PaddedLong} with the specified initial value.
   *
   * @param initialValue the initial value
   */
  public PaddedLong(long initialValue) {
    setPlain(initialValue);
  }

  /**
   * Returns the current value using a volatile read.
   *
   * <p>This provides full read visibility: all writes published via release/volatile stores by
   * other threads are guaranteed visible to this load.
   *
   * @return the current value
   */
  public long getVolatile() {
    return (long) VALUE_HANDLE.getVolatile(this);
  }

  /**
   * Returns the current value using acquire semantics.
   *
   * <p>This read guarantees that subsequent loads and stores cannot move before this operation. It
   * is typically paired with {@code setRelease(long)} on the publishing side for efficient
   * message-passing.
   *
   * @return the current value with acquire ordering
   */
  public long getAcquire() {
    return (long) VALUE_HANDLE.getAcquire(this);
  }

  /**
   * Returns the current value using a plain (non-volatile) load.
   *
   * <p>No memory ordering or visibility guarantees are provided. Useful for single-threaded readers
   * or cases where explicit fencing is used separately.
   *
   * @return the current value with no memory barrier
   */
  public long getPlain() {
    return (long) VALUE_HANDLE.get(this);
  }

  /**
   * Returns the current value using an opaque read.
   *
   * <p>Opaque mode guarantees that the read is not reordered with respect to other opaque or
   * stronger operations on the same variable, but allows the JVM considerable freedom to reorder
   * relative to unrelated memory accesses.
   *
   * <p>It provides weaker semantics than acquire but stronger than a plain read, making it useful
   * for performance-sensitive code paths where minimal ordering is required but visibility should
   * not be completely relaxed.
   *
   * @return the current value using opaque semantics
   */
  public long getOpaque() {
    return (long) VALUE_HANDLE.getOpaque(this);
  }

  /**
   * Stores a new value using a volatile write.
   *
   * <p>This provides <strong>full write visibility</strong> and prevents reorderings of preceding
   * operations. Equivalent to a traditional {@code volatile long} assignment.
   *
   * @param newValue the value to store
   */
  public void setVolatile(long newValue) {
    VALUE_HANDLE.setVolatile(this, newValue);
  }

  /**
   * Stores a new value using release semantics.
   *
   * <p>This guarantees that all prior writes cannot be reordered after this store, but does not
   * impose the full cost of a volatile write. Functionally equivalent to {@code lazySet} or ordered
   * write in {@code Unsafe}.
   *
   * @param newValue the value to store with release ordering
   */
  public void setRelease(long newValue) {
    VALUE_HANDLE.setRelease(this, newValue);
  }

  /**
   * Stores a value using a plain (non-volatile) write.
   *
   * <p>No ordering guarantees are provided. Useful for single-threaded initialization or when
   * explicit fences are applied externally.
   *
   * @param newValue the value to store with no memory barrier
   */
  public void setPlain(long newValue) {
    VALUE_HANDLE.set(this, newValue);
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
  public void setOpaque(long newValue) {
    VALUE_HANDLE.setOpaque(this, newValue);
  }

  /**
   * Atomically updates the value to {@code newValue} if the current value equals {@code expected}.
   *
   * @param expected the value the field must currently hold
   * @param newValue the new value to set
   * @return {@code true} on success, {@code false} otherwise
   */
  public boolean compareAndSet(long expected, long newValue) {
    return VALUE_HANDLE.compareAndSet(this, expected, newValue);
  }
}
