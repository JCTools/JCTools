package org.jctools.varhandle.padding;

/**
 * Holds the actual sequence value, padded on the left to prevent false sharing with preceding
 * fields.
 */
class LongValue extends LhsPadding {
  /** The volatile value being protected from false sharing. */
  protected volatile long value;
}
