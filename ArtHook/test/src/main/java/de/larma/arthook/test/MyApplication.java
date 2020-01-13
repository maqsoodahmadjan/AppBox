package de.larma.arthook.test;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.hardware.Camera;
import android.net.ConnectivityManager;
import android.net.sip.SipAudioCall;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.Date;

import de.larma.arthook.$;
import de.larma.arthook.ArtHook;
import de.larma.arthook.BackupIdentifier;
import de.larma.arthook.Hook;
import de.larma.arthook.OriginalMethod;

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";
    public static boolean madePiece = false;

    @Override
    public void onCreate() {
        super.onCreate();

        ArtHook.hook(MyApplication.class);
        ArtHook.hook(Hooks.class);

        Log.d("MyApplication", "Dying soon...");
        try {
            Log.d("MyApplication", "..." + MyApplication.class.getDeclaredMethod("warGame"));
            Log.d("MyApplication", "..." + MyApplication.class.getDeclaredMethod("endGame"));
        } catch (NoSuchMethodException e) {
            Log.w(TAG, e);
        }
        try {
            Camera.open();
        } catch (Exception e) {
            Log.w(TAG, e);
        }

        try {
            Log.d("MyApplication", "Time:" + System.currentTimeMillis());
            Log.d("MyApplication", "BackupTime:" + OriginalMethod.byOriginal(System.class
                    .getDeclaredMethod("currentTimeMillis")).invokeStatic());
            pieceGame();
        } catch (Exception e) {
            Log.w(TAG, e);
        }

        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.setNetworkPreference(0);
        } catch (Exception e) {
            Log.w(TAG, e);
        }

    }

    public void pieceGame() {
        Log.d(TAG, "broken pieceGame()");
    }

    @Hook("de.larma.arthook.test.MyApplication->pieceGame")
    public static void fix_pieceGame(MyApplication app) {
        Log.d(TAG, "fixed pieceGame()");
        madePiece = true;
        OriginalMethod.by(new $() {}).invoke(app);
    }

    /**
     * Sample hook of a public member method
     */
    @Hook("android.app.Activity->setContentView")
    public static void Activity_setContentView(Activity activity, int layoutResID) {
        Log.d(TAG, "before Original[Activity.setContentView]");
        OriginalMethod.by(new $() {}).invoke(activity, layoutResID);
        Log.d(TAG, "after Original[Activity.setContentView]");
        TextView text = ((TextView) activity.findViewById(R.id.helloWorldText));
        text.append("\n -- I am god and made " + (madePiece ? "piece" : "war"));
        text.append("\n " + new Date().toString());
        Log.d(TAG, "end Hook[Activity.setContentView]");
    }
}
