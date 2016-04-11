package org.jctools.maps;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

/*
 * Written by Cliff Click and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

/**
 * A simple wrapper around {@link NonBlockingHashMap} making it implement the
 * {@link Set} interface.  All operations are Non-Blocking and multi-thread safe.
 *
 * @since 1.5
 * @author Cliff Click
 */
public class NonBlockingHashSet<E> extends AbstractSet<E> implements Serializable {
  private static final Object V = "";

  private final NonBlockingHashMap<E,Object> _map;

  /** Make a new empty {@link NonBlockingHashSet}.  */
  public NonBlockingHashSet() { super(); _map = new NonBlockingHashMap<E,Object>(); }

  /** Add {@code o} to the set.  
   *  @return <tt>true</tt> if {@code o} was added to the set, <tt>false</tt>
   *  if {@code o} was already in the set.  */
  public boolean add( final E o ) { return _map.putIfAbsent(o,V) == null; }

  /**  @return <tt>true</tt> if {@code o} is in the set.  */
  public boolean contains   ( final Object     o ) { return _map.containsKey(o); }

  /**  @return Returns the match for {@code o} if {@code o} is in the set.  */
  public E get( final E o ) { return _map.getk(o); }

  /** Remove {@code o} from the set.  
   * @return <tt>true</tt> if {@code o} was removed to the set, <tt>false</tt>
   * if {@code o} was not in the set.
   */
  public boolean remove( final Object o ) { return _map.remove(o) == V; }
  /** Current count of elements in the set.  Due to concurrent racing updates,
   *  the size is only ever approximate.  Updates due to the calling thread are
   *  immediately visible to calling thread.
   *  @return count of elements.   */
  public int size( ) { return _map.size(); }
  /** Empty the set. */
  public void clear( ) { _map.clear(); }

  public Iterator<E>iterator( ) { return _map.keySet().iterator(); }
}
