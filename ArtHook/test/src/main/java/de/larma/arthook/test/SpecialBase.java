package de.larma.arthook.test;

import android.util.Log;

/**
 * Created by vaioco on 22/11/2016.
 */

public class SpecialBase extends Base {
    public final String name = "Specialbase";
    @Override
    public void id(){
        Log.d(TAG,this.name);
    }
}
