package org.jctools.varhandle.padding;

/** Right-hand-side (RHS) padding to prevent false sharing. */
class LongRhsPadding extends LongValue {
  /** 7 bytes fields to occupy space on the right side of the value. */
  @SuppressWarnings("unused")
  private long p9, p10, p11, p12, p13, p14, p15;
}
