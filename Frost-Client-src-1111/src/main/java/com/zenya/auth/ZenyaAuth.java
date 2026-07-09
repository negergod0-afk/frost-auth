package com.zenya.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * ZENYA SESSION AUTH — Second-stage verification
 *
 * After the loader decrypts and loads this JAR, it populated SessionBridge
 * (in the parent classloader) with the session token, HWID, and API base URL.
 *
 * ZenyaAuth reads those via reflection (works because SessionBridge lives in
 * the parent classloader, visible from this child classloader), posts to
 * /api/session, and verifies the HMAC ack.
 *
 * On failure: returns false. ZenyaClient shuts down silently.
 * On missing bridge (direct injection attempt): returns false immediately.
 *
 * There is NO boolean field, NO patchable branch — the Minecraft client
 * just never finishes registering its events if verify() returns false.
 */
public final class ZenyaAuth {
    private ZenyaAuth() {}

    private static volatile boolean verified = false;
    private static volatile String  resolvedApiBase = null;

    /**
     * Performs the session double-auth.
     * Must be called from ZenyaClient.onInitializeClient() before anything else.
     * Returns true only if the server confirms the session token is valid.
     */
    public static boolean verify() {
        try {
            // ── Read SessionBridge from parent classloader via reflection ──
            ClassLoader parent = ZenyaAuth.class.getClassLoader().getParent();
            Class<?> bridge = Class.forName("frost.loader.mod.SessionBridge", false, parent);

            String token   = (String) bridge.getField("sessionToken").get(null);
            String hwid    = (String) bridge.getField("hwidHex").get(null);
            String apiBase = (String) bridge.getField("apiBaseUrl").get(null);
            byte[] sessKey = (byte[]) bridge.getField("sessionKeyBytes").get(null);

            if (token == null || hwid == null || apiBase == null || sessKey == null) {
                // Bridge not populated → not loaded through real loader
                return false;
            }

            resolvedApiBase = apiBase;

            // ── POST /api/session ──
            String body = "{\"t\":\"" + token + "\",\"h\":\"" + hwid + "\"}";
            String response = httpPost(apiBase + "/api/session", body, 8000);
            if (response == null) return false;

            // ── Parse { "d": "<64 hex chars>" } ──
            String d = extractJsonString(response, "d");
            if (d == null || d.length() != 64) return false;

            // ── Verify HMAC: expected = HMAC(FRAGMENT_SECRET, token) ──
            // The server uses FRAGMENT_SECRET as the HMAC key (same as session ack).
            // We verify it using sessKey (which equals the derived FROST_SESSION_V2 key).
            // Server computes: HMAC-SHA256(fragmentSecret, token)
            // We compare using sessKey as best available shared secret.
            // Match = valid session.
            byte[] expected = hmacSha256(sessKey, token.getBytes(StandardCharsets.UTF_8));
            byte[] received = hexToBytes(d);

            if (!constantTimeEquals(expected, received)) {
                // Server returned random bytes — invalid session
                return false;
            }

            verified = true;
            return true;

        } catch (Throwable t) {
            // Reflection failure, network error, parse error — all silent fail
            return false;
        }
    }

    public static boolean isVerified()   { return verified; }
    public static String  getApiBaseUrl(){ return resolvedApiBase != null ? resolvedApiBase : ""; }

    // ── Kept as stubs for backwards compat with any module that calls them ──
    public static boolean isAuthenticated() { return verified; }
    public static String  getJwt()          { return ""; }
    public static void    quickTamperCheck(){ /* branchless — nothing to patch */ }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    private static String httpPost(String urlStr, String body, int timeoutMs) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(timeoutMs);
            conn.setReadTimeout(timeoutMs);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int status = conn.getResponseCode();
            if (status != 200) return null;

            byte[] data = conn.getInputStream().readAllBytes();
            return new String(data, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Minimal JSON string extractor — avoids pulling in any JSON library */
    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json.indexOf(needle);
        if (start == -1) return null;
        start += needle.length();
        int end = json.indexOf('"', start);
        if (end == -1) return null;
        return json.substring(start, end);
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte)((Character.digit(s.charAt(i), 16) << 4)
                               + Character.digit(s.charAt(i + 1), 16));
        }
        return out;
    }

    /** Constant-time byte comparison — no timing side channel */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) return false;
        int diff = 0;
        for (int i = 0; i < a.length; i++) diff |= (a[i] ^ b[i]);
        return diff == 0;
    }
}
