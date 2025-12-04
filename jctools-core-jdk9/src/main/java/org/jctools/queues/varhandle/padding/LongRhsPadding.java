package org.jctools.queues.varhandle.padding;

/** Right padding: 128 bytes for Apple Silicon, x86 prefetch, and future CPUs. */
class LongRhsPadding extends LongValue {
  @SuppressWarnings("unused")
  private long p9, p10, p11, p12, p13, p14, p15, p16, p17, p18, p19, p20, p21, p22, p23, p24;
}
