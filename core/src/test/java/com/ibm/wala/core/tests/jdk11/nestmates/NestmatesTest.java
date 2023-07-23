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
package com.ibm.wala.core.tests.jdk11.nestmates;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.core.tests.util.WalaTestCase;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class NestmatesTest extends WalaTestCase {
  @Test
  public void testPrivateInterfaceMethods()
      throws ClassHierarchyException, IllegalArgumentException, CancelException, IOException {
    AnalysisScope scope =
        CallGraphTestUtil.makeJ2SEAnalysisScope(
            "wala.testdata.txt", CallGraphTestUtil.REGRESSION_EXCLUSIONS);
    ClassHierarchy cha = ClassHierarchyFactory.make(scope);
    Iterable<Entrypoint> entrypoints =
        com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(cha, "Lnestmates/TestNestmates");

    AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);

    CallGraph cg = CallGraphTestUtil.buildZeroCFA(options, new AnalysisCacheImpl(), cha, false);

    // Find node corresponding to main
    TypeReference tm =
        TypeReference.findOrCreate(ClassLoaderReference.Application, "Lnestmates/TestNestmates");
    MethodReference mm = MethodReference.findOrCreate(tm, "main", "([Ljava/lang/String;)V");
    assertTrue(cg.getNodes(mm).iterator().hasNext(), "expect main node");
    CGNode mnode = cg.getNodes(mm).iterator().next();

    // should be from main to Triple()
    TypeReference t1s =
        TypeReference.findOrCreate(ClassLoaderReference.Application, "Lnestmates/Outer$Inner");
    MethodReference t1m = MethodReference.findOrCreate(t1s, "triple", "()I");
    assertTrue(cg.getNodes(t1m).iterator().hasNext(), "expect Outer.Inner.triple node");
    CGNode t1node = cg.getNodes(t1m).iterator().next();

    // Check call from main to Triple()
    assertTrue(
        cg.getPossibleSites(mnode, t1node).hasNext(),
        "should have call site from main to TestNestmates.triple()");

    // check that triple() does not call an accessor method
    assertFalse(
        cg.getSuccNodes(t1node).hasNext(),
        "there should not be a call from triple() to an accessor method");
  }
}
