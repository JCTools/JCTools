package org.jctools.sets;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.I_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/**
 * @author Tolstopyatov Vsevolod
 * @since 13/05/17
 */
@JCStressTest
@Outcome(id = "0", expect = FORBIDDEN, desc = "Missing read")
@Outcome(id = "1", expect = ACCEPTABLE, desc = "Ok")
@Outcome(id = "2", expect = FORBIDDEN, desc = "Duplicate reads")
@Outcome(expect = FORBIDDEN, desc = "Can't happen")
@State
public class SingleWriterHashSetDuplicateReadsTest {

    private final SingleWriterHashSet<Integer> set = new SingleWriterHashSet<>(16);

    public SingleWriterHashSetDuplicateReadsTest() {
        // Collide elements so removal of 1 will shift 17
        set.add(1);
        set.add(17);
    }

    @Actor
    public void actor1() {
        set.remove(1);
    }

    @Actor
    public void actor2(I_Result r) {
        int counter = 0;
        for (Integer integer : set) {
            if (integer == 17) {
                ++counter;
            }
        }

        r.r1 = counter;
    }
}
