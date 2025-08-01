/*
 * Copyright (c) 2014 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package com.ibm.wala.dalvik.test.callGraph;

import static com.ibm.wala.dalvik.test.util.Util.convertJarToDex;
import static com.ibm.wala.dalvik.test.util.Util.getJavaJar;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.core.tests.util.TestConstants;
import com.ibm.wala.dalvik.classLoader.DexIMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class JVMLDalvikComparisonTest extends DalvikCallGraphTestBase {

  private static Pair<CallGraph, PointerAnalysis<InstanceKey>> makeJavaBuilder(
      String scopeFile, String mainClass)
      throws IOException, ClassHierarchyException, IllegalArgumentException, CancelException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(scopeFile, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints =
        com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(cha, mainClass);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
    SSAPropagationCallGraphBuilder builder =
        Util.makeZeroCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha);
    CallGraph CG = builder.makeCallGraph(options);
    return Pair.make(CG, builder.getPointerAnalysis());
  }

  private static Set<Pair<CGNode, CGNode>> edgeDiff(
      CallGraph from, CallGraph to, boolean userOnly) {
    Set<Pair<CGNode, CGNode>> result = HashSetFactory.make();
    for (CGNode f : from) {
      if (!f.getMethod().isWalaSynthetic()) {
        outer:
        for (CGNode t : from) {
          if (!t.getMethod().isWalaSynthetic()
              && from.hasEdge(f, t)
              && (!userOnly
                  || !t.getMethod()
                      .getDeclaringClass()
                      .getClassLoader()
                      .getReference()
                      .equals(ClassLoaderReference.Primordial))) {
            Set<CGNode> fts = to.getNodes(f.getMethod().getReference());
            Set<CGNode> tts = to.getNodes(t.getMethod().getReference());
            for (CGNode x : fts) {
              for (CGNode y : tts) {
                if (to.hasEdge(x, y)) {
                  continue outer;
                }
              }
            }
            result.add(Pair.make(f, t));
          }
        }
      }
    }
    return result;
  }

  /**
   * Run tests to compare the call graphs computing with the JVM bytecode frontend vs the Dalvik
   * frontend
   *
   * @param mainClass main class for the test
   * @param javaScopeFile scope file for the test
   */
  private static void test(String mainClass, String javaScopeFile)
      throws IllegalArgumentException, IOException, CancelException, ClassHierarchyException {
    Pair<CallGraph, PointerAnalysis<InstanceKey>> java = makeJavaBuilder(javaScopeFile, mainClass);

    AnalysisScope javaScope = java.fst.getClassHierarchy().getScope();
    String javaJarPath = getJavaJar(javaScope);
    File androidDex = convertJarToDex(javaJarPath);
    Pair<CallGraph, PointerAnalysis<InstanceKey>> android =
        makeDalvikCallGraph(null, null, mainClass, androidDex.toPath().toAbsolutePath());

    Set<MethodReference> androidMethods = applicationMethods(android.fst);
    Set<MethodReference> javaMethods = applicationMethods(java.fst);

    Set<Pair<CGNode, CGNode>> javaExtraEdges = edgeDiff(java.fst, android.fst, false);
    assert !checkEdgeDiff(android, androidMethods, javaMethods, javaExtraEdges)
        : "found extra edges in Java call graph";

    Set<Pair<CGNode, CGNode>> androidExtraEdges = edgeDiff(android.fst, java.fst, true);
    assert !checkEdgeDiff(java, javaMethods, androidMethods, androidExtraEdges)
        : "found extra edges in Android call graph";

    checkSourceLines(java.fst, android.fst);
  }

  private static void checkSourceLines(CallGraph java, CallGraph android) {
    MutableIntSet ajlines = IntSetUtil.make();
    MutableIntSet aalines = IntSetUtil.make();
    java.forEach(
        jnode -> {
          if (jnode
              .getMethod()
              .getReference()
              .getDeclaringClass()
              .getClassLoader()
              .equals(ClassLoaderReference.Application)) {
            if (jnode.getMethod() instanceof ShrikeCTMethod) {
              ShrikeCTMethod m = (ShrikeCTMethod) jnode.getMethod();
              MutableIntSet jlines = IntSetUtil.make();
              for (SSAInstruction inst : jnode.getIR().getInstructions()) {
                if (inst != null) {
                  try {
                    int bcIndex = m.getBytecodeIndex(inst.iIndex());
                    int javaLine = m.getLineNumber(bcIndex);
                    jlines.add(javaLine);
                    ajlines.add(javaLine);
                  } catch (InvalidClassFileException e) {
                    assert false : e;
                  }
                }
              }

              for (CGNode an : android.getNodes(m.getReference())) {
                DexIMethod am = (DexIMethod) an.getMethod();
                MutableIntSet alines = IntSetUtil.make();
                for (SSAInstruction ainst : an.getIR().getInstructions()) {
                  if (ainst != null) {
                    int ai = am.getLineNumber(am.getBytecodeIndex(ainst.iIndex()));
                    if (ai >= 0) {
                      alines.add(ai);
                      aalines.add(ai);
                    }
                  }
                }

                assert !alines.isEmpty() : "no debug info";
              }
            }
          }
        });

    IntSet both = ajlines.intersection(aalines);
    assert both.size() >= .8 * ajlines.size()
        : "inconsistent debug info: " + ajlines + " " + aalines;
  }

  private static boolean checkEdgeDiff(
      Pair<CallGraph, PointerAnalysis<InstanceKey>> firstResult,
      Set<MethodReference> methodsInFirst,
      Set<MethodReference> methodsInSecond,
      Set<Pair<CGNode, CGNode>> extraEdgesInSecond) {
    boolean fail = false;
    if (!extraEdgesInSecond.isEmpty()) {
      fail = true;
      Set<MethodReference> extraMethodsInSecond = HashSetFactory.make(methodsInSecond);
      extraMethodsInSecond.removeAll(methodsInFirst);

      System.err.println(extraEdgesInSecond);
      System.err.println(extraMethodsInSecond);

      CallGraph firstCG = firstResult.fst;
      for (Pair<CGNode, CGNode> p : extraEdgesInSecond) {
        System.err.println("### " + p.fst);
        System.err.println("### " + p.snd);
        System.err.println("### " + p.fst.getIR());
        System.err.println("====");
        Set<CGNode> nodes = firstCG.getNodes(p.fst.getMethod().getReference());
        for (CGNode n : nodes) {
          System.err.println("### " + n);
          System.err.println("### " + n.getIR());
        }
      }
    }
    return fail;
  }

  @Test
  public void testJLex()
      throws ClassHierarchyException, IllegalArgumentException, IOException, CancelException {
    test(TestConstants.JLEX_MAIN, TestConstants.JLEX);
  }

  @Test
  public void testJavaCup()
      throws ClassHierarchyException, IllegalArgumentException, IOException, CancelException {
    test(TestConstants.JAVA_CUP_MAIN, TestConstants.JAVA_CUP);
  }

  @Test
  public void testBCEL()
      throws ClassHierarchyException, IllegalArgumentException, IOException, CancelException {
    test(TestConstants.BCEL_VERIFIER_MAIN, TestConstants.BCEL);
  }
}
