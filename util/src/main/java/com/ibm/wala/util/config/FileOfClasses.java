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
package com.ibm.wala.util.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** An object which represents a set of classes read from a text file. */
public class FileOfClasses extends SetOfClasses {

  /* Serial version */
  private static final long serialVersionUID = -3256390509887654322L;

  private static final boolean DEBUG = false;

  private @Nullable Pattern pattern = null;

  private @Nullable String regex = null;

  private boolean needsCompile = false;

  public FileOfClasses(InputStream input) throws IOException {
    if (input == null) {
      throw new IllegalArgumentException("null input");
    }
    try (final BufferedReader is =
        new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {

      StringBuilder regex = null;
      String line;
      while ((line = is.readLine()) != null) {

        if (line.startsWith("#")) continue;

        if (regex == null) {
          regex = new StringBuilder('(' + line + ')');
        } else {
          regex.append("|(").append(line).append(')');
        }
      }

      if (regex != null) {
        this.regex = regex.toString();
        needsCompile = true;
      }
    }
  }

  private void compile() {
    pattern = Pattern.compile(regex);
    needsCompile = false;
  }

  @Override
  public boolean contains(String klassName) {
    if (needsCompile) {
      compile();
    }
    if (pattern == null) {
      return false;
    }
    Matcher m = pattern.matcher(klassName);
    if (DEBUG) {
      if (m.matches()) {
        System.err.println(klassName + ' ' + true);
      } else {
        System.err.println(klassName + ' ' + false);
      }
    }
    return m.matches();
  }

  @Override
  public void add(String klass) {
    if (klass == null) {
      throw new IllegalArgumentException("klass is null");
    }
    if (regex == null) {
      regex = klass;
    } else {
      regex = regex + '|' + klass;
    }
    needsCompile = true;
  }

  @Override
  public @Nullable String toString() {
    return this.regex;
  }
}
