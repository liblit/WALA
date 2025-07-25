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
package com.ibm.wala.util.processes;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

/** Abstract base class for a process launcher */
public abstract class Launcher {

  // use a fairly big buffer size to avoid performance problems with the default
  private static final int BUFFER_SIZE = 32 * 1024;

  protected @Nullable File workingDir = null;

  protected @Nullable Map<String, String> env = null;

  protected byte @Nullable [] stdOut = null;

  protected byte @Nullable [] stdErr = null;

  private byte @Nullable [] input = null;

  /** capture the contents of stdout? */
  private final boolean captureOutput;

  /** capture the contents of stderr? */
  private final boolean captureErr;

  private final Logger logger;

  protected Launcher(Logger logger) {
    this.captureOutput = false;
    this.captureErr = false;
    this.logger = logger;
  }

  protected Launcher(boolean captureOutput, boolean captureErr, Logger logger) {
    this.captureOutput = captureOutput;
    this.captureErr = captureErr;
    this.logger = logger;
  }

  public @Nullable File getWorkingDir() {
    return workingDir;
  }

  public void setWorkingDir(File newWorkingDir) {
    workingDir = newWorkingDir;
  }

  public @Nullable Map<String, String> getEnv() {
    return env;
  }

  public void setEnv(Map<String, String> newEnv) {
    env = newEnv;
  }

  @Override
  public String toString() {
    return super.toString() + " (workingDir: " + workingDir + ", env: " + env + ')';
  }

  /**
   * Spawn a process to execute the given command
   *
   * @return an object representing the process
   */
  protected Process spawnProcess(@Nullable String cmd)
      throws IllegalArgumentException, IOException {
    if (cmd == null) {
      throw new IllegalArgumentException("cmd cannot be null");
    }
    if (logger != null) {
      logger.info("spawning process " + cmd);
    }
    String[] ev = getEnv() == null ? null : buildEnv(getEnv());
    Process p = Runtime.getRuntime().exec(cmd, ev, getWorkingDir());
    return p;
  }

  /**
   * Spawn a process to execute the given command
   *
   * @return an object representing the process
   */
  protected Process spawnProcess(String[] cmd) throws IllegalArgumentException, IOException {
    if (cmd == null) {
      throw new IllegalArgumentException("cmd cannot be null");
    }
    if (logger != null) {
      logger.info("spawning process " + Arrays.toString(cmd));
    }
    String[] ev = getEnv() == null ? null : buildEnv(getEnv());
    Process p = Runtime.getRuntime().exec(cmd, ev, getWorkingDir());
    return p;
  }

  private static String[] buildEnv(Map<String, String> ev) {
    String[] result = new String[ev.size()];
    int i = 0;
    for (Map.Entry<String, String> e : ev.entrySet()) {
      result[i++] = e.getKey() + '=' + e.getValue();
    }
    return result;
  }

  protected Thread drainStdOut(Process p) {
    final BufferedInputStream out = new BufferedInputStream(p.getInputStream(), BUFFER_SIZE);
    Thread result =
        new Drainer(p) {
          @Override
          void drain() throws IOException {
            drainAndPrint(out, System.out);
          }

          @Override
          void blockingDrain() throws IOException {
            blockingDrainAndPrint(out, System.out);
          }
        };
    result.start();
    return result;
  }

  protected Drainer captureStdOut(Process p) {
    final BufferedInputStream out = new BufferedInputStream(p.getInputStream(), BUFFER_SIZE);
    final ByteArrayOutputStream b = new ByteArrayOutputStream(BUFFER_SIZE);
    Drainer result =
        new Drainer(p) {
          @Override
          void drain() throws IOException {
            drainAndCatch(out, b);
          }

          @Override
          void blockingDrain() throws IOException {
            blockingDrainAndCatch(out, b);
          }
        };
    result.setCapture(b);
    result.start();
    return result;
  }

  protected Thread drainStdErr(Process p) {
    final BufferedInputStream err = new BufferedInputStream(p.getErrorStream(), BUFFER_SIZE);
    Thread result =
        new Drainer(p) {
          @Override
          void drain() throws IOException {
            drainAndPrint(err, System.err);
          }

          @Override
          void blockingDrain() throws IOException {
            blockingDrainAndPrint(err, System.err);
          }
        };
    result.start();
    return result;
  }

  protected Drainer captureStdErr(Process p) {
    final BufferedInputStream out = new BufferedInputStream(p.getErrorStream(), BUFFER_SIZE);
    final ByteArrayOutputStream b = new ByteArrayOutputStream(BUFFER_SIZE);
    Drainer result =
        new Drainer(p) {
          @Override
          void drain() throws IOException {
            drainAndCatch(out, b);
          }

          @Override
          void blockingDrain() throws IOException {
            blockingDrainAndCatch(out, b);
          }
        };
    result.setCapture(b);
    result.start();
    return result;
  }

  /** A thread that runs in a loop, performing the drain() action until a process terminates */
  protected abstract class Drainer extends Thread {

    // how many ms to sleep before waking up to check the streams?
    private static final int SLEEP_MS = 5;

    private final Process p;

    private @Nullable ByteArrayOutputStream capture;

    /** Drain data from the stream, but don't block. */
    abstract void drain() throws IOException;

    /** Drain data from the stream until it is finished. Block if necessary. */
    abstract void blockingDrain() throws IOException;

    Drainer(Process p) {
      this.p = p;
    }

    @Override
    public void run() {
      try {
        boolean repeat = true;
        while (repeat) {
          try {
            Thread.sleep(SLEEP_MS);
          } catch (InterruptedException e1) {
            // e1.printStackTrace();
            // just ignore and continue
          }
          drain();
          try {
            p.exitValue();
            // if we get here, the process has terminated
            repeat = false;
            blockingDrain();
            if (logger != null) {
              logger.fine("process terminated with exit code " + p.exitValue());
            }
          } catch (IllegalThreadStateException e) {
            // this means the process has not yet terminated.
            repeat = true;
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    @NullUnmarked
    public ByteArrayOutputStream getCapture() {
      return capture;
    }

    public void setCapture(ByteArrayOutputStream capture) {
      this.capture = capture;
    }
  }

  /** Drain some data from the input stream, and print said data to p. Do not block. */
  private static void drainAndPrint(BufferedInputStream s, PrintStream p) {
    try {
      while (s.available() > 0) {
        byte[] data = new byte[s.available()];
        s.read(data);
        p.print(new String(data, StandardCharsets.UTF_8));
      }
    } catch (IOException e) {
      // assume the stream has been closed (e.g. the process died)
      // so, just exit
    }
  }

  /** Drain all data from the input stream, and print said data to p. Block if necessary. */
  private static void blockingDrainAndPrint(BufferedInputStream s, PrintStream p) {
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    try {
      // gather all the data from the stream.
      int next = s.read();
      while (next != -1) {
        b.write(next);
        next = s.read();
      }
    } catch (IOException e) {
      // assume the stream has been closed (e.g. the process died)
      // so, just print the data and then leave
    }

    // print the data.
    p.print(b);
  }

  /** Drain some data from the input stream, and append said data to b. Do not block. */
  private static void drainAndCatch(BufferedInputStream s, ByteArrayOutputStream b) {
    try {
      while (s.available() > 0) {
        byte[] data = new byte[s.available()];
        int nRead = s.read(data);
        b.write(data, 0, nRead);
      }
    } catch (IOException e) {
      // assume the stream has been closed (e.g. the process died)
      // so, just exit
    }
  }

  /** Drain all data from the input stream, and append said data to p. Block if necessary. */
  private static void blockingDrainAndCatch(BufferedInputStream s, ByteArrayOutputStream b) {
    try {
      int next = s.read();
      while (next != -1) {
        b.write(next);
        next = s.read();
      }
    } catch (IOException e) {
      // assume the stream has been closed (e.g. the process died)
      // so, just exit
    }
  }

  public boolean isCaptureOutput() {
    return captureOutput;
  }

  public boolean isCaptureErr() {
    return captureErr;
  }

  @NullUnmarked
  public @Nullable byte[] getStdOut() {
    return stdOut;
  }

  @NullUnmarked
  public @Nullable byte[] getStderr() {
    return stdErr;
  }

  protected void setStdOut(byte[] newOutput) {
    stdOut = newOutput;
  }

  protected void setStdErr(byte[] newErr) {
    stdErr = newErr;
  }

  @NullUnmarked
  public @Nullable byte[] getInput() {
    return input;
  }

  /** Set input which will be fed to the launched process's stdin */
  public void setInput(byte[] input) {
    this.input = input;
  }
}
