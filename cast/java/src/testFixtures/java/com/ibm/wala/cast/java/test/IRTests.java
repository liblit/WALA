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
/*
 * Created on Oct 3, 2005
 */
package com.ibm.wala.cast.java.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.ibm.wala.cast.java.client.JavaSourceAnalysisEngine;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.loader.AstClass;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.loader.AstMethod.DebuggingInformation;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.SourceDirectoryTreeModule;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.client.AbstractAnalysisEngine;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.collections.Pair;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;

public abstract class IRTests {

  protected boolean dump = true;

  protected IRTests(String projectName) {
    this.projectName = projectName;
  }

  protected final String projectName;

  protected static String javaHomePath;

  public static final List<String> rtJar = Arrays.asList(WalaProperties.getJ2SEJarFiles());

  protected static List<IRAssertion> emptyList = Collections.emptyList();

  public interface IRAssertion {

    void check(CallGraph cg);
  }

  protected static class EdgeAssertions implements IRAssertion {
    public final String srcDescriptor;

    public final List /* <String> */<String> tgtDescriptors = new ArrayList<>();

    public EdgeAssertions(String srcDescriptor) {
      this.srcDescriptor = srcDescriptor;
    }

    public static EdgeAssertions make(String srcDescriptor, String tgtDescriptor) {
      EdgeAssertions ea = new EdgeAssertions(srcDescriptor);
      ea.tgtDescriptors.add(tgtDescriptor);
      return ea;
    }

    public static EdgeAssertions make(
        String srcDescriptor, String tgtDescriptor1, String tgtDescriptor2) {
      EdgeAssertions ea = new EdgeAssertions(srcDescriptor);
      ea.tgtDescriptors.add(tgtDescriptor1);
      ea.tgtDescriptors.add(tgtDescriptor2);
      return ea;
    }

    public static EdgeAssertions make(
        String srcDescriptor, String tgtDescriptor1, String tgtDescriptor2, String tgtDescriptor3) {
      EdgeAssertions ea = new EdgeAssertions(srcDescriptor);
      ea.tgtDescriptors.add(tgtDescriptor1);
      ea.tgtDescriptors.add(tgtDescriptor2);
      ea.tgtDescriptors.add(tgtDescriptor3);
      return ea;
    }

    public static EdgeAssertions make(
        String srcDescriptor,
        String tgtDescriptor1,
        String tgtDescriptor2,
        String tgtDescriptor3,
        String tgtDescriptor4) {
      EdgeAssertions ea = new EdgeAssertions(srcDescriptor);
      ea.tgtDescriptors.add(tgtDescriptor1);
      ea.tgtDescriptors.add(tgtDescriptor2);
      ea.tgtDescriptors.add(tgtDescriptor3);
      ea.tgtDescriptors.add(tgtDescriptor4);
      return ea;
    }

    @Override
    public void check(CallGraph callGraph) {
      MethodReference srcMethod =
          descriptorToMethodRef(this.srcDescriptor, callGraph.getClassHierarchy());
      Set<CGNode> srcNodes = callGraph.getNodes(srcMethod);

      if (srcNodes.isEmpty()) {
        System.err.println(("Unreachable/non-existent method: " + srcMethod));
        return;
      }
      if (srcNodes.size() > 1) {
        System.err.println("Context-sensitive call graph?");
      }

      // Assume only one node for src method
      CGNode srcNode = srcNodes.iterator().next();

      for (String target : this.tgtDescriptors) {
        MethodReference tgtMethod = descriptorToMethodRef(target, callGraph.getClassHierarchy());
        // Assume only one node for target method
        Set<CGNode> tgtNodes = callGraph.getNodes(tgtMethod);
        if (tgtNodes.isEmpty()) {
          System.err.println(("Unreachable/non-existent method: " + tgtMethod));
          continue;
        }
        CGNode tgtNode = tgtNodes.iterator().next();

        boolean found = false;
        for (CGNode succ : Iterator2Iterable.make(callGraph.getSuccNodes(srcNode))) {
          if (tgtNode == succ) {
            found = true;
            break;
          }
        }
        if (!found) {
          System.err.println(("Missing edge: " + srcMethod + " -> " + tgtMethod));
        }
      }
    }
  }

  protected static class InstructionOperandAssertion implements IRAssertion {
    private final String method;
    private final Predicate<SSAInstruction> findInstruction;
    private final int operand;
    private final int[] position;

    public InstructionOperandAssertion(
        String method, Predicate<SSAInstruction> findInstruction, int operand, int[] position) {
      this.method = method;
      this.findInstruction = findInstruction;
      this.operand = operand;
      this.position = position;
    }

    @Override
    public void check(CallGraph cg) {
      MethodReference mref = descriptorToMethodRef(method, cg.getClassHierarchy());

      assertThat(cg.getNodes(mref))
          .allMatch(cgNode -> cgNode.getMethod() instanceof AstMethod)
          .anySatisfy(
              cgNode -> {
                DebuggingInformation dbg = ((AstMethod) cgNode.getMethod()).debugInfo();
                assertThat(cgNode.getIR().getInstructions())
                    .filteredOn(findInstruction)
                    .extracting(inst -> dbg.getOperandPosition(inst.iIndex(), operand))
                    .filteredOn(Objects::nonNull)
                    .anyMatch(
                        pos ->
                            pos.getFirstLine() == position[0]
                                && pos.getFirstCol() == position[1]
                                && pos.getLastLine() == position[2]
                                && pos.getLastCol() == position[3]);
              });
    }
  }

  protected static class SourceMapAssertion implements IRAssertion {
    private final String method;

    private final String variableName;

    private final int definingLineNumber;

    protected SourceMapAssertion(String method, String variableName, int definingLineNumber) {
      this.method = method;
      this.variableName = variableName;
      this.definingLineNumber = definingLineNumber;
    }

    @Override
    public void check(CallGraph cg) {
      MethodReference mref = descriptorToMethodRef(method, cg.getClassHierarchy());
      assertThat(cg.getNodes(mref)).allMatch(cgNode -> check(cgNode.getMethod(), cgNode.getIR()));
    }

    boolean check(IMethod m, IR ir) {
      System.err.println(("check for " + variableName + " defined at " + definingLineNumber));
      SSAInstruction[] insts = ir.getInstructions();
      for (int i = 0; i < insts.length; i++) {
        if (insts[i] != null) {
          int ln = m.getLineNumber(i);
          if (ln == definingLineNumber) {
            System.err.println(("  found " + insts[i] + " at " + ln));
            for (int j = 0; j < insts[i].getNumberOfDefs(); j++) {
              int def = insts[i].getDef(j);
              System.err.println(("    looking at def " + j + ": " + def));
              String[] names = ir.getLocalNames(i, def);
              if (names != null) {
                for (String name : names) {
                  System.err.println(("      looking at name " + name));
                  if (name.equals(variableName)) {
                    return true;
                  }
                }
              }
            }
          }
        }
      }

      return false;
    }
  }

  protected static class AnnotationAssertions implements IRAssertion {

    public static class ClassAnnotation {
      private final String className;
      private final String annotationTypeName;

      public ClassAnnotation(String className, String annotationTypeName) {
        this.className = className;
        this.annotationTypeName = annotationTypeName;
      }
    }

    public static class MethodAnnotation {
      private final String methodSig;
      private final String annotationTypeName;

      public MethodAnnotation(String methodSig, String annotationTypeName) {
        this.methodSig = methodSig;
        this.annotationTypeName = annotationTypeName;
      }
    }

    public final Set<ClassAnnotation> classAnnotations = HashSetFactory.make();
    public final Set<MethodAnnotation> methodAnnotations = HashSetFactory.make();

    @Override
    public void check(CallGraph cg) {
      classes:
      for (ClassAnnotation ca : classAnnotations) {
        IClass cls =
            cg.getClassHierarchy()
                .lookupClass(
                    TypeReference.findOrCreate(ClassLoaderReference.Application, ca.className));
        IClass at =
            cg.getClassHierarchy()
                .lookupClass(
                    TypeReference.findOrCreate(
                        ClassLoaderReference.Application, ca.annotationTypeName));
        for (Annotation a : cls.getAnnotations()) {
          if (a.getType().equals(at.getReference())) {
            continue classes;
          }
        }

        fail("cannot find %s in %s", at, cls);
      }

      annot:
      for (MethodAnnotation ma : methodAnnotations) {
        IClass at =
            cg.getClassHierarchy()
                .lookupClass(
                    TypeReference.findOrCreate(
                        ClassLoaderReference.Application, ma.annotationTypeName));
        for (CGNode n : cg) {
          if (n.getMethod().getSignature().equals(ma.methodSig)) {
            for (Annotation a : n.getMethod().getAnnotations()) {
              if (a.getType().equals(at.getReference())) {
                continue annot;
              }
            }

            fail("cannot find " + at);
          }
        }
      }
    }
  }

  private Collection<Path> singleTestResource(String resourceName) {
    // Try to find the test file using the current classpath
    final URL resourceUrl = getClass().getClassLoader().getResource(resourceName);
    if (resourceUrl == null) {
      throw new RuntimeException("Resource not found on classpath: " + resourceName);
    }

    // Convert the URL to a file path
    final Path resourceFilePath;
    try {
      resourceFilePath = Path.of(resourceUrl.toURI());
    } catch (final URISyntaxException problem) {
      throw new RuntimeException(
          "Error converting resource URL to file path: " + resourceUrl, problem);
    }

    // Return the found resource's file path to the caller as a one-item collection
    return Collections.singletonList(resourceFilePath);
  }

  protected Collection<Path> singleTestSrc(String testName) {
    return singleTestResource(testName + ".java");
  }

  protected Collection<Path> singlePkgTestSrc(String pkgName, String testName) {
    return singleTestResource(singleJavaPkgInputForTest(pkgName, testName));
  }

  protected String[] simpleTestEntryPoint(String testName) {
    return new String[] {'L' + testName};
  }

  protected String[] simplePkgTestEntryPoint(String pkgName, String testName) {
    return new String[] {"L" + pkgName + "/" + testName};
  }

  protected abstract AbstractAnalysisEngine<InstanceKey, CallGraphBuilder<InstanceKey>, ?>
      getAnalysisEngine(String[] mainClassDescriptors, Collection<Path> sources, List<String> libs);

  public Pair<CallGraph, CallGraphBuilder<? super InstanceKey>> runTest(String testName)
      throws CancelException, IOException {
    return runTest(
        singleTestSrc(testName), rtJar, simpleTestEntryPoint(testName), emptyList, true, null);
  }

  public Pair<CallGraph, CallGraphBuilder<? super InstanceKey>> runTest(
      Collection<Path> sources,
      List<String> libs,
      String[] mainClassDescriptors,
      List<? extends IRAssertion> ca,
      boolean assertReachable,
      String exclusionsFile)
      throws IllegalArgumentException, CancelException, IOException {
    AbstractAnalysisEngine<InstanceKey, CallGraphBuilder<InstanceKey>, ?> engine =
        getAnalysisEngine(mainClassDescriptors, sources, libs);

    if (exclusionsFile != null) {
      engine.setExclusionsFile(exclusionsFile);
    }

    CallGraphBuilder<? super InstanceKey> builder = engine.defaultCallGraphBuilder();
    CallGraph callGraph = builder.makeCallGraph(engine.getOptions(), new NullProgressMonitor());
    // System.err.println(callGraph.toString());

    // If we've gotten this far, IR has been produced.
    if (dump) {
      dumpIR(callGraph, sources, assertReachable);
    }

    // Now check any assertions as to source mapping
    for (IRAssertion IRAssertion : ca) {
      IRAssertion.check(callGraph);
    }

    return Pair.make(callGraph, builder);
  }

  protected static void dumpIR(CallGraph cg, Collection<Path> sources, boolean assertReachable) {
    Set<Path> sourcePaths = HashSetFactory.make();
    for (Path src : sources) {
      sourcePaths.add(src.getFileName());
    }

    Set<IMethod> unreachable = HashSetFactory.make();
    IClassHierarchy cha = cg.getClassHierarchy();
    IClassLoader sourceLoader = cha.getLoader(JavaSourceAnalysisScope.SOURCE);
    for (IClass clazz : Iterator2Iterable.make(sourceLoader.iterateAllClasses())) {

      System.err.println(clazz);
      if (clazz.isInterface()) continue;

      for (IMethod m : clazz.getDeclaredMethods()) {
        if (m.isAbstract()) {
          System.err.println(m);
        } else {
          Iterator<CGNode> nodeIter = cg.getNodes(m.getReference()).iterator();
          if (!nodeIter.hasNext()) {
            if (m instanceof AstMethod) {
              final Path fn;
              try {
                fn =
                    Path.of(
                        ((AstClass) m.getDeclaringClass()).getSourcePosition().getURL().toURI());
              } catch (URISyntaxException problem) {
                throw new RuntimeException(problem);
              }
              if (sourcePaths.contains(fn.getFileName())) {
                System.err.println(("Method " + m.getReference() + " not reachable?"));
                unreachable.add(m);
              }
            }
            continue;
          }
          CGNode node = nodeIter.next();
          System.err.println(node.getIR());
        }
      }
    }

    if (assertReachable) {
      assertThat(unreachable).isEmpty();
    }
  }

  /**
   * @param srcMethodDescriptor a full method descriptor of the form ldr#type#methName#methSig
   *     example: Source#Simple1#main#([Ljava/lang/String;)V
   */
  public static MethodReference descriptorToMethodRef(
      String srcMethodDescriptor, IClassHierarchy cha) {
    String[] ldrTypeMeth = srcMethodDescriptor.split("#");

    String loaderName = ldrTypeMeth[0];
    String typeStr = ldrTypeMeth[1];
    String methName = ldrTypeMeth[2];
    String methSig = ldrTypeMeth[3];

    TypeReference typeRef = findOrCreateTypeReference(loaderName, typeStr, cha);

    Language l = cha.getLoader(typeRef.getClassLoader()).getLanguage();
    return MethodReference.findOrCreate(l, typeRef, methName, methSig);
  }

  static TypeReference findOrCreateTypeReference(
      String loaderName, String typeStr, IClassHierarchy cha) {
    ClassLoaderReference clr = findLoader(loaderName, cha);
    TypeName typeName = TypeName.string2TypeName('L' + typeStr);
    TypeReference typeRef = TypeReference.findOrCreate(clr, typeName);
    return typeRef;
  }

  private static ClassLoaderReference findLoader(String loaderName, IClassHierarchy cha) {
    Atom loaderAtom = Atom.findOrCreateUnicodeAtom(loaderName);
    IClassLoader[] loaders = cha.getLoaders();
    for (IClassLoader loader : loaders) {
      if (loader.getName() == loaderAtom) {
        return loader.getReference();
      }
    }
    fail("This code should be unreachable");
    return null;
  }

  public static void populateScope(
      JavaSourceAnalysisEngine engine, Collection<Path> sources, List<String> libs) {
    assertThat(libs)
        .map(File::new)
        .filteredOn(File::exists)
        .isNotEmpty()
        .allSatisfy(
            libFile -> engine.addSystemModule(new JarFileModule(new JarFile(libFile, false))));

    for (Path srcFilePath : sources) {
      Path srcFileName = srcFilePath.getFileName();
      File f = srcFilePath.toFile();
      assertThat(f).exists();
      if (f.isDirectory()) {
        engine.addSourceModule(new SourceDirectoryTreeModule(f));
      } else {
        engine.addSourceModule(new SourceFileModule(f, srcFileName.toString(), null));
      }
    }
  }

  protected static String singleInputForTest(String testName) {
    return testName;
  }

  protected String singleJavaPkgInputForTest(String pkgName, String testName) {
    return pkgName + File.separator + testName + ".java";
  }
}
