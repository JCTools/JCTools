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
package org.jctools.queues.varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.jctools.util.InternalAPI;

@InternalAPI
public final class VarHandleQueueUtil {
  private static final VarHandle ARRAY_REF_ELEMENT;
  private static final VarHandle ARRAY_LONG_ELEMENT;

  static {
    try {
      ARRAY_REF_ELEMENT = MethodHandles.arrayElementVarHandle(Object[].class);
      ARRAY_LONG_ELEMENT = MethodHandles.arrayElementVarHandle(long[].class);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static <E> E lvRefElement(E[] buffer, long offset) {
    return (E) ARRAY_REF_ELEMENT.getVolatile(buffer, (int)offset);
  }

  public static <E> E laRefElement(E[] buffer, long offset) {
    return (E) ARRAY_REF_ELEMENT.getAcquire(buffer, (int)offset);
  }

  public static <E> E lpRefElement(E[] buffer, long offset) {
    return (E) buffer[(int)offset];
  }

  public static <E> void spRefElement(E[] buffer, long offset, E value) {
    ARRAY_REF_ELEMENT.set(buffer, (int)offset, value);
  }

  public static <E> void soRefElement(E[] buffer, long offset, E value) {
    ARRAY_REF_ELEMENT.setRelease(buffer, (int)offset, value);
  }

  public static <E> void svRefElement(E[] buffer, long offset, E value) {
    ARRAY_REF_ELEMENT.setVolatile(buffer, (int)offset, value);
  }

  public static long calcRefElementOffset(long index) {
    return index;
  }

  public static long calcCircularRefElementOffset(long index, long mask) {
    return (index & mask);
  }

  public static <E> E[] allocateRefArray(int capacity) {
    return (E[]) new Object[capacity];
  }

  public static void spLongElement(long[] buffer, long offset, long e) {
    ARRAY_LONG_ELEMENT.set(buffer, (int)offset, e);
  }

  public static void soLongElement(long[] buffer, long offset, long e) {
    ARRAY_LONG_ELEMENT.setRelease(buffer, (int)offset, e);
  }

  public static long lpLongElement(long[] buffer, long offset) {
    return buffer[(int)offset];
  }

  public static long laLongElement(long[] buffer, long offset) {
    return (long) ARRAY_LONG_ELEMENT.getAcquire(buffer, (int)offset);
  }

  public static long lvLongElement(long[] buffer, long offset) {
    return (long) ARRAY_LONG_ELEMENT.getVolatile(buffer, (int)offset);
  }

  public static long calcCircularLongElementOffset(long index, long mask) {
    return (index & mask);
  }

  public static long[] allocateLongArray(int capacity) {
    return new long[capacity];
  }

  public static int length(Object[] buf) {
    return buf.length;
  }

  /**
   * This method assumes index is actually (index << 1) because lower bit is used for resize hence
   * the >> 1
   */
  public static long modifiedCalcCircularRefElementOffset(long index, long mask) {
    return (index & mask) >> 1;
  }

  public static long nextArrayOffset(Object[] curr) {
    return length(curr) - 1;
  }
}
