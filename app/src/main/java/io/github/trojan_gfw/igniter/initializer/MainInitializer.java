package io.github.trojan_gfw.igniter.initializer;

import android.content.Context;

import io.github.trojan_gfw.igniter.Globals;
import io.github.trojan_gfw.igniter.common.sp.CommonSP;

/**
 * Initializer that runs in Main Process (Default process).
 */
public class MainInitializer extends Initializer {

    @Override
    public void init(Context context) {
        try {
            io.github.trojan_gfw.igniter.LogHelper.i("MainInitializer", "Initializing main process");
            Globals.Init(context);
            CommonSP.init(context);
            io.github.trojan_gfw.igniter.LogHelper.i("MainInitializer", "Main process initialized successfully");
        } catch (Exception e) {
            io.github.trojan_gfw.igniter.LogHelper.e("MainInitializer", "Failed to initialize main process: " + e.getMessage());
        }
    }

    @Override
    public boolean runsInWorkerThread() {
        return false;
    }
}
