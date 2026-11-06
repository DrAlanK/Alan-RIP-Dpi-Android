package com.alan.ripdpi.core;

public final class AlanNativeSocket {
    private static volatile boolean loaded = false;

    static {
        ensureLoaded();
    }

    private AlanNativeSocket() {
    }

    public static synchronized boolean ensureLoaded() {
        if (loaded) return true;
        try {
            System.loadLibrary("alan-oob");
            loaded = true;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int sendOob(int fd, byte[] chunk, int offset, int len, byte urgentByte) {
        if (!ensureLoaded() || fd < 0) return -1;
        try {
            return nativeSendOob(fd, chunk, offset, len, urgentByte);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static boolean waitReadable(int fd, int timeoutMs) {
        if (!ensureLoaded() || fd < 0) return false;
        try {
            return nativeWaitReadable(fd, timeoutMs);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void tuneTcpSocket(int fd, int bufBytes) {
        if (!ensureLoaded() || fd < 0) return;
        try {
            nativeTuneTcpSocket(fd, bufBytes);
        } catch (Throwable ignored) {
        }
    }

    private static native int nativeSendOob(int fd, byte[] chunk, int offset, int len, byte urgentByte);
    private static native boolean nativeWaitReadable(int fd, int timeoutMs);
    private static native void nativeTuneTcpSocket(int fd, int bufBytes);
}
