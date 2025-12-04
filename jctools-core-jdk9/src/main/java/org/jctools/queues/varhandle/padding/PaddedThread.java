package org.jctools.queues.varhandle.padding;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/** Padded volatile Thread to prevent false sharing. */
public final class PaddedThread extends ThreadRhsPadding {

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
   * Stores thread with release semantics.
   *
   * @param newValue thread to store
   */
  public void setRelease(Thread newValue) {
    VALUE_HANDLE.setRelease(this, newValue);
  }

  /**
   * Loads thread with acquire semantics.
   *
   * @return current thread value
   */
  public Thread getAcquire() {
    return (Thread) VALUE_HANDLE.getAcquire(this);
  }

  /**
   * Loads thread with opaque semantics.
   *
   * @return current thread value
   */
  public Thread getOpaque() {
    return (Thread) VALUE_HANDLE.getOpaque(this);
  }
}
