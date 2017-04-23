package org.jctools.sets;

import com.google.common.collect.testing.SetTestSuiteBuilder;
import com.google.common.collect.testing.TestStringSetGenerator;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.SetFeature;
import junit.framework.Test;
import junit.framework.TestCase;

import java.util.Collections;
import java.util.Set;

/**
 * @author Tolstopyatov Vsevolod
 * @since 23/04/17
 */
public class SingleWriterHashSetTest extends TestCase {

    public static Test suite() throws Exception {
        return SetTestSuiteBuilder.using(new TestStringSetGenerator() {
            @Override
            protected Set<String> create(String[] elements) {
                Set<String> set = new SingleWriterHashSet<>(elements.length);
                Collections.addAll(set, elements);
                return set;
            }
        }).withFeatures(
                SetFeature.GENERAL_PURPOSE,
                CollectionSize.ANY,
                CollectionFeature.NON_STANDARD_TOSTRING)
          .named(SingleWriterHashSet.class.getSimpleName())
          .createTestSuite();
    }
}
