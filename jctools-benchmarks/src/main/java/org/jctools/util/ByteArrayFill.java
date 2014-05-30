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
package org.jctools.util;

import static org.jctools.util.UnsafeAccess.UNSAFE;

import java.util.Arrays;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.GenerateMicroBenchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * POC to demonstrate potential gain from batching the consumer nulling out of array slots. For bytes it seems
 * there's very little to gain.
 * 
 * @author nitsanw
 *
 */
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class ByteArrayFill {
    private static final int ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    private static final int ELEMENT_SIZE;
    private static final int ARRAY_ELEMENT_SHIFT;
    static {
        ELEMENT_SIZE = UNSAFE.arrayIndexScale(byte[].class);

        if (1 == ELEMENT_SIZE) {
            ARRAY_ELEMENT_SHIFT = 0;
        } else if (2 == ELEMENT_SIZE) {
            ARRAY_ELEMENT_SHIFT = 1;
        } else if (4 == ELEMENT_SIZE) {
            ARRAY_ELEMENT_SHIFT = 2;
        } else if (8 == ELEMENT_SIZE) {
            ARRAY_ELEMENT_SHIFT = 3;
        } else {
            throw new IllegalStateException("Unknown element size");
        }
    }
    private static final int Z_LENGTH = Integer.getInteger("n.length", 1024);
    private static final int D_LENGTH = Integer.getInteger("r.length", 1024);
    protected static final byte[] ZEROS = new byte[Z_LENGTH];
    protected static final int Z_SIZE = Z_LENGTH << ARRAY_ELEMENT_SHIFT;
    private final byte[] data = new byte[D_LENGTH];

    @GenerateMicroBenchmark
    public void fill() {
        Arrays.fill(data, (byte)0);
    }

    @GenerateMicroBenchmark
    public void copy() {
        int i = 0;
        for (; i < data.length - Z_LENGTH; i += Z_LENGTH) {
            System.arraycopy(ZEROS, 0, data, i, Z_LENGTH);
        }
        System.arraycopy(ZEROS, 0, data, i, data.length - i);
    }
    @GenerateMicroBenchmark
    public void unsafe() {
        long fromOffset = ARRAY_BASE_OFFSET;
        long toOffset = ARRAY_BASE_OFFSET + data.length << ARRAY_ELEMENT_SHIFT;
        // no wrapping, go wild
        for (; fromOffset  < toOffset - Z_SIZE; fromOffset += Z_SIZE) {
            UNSAFE.copyMemory(ZEROS, ARRAY_BASE_OFFSET, data, fromOffset, Z_SIZE);
        }
        UNSAFE.copyMemory(ZEROS, ARRAY_BASE_OFFSET, data, fromOffset, toOffset - fromOffset);
    }
}