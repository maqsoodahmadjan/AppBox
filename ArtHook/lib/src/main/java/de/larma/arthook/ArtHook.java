/*
 * Copyright 2014-2015 Marvin Wißfeld
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.larma.arthook;

import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import de.larma.arthook.instrs.Arm32;
import de.larma.arthook.instrs.Arm64;
import de.larma.arthook.instrs.InstructionHelper;
import de.larma.arthook.instrs.Thumb2;

import static de.larma.arthook.DebugHelper.logd;
import static de.larma.arthook.DebugHelper.logw;

public final class ArtHook {
    private static final Map<Long, HookPage> pages = new HashMap<>();
    private static InstructionHelper INSTRUCTION_SET_HELPER;
    private static final String TAG = "ArtHook";

    private ArtHook() {
    }

    static {
        try {
            boolean isArm = true; // TODO: logic
            if (isArm) {
                if (Native.is64Bit()) {
                    INSTRUCTION_SET_HELPER = new Arm64();
                } else if ((ArtMethod.of(ArtMethod.class.getDeclaredMethod("of",
                        Method.class)).getEntryPointFromQuickCompiledCode() & 1) == 1) {
                    INSTRUCTION_SET_HELPER = new Thumb2();
                } else {
                    INSTRUCTION_SET_HELPER = new Arm32();
                }
            }
            logd("Using: " + INSTRUCTION_SET_HELPER.getName());
        } catch (Exception ignored) {
        }
    }

    /*
        Create an HookPage with trampoline for original to replacement

     */
    private static HookPage handleHookPage(ArtMethod original, ArtMethod replacement) {
        long originalEntryPoint = INSTRUCTION_SET_HELPER.toMem(
                original.getEntryPointFromQuickCompiledCode());
        if (!pages.containsKey(originalEntryPoint)) {
            pages.put(originalEntryPoint, new HookPage(INSTRUCTION_SET_HELPER, originalEntryPoint,
                    getQuickCompiledCodeSize(original)));
        }

        HookPage page = pages.get(originalEntryPoint);
        page.addHook(new HookPage.Hook(original, replacement));
        page.update();
        return page;
    }
    /*
        External function called to start hooking
        For each method (target) in clazz with a valid Assertion Hook.class it calls hook(method)
     */
    public static void hook(Class clazz) {
        for (Method method : Assertions.argumentNotNull(clazz, "clazz").getDeclaredMethods()) {
            if (method.isAnnotationPresent(Hook.class)) {
                try {
                    hook(method);
                } catch (RuntimeException e) {
                    logw(e);
                }
            }
        }
    }

    /*
        get original method calling findOriginalMethod
        call next hook function
     */
    public static OriginalMethod hook(Method method) {
        if (!method.isAnnotationPresent(Hook.class))
            throw new IllegalArgumentException("method must have @Hook annotation");

        Object original;
        try {
            original = findOriginalMethod(method);
        } catch (Throwable e) {
            throw new RuntimeException("Can't find original method (" + method.getName() + ")", e);
        }
        String ident = null;
        if (method.isAnnotationPresent(BackupIdentifier.class)) {
            ident = method.getAnnotation(BackupIdentifier.class).value();
        }
        return hook(original, method, ident);
    }
    /*
        just casting function
     */
    public static OriginalMethod hook(Method originalMethod, Method replacementMethod, String backupIdentifier) {
        return hook((Object) originalMethod, replacementMethod, backupIdentifier);
    }
    /*
        real hook starting
        first get an Artmethod calling next hook function
        then store original and backup calling OriginalMethod.store()
     */
    public static OriginalMethod hook(Object originalMethod, Method replacementMethod, String backupIdentifier) {
        ArtMethod backArt;
        if (originalMethod instanceof Method) {
            backArt = hook((Method) originalMethod, replacementMethod);
        } else if (originalMethod instanceof Constructor) {
            backArt = hook((Constructor<?>) originalMethod, replacementMethod);
            backArt.convertToMethod();
        } else {
            throw new RuntimeException("original method must be of type Method or Constructor");
        }

        Method backupMethod = (Method) backArt.getAssociatedMethod();
        backupMethod.setAccessible(true);
        OriginalMethod.store(originalMethod, backupMethod, backupIdentifier);

        return new OriginalMethod(backupMethod);
    }
    /*
        just checks and casting to ArtMethod function
     */
    public static ArtMethod hook(Method originalMethod, Method replacementMethod) {
        Assertions.argumentNotNull(originalMethod, "originalMethod");
        Assertions.argumentNotNull(replacementMethod, "replacementMethod");
        if (originalMethod == replacementMethod || originalMethod.equals(replacementMethod))
            throw new IllegalArgumentException("originalMethod and replacementMethod can't be the same");
        if (!replacementMethod.getReturnType().isAssignableFrom(originalMethod.getReturnType()))
            throw new IllegalArgumentException("return types of originalMethod and replacementMethod do not match");

        return hook(ArtMethod.of(originalMethod), ArtMethod.of(replacementMethod));
    }

    public static ArtMethod hook(Constructor<?> originalMethod, Method replacementMethod) {
        Assertions.argumentNotNull(originalMethod, "originalMethod");
        Assertions.argumentNotNull(replacementMethod, "replacementMethod");
        if (replacementMethod.getReturnType() != Void.TYPE)
            throw new IllegalArgumentException("return types of replacementMethod has to be 'void'");

        return hook(ArtMethod.of(originalMethod), ArtMethod.of(replacementMethod));
    }
    /*
        finally real hooking function
        first create an HookPage, then clone the original and make the resulting copy private.
        finally write the address of page (where the trampoline is stored) into
        original's entrypointfrometc... field
     */
    private static ArtMethod hook(ArtMethod original, ArtMethod replacement) {
        HookPage page = handleHookPage(original, replacement);
        ArtMethod backArt = original.clone();
        backArt.makePrivate();
        if (getQuickCompiledCodeSize(original) < INSTRUCTION_SET_HELPER.sizeOfDirectJump()) {
            original.setEntryPointFromQuickCompiledCode(page.getCallHook());
        } else {
            boolean result = page.activate();
            if (!result) {
                return null;
            }
        }
        return backArt;
    }

    private static int getQuickCompiledCodeSize(ArtMethod method) {
        long entryPoint = INSTRUCTION_SET_HELPER.toMem(method.getEntryPointFromQuickCompiledCode());
        long sizeInfo1 = entryPoint - 4;
        byte[] bytes = Memory.get(sizeInfo1, 4);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    static Object findOriginalMethod(Method method) throws NoSuchMethodException, ClassNotFoundException {
        Hook hook = method.getAnnotation(Hook.class);
        String[] split = hook.value().split("->");
        return findOriginalMethod(method, Class.forName(split[0]), split.length == 1 ? method.getName() : split[1]);
    }

    private static Object findOriginalMethod(Method method, Class<?> targetClass, String methodName)
            throws NoSuchMethodException {
        logd("looking for method: " + methodName + " in class: " + targetClass.getName());
        logd("method is static: " + Modifier.isStatic(method.getModifiers()));
        logd("method is native: " + Modifier.isNative(method.getModifiers()));
        Class<?>[] params = null;
        if (method.getParameterTypes().length > 0) {
            for(Class<?> x : method.getParameterTypes())
                logd("param type: " + x.getName());
            params = new Class<?>[method.getParameterTypes().length - 1];
            System.arraycopy(method.getParameterTypes(), 1, params, 0, method.getParameterTypes().length - 1);

        }
        if (methodName.equals("()") || methodName.equals("<init>")) {
            // Constructor
            return targetClass.getConstructor(params);
        }
        try {
            logd("getting1 declaredmethod: " + methodName);
            Method m = targetClass.getDeclaredMethod(methodName, method.getParameterTypes());
            logd("got method: " + m);
            if (Modifier.isStatic(m.getModifiers())) return m;
        } catch (NoSuchMethodException ignored) {
        }
        try {
            logd("getting2 declaredmethod: " + methodName);
            Method m = targetClass.getDeclaredMethod(methodName, params);
            logd("got method: " + m);
            if (!Modifier.isStatic(m.getModifiers())) return m;
        } catch (NoSuchMethodException ignored) {
        }
        throw new NoSuchMethodException();
    }
}
