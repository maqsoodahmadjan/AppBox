package de.larma.arthook.test;

import android.util.Log;

/**
 * Created by vaioco on 22/11/2016.
 */

public class Base {
    public final String TAG = "base";
    public final String name = "base";
    public void id(){
        Log.d(TAG, this.name);
    }
}
