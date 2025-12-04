package org.jctools.varhandle.padding;

/** Left-hand-side (LHS) padding to prevent false sharing. */
class LhsPadding {
  /** 7 bytes padding field to occupy space on the left side of the value. */
  @SuppressWarnings("unused")
  private long p1, p2, p3, p4, p5, p6, p7;
}
