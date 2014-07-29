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
package org.jctools.channels.mapping;

import org.jctools.util.UnsafeAccess;

/**
 * Parent class of the generated flyweight objects.
 */
public abstract class Flyweight {

    public static final int TAG_SIZE_IN_BYTES = 1;

    public static final byte CLAIMED = 1;
    public static final byte COMMITTED = 2;

    protected long pointer;

    public Flyweight(final long pointer) {
        this.pointer = pointer;
    }

    public void moveTo(final long pointer) {
        this.pointer = pointer;
    }

    public boolean isCommitted() {
        return UnsafeAccess.UNSAFE.getByte(pointer) == COMMITTED;
    }

    public void claim() {
        UnsafeAccess.UNSAFE.putByte(pointer, CLAIMED);
    }

    public void commit() {
        UnsafeAccess.UNSAFE.putByte(pointer, COMMITTED);
    }

}
