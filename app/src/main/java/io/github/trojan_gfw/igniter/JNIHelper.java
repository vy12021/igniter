package io.github.trojan_gfw.igniter;

public class JNIHelper {
    private static boolean libraryLoaded;
    
    static {
        try {
            System.loadLibrary("jni-helper");
            libraryLoaded = true;
            LogHelper.i("JNIHelper", "Native library loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            LogHelper.e("JNIHelper", "Failed to load native library: " + e.getMessage());
            libraryLoaded = false;
        }
    }

    public static boolean isLibraryLoaded() {
        return libraryLoaded;
    }

    public static void trojan(String config) {
        if (!libraryLoaded) {
            throw new RuntimeException("Native library not loaded");
        }
        trojanNative(config);
    }

    public static void stop() {
        if (!libraryLoaded) {
            LogHelper.w("JNIHelper", "Native library not loaded, cannot stop");
            return;
        }
        stopNative();
    }

    public static String testNativeLibrary() {
        if (!libraryLoaded) {
            return "Native library not loaded";
        }
        try {
            return testNative();
        } catch (Exception e) {
            return "Native test failed: " + e.getMessage();
        }
    }

    private static native String testNative();
    private static native void trojanNative(String config);
    private static native void stopNative();
}
