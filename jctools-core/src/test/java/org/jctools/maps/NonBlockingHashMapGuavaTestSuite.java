package org.jctools.maps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.testing.*;
import com.google.common.collect.testing.features.CollectionFeature;
import com.google.common.collect.testing.features.CollectionSize;
import com.google.common.collect.testing.features.MapFeature;
import com.google.common.collect.testing.testers.MapReplaceEntryTester;
import com.google.common.collect.testing.testers.MapReplaceTester;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author Tolstopyatov Vsevolod
 * @since 04/06/17
 */
@SuppressWarnings("unchecked")
public class NonBlockingHashMapGuavaTestSuite extends TestCase
{

    public static Test suite() throws Exception
    {
        TestSuite suite = new TestSuite();

        TestSuite mapSuite = mapTestSuite(new TestStringMapGenerator()
        {
            @Override
            protected Map<String, String> create(Map.Entry<String, String>[] entries)
            {
                Map<String, String> map = new NonBlockingHashMap<>();
                for (Map.Entry<String, String> entry : entries)
                {
                    map.put(entry.getKey(), entry.getValue());
                }
                return map;
            }
        }, NonBlockingHashMap.class.getSimpleName());

        TestSuite idMapSuite = mapTestSuite(new TestStringMapGenerator()
        {
            @Override
            protected Map<String, String> create(Map.Entry<String, String>[] entries)
            {
                Map<String, String> map = new NonBlockingIdentityHashMap<>();
                for (Map.Entry<String, String> entry : entries)
                {
                    map.put(entry.getKey(), entry.getValue());
                }
                return map;
            }
        }, NonBlockingIdentityHashMap.class.getSimpleName());

        TestSuite longMapSuite = mapTestSuite(new TestMapGenerator<Long, Long>()
        {
            @Override
            public Long[] createKeyArray(int length)
            {
                return new Long[length];
            }

            @Override
            public Long[] createValueArray(int length)
            {
                return new Long[length];
            }

            @Override
            public SampleElements<Map.Entry<Long, Long>> samples()
            {
                return new SampleElements<>(
                    Helpers.mapEntry(1L, 1L),
                    Helpers.mapEntry(2L, 2L),
                    Helpers.mapEntry(3L, 3L),
                    Helpers.mapEntry(4L, 4L),
                    Helpers.mapEntry(5L, 5L));
            }

            @Override
            public Map<Long, Long> create(Object... elements)
            {
                Map<Long, Long> map = new NonBlockingHashMapLong<>();
                for (Object o : elements)
                {
                    Map.Entry<Long, Long> e = (Map.Entry<Long, Long>) o;
                    map.put(e.getKey(), e.getValue());
                }
                return map;
            }

            @Override
            public Map.Entry<Long, Long>[] createArray(int length)
            {
                return new Map.Entry[length];
            }

            @Override
            public Iterable<Map.Entry<Long, Long>> order(List<Map.Entry<Long, Long>> insertionOrder)
            {
                return insertionOrder;
            }
        }, NonBlockingHashMapLong.class.getSimpleName());

        suite.addTest(mapSuite);
        suite.addTest(idMapSuite);
        suite.addTest(longMapSuite);
        return suite;
    }

    private static <T> TestSuite mapTestSuite(TestMapGenerator<T, T> testMapGenerator, String name)
    {
        return new MapTestSuiteBuilder<T, T>()
        {
            {
                usingGenerator(testMapGenerator);
            }

            @Override
            protected List<Class<? extends AbstractTester>> getTesters()
            {
                List<Class<? extends AbstractTester>> testers = new ArrayList<>(super.getTesters());
                // NonBlockingHashMap doesn't support null in putIfAbsent and provides putIfAbsentAllowsNull instead
                testers.remove(MapReplaceEntryTester.class);
                testers.remove(MapReplaceTester.class);
                return testers;
            }
        }.withFeatures(
            MapFeature.GENERAL_PURPOSE,
            CollectionSize.ANY,
            CollectionFeature.SUPPORTS_ITERATOR_REMOVE)
            .named(name)
            .createTestSuite();
    }

}
