/*
 * Copyright (c) 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package com.ibm.wala.core.tests.callGraph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.InstanceOfAssertFactories.iterator;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.core.tests.demandpa.AbstractPtrTest;
import com.ibm.wala.core.tests.util.TestConstants;
import com.ibm.wala.core.tests.util.WalaTestCase;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.core.util.warnings.Warnings;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.CallGraphStats;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.util.CallGraphSearchUtil;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.InterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.collections.IteratorUtil;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;
import com.ibm.wala.util.intset.OrdinalSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests for Call Graph construction */
public class CallGraphTest extends WalaTestCase {

  public static void main(String[] args) {
    justThisTest(CallGraphTest.class);
  }

  @Tag("slow")
  @Test
  public void testJava_cup()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.JAVA_CUP, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints =
        com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(cha, TestConstants.JAVA_CUP_MAIN);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options, new AnalysisCacheImpl(), cha, useShortProfile());
  }

  @Tag("slow")
  @Test
  public void testBcelVerifier()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.BCEL, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints =
        com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(
            cha, TestConstants.BCEL_VERIFIER_MAIN);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
    // this speeds up the test
    options.setReflectionOptions(ReflectionOptions.NONE);

    doCallGraphs(options, new AnalysisCacheImpl(), cha);
  }

  @Test
  public void testJLex()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.JLEX, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints =
        com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(cha, TestConstants.JLEX_MAIN);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options, new AnalysisCacheImpl(), cha);
  }

  @Test
  public void testCornerCases()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.WALA_TESTDATA, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints = new AllApplicationEntrypoints(scope, cha);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
    // this speeds up the test
    options.setReflectionOptions(ReflectionOptions.NONE);

    doCallGraphs(options, new AnalysisCacheImpl(), cha);

    // we expect a warning or two about class Abstract1, which has no concrete
    // subclasses
    String ws = Warnings.asString();
    assertThat(ws).contains("cornerCases/Abstract1");

    // we do not expect a warning about class Abstract2, which has a concrete
    // subclasses
    assertThat(ws).doesNotContain("cornerCases/Abstract2");
  }

  @Test
  public void testHello()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.HELLO, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints =
        com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(cha, TestConstants.HELLO_MAIN);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options, new AnalysisCacheImpl(), cha);
  }

  @Test
  public void testStaticInit()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.WALA_TESTDATA, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints =
        com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(cha, "LstaticInit/TestStaticInit");
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
    CallGraph cg = CallGraphTestUtil.buildZeroCFA(options, new AnalysisCacheImpl(), cha, false);
    assertThat(cg).anyMatch(n -> n.toString().contains("doNothing"), "name contains \"doNothing\"");
    options.setHandleStaticInit(false);
    cg = CallGraphTestUtil.buildZeroCFA(options, new AnalysisCacheImpl(), cha, false);
    for (CGNode n : cg) {
      assertThat(n).asString().doesNotContain("doNothing");
    }
  }

  @Test
  public void testJava8Smoke()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.WALA_TESTDATA, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints =
        com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(cha, "Llambda/SortingExample");
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
    CallGraph cg = CallGraphTestUtil.buildZeroCFA(options, new AnalysisCacheImpl(), cha, false);
    assertThat(cg)
        .anyMatch(n -> n.toString().contains("sortForward"), "name contains \"sortForward\"");
  }

  @Test
  public void testSystemProperties()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.WALA_TESTDATA, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints =
        com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(
            cha, "LstaticInit/TestSystemProperties");
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
    SSAPropagationCallGraphBuilder builder =
        Util.makeZeroCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha);
    CallGraph cg = builder.makeCallGraph(options);
    assertThat(cg)
        .filteredOn(
            n ->
                n.toString()
                    .equals(
                        "Node: < Application, LstaticInit/TestSystemProperties, main([Ljava/lang/String;)V > Context: Everywhere"))
        .first()
        .extracting(cg::getSuccNodes, iterator(CGNode.class))
        .toIterable()
        .anyMatch(
            callee -> callee.getMethod().getName().toString().equals("toCharArray"),
            "called method name is \"toCharArray\"");
  }

  @Test
  public void testRecursion()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.WALA_TESTDATA, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints =
        com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(cha, TestConstants.RECURSE_MAIN);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options, new AnalysisCacheImpl(), cha);
  }

  @Test
  public void testHelloAllEntrypoints()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.HELLO, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints = new AllApplicationEntrypoints(scope, cha);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    doCallGraphs(options, new AnalysisCacheImpl(), cha);
  }

  @Test
  public void testIO()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            "primordial-base.txt", CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints = makePrimordialPublicEntrypoints(cha, "java/io");
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    CallGraphTestUtil.buildZeroCFA(options, new AnalysisCacheImpl(), cha, false);
  }

  public static Iterable<Entrypoint> makePrimordialPublicEntrypoints(
      ClassHierarchy cha, String pkg) {
    final HashSet<Entrypoint> result = HashSetFactory.make();
    for (IClass clazz : cha) {

      if (clazz.getName().toString().contains(pkg) && !clazz.isInterface() && !clazz.isAbstract()) {
        for (IMethod method : clazz.getDeclaredMethods()) {
          if (method.isPublic() && !method.isAbstract()) {
            System.out.println("Entry:" + method.getReference());
            result.add(new DefaultEntrypoint(method, cha));
          }
        }
      }
    }
    return result::iterator;
  }

  @Test
  public void testPrimordial()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    if (useShortProfile()) {
      return;
    }

    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            "primordial.txt",
            System.getProperty("os.name").equals("Mac OS X")
                ? "Java60RegressionExclusions.txt"
                : "GUIExclusions.txt");
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints = makePrimordialMainEntrypoints(cha);
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    CallGraphTestUtil.buildZeroCFA(options, new AnalysisCacheImpl(), cha, false);
  }

  @Test
  public void testZeroOneContainerCopyOf()
      throws IOException, ClassHierarchyException, IllegalArgumentException, CancelException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.WALA_TESTDATA, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(cha, "Ldemandpa/TestArraysCopyOf");
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
    IAnalysisCacheView cache = new AnalysisCacheImpl();
    CallGraphBuilder<InstanceKey> builder =
        Util.makeZeroOneContainerCFABuilder(options, cache, cha);
    CallGraph cg = builder.makeCallGraph(options, null);
    PointerAnalysis<InstanceKey> pa = builder.getPointerAnalysis();
    CGNode mainMethod = AbstractPtrTest.findMainMethod(cg);
    PointerKey keyToQuery = AbstractPtrTest.getParam(mainMethod, "testThisVar", pa.getHeapModel());
    OrdinalSet<InstanceKey> pointsToSet = pa.getPointsToSet(keyToQuery);
    assertThat(pointsToSet).hasSize(1);
  }

  @Test
  public void testZeroOneContainerStaticMethods()
      throws IOException, ClassHierarchyException, IllegalArgumentException, CancelException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.WALA_TESTDATA, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(cha, "Lslice/TestIntegerValueOf");
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
    IAnalysisCacheView cache = new AnalysisCacheImpl();
    CallGraphBuilder<InstanceKey> builder =
        Util.makeZeroOneContainerCFABuilder(options, cache, cha);
    CallGraph cg = builder.makeCallGraph(options, null);
    CGNode mainMethod = CallGraphSearchUtil.findMainMethod(cg);
    assertThat(
            StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(
                        cg.getSuccNodes(mainMethod), Spliterator.ORDERED),
                    false)
                .filter(succ -> succ.getMethod().getName().toString().equals("valueOf")))
        .isNotEmpty();
  }

  /** Testing that there is no crash during iteration of points to sets */
  @Test
  public void testIteratingPointsToSetsForCreationSites()
      throws CallGraphBuilderCancelException, IOException, ClassHierarchyException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            TestConstants.WALA_TESTDATA, CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(cha, "Lsimple/Example");
    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
    IAnalysisCacheView cache = new AnalysisCacheImpl();
    CallGraphBuilder<InstanceKey> builder =
        Util.makeZeroCFABuilder(Language.JAVA, options, cache, cha);
    CallGraph cg = builder.makeCallGraph(options, null);
    PointerAnalysis<InstanceKey> pointerAnalysis = builder.getPointerAnalysis();
    for (PointerKey pk : pointerAnalysis.getPointerKeys()) {
      if (pk instanceof LocalPointerKey) {
        LocalPointerKey lpk = (LocalPointerKey) pk;
        String className = lpk.getNode().getMethod().getDeclaringClass().getName().toString();
        String methodName = lpk.getNode().getMethod().getName().toString();
        ClassLoaderReference clr =
            lpk.getNode().getMethod().getDeclaringClass().getClassLoader().getReference();
        if (clr.equals(ClassLoaderReference.Application)
            && className.equals("Lsimple/Example")
            && methodName.equals("foo")) {
          if (lpk.isParameter()) {
            for (InstanceKey ik : pointerAnalysis.getPointsToSet(lpk)) {
              Iterator<Pair<CGNode, NewSiteReference>> iterator = ik.getCreationSites(cg);
              while (iterator.hasNext()) {
                iterator.next(); // making sure there is no crash here
              }
            }
          }
        }
      }
    }
  }

  /** make main entrypoints, even in the primordial loader. */
  public static Iterable<Entrypoint> makePrimordialMainEntrypoints(ClassHierarchy cha) {
    final Atom mainMethod = Atom.findOrCreateAsciiAtom("main");
    final HashSet<Entrypoint> result = HashSetFactory.make();
    for (IClass klass : cha) {
      MethodReference mainRef =
          MethodReference.findOrCreate(
              klass.getReference(),
              mainMethod,
              Descriptor.findOrCreateUTF8("([Ljava/lang/String;)V"));
      IMethod m = klass.getMethod(mainRef.getSelector());
      if (m != null) {
        result.add(new DefaultEntrypoint(m, cha));
      }
    }
    return result::iterator;
  }

  public static void doCallGraphs(
      AnalysisOptions options, IAnalysisCacheView cache, IClassHierarchy cha)
      throws IllegalArgumentException, CancelException {
    doCallGraphs(options, cache, cha, false);
  }

  /** TODO: refactor this to avoid excessive code bloat. */
  public static void doCallGraphs(
      AnalysisOptions options,
      IAnalysisCacheView cache,
      IClassHierarchy cha,
      boolean testPAToString)
      throws IllegalArgumentException, CancelException {

    // ///////////////
    // // RTA /////
    // ///////////////
    CallGraph cg = CallGraphTestUtil.buildRTA(options, cache, cha);
    try {
      GraphIntegrity.check(cg);
    } catch (UnsoundGraphException e1) {
      e1.printStackTrace();
      fail(e1.getMessage());
    }

    Set<MethodReference> rtaMethods = CallGraphStats.collectMethods(cg);
    System.err.println("RTA methods reached: " + rtaMethods.size());
    System.err.println(CallGraphStats.getStats(cg));
    System.err.println("RTA warnings:\n");

    // ///////////////
    // // 0-CFA /////
    // ///////////////
    cg = CallGraphTestUtil.buildZeroCFA(options, cache, cha, testPAToString);

    // FIXME: annoying special cases caused by clone2assign mean using
    // the rta graph for proper graph subset checking does not work.
    // (note that all the other such checks do use proper graph subset)
    Graph<MethodReference> squashZero = checkCallGraph(cg, null, "0-CFA");

    // test Pretransitive 0-CFA
    // not currently supported
    // warnings = new WarningSet();
    // options.setUsePreTransitiveSolver(true);
    // CallGraph cgP = CallGraphTestUtil.buildZeroCFA(options, cha, scope,
    // warnings);
    // options.setUsePreTransitiveSolver(false);
    // Graph squashPT = checkCallGraph(warnings, cgP, squashZero, null, "Pre-T
    // 1");
    // checkCallGraph(warnings, cg, squashPT, null, "Pre-T 2");

    // ///////////////
    // // 0-1-CFA ///
    // ///////////////
    cg = CallGraphTestUtil.buildZeroOneCFA(options, cache, cha, testPAToString);
    Graph<MethodReference> squashZeroOne = checkCallGraph(cg, squashZero, "0-1-CFA");

    // ///////////////////////////////////////////////////
    // // 0-CFA augmented to disambiguate containers ///
    // ///////////////////////////////////////////////////
    cg = CallGraphTestUtil.buildZeroContainerCFA(options, cache, cha);
    Graph<MethodReference> squashZeroContainer = checkCallGraph(cg, squashZero, "0-Container-CFA");

    // ///////////////////////////////////////////////////
    // // 0-1-CFA augmented to disambiguate containers ///
    // ///////////////////////////////////////////////////
    cg = CallGraphTestUtil.buildZeroOneContainerCFA(options, cache, cha);
    checkCallGraph(cg, squashZeroContainer, "0-1-Container-CFA");
    checkCallGraph(cg, squashZeroOne, "0-1-Container-CFA");

    // test ICFG
    checkICFG(cg);
    return;
    // /////////////
    // // 1-CFA ///
    // /////////////
    // warnings = new WarningSet();
    // cg = buildOneCFA();

  }

  /** Check properties of the InterproceduralCFG */
  private static void checkICFG(CallGraph cg) {
    InterproceduralCFG icfg = new InterproceduralCFG(cg);

    try {
      GraphIntegrity.check(icfg);
    } catch (UnsoundGraphException e) {
      e.printStackTrace();
      fail();
    }

    // perform a little icfg exercise
    @SuppressWarnings("unused")
    int count = 0;
    for (BasicBlockInContext<ISSABasicBlock> bb : icfg) {
      if (icfg.hasCall(bb)) {
        count++;
      }
    }
  }

  /**
   * Check consistency of a callgraph, and check that this call graph is a subset of a super-graph
   *
   * @return a squashed version of cg
   */
  private static Graph<MethodReference> checkCallGraph(
      CallGraph cg, Graph<MethodReference> superCG, String thisAlgorithm) {
    try {
      GraphIntegrity.check(cg);
    } catch (UnsoundGraphException e1) {
      fail(e1.getMessage());
    }
    Set<MethodReference> callGraphMethods = CallGraphStats.collectMethods(cg);
    System.err.println(thisAlgorithm + " methods reached: " + callGraphMethods.size());
    System.err.println(CallGraphStats.getStats(cg));

    Graph<MethodReference> thisCG = squashCallGraph(thisAlgorithm, cg);

    if (superCG != null) {
      com.ibm.wala.ipa.callgraph.impl.Util.checkGraphSubset(superCG, thisCG);
    } else {
      // SJF: RTA has rotted a bit since it doesn't handle LoadClass instructions.
      // Commenting this out for now.
      // if (!superMethods.containsAll(callGraphMethods)) {
      // Set<MethodReference> temp = HashSetFactory.make();
      // temp.addAll(callGraphMethods);
      // temp.removeAll(superMethods);
      // System.err.println("Violations");
      // for (MethodReference m : temp) {
      // System.err.println(m);
      // }
      // Assertions.UNREACHABLE();
      // }
    }

    return thisCG;
  }

  /**
   * @return a graph whose nodes are MethodReferences, and whose edges represent calls between
   *     MethodReferences
   * @throws IllegalArgumentException if cg is null
   */
  public static Graph<MethodReference> squashCallGraph(final String name, final CallGraph cg) {
    if (cg == null) {
      throw new IllegalArgumentException("cg is null");
    }
    final Set<MethodReference> nodes = HashSetFactory.make();
    for (CGNode cgNode : cg) {
      nodes.add(cgNode.getMethod().getReference());
    }

    return new Graph<>() {
      @Override
      public String toString() {
        return "squashed " + name + " call graph\n" + "Original graph:" + cg;
      }

      /*
       * @see com.ibm.wala.util.graph.NodeManager#iterator()
       */
      @Override
      public Iterator<MethodReference> iterator() {
        return nodes.iterator();
      }

      @Override
      public Stream<MethodReference> stream() {
        return nodes.stream();
      }

      /*
       * @see com.ibm.wala.util.graph.NodeManager#containsNode(java.lang.Object)
       */
      @Override
      public boolean containsNode(MethodReference N) {
        return nodes.contains(N);
      }

      /*
       * @see com.ibm.wala.util.graph.NodeManager#getNumberOfNodes()
       */
      @Override
      public int getNumberOfNodes() {
        return nodes.size();
      }

      /*
       * @see com.ibm.wala.util.graph.EdgeManager#getPredNodes(java.lang.Object)
       */
      @Override
      public Iterator<MethodReference> getPredNodes(MethodReference N) {
        Set<MethodReference> pred = HashSetFactory.make(10);
        MethodReference methodReference = N;
        for (CGNode cgNode : cg.getNodes(methodReference))
          for (CGNode p : Iterator2Iterable.make(cg.getPredNodes(cgNode)))
            pred.add(p.getMethod().getReference());

        return pred.iterator();
      }

      /*
       * @see com.ibm.wala.util.graph.EdgeManager#getPredNodeCount(java.lang.Object)
       */
      @Override
      public int getPredNodeCount(MethodReference N) {
        return IteratorUtil.count(getPredNodes(N));
      }

      /*
       * @see com.ibm.wala.util.graph.EdgeManager#getSuccNodes(java.lang.Object)
       */
      @Override
      public Iterator<MethodReference> getSuccNodes(MethodReference N) {
        Set<MethodReference> succ = HashSetFactory.make(10);
        MethodReference methodReference = N;
        for (CGNode node : cg.getNodes(methodReference))
          for (CGNode p : Iterator2Iterable.make(cg.getSuccNodes(node)))
            succ.add(p.getMethod().getReference());

        return succ.iterator();
      }

      /*
       * @see com.ibm.wala.util.graph.EdgeManager#getSuccNodeCount(java.lang.Object)
       */
      @Override
      public int getSuccNodeCount(MethodReference N) {
        return IteratorUtil.count(getSuccNodes(N));
      }

      /*
       * @see com.ibm.wala.util.graph.NodeManager#addNode(java.lang.Object)
       */
      @Override
      public void addNode(MethodReference n) {
        Assertions.UNREACHABLE();
      }

      /*
       * @see com.ibm.wala.util.graph.NodeManager#removeNode(java.lang.Object)
       */
      @Override
      public void removeNode(MethodReference n) {
        Assertions.UNREACHABLE();
      }

      /*
       * @see com.ibm.wala.util.graph.EdgeManager#addEdge(java.lang.Object, java.lang.Object)
       */
      @Override
      public void addEdge(MethodReference src, MethodReference dst) {
        Assertions.UNREACHABLE();
      }

      @Override
      public void removeEdge(MethodReference src, MethodReference dst) {
        Assertions.UNREACHABLE();
      }

      /*
       * @see com.ibm.wala.util.graph.EdgeManager#removeAllIncidentEdges(java.lang.Object)
       */
      @Override
      public void removeAllIncidentEdges(MethodReference node) {
        Assertions.UNREACHABLE();
      }

      /*
       * @see com.ibm.wala.util.graph.Graph#removeNodeAndEdges(java.lang.Object)
       */
      @Override
      public void removeNodeAndEdges(MethodReference N) {
        Assertions.UNREACHABLE();
      }

      @Override
      public void removeIncomingEdges(MethodReference node) {
        // TODO Auto-generated method stubMethodReference
        Assertions.UNREACHABLE();
      }

      @Override
      public void removeOutgoingEdges(MethodReference node) {
        // TODO Auto-generated method stub
        Assertions.UNREACHABLE();
      }

      @Override
      public boolean hasEdge(MethodReference src, MethodReference dst) {
        for (MethodReference succ : Iterator2Iterable.make(getSuccNodes(src))) {
          if (dst.equals(succ)) {
            return true;
          }
        }
        return false;
      }
    };
  }
}
