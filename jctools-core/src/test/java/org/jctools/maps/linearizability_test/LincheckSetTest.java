package org.jctools.maps.linearizability_test;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlinx.lincheck.*;
import org.jetbrains.kotlinx.lincheck.annotations.*;
import org.jetbrains.kotlinx.lincheck.paramgen.*;
import org.jetbrains.kotlinx.lincheck.strategy.managed.modelchecking.ModelCheckingOptions;
import org.jetbrains.kotlinx.lincheck.strategy.stress.StressOptions;
import org.jetbrains.kotlinx.lincheck.verifier.VerifierState;
import org.junit.Test;

import java.util.Set;

@Param(name = "key", gen = IntGen.class, conf = "1:5") // keys are longs in 1..5 range
public abstract class LincheckSetTest extends VerifierState
{
    private final Set<Integer> set;

    public LincheckSetTest(Set<Integer> set)
    {
        this.set = set;
    }

    @Operation
    public boolean contains(@Param(name = "key") int key)
    {
        return set.contains(key);
    }

    @Operation
    public boolean add(@Param(name = "key") int key)
    {
        return set.add(key);
    }

    @Operation
    public boolean remove(@Param(name = "key") int key)
    {
        return set.remove(key);
    }

    @Operation
    public void clear()
    {
        set.clear();
    }

    /**
     * This test checks that the concurrent set is linearizable with bounded model checking.
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
     * This test checks that the concurrent set is linearizable with stress testing.
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
     * For {@link Set} it itself is used.
     * @return object representing internal state
     */
    @NotNull
    @Override
    protected Object extractState()
    {
        return set;
    }
}
