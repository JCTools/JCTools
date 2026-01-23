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
public final class LinkedQueueVarHandleNode<E> {
  private static final VarHandle VH_NEXT;

  static {
    try {
      VH_NEXT =
          MethodHandles.lookup()
              .findVarHandle(
                  LinkedQueueVarHandleNode.class, "next", LinkedQueueVarHandleNode.class);
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private E value;
  private volatile LinkedQueueVarHandleNode<E> next;

  public LinkedQueueVarHandleNode() {
    this(null);
  }

  public LinkedQueueVarHandleNode(E val) {
    spValue(val);
  }

  /**
   * Gets the current value and nulls out the reference to it from this node.
   *
   * @return value
   */
  public E getAndNullValue() {
    E temp = lpValue();
    spValue(null);
    return temp;
  }

  public E lpValue() {
    return value;
  }

  public void spValue(E newValue) {
    value = newValue;
  }

  public void soNext(LinkedQueueVarHandleNode<E> n) {
    VH_NEXT.setRelease(this, n);
  }

  public void spNext(LinkedQueueVarHandleNode<E> n) {
    VH_NEXT.set(this, n);
  }

  public LinkedQueueVarHandleNode<E> lvNext() {
    return next;
  }
}
