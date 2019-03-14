/*
 * ShiftLeft, Inc. CONFIDENTIAL
 * Unpublished Copyright (c) 2017 ShiftLeft, Inc., All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains the property of ShiftLeft, Inc.
 * The intellectual and technical concepts contained herein are proprietary to ShiftLeft, Inc.
 * and may be covered by U.S. and Foreign Patents, patents in process, and are protected by
 * trade secret or copyright law. Dissemination of this information or reproduction of this
 * material is strictly forbidden unless prior written permission is obtained
 * from ShiftLeft, Inc. Access to the source code contained herein is hereby forbidden to
 * anyone except current ShiftLeft, Inc. employees, managers or contractors who have executed
 * Confidentiality and Non-disclosure agreements explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication or disclosure
 * of this source code, which includes information that is confidential and/or proprietary, and
 * is a trade secret, of ShiftLeft, Inc.
 *
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC PERFORMANCE, OR PUBLIC DISPLAY
 * OF OR THROUGH USE OF THIS SOURCE CODE WITHOUT THE EXPRESS WRITTEN CONSENT OF ShiftLeft, Inc.
 * IS STRICTLY PROHIBITED, AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION DOES NOT
 * CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS
 * CONTENTS, OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package io.shiftleft.bctrace.asm;

import io.shiftleft.bctrace.Bctrace;
import io.shiftleft.bctrace.InstrumentationImpl;
import io.shiftleft.bctrace.MethodInfo;
import io.shiftleft.bctrace.MethodRegistry;
import io.shiftleft.bctrace.SystemProperty;
import io.shiftleft.bctrace.asm.helper.direct.callsite.CallSiteHelper;
import io.shiftleft.bctrace.asm.helper.direct.method.DirectMethodReturnHelper;
import io.shiftleft.bctrace.asm.helper.direct.method.DirectMethodStartHelper;
import io.shiftleft.bctrace.asm.helper.direct.method.DirectMethodThrowableHelper;
import io.shiftleft.bctrace.asm.helper.generic.method.GenericMethodMutableStartHelper;
import io.shiftleft.bctrace.asm.helper.generic.method.GenericMethodReturnHelper;
import io.shiftleft.bctrace.asm.helper.generic.method.GenericMethodStartHelper;
import io.shiftleft.bctrace.asm.helper.generic.method.GenericMethodThrowableHelper;
import io.shiftleft.bctrace.asm.util.ASMUtils;
import io.shiftleft.bctrace.hierarchy.BctraceClass;
import io.shiftleft.bctrace.hierarchy.UnloadedClass;
import io.shiftleft.bctrace.hook.GenericMethodHook;
import io.shiftleft.bctrace.hook.Hook;
import io.shiftleft.bctrace.jmx.ClassMetrics;
import io.shiftleft.bctrace.jmx.MethodMetrics;
import io.shiftleft.bctrace.logging.Level;
import io.shiftleft.bctrace.runtime.Callback;
import io.shiftleft.bctrace.runtime.CallbackEnabler;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * @author Ignacio del Valle Alles idelvall@shiftleft.io
 */
public class Transformer implements ClassFileTransformer {

  static String TRANSFORMATION_SUPPORT_CLASS_NAME = TransformationSupport.class.getName()
      .replace('.', '/');
  static String CALL_BACK_ENABLED_CLASS_NAME = CallbackEnabler.class.getName()
      .replace('.', '/');
  static String CALL_BACK_CLASS_NAME = Callback.class.getName()
      .replace('.', '/');

  private static final Field CLASS_WRITER_VERSION_FIELD;

  static {
    try {
      CLASS_WRITER_VERSION_FIELD = ClassWriter.class.getDeclaredField("version");
      CLASS_WRITER_VERSION_FIELD.setAccessible(true);
    } catch (NoSuchFieldException ex) {
      throw new AssertionError();
    }
  }

  private static final File DUMP_FOLDER;

  static {
    if (System.getProperty(SystemProperty.DUMP_FOLDER) != null) {
      File file = new File(System.getProperty(SystemProperty.DUMP_FOLDER));
      if (file.exists() && file.isFile()) {
        file = null;
      }
      DUMP_FOLDER = file;
    } else {
      DUMP_FOLDER = null;
    }
    if (DUMP_FOLDER != null) {
      DUMP_FOLDER.mkdirs();
    }
  }


  private final CallbackTransformer cbTransformer;
  private final GenericMethodStartHelper genericMethodStartHelper = new GenericMethodStartHelper();
  private final GenericMethodMutableStartHelper genericMethodMutableStartHelper = new GenericMethodMutableStartHelper();
  private final GenericMethodReturnHelper genericMethodReturnHelper = new GenericMethodReturnHelper();
  private final GenericMethodThrowableHelper genericMethodThrowableHelper = new GenericMethodThrowableHelper();

  private final CallSiteHelper callSiteHelper = new CallSiteHelper();
  private final DirectMethodStartHelper directMethodStartHelper = new DirectMethodStartHelper();
  private final DirectMethodReturnHelper directMethodReturnHelper = new DirectMethodReturnHelper();
  private final DirectMethodThrowableHelper directMethodThrowableHelper = new DirectMethodThrowableHelper();

  private final InstrumentationImpl instrumentation;
  private final Hook[] hooks;
  private final Bctrace bctrace;
  private final AtomicInteger TRANSFORMATION_COUNTER = new AtomicInteger();

  public Transformer(InstrumentationImpl instrumentation, Bctrace bctrace,
      CallbackTransformer cbTransformer) {
    this.instrumentation = instrumentation;
    this.bctrace = bctrace;
    this.hooks = bctrace.getHooks();
    this.cbTransformer = cbTransformer;

    this.genericMethodStartHelper.setBctrace(bctrace);
    this.genericMethodMutableStartHelper.setBctrace(bctrace);
    this.genericMethodReturnHelper.setBctrace(bctrace);
    this.genericMethodThrowableHelper.setBctrace(bctrace);

    this.directMethodStartHelper.setBctrace(bctrace);
    this.directMethodReturnHelper.setBctrace(bctrace);
    this.directMethodThrowableHelper.setBctrace(bctrace);

    this.callSiteHelper.setBctrace(bctrace);

    ClassMetrics.getInstance();

  }

  @Override
  public byte[] transform(final ClassLoader loader,
      final String className,
      final Class<?> classBeingRedefined,
      final ProtectionDomain protectionDomain,
      final byte[] classfileBuffer)
      throws IllegalClassFormatException {

    byte[] ret = null;
    boolean transformed = false;
    int counter = TRANSFORMATION_COUNTER.incrementAndGet();

    try {
      CallbackEnabler.disableThreadNotification();
      if (className == null || className.equals(CALL_BACK_ENABLED_CLASS_NAME)) {
        return null;
      }
      // Wait for CallbackTransformer to finish
      if (this.cbTransformer != null && !this.cbTransformer.isCompleted()) {
        return null;
      }
      ClassMetrics.getInstance().addInstrumentableClass(className, loader);
      instrumentation.removeTransformedClass(className.replace('/', '.'), loader);

      if (classfileBuffer == null) {
        return null;
      }
      if (className.equals(TRANSFORMATION_SUPPORT_CLASS_NAME)) {
        return null;
      }
      if (!TransformationSupport.isTransformable(className, loader)) {
        return null;
      }
      ArrayList<Integer> classMatchingHooks = getMatchingHooksByName(className, protectionDomain,
          loader);
      if (classMatchingHooks == null || classMatchingHooks.isEmpty()) {
        return null;
      }
      ClassReader cr = new ClassReader(classfileBuffer);
      ClassNode cn = new ClassNode();
      cr.accept(cn, 0);

      UnloadedClass unloadedClass = new UnloadedClass(className.replace('/', '.'), loader, cn,
          instrumentation);

      classMatchingHooks = getMatchingHooksByClassInfo(classMatchingHooks, unloadedClass, protectionDomain,
          loader);

      transformed = transformMethods(unloadedClass, classMatchingHooks);
      if (!transformed) {
        return null;
      } else {
        if (classBeingRedefined != null && (cn.version & 0xFFFF) >= Opcodes.V1_7) {
          /**
           * Retransformed bytecode does not contain the original stack maps frames, so computation
           * of max stack size and locals cannot be reliably performed for classes of version 1.7 or
           * higher using ASM {@link ClassWriter.COMPUTE_MAXS}.
           *
           * This branch temporary changes the class version to 1.6 so the class writer performs the
           * computation without using stack frames, and then restores the original class version
           * back.
           */
          Integer originalClassVersion = cn.version;
          cn.version = 50;
          ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
          cn.accept(cw);
          CLASS_WRITER_VERSION_FIELD.set(cw, originalClassVersion);
          ret = cw.toByteArray();
        } else {
          ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
          cn.accept(cw);
          ret = cw.toByteArray();
        }
        return ret;
      }
    } catch (Throwable th) {
      th.printStackTrace(System.err);
      return null;
    } finally {
      try {
        if (className != null) {
          if (DUMP_FOLDER != null) {
            dump(counter, className, classfileBuffer, ret);
          }
          instrumentation.addLoadedClass(className.replace('/', '.'), loader);
          if (transformed) {
            instrumentation.addTransformedClass(className.replace('/', '.'), loader);
          }
        }
        CallbackEnabler.enableThreadNotification();
      } catch (Throwable th) {
        th.printStackTrace(System.err);
      }
    }
  }

  private static void dump(int counter, String className, byte[] original, byte[] transformed) {
    try {
      if (transformed != null) {
        FileOutputStream fos = new FileOutputStream(
            new File(DUMP_FOLDER, counter + "#" + className.replace('/', '.') + "(input).class"));
        fos.write(original);
        fos.close();
        fos = new FileOutputStream(
            new File(DUMP_FOLDER, counter + "#" + className.replace('/', '.') + "(output).class"));
        fos.write(transformed);
        fos.close();
      } else {
        FileOutputStream fos = new FileOutputStream(new File(DUMP_FOLDER, "noop.txt"), true);
        fos.write((counter + "#" + className).getBytes());
        fos.write("\n".getBytes());
        fos.close();
      }
    } catch (Exception ex) {
      Bctrace.getAgentLogger()
          .log(Level.ERROR, "Error dumping to disk instrumenting class " + className, ex);
    }
  }

  private ArrayList<Integer> getMatchingHooksByName(String className,
      ProtectionDomain protectionDomain,
      ClassLoader loader) {
    if (this.hooks == null) {
      return null;
    }
    ArrayList<Integer> ret = new ArrayList<Integer>(hooks.length);
    for (int i = 0; i < hooks.length; i++) {
      if (hooks[i].getFilter() != null &&
          hooks[i].getFilter().acceptClass(className, protectionDomain, loader)) {
        ret.add(i);
      }
    }
    return ret;
  }

  private ArrayList<Integer> getMatchingHooksByClassInfo(ArrayList<Integer> candidateHookIndexes,
      BctraceClass bctraceClass, ProtectionDomain protectionDomain,
      ClassLoader loader) {

    if (candidateHookIndexes == null) {
      return null;
    }
    ArrayList<Integer> ret = new ArrayList<Integer>(hooks.length);
    for (int i = 0; i < candidateHookIndexes.size(); i++) {
      Integer hookIndex = candidateHookIndexes.get(i);
      if (hooks[hookIndex].getFilter().acceptClass(bctraceClass, protectionDomain, loader)) {
        ret.add(hookIndex);
      }
    }
    // Add additional hooks (those who have a null filter and apply only where others are registered)
    if (!ret.isEmpty()) {
      for (int i = 0; i < hooks.length; i++) {
        if (hooks[i].getFilter() == null) {
          ret.add(i);
        }
      }
    }
    return ret;
  }

  private boolean transformMethods(UnloadedClass unloadedClass, ArrayList<Integer> classMatchingHooks) {
    ClassNode cn = unloadedClass.getClassNode();
    List<MethodNode> methods = cn.methods;
    boolean classTransformed = false;
    for (int m = 0; m < methods.size(); m++) {
      MethodNode mn = methods.get(m);
      ArrayList<Integer> hooksToUse = new ArrayList<Integer>(classMatchingHooks.size());
      for (int h = 0; h < classMatchingHooks.size(); h++) {
        Integer i = classMatchingHooks.get(h);
        if (hooks[i] != null && hooks[i].getFilter() != null && hooks[i].getFilter()
            .acceptMethod(cn, mn)) {
          hooksToUse.add(i);
        }
      }
      if (ASMUtils.isAbstract(mn.access) || ASMUtils.isNative(mn.access)) {
        continue;
      }
      boolean methodTransformed = false;
      if (!hooksToUse.isEmpty()) {
        methodTransformed = modifyMethod(cn, mn, hooksToUse);
      }
      if (methodTransformed) {
        modifyMethod(cn, mn, getAdditionalHooks(classMatchingHooks));
        classTransformed = true;
        MethodMetrics.getInstance().reportInstrumented(
            MethodRegistry.getInstance().registerMethodId(MethodInfo.from(cn.name, mn)));
      }
    }
    return classTransformed;
  }

  private ArrayList<Integer> getAdditionalHooks(ArrayList<Integer> classMatchingHooks) {
    ArrayList<Integer> additionalHooks = new ArrayList<Integer>(1);
    // Add additional hooks
    for (int h = 0; h < classMatchingHooks.size(); h++) {
      Integer i = classMatchingHooks.get(h);
      if (hooks[i].getFilter() == null) {
        additionalHooks.add(i);
      }
    }
    return additionalHooks;
  }

  private boolean modifyMethod(ClassNode cn, MethodNode mn, ArrayList<Integer> hooksToUse) {
    boolean transformed = false;
    boolean hasGenericHooks = false;
    for (int h = 0; h < hooksToUse.size(); h++) {
      Integer i = hooksToUse.get(h);
      Hook hook = hooks[i];
      if (hooks[i] instanceof GenericMethodHook) {
        hasGenericHooks = true;
        break;
      }
    }
    if (hasGenericHooks) {
      if (genericMethodMutableStartHelper.addByteCodeInstructions(cn, mn, hooksToUse)) {
        transformed = true;
      }
      if (genericMethodStartHelper.addByteCodeInstructions(cn, mn, hooksToUse)) {
        transformed = true;
      }
      if (genericMethodReturnHelper.addByteCodeInstructions(cn, mn, hooksToUse)) {
        transformed = true;
      }
      if (genericMethodThrowableHelper.addByteCodeInstructions(cn, mn, hooksToUse)) {
        transformed = true;
      }
    }
    if (callSiteHelper.addByteCodeInstructions(cn, mn, hooksToUse)) {
      transformed = true;
    }
    if (directMethodStartHelper.addByteCodeInstructions(cn, mn, hooksToUse)) {
      transformed = true;
    }
    if (directMethodReturnHelper.addByteCodeInstructions(cn, mn, hooksToUse)) {
      transformed = true;
    }
    if (directMethodThrowableHelper.addByteCodeInstructions(cn, mn, hooksToUse)) {
      transformed = true;
    }
    return transformed;
  }
}
