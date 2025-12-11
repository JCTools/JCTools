/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.queues.varhandle.padding;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Padded volatile Thread to prevent false sharing.
 *
 * @author <a href="https://github.com/amarziali">Andrea Marziali</a>
 */
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
