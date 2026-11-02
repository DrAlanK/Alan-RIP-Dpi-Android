package hev.htproxy;

public final class TProxyService {
    private static volatile boolean loaded = false;
    private static volatile String lastLoadError = "";

    private TProxyService() {
    }

    public static synchronized String ensureLoaded() {
        if (loaded) {
            return null;
        }
        try {
            System.loadLibrary("hev-socks5-tunnel");
            loaded = true;
            lastLoadError = "";
            return null;
        } catch (Throwable t) {
            Throwable root = t.getCause() != null ? t.getCause() : t;
            lastLoadError = root.getClass().getSimpleName() + ": " +
                (root.getMessage() != null ? root.getMessage() : "native load failed");
            return lastLoadError;
        }
    }

    public static native void TProxyStartService(String configPath, int fd);

    public static native void TProxyStopService();

    public static native long[] TProxyGetStats();
}
