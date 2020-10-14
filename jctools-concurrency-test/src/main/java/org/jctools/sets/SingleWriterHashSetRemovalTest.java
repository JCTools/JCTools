package org.jctools.sets;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.Z_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/**
 * @author Tolstopyatov Vsevolod
 * @since 23/04/17
 */
@JCStressTest
@Outcome(id = "true", expect = ACCEPTABLE, desc = "Ok")
@Outcome(expect = FORBIDDEN, desc = "Removal considered harmful")
@State
public class SingleWriterHashSetRemovalTest {

    private final SingleWriterHashSet<Integer> set = new SingleWriterHashSet<>(16);

    public SingleWriterHashSetRemovalTest() {
        // Collide elements so removal of 1 will shift 17
        set.add(1);
        set.add(17);
    }

    @Actor
    public void actor1() {
        set.remove(1);
    }

    @Actor
    public void actor2(Z_Result r) {
        r.r1 = set.contains(17);
    }
}
