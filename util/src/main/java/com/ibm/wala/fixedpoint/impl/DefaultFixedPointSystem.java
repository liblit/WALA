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
package com.ibm.wala.fixedpoint.impl;

import com.ibm.wala.fixpoint.AbstractStatement;
import com.ibm.wala.fixpoint.IFixedPointStatement;
import com.ibm.wala.fixpoint.IFixedPointSystem;
import com.ibm.wala.fixpoint.IVariable;
import com.ibm.wala.fixpoint.UnaryStatement;
import com.ibm.wala.util.collections.EmptyIterator;
import com.ibm.wala.util.collections.FilterIterator;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.debug.UnimplementedError;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.INodeWithNumber;
import com.ibm.wala.util.graph.NumberedGraph;
import com.ibm.wala.util.graph.impl.SparseNumberedGraph;
import com.ibm.wala.util.graph.traverse.Topological;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/** Default implementation of a dataflow graph */
public class DefaultFixedPointSystem<T extends IVariable<T>> implements IFixedPointSystem<T> {
  static final boolean DEBUG = false;

  /** A graph which defines the underlying system of statements and variables */
  private final NumberedGraph<INodeWithNumber> graph;

  /**
   * We maintain a hash set of equations in order to check for equality with equals() ... the
   * NumberedGraph does not support this. TODO: use a custom NumberedNodeManager to save space
   */
  private final Set<IFixedPointStatement<?>> equations = HashSetFactory.make();

  /**
   * We maintain a hash set of variables in order to check for equality with equals() ... the
   * NumberedGraph does not support this. TODO: use a custom NumberedNodeManager to save space
   */
  private final Set<IVariable<?>> variables = HashSetFactory.make();

  /**
   * @param expectedOut number of expected out edges in the "usual" case for constraints .. used to
   *     tune graph representation
   */
  public DefaultFixedPointSystem(int expectedOut) {
    graph = new SparseNumberedGraph<>(expectedOut);
  }

  /** default constructor ... tuned for one use for each def in dataflow graph. */
  public DefaultFixedPointSystem() {
    this(1);
  }

  @Override
  public boolean equals(Object obj) {
    return graph.equals(obj);
  }

  @Override
  public int hashCode() {
    return graph.hashCode();
  }

  @Override
  public String toString() {
    return graph.toString();
  }

  @Override
  public void removeStatement(IFixedPointStatement<T> s) {
    graph.removeNodeAndEdges(s);
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Iterator<AbstractStatement> getStatements() {
    return new FilterIterator(graph.iterator(), AbstractStatement.class::isInstance);
  }

  @Override
  public void addStatement(IFixedPointStatement<T> statement)
      throws IllegalArgumentException, UnimplementedError {
    if (statement == null) {
      throw new IllegalArgumentException("statement == null");
    }
    if (statement instanceof UnaryStatement) {
      addStatement((UnaryStatement<T>) statement);
    } else if (statement instanceof NullaryStatement) {
      addStatement((NullaryStatement<T>) statement);
    } else if (statement instanceof GeneralStatement) {
      addStatement((GeneralStatement<T>) statement);
    } else {
      Assertions.UNREACHABLE("unexpected: " + statement.getClass());
    }
  }

  public void addStatement(GeneralStatement<T> s) {
    if (s == null) {
      throw new IllegalArgumentException("s is null");
    }
    IVariable<?>[] rhs = s.getRHS();
    IVariable<?> lhs = s.getLHS();

    equations.add(s);
    graph.addNode(s);
    if (lhs != null) {
      variables.add(lhs);
      graph.addNode(lhs);
      graph.addEdge(s, lhs);
    }
    for (IVariable<?> v : rhs) {
      IVariable<?> variable = v;
      if (variable != null) {
        variables.add(variable);
        graph.addNode(variable);
        graph.addEdge(variable, s);
      }
    }

    if (DEBUG) {
      checkGraph();
    }
  }

  public void addStatement(UnaryStatement<T> s) {
    if (s == null) {
      throw new IllegalArgumentException("s is null");
    }
    IVariable<?> lhs = s.getLHS();
    IVariable<?> rhs = s.getRightHandSide();

    equations.add(s);
    graph.addNode(s);
    if (lhs != null) {
      variables.add(lhs);
      graph.addNode(lhs);
      graph.addEdge(s, lhs);
    }
    variables.add(rhs);
    graph.addNode(rhs);
    graph.addEdge(rhs, s);

    if (DEBUG) {
      checkGraph();
    }
  }

  public void addStatement(NullaryStatement<T> s) {
    if (s == null) {
      throw new IllegalArgumentException("s is null");
    }
    IVariable<?> lhs = s.getLHS();

    equations.add(s);
    graph.addNode(s);
    if (lhs != null) {
      variables.add(lhs);
      graph.addNode(lhs);
      graph.addEdge(s, lhs);
    }

    if (DEBUG) {
      checkGraph();
    }
  }

  public void addVariable(T v) {
    variables.add(v);
    graph.addNode(v);
    if (DEBUG) {
      checkGraph();
    }
  }

  public AbstractStatement<?, ?> getStep(int number) {
    return (AbstractStatement<?, ?>) graph.getNode(number);
  }

  @Override
  public void reorder() {
    if (DEBUG) {
      checkGraph();
    }

    Iterator<INodeWithNumber> order = Topological.makeTopologicalIter(graph).iterator();
    int number = 0;
    while (order.hasNext()) {
      Object elt = order.next();
      if (elt instanceof IVariable) {
        @SuppressWarnings("unchecked")
        T v = (T) elt;
        v.setOrderNumber(number++);
      }
    }
  }

  /** check that this graph is well-formed */
  private void checkGraph() {
    try {
      GraphIntegrity.check(graph);
    } catch (Throwable e) {
      e.printStackTrace();
      Assertions.UNREACHABLE();
    }
  }

  @Override
  public Iterator<? extends INodeWithNumber> getStatementsThatUse(T v) {
    return (graph.containsNode(v) ? graph.getSuccNodes(v) : EmptyIterator.instance());
  }

  @Override
  public Iterator<? extends INodeWithNumber> getStatementsThatDef(T v) {
    return (graph.containsNode(v) ? graph.getPredNodes(v) : EmptyIterator.instance());
  }

  @SuppressWarnings("unchecked")
  public T getVariable(int n) {
    return (T) graph.getNode(n);
  }

  @Override
  public int getNumberOfStatementsThatUse(T v) {
    return (graph.containsNode(v) ? graph.getSuccNodeCount(v) : 0);
  }

  @Override
  public int getNumberOfStatementsThatDef(T v) {
    return (graph.containsNode(v) ? graph.getPredNodeCount(v) : 0);
  }

  @Override
  public Iterator<? extends INodeWithNumber> getVariables() {
    return new FilterIterator<>(graph.iterator(), Objects::nonNull);
  }

  public int getNumberOfNodes() {
    return graph.getNumberOfNodes();
  }

  public Iterator<? extends INodeWithNumber> getPredNodes(INodeWithNumber n) {
    return graph.getPredNodes(n);
  }

  public int getPredNodeCount(INodeWithNumber n) {
    return graph.getPredNodeCount(n);
  }

  @Override
  public boolean containsStatement(IFixedPointStatement<T> s) {
    return equations.contains(s);
  }

  @Override
  public boolean containsVariable(T v) {
    return variables.contains(v);
  }
}
