package org.jctools.maps.linearizability_test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlinx.lincheck.*;
import org.jetbrains.kotlinx.lincheck.annotations.*;
import org.jetbrains.kotlinx.lincheck.paramgen.*;
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions;
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions;
import org.jetbrains.kotlinx.lincheck.verifier.VerifierState;
import org.junit.Test;

import java.util.Map;

@Param(name = "key", gen = LongGen.class, conf = "1:5") // keys are longs in 1..5 range
@Param(name = "value", gen = IntGen.class, conf = "1:10") // values are longs in 1..10 range
public abstract class LincheckMapTest extends VerifierState
{
    private final Map<Long, Integer> map;

    public LincheckMapTest(Map<Long, Integer> map)
    {
        this.map = map;
    }

    @Operation
    public Integer get(@Param(name = "key") long key)
    {
        return map.get(key);
    }

    @Operation
    public Integer put(@Param(name = "key") long key, @Param(name = "value") int value)
    {
        return map.put(key, value);
    }

    @Operation
    public boolean replace(@Param(name = "key") long key, @Param(name = "value") int previousValue, @Param(name = "value") int nextValue)
    {
        return map.replace(key, previousValue, nextValue);
    }

    @Operation
    public Integer remove(@Param(name = "key") long key)
    {
        return map.remove(key);
    }

    @Operation
    public boolean containsKey(@Param(name = "key") long key)
    {
        return map.containsKey(key);
    }

    @Operation
    public boolean containsValue(@Param(name = "value") int value)
    {
        return map.containsValue(value);
    }

    @Operation
    public void clear()
    {
        map.clear();
    }

    /**
     * This test checks that the concurrent map is linearizable with bounded model checking.
     * Unlike stress testing, this approach can also provide a trace of an incorrect execution.
     * However, it uses sequential consistency model, so it can not find any low-level bugs (e.g., missing 'volatile'),
     * and thus, it it recommended to have both test modes.
     */
    @Test
    public void modelCheckingTest()
    {
        ModelCheckingOptions options = new ModelCheckingOptions();
        // The size of the test can be changed with 'options.iterations' or `options.invocationsPerIteration`.
        // The first one defines the number of different scenarios generated,
        // while the second one determines how deeply each scenario is tested.
        new LinChecker(this.getClass(), options).check();
    }

    /**
     * This test checks that the concurrent map is linearizable with stress testing.
     */
    @Test
    public void stressTest()
    {
        StressOptions options = new StressOptions();
        // The size of the test can be changed with 'options.iterations' or `options.invocationsPerIteration`.
        // The first one defines the number of different scenarios generated,
        // while the second one determines how deeply each scenario is tested.
        new LinChecker(this.getClass(), options).check();
    }

    /**
     * Provides something with correct <tt>equals</tt> and <tt>hashCode</tt> methods
     * that can be interpreted as an internal data structure state for faster verification.
     * The only limitation is that it should be different for different data structure states.
     * For {@link Map} it itself is used.
     * @return object representing internal state
     */
    @NotNull
    @Override
    protected Object extractState()
    {
        return map;
    }
}
