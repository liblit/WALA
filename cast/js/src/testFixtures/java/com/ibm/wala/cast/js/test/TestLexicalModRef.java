/*
 * Copyright (c) 2008 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package com.ibm.wala.cast.js.test;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.wala.cast.ipa.lexical.LexicalModRef;
import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.OrdinalSet;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

public abstract class TestLexicalModRef {

  @Test
  public void testSimpleLexical()
      throws IOException, IllegalArgumentException, CancelException, WalaException {
    JSCFABuilder b = JSCallGraphBuilderUtil.makeScriptCGBuilder("tests", "simple-lexical.js");
    CallGraph CG = b.makeCallGraph(b.getOptions());
    LexicalModRef lexAccesses = LexicalModRef.make(CG, b.getPointerAnalysis());
    Map<CGNode, OrdinalSet<Pair<CGNode, String>>> readResult = lexAccesses.computeLexicalRef();
    Map<CGNode, OrdinalSet<Pair<CGNode, String>>> writeResult = lexAccesses.computeLexicalMod();
    for (Map.Entry<CGNode, OrdinalSet<Pair<CGNode, String>>> entry : readResult.entrySet()) {
      final CGNode n = entry.getKey();
      if (n.toString()
          .contains("Node: <Code body of function Ltests/simple-lexical.js/outer/inner>")) {
        // function "inner" reads exactly x and z
        OrdinalSet<Pair<CGNode, String>> readVars = entry.getValue();
        assertThat(readVars).hasSize(2);
        assertThat(readVars)
            .hasToString(
                "[[Node: <Code body of function Ltests/simple-lexical.js/outer> Context: Everywhere,x], [Node: <Code body of function Ltests/simple-lexical.js/outer> Context: Everywhere,z]]");
        // writes x and z as well
        OrdinalSet<Pair<CGNode, String>> writtenVars = writeResult.get(n);
        assertThat(writtenVars).hasSize(2);
        assertThat(writtenVars)
            .hasToString(
                "[[Node: <Code body of function Ltests/simple-lexical.js/outer> Context: Everywhere,x], [Node: <Code body of function Ltests/simple-lexical.js/outer> Context: Everywhere,z]]");
      }
      if (n.toString()
          .contains("Node: <Code body of function Ltests/simple-lexical.js/outer/inner2>")) {
        // function "inner3" reads exactly innerName, inner3, and x and z via callees
        OrdinalSet<Pair<CGNode, String>> readVars = entry.getValue();
        assertThat(readVars).hasSize(4);
        for (Pair<CGNode, String> rv : readVars) {
          assertThat(rv)
              .asString()
              .isIn(
                  "[Node: <Code body of function Ltests/simple-lexical.js/outer> Context: Everywhere,x]",
                  "[Node: <Code body of function Ltests/simple-lexical.js/outer> Context: Everywhere,inner3]",
                  "[Node: <Code body of function Ltests/simple-lexical.js/outer> Context: Everywhere,innerName]",
                  "[Node: <Code body of function Ltests/simple-lexical.js/outer> Context: Everywhere,z]");
        }
      }
    }
  }
}
