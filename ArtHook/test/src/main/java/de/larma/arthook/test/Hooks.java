package de.larma.arthook.test;

import android.app.Activity;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.sip.SipAudioCall;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Date;

import de.larma.arthook.$;
import de.larma.arthook.BackupIdentifier;
import de.larma.arthook.Hook;
import de.larma.arthook.OriginalMethod;

/**
 * Created by vaioco on 23/11/2016.
 */

public class Hooks {
    private static final String TAG = "Hooks";

    //public native Object invoke(Object var1, Object... var2) throws IllegalAccessException,
    // IllegalArgumentException, InvocationTargetException;

    @Hook("java.lang.reflect.Method->invoke")
    @BackupIdentifier("Method_invoke")
    public static Object Method_invoke(Object var1, Object... var2)
    {
        Log.d(TAG, "fixed Method invoke()");
        return OriginalMethod.by("Method_invoke").invokeStatic(var1,var2);
        //        ("Method_invoke").invoke(thiz,var1,var2);

    }
    /*
    @Hook("de.larma.arthook.test.SpecialBase->id")
    public static void fix_SpecialBase(Object o) {
        Log.d(TAG, "fixed SpecialBase id()");
        OriginalMethod.by(new $() {}).invoke(o);
    }*/
    @Hook("de.larma.arthook.test.Base->id")
    public static void fix_Base(Object o) {
        Log.d(TAG, "fixed Base id()");
        OriginalMethod.by(new $() {}).invoke(o);
    }
    @Hook("android.telephony.TelephonyManager->getDeviceId")
    public static String fix_getDeviceId(TelephonyManager tm){
        Log.d(TAG, "fixed getdeviceid");
        return "w00t" + OriginalMethod.by(new $() {}).invoke(tm);
    }

    @Hook("android.net.sip.SipAudioCall->startAudio")
    public static void SipAudioCall_startAudio(SipAudioCall call) {
        Log.d(TAG, "SipAudioCall_startAudio");
        OriginalMethod.by(new $() {}).invoke(call);
    }
    @Hook("android.app.Activity-><init>")
    public static void Activity_init(Activity a) {
        Log.d(TAG, "Activity_init");
        OriginalMethod.by(new $() {}).invoke(a);
    }
    /**
     * Sample hook of a static method
     */
    @Hook("android.hardware.Camera->open")
    public static Camera Camera_open() {
        try {
            return OriginalMethod.by(new $() {}).invokeStatic();
        } catch (Exception e) {
            throw new SecurityException("We do not allow Camera access", e);
        }
    }
    /**
     * Sample hook of a static native method
     */
    @Hook("java.lang.System->currentTimeMillis")
    public static long System_currentTimeMillis() {
        Log.d(TAG, "currentTimeMillis is much better in seconds :)");
        return (long) OriginalMethod.by(new $() {}).invokeStatic() / 1000L;
    }
    /**
     * Hooking an empty method
     */
    @Hook("android.net.ConnectivityManager->setNetworkPreference")
    public static void ConnectivityManager_setNetworkPreference(ConnectivityManager manager, int preference) {
        Log.d(TAG, "Making something from nothing!");
        OriginalMethod.by(new $() {}).invoke(manager, preference);
    }

    /**
     * Sample hook of a member method used internally by the system
     * <p/>
     * Note how we use the BackupIdentifier here, because using reflection APIs to access
     * reflection APIs will cause loops...
     */
    @Hook("java.lang.Class->getDeclaredMethod")
    @BackupIdentifier("Class_getDeclaredMethod")
    public static Method Class_getDeclaredMethod(Class cls, String name, Class[] params) {
        //Log.d(TAG, "I'm hooked in getDeclaredMethod: " + cls + " -> " + name);
        if (name.contains("War") || name.contains("war")) {
            //Log.d(TAG, "make piece not war!"); // This is a political statement!
            name = name.replace("War", "Piece").replace("war", "piece");
        }
        return OriginalMethod.by("Class_getDeclaredMethod").invoke(cls, name, params);
    }
}
