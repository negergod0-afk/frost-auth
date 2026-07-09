package frost.loader.mod;

/**
 * FROST SESSION BRIDGE
 *
 * Populated by FrostLoaderMod after a successful ECDH auth.
 * ZenyaClient reads these via reflection through the parent classloader
 * to perform the second-stage session verification.
 *
 * Lives in the loader classloader (parent) so it is visible from the
 * in-memory-loaded ZenyaClient classloader (child).
 *
 * Fields are volatile to ensure cross-thread visibility between the
 * loader init thread and any thread ZenyaClient runs on.
 */
public final class SessionBridge {
    private SessionBridge() {}

    /** Raw 64-hex-char session token XOR'd with sessionKey — set by loader after auth */
    public static volatile String sessionToken = null;

    /** HWID hex string used during auth — needed for session validation */
    public static volatile String hwidHex = null;

    /** HTTP API base URL (e.g. "http://127.0.0.1:3000") */
    public static volatile String apiBaseUrl = null;

    /** Fragment secret used to verify the session ack HMAC — derived during auth */
    public static volatile byte[] sessionKeyBytes = null;

    /**
     * Called by FrostLoaderMod after successful auth to populate the bridge.
     * sessionTokenRaw: the 64-hex session token recovered from the XOR
     */
    public static void populate(String sessionTokenRaw, String hwid,
                                String apiBase, byte[] sessionKey) {
        sessionToken    = sessionTokenRaw;
        hwidHex         = hwid;
        apiBaseUrl      = apiBase;
        sessionKeyBytes = sessionKey;
    }

    public static boolean isPopulated() {
        return sessionToken != null && hwidHex != null && apiBaseUrl != null;
    }
}
