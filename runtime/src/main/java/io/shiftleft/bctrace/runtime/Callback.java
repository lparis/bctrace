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
 * of this source code, which includeas information that is confidential and/or proprietary, and
 * is a trade secret, of ShiftLeft, Inc.
 *
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC PERFORMANCE, OR PUBLIC DISPLAY
 * OF OR THROUGH USE OF THIS SOURCE CODE WITHOUT THE EXPRESS WRITTEN CONSENT OF ShiftLeft, Inc.
 * IS STRICTLY PROHIBITED, AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION DOES NOT
 * CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS
 * CONTENTS, OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package io.shiftleft.bctrace.runtime;

import io.shiftleft.bctrace.runtime.listener.Listener;
import io.shiftleft.bctrace.runtime.listener.info.BeforeThrownListener;
import io.shiftleft.bctrace.runtime.listener.info.FinishReturnListener;
import io.shiftleft.bctrace.runtime.listener.info.FinishThrowableListener;
import io.shiftleft.bctrace.runtime.listener.info.StartArgumentsListener;
import io.shiftleft.bctrace.runtime.listener.info.StartListener;
import io.shiftleft.bctrace.runtime.listener.min.MinStartListener;

/**
 *
 * @author Ignacio del Valle Alles idelvall@shiftleft.io
 */
public final class Callback {

  private static final ThreadLocal<Boolean> NOTIFY_DISABLED_FLAG = new ThreadLocal<Boolean>();
  private static final ThreadLocal<Boolean> NOTIFYING_FLAG = new ThreadLocal<Boolean>();

  public static Listener[] listeners;

  @SuppressWarnings("BoxedValueEquality")
  public static void onStart(int methodId, int i) {
    if (!isThreadNotificationEnabled()) {
      return;
    }
    if (Boolean.TRUE == NOTIFYING_FLAG.get()) {
      return;
    }
    if (DebugInfo.isEnabled()) {
      DebugInfo.getInstance().increaseCallCounter(methodId);
    }
    try {
      NOTIFYING_FLAG.set(Boolean.TRUE);
      ((MinStartListener) listeners[i]).onStart(methodId);
    } finally {
      NOTIFYING_FLAG.remove();
    }
  }

  @SuppressWarnings("BoxedValueEquality")
  public static void onStart(int methodId, Object instance, int i) {
    if (!isThreadNotificationEnabled()) {
      return;
    }
    if (Boolean.TRUE == NOTIFYING_FLAG.get()) {
      return;
    }
    if (DebugInfo.isEnabled()) {
      DebugInfo.getInstance().increaseCallCounter(methodId);
    }
    try {
      NOTIFYING_FLAG.set(Boolean.TRUE);
      ((StartListener) listeners[i]).onStart(methodId, instance);
    } finally {
      NOTIFYING_FLAG.remove();
    }
  }

  @SuppressWarnings("BoxedValueEquality")
  public static void onStart(Object[] args, int methodId, Object instance, int i) {
    if (!isThreadNotificationEnabled()) {
      return;
    }
    if (Boolean.TRUE == NOTIFYING_FLAG.get()) {
      return;
    }
    if (DebugInfo.isEnabled()) {
      DebugInfo.getInstance().increaseCallCounter(methodId);
    }
    try {
      NOTIFYING_FLAG.set(Boolean.TRUE);
      ((StartArgumentsListener) listeners[i]).onStart(methodId, instance, args);
    } finally {
      NOTIFYING_FLAG.remove();
    }
  }

  @SuppressWarnings("BoxedValueEquality")
  public static void onFinishedReturn(Object ret, int methodId, Object instance, int i) {
    if (!isThreadNotificationEnabled()) {
      return;
    }
    if (Boolean.TRUE == NOTIFYING_FLAG.get()) {
      return;
    }
    try {
      NOTIFYING_FLAG.set(Boolean.TRUE);
      ((FinishReturnListener) listeners[i]).onFinishedReturn(methodId, instance, ret);
    } finally {
      NOTIFYING_FLAG.remove();
    }
  }

  @SuppressWarnings("BoxedValueEquality")
  public static void onFinishedThrowable(Throwable th, int methodId, Object instance, int i) {
    if (!isThreadNotificationEnabled()) {
      return;
    }
    if (Boolean.TRUE == NOTIFYING_FLAG.get()) {
      return;
    }
    try {
      NOTIFYING_FLAG.set(Boolean.TRUE);
      ((FinishThrowableListener) listeners[i]).onFinishedThrowable(methodId, instance, th);
    } finally {
      NOTIFYING_FLAG.remove();
    }
  }

  @SuppressWarnings("BoxedValueEquality")
  public static void onBeforeThrown(Throwable th, int methodId, Object instance, int i) {
    if (!isThreadNotificationEnabled()) {
      return;
    }
    if (Boolean.TRUE == NOTIFYING_FLAG.get()) {
      return;
    }
    try {
      NOTIFYING_FLAG.set(Boolean.TRUE);
      ((BeforeThrownListener) listeners[i]).onBeforeThrown(methodId, instance, th);
    } finally {
      NOTIFYING_FLAG.remove();
    }
  }
  
  @SuppressWarnings("BoxedValueEquality")
  public static boolean isThreadNotificationEnabled() {
    return NOTIFY_DISABLED_FLAG.get() != Boolean.TRUE;
  }

  @SuppressWarnings("BoxedValueEquality")
  public static void enableThreadNotification() {
    NOTIFY_DISABLED_FLAG.remove();
  }

  @SuppressWarnings("BoxedValueEquality")
  public static void disableThreadNotification() {
    NOTIFY_DISABLED_FLAG.set(Boolean.TRUE);
  }
}