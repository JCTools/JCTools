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

import java.util.Arrays;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

/**
 * POC to demonstrate potential gain from batching the consumer nulling out of array slots. Unsafe copy only
 * works for primitive arrays. Copy seems nearly 2 times faster than fill.
 * 
 * @author nitsanw
 * 
 */
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class RefArrayFill {
    private static final int N_LENGTH = Integer.getInteger("n.length", 1024);
    private static final int R_LENGTH = Integer.getInteger("r.length", 1024);
    protected static final Object[] NULLS = new Object[N_LENGTH];
    private final Object[] refs = new Object[R_LENGTH];

    @Benchmark
    public void fill() {
        Arrays.fill(refs, null);
    }

    @Benchmark
    public void copy() {
        int i = 0;
        for (; i < refs.length - N_LENGTH; i += N_LENGTH) {
            System.arraycopy(NULLS, 0, refs, i, N_LENGTH);
        }
        System.arraycopy(NULLS, 0, refs, i, refs.length - i);
    }
}