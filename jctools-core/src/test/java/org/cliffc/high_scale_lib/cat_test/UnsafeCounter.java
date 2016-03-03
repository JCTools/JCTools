package org.cliffc.high_scale_lib.cat_test;
import java.lang.reflect.Field;
import sun.misc.Unsafe;

public final class UnsafeCounter extends Counter {
  public String name() { return "Unsafe"; }
  private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
  private static final long CNT_OFFSET;
  static { {			// <clinit>
    Field f = null;
    try { 
      f = UnsafeCounter.class.getDeclaredField("_cnt"); 
    } catch( java.lang.NoSuchFieldException e ) {
      throw new Error(e);
    } 
    CNT_OFFSET = _unsafe.objectFieldOffset(f);
    }
  }

  private long _cnt;
  public long get(){ return _cnt; }
  public void add( final long x ) { 
    long cnt=0;
    do { 
      cnt = _cnt;
    } while( !_unsafe.compareAndSwapLong(this,CNT_OFFSET,cnt,cnt+x) );
  }


  private static class UtilUnsafe {
    private UtilUnsafe() { } // dummy private constructor
    public static Unsafe getUnsafe() {
      // Not on bootclasspath
      if( UtilUnsafe.class.getClassLoader() == null )
        return Unsafe.getUnsafe();
      try {
        final Field fld = Unsafe.class.getDeclaredField("theUnsafe");
        fld.setAccessible(true);
        return (Unsafe) fld.get(UtilUnsafe.class);
      } catch (Exception e) {
        throw new RuntimeException("Could not obtain access to sun.misc.Unsafe", e);
      }
    }
  }

}
