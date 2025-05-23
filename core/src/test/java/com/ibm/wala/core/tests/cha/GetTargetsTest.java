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
package com.ibm.wala.core.tests.cha;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.wala.classLoader.ClassLoaderFactory;
import com.ibm.wala.classLoader.ClassLoaderFactoryImpl;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.core.tests.util.TestConstants;
import com.ibm.wala.core.tests.util.WalaTestCase;
import com.ibm.wala.core.util.config.AnalysisScopeReader;
import com.ibm.wala.core.util.io.FileProvider;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import java.util.Collection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Test ClassHierarchy.getPossibleTargets */
public class GetTargetsTest extends WalaTestCase {

  private static final ClassLoader MY_CLASSLOADER = GetTargetsTest.class.getClassLoader();

  private static AnalysisScope scope;
  private static ClassHierarchy cha;

  public static void main(String[] args) {
    justThisTest(GetTargetsTest.class);
  }

  @BeforeAll
  public static void beforeClass() throws Exception {

    scope =
        AnalysisScopeReader.instance.readJavaScope(
            TestConstants.WALA_TESTDATA,
            new FileProvider().getFile("J2SEClassHierarchyExclusions.txt"),
            MY_CLASSLOADER);

    ClassLoaderFactory factory = new ClassLoaderFactoryImpl(scope.getExclusions());

    try {
      cha = ClassHierarchyFactory.make(scope, factory);
    } catch (ClassHierarchyException e) {
      throw new Exception(e);
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see junit.framework.TestCase#tearDown()
   */
  @AfterAll
  public static void afterClass() throws Exception {
    scope = null;
    cha = null;
  }

  /** Test for bug 1714480, reported OOM on {@link ClassHierarchy} getPossibleTargets() */
  @Test
  public void testCell() {
    TypeReference t = TypeReference.findOrCreate(ClassLoaderReference.Application, "Lcell/Cell");
    MethodReference m = MethodReference.findOrCreate(t, "<init>", "(Ljava/lang/Object;)V");
    Collection<IMethod> c = cha.getPossibleTargets(m);
    for (IMethod method : c) {
      System.err.println(method);
    }
    assertThat(c).hasSize(1);
  }

  /** test that calls to &lt;init&gt; methods are treated specially */
  @Test
  public void testObjInit() {
    MethodReference m =
        MethodReference.findOrCreate(TypeReference.JavaLangObject, MethodReference.initSelector);
    Collection<IMethod> c = cha.getPossibleTargets(m);
    for (IMethod method : c) {
      System.err.println(method);
    }
    assertThat(c).hasSize(1);
  }

  @Test
  public void testConstructorLookup() {
    IClass testKlass =
        cha.lookupClass(
            TypeReference.findOrCreate(
                ClassLoaderReference.Application, "LmethodLookup/MethodLookupStuff$B"));
    IMethod m = testKlass.getMethod(Selector.make("<init>(I)V"));
    assertThat(m).isNull();
  }
}
