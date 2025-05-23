/*
 * Copyright (c) 2002 - 2010 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package com.ibm.wala.core.tests.basic;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.wala.core.tests.util.WalaTestCase;
import com.ibm.wala.util.graph.INodeWithNumberedEdges;
import com.ibm.wala.util.graph.NumberedGraph;
import com.ibm.wala.util.graph.impl.DelegatingNumberedGraph;
import com.ibm.wala.util.graph.traverse.FloydWarshall;
import com.ibm.wala.util.graph.traverse.FloydWarshall.GetPath;
import com.ibm.wala.util.graph.traverse.FloydWarshall.GetPaths;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class FloydWarshallTest extends WalaTestCase {

  public static class Node implements INodeWithNumberedEdges {
    private final int number;
    private final MutableIntSet preds = IntSetUtil.make();
    private final MutableIntSet succs = IntSetUtil.make();

    @Override
    public int getGraphNodeId() {
      return number;
    }

    public Node(int number) {
      this.number = number;
    }

    @Override
    public void setGraphNodeId(int number) {
      throw new UnsupportedOperationException();
    }

    @Override
    public IntSet getSuccNumbers() {
      return succs;
    }

    @Override
    public IntSet getPredNumbers() {
      return preds;
    }

    @Override
    public void addSucc(int n) {
      succs.add(n);
    }

    @Override
    public void addPred(int n) {
      preds.add(n);
    }

    @Override
    public void removeAllIncidentEdges() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeIncomingEdges() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeOutgoingEdges() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
      return "<" + number + ">";
    }
  }

  public static NumberedGraph<Node> makeGraph() {
    NumberedGraph<Node> G = new DelegatingNumberedGraph<>();

    for (int i = 0; i <= 8; i++) {
      G.addNode(new Node(i));
    }

    G.addEdge(G.getNode(1), G.getNode(2));
    G.addEdge(G.getNode(2), G.getNode(3));
    G.addEdge(G.getNode(3), G.getNode(4));
    G.addEdge(G.getNode(3), G.getNode(5));
    G.addEdge(G.getNode(4), G.getNode(6));
    G.addEdge(G.getNode(5), G.getNode(7));
    G.addEdge(G.getNode(6), G.getNode(8));
    G.addEdge(G.getNode(7), G.getNode(8));

    G.addEdge(G.getNode(6), G.getNode(4));
    G.addEdge(G.getNode(6), G.getNode(2));

    return G;
  }

  private final NumberedGraph<Node> G = makeGraph();

  private final int[][] shortestPaths =
      new int[][] {
        {
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE
        },
        {Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 2, 3, 3, 4, 4, 5},
        {Integer.MAX_VALUE, Integer.MAX_VALUE, 4, 1, 2, 2, 3, 3, 4},
        {Integer.MAX_VALUE, Integer.MAX_VALUE, 3, 4, 1, 1, 2, 2, 3},
        {Integer.MAX_VALUE, Integer.MAX_VALUE, 2, 3, 2, 4, 1, 5, 2},
        {
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          1,
          2
        },
        {Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 2, 1, 3, 2, 4, 1},
        {
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          1
        },
        {
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE,
          Integer.MAX_VALUE
        }
      };

  @Test
  public void TestPathLengths() {
    assertThat(FloydWarshall.shortestPathLengths(G)).isDeepEqualTo(shortestPaths);
  }

  @Test
  public void TestShortestPath() {
    GetPath<Node> result = FloydWarshall.allPairsShortestPath(G);
    assertThat(singletonList(G.getNode(2))).isEqualTo(result.getPath(G.getNode(1), G.getNode(3)));
    assertThat(singletonList(G.getNode(7))).isEqualTo(result.getPath(G.getNode(5), G.getNode(8)));
    assertThat(asList(G.getNode(2), G.getNode(3), G.getNode(5)))
        .isEqualTo(result.getPath(G.getNode(1), G.getNode(7)));
    assertThat(asList(G.getNode(2), G.getNode(3), G.getNode(4)))
        .isEqualTo(result.getPath(G.getNode(1), G.getNode(6)));
  }

  @Test
  public void TestShortestPaths() {
    GetPaths<Node> result = FloydWarshall.allPairsShortestPaths(G);

    Set<List<Node>> expectedPaths = expectedPaths(G);
    Set<List<Node>> resultPaths = result.getPaths(G.getNode(1), G.getNode(8));

    assertThat(expectedPaths).hasSameSizeAs(resultPaths);
    for (List<Node> rp : resultPaths) {
      assertThat(expectedPaths).contains(rp);
    }
  }

  public static Set<List<Node>> expectedPaths(NumberedGraph<Node> G) {
    Set<List<Node>> paths = new HashSet<>();
    paths.add(
        new ArrayList<>(Arrays.asList(G.getNode(2), G.getNode(3), G.getNode(4), G.getNode(6))));
    paths.add(
        new ArrayList<>(Arrays.asList(G.getNode(2), G.getNode(3), G.getNode(5), G.getNode(7))));
    return paths;
  }
}
