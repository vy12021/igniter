package io.github.trojan_gfw.igniter;

import android.app.Application;
import android.content.Context;

import io.github.trojan_gfw.igniter.initializer.InitializerHelper;

public class IgniterApplication extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        
        // Set up uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                android.util.Log.e("IgniterApplication", "Uncaught exception in thread " + t.getName(), e);
                // Call the default handler to ensure the app still crashes
                Thread.getDefaultUncaughtExceptionHandler().uncaughtException(t, e);
            }
        });
        
        try {
            android.util.Log.i("IgniterApplication", "Starting application initialization");
            InitializerHelper.runInit(this);
            android.util.Log.i("IgniterApplication", "Application initialization completed");
        } catch (Exception e) {
            android.util.Log.e("IgniterApplication", "Failed to initialize application", e);
            throw e;
        }
    }
}
