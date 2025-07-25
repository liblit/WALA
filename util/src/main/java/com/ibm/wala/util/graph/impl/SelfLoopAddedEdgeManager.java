package com.ibm.wala.util.graph.impl;

import com.ibm.wala.util.graph.EdgeManager;
import java.util.Iterator;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

public class SelfLoopAddedEdgeManager<T> implements EdgeManager<T> {
  private class PrependItterator implements Iterator<T> {
    private boolean usedFirst = false;
    private final Iterator<T> original;
    private @Nullable T first;

    public PrependItterator(Iterator<T> original, @Nullable T first) {
      this.original = original;
      this.first = first;
    }

    @Override
    public boolean hasNext() {
      if (!usedFirst) {
        return true;
      } else {
        return original.hasNext();
      }
    }

    @Override
    public @Nullable T next() {
      if (!usedFirst) {
        T tmp = first;
        first = null;
        usedFirst = true;
        return tmp;
      } else {
        return original.next();
      }
    }

    @Override
    public void remove() {
      assert false;
    }
  }

  private final EdgeManager<T> original;

  public SelfLoopAddedEdgeManager(EdgeManager<T> original) {
    if (original == null) {
      throw new IllegalArgumentException("original is null");
    }
    this.original = original;
  }

  @Override
  public Iterator<T> getPredNodes(@Nullable T n) {
    if (original.hasEdge(n, n)) {
      return original.getPredNodes(n);
    } else {
      return new PrependItterator(original.getPredNodes(n), n);
    }
  }

  @Override
  public int getPredNodeCount(T n) {
    if (original.hasEdge(n, n)) {
      return original.getPredNodeCount(n);
    } else {
      return original.getPredNodeCount(n) + 1;
    }
  }

  @Override
  public Iterator<T> getSuccNodes(@Nullable T n) {
    if (original.hasEdge(n, n)) {
      return original.getSuccNodes(n);
    } else {
      return new PrependItterator(original.getSuccNodes(n), n);
    }
  }

  @Override
  public int getSuccNodeCount(T n) {
    if (original.hasEdge(n, n)) {
      return original.getSuccNodeCount(n);
    } else {
      return original.getSuccNodeCount(n) + 1;
    }
  }

  @Override
  public void addEdge(T src, T dst) {
    original.addEdge(src, dst);
  }

  @Override
  public void removeEdge(T src, T dst) throws UnsupportedOperationException {
    original.removeEdge(src, dst);
  }

  @Override
  public void removeAllIncidentEdges(T node) throws UnsupportedOperationException {
    original.removeAllIncidentEdges(node);
  }

  @Override
  public void removeIncomingEdges(T node) throws UnsupportedOperationException {
    original.removeIncomingEdges(node);
  }

  @Override
  public void removeOutgoingEdges(T node) throws UnsupportedOperationException {
    original.removeOutgoingEdges(node);
  }

  @NullUnmarked
  @Override
  public boolean hasEdge(@Nullable T src, @Nullable T dst) {
    if (src.equals(dst)) {
      return true;
    } else {
      return original.hasEdge(src, dst);
    }
  }
}
