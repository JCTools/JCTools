package org.jctools.maps.cat_test;
public final class StripeUnsafeCounter extends Counter {
  private final UnsafeCounter _cnts[];
  StripeUnsafeCounter(int stripes) {
    _cnts = new UnsafeCounter[stripes];
    for( int i=0; i<stripes; i++ )
      _cnts[i] = new UnsafeCounter();
  }
  public String name() { return "Unsafes"+_cnts.length; }
  public long get() {
    long sum = 0;
    for( int i=0; i<_cnts.length; i++ )
      sum += _cnts[i].get();
    return sum;
  }
  public void add( long x ) {
    int hash = System.identityHashCode( Thread.currentThread());
    int idx = hash & (_cnts.length-1);
    _cnts[idx].add(x);
  }
}
