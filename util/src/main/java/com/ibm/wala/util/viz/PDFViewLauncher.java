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
package com.ibm.wala.util.viz;

import com.ibm.wala.util.WalaException;
import java.io.IOException;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Launch gsview on a postscript file
 *
 * <p>TODO: inherit from a launcher?
 */
public class PDFViewLauncher {

  private @Nullable Process process;

  /** Name of the postscript file to view */
  protected @Nullable String pdffile = null;

  /** Path to ghostview executable */
  protected @Nullable String gvExe = null;

  public @Nullable String getPDFFile() {
    return pdffile;
  }

  public void setPDFFile(String newPsfile) {
    pdffile = newPsfile;
  }

  public @Nullable String getGvExe() {
    return gvExe;
  }

  public void setGvExe(String newGvExe) {
    gvExe = newGvExe;
  }

  @Override
  public String toString() {
    return super.toString() + ", psfile: " + pdffile + ", gvExe: " + gvExe + ')';
  }

  private @Nullable WalaException exception = null;

  /**
   * @see java.lang.Runnable#run()
   */
  public void run() {
    String[] cmdarray = {getGvExe(), getPDFFile()};
    try {
      Process p = Runtime.getRuntime().exec(cmdarray);
      setProcess(p);
    } catch (IOException e) {
      e.printStackTrace();
      exception = new WalaException("gv invocation failed for\n" + Arrays.toString(cmdarray));
    }
  }

  public @Nullable WalaException getException() {
    return exception;
  }

  public @Nullable Process getProcess() {
    return process;
  }

  public void setProcess(Process process) {
    this.process = process;
  }
}
