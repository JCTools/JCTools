package org.jctools.queues;

import org.jctools.util.InternalAPI;

/**
 * This is internal API for ease of generic testing
 */
@InternalAPI
public interface OfferIfBelowThreshold<E>
{
    boolean offerIfBelowThreshold(final E e, int threshold);
}
