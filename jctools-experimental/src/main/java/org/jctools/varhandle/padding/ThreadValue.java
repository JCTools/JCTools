package org.jctools.varhandle.padding;

/**
 * Holds the actual thread value, padded on the left to prevent false sharing with preceding fields.
 */
class ThreadValue extends LhsPadding {
  /** A left padded {@link Thread} */
  protected volatile Thread value;
}
