package org.jctools.queues.varhandle.padding;

/** Holds the actual Thread value, padded on left to prevent false sharing. */
class ThreadValue extends LhsPadding {
  protected volatile Thread value;
}
