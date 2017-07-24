package org.jctools.queues.matchers;

import org.hamcrest.Matcher;

import java.util.Collection;

import static org.hamcrest.Matchers.*;

/**
 * @author Andrey Satarin (https://github.com/asatarin)
 */
public class Matchers
{
    private Matchers()
    {
    }

    public static Matcher<Collection<?>> emptyAndZeroSize()
    {
        return allOf(hasSize(0), empty());
    }
}
