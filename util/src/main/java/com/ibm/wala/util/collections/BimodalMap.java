/*
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package com.ibm.wala.util.collections;

import com.ibm.wala.util.debug.UnimplementedError;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/**
 * This implementation of {@link Map} chooses between one of two implementations, depending on the
 * size of the map.
 */
public class BimodalMap<K, V> implements Map<K, V> {

  // what's the cutoff between small and big maps?
  // this may be a time-space tradeoff; the caller must determine if
  // it's willing to put up with slower random access in exchange for
  // smaller footprint.
  private final int cutOff;

  /** The implementation we delegate to */
  private @Nullable Map<K, V> backingStore;

  /**
   * @param cutoff the map size at which to switch from the small map implementation to the large
   *     map implementation
   */
  public BimodalMap(int cutoff) {
    this.cutOff = cutoff;
  }

  @Override
  public int size() {
    return (backingStore == null) ? 0 : backingStore.size();
  }

  @Override
  public boolean isEmpty() {
    return (backingStore == null) ? true : backingStore.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    return (backingStore == null) ? false : backingStore.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return (backingStore == null) ? false : backingStore.containsValue(value);
  }

  @Override
  public @Nullable V get(Object key) {
    return (backingStore == null) ? null : backingStore.get(key);
  }

  @Override
  public @Nullable V put(K key, V value) {
    if (backingStore == null) {
      backingStore = new SmallMap<>();
      backingStore.put(key, value);
      return null;
    } else {
      if (backingStore instanceof SmallMap) {
        V result = backingStore.put(key, value);
        if (backingStore.size() > cutOff) {
          transferBackingStore();
        }
        return result;
      } else {
        return backingStore.put(key, value);
      }
    }
  }

  /** Switch backing implementation from a SmallMap to a HashMap */
  @NullUnmarked
  private void transferBackingStore() {
    assert backingStore instanceof SmallMap;
    SmallMap<K, V> S = (SmallMap<K, V>) backingStore;
    backingStore = HashMapFactory.make(2 * S.size());
    backingStore.putAll(S);
  }

  /**
   * @throws UnsupportedOperationException if the backingStore doesn't support remove
   */
  @Override
  public @Nullable V remove(Object key) {
    return (backingStore == null) ? null : backingStore.remove(key);
  }

  @Override
  @SuppressWarnings("unchecked")
  public void putAll(Map<? extends K, ? extends V> t) throws UnsupportedOperationException {
    if (t == null) {
      throw new IllegalArgumentException("null t");
    }
    if (backingStore == null) {
      int size = t.size();
      if (size > cutOff) {
        backingStore = HashMapFactory.make();
      } else {
        backingStore = new SmallMap<>();
      }
      backingStore.putAll(t);
      return;
    } else {
      if (backingStore instanceof SmallMap) {
        if (t.size() > cutOff) {
          Map<K, V> old = backingStore;
          backingStore = (Map<K, V>) HashMapFactory.make(t);
          backingStore.putAll(old);
        } else {
          backingStore.putAll(t);
          if (backingStore.size() > cutOff) {
            transferBackingStore();
          }
          return;
        }
      } else {
        backingStore.putAll(t);
      }
    }
  }

  @Override
  public void clear() {
    backingStore = null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public Set<K> keySet() {
    return (Set<K>) ((backingStore == null) ? Collections.emptySet() : backingStore.keySet());
  }

  @Override
  @SuppressWarnings("unchecked")
  public Collection<V> values() {
    return (Collection<V>)
        ((backingStore == null) ? Collections.emptySet() : backingStore.values());
  }

  /**
   * @throws UnimplementedError if the backingStore implementation does
   */
  @Override
  @SuppressWarnings("unchecked")
  public Set<Map.Entry<K, V>> entrySet() {
    return (Set<Entry<K, V>>)
        ((backingStore == null) ? Collections.emptySet() : backingStore.entrySet());
  }
}
