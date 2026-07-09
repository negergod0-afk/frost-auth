package frost.loader.mod;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.security.*;
import java.security.spec.*;
import java.util.Arrays;

/**
 * FROST AUTH CLIENT — 5-Stage Server Authentication
 *
 * Stage 1: CDN Integrity Check       → Fragment #1 (16 bytes)
 * Stage 2: Telemetry Sync            → Fragment #2 (16 bytes)
 * Stage 3: Configuration Fetch       → Fragment #3 (16 bytes)
 * Stage 4: Patch Verification        → Fragment #4 (16 bytes)
 * Stage 5: ECDH Key Exchange + Auth  → Double-encrypted mod payload
 *
 * ALL 5 stages required. Skip any → wrong composite key → garbage mod.
 * Each stage also performs a local security check that feeds into the key.
 *
 * Double-layer decryption:
 *   Outer: AES-256-GCM  (ECDH key)     → transport auth
 *   Inner: AES-256-CTR  (composite key) → NO auth tag = always "succeeds"
 *   Wrong composite key → CTR produces garbage → ClassLoader fails naturally
 */
public class ModAuthClient {

    // Fragment command bytes (must match server)
    private static final byte CMD_CDN       = 0x01;
    private static final byte CMD_TELEMETRY = 0x02;
    private static final byte CMD_CONFIG    = 0x03;
    private static final byte CMD_PATCH     = 0x04;
    private static final byte CMD_AUTH      = (byte) 0xF0;

    /**
     * Performs full 5-stage authentication and returns decrypted mod bytes.
     * Returns either a valid JAR or garbage — there is NO boolean result.
     * Also populates SessionBridge for the client's second-stage verification.
     */
    public static byte[] authenticate(
            String license, byte[] hwid, byte[] envProfile,
            String host, int port, int apiPort
    ) throws Exception {

        // Compute HWID hash (used for fragment requests)
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] hwidHexBytes = bytesToHex(hwid).getBytes();
        byte[] hwidHash = sha.digest(hwidHexBytes);

        // ════════════════════════════════════════════════
        // STAGE 1: CDN Integrity Check
        // Connects as if checking CDN health
        // Also verifies: ClassLoader chain integrity
        // ════════════════════════════════════════════════
        byte[] f1 = performCdnIntegrity(host, apiPort, hwidHash);

        // ════════════════════════════════════════════════
        // STAGE 2: Telemetry Sync
        // Connects as if submitting usage telemetry
        // Also verifies: No Java agents attached
        // ════════════════════════════════════════════════
        byte[] f2 = performTelemetrySync(host, apiPort, hwidHash);

        // ════════════════════════════════════════════════
        // STAGE 3: Configuration Fetch
        // Connects as if fetching remote config
        // Also verifies: Stack trace depth is normal
        // ════════════════════════════════════════════════
        byte[] f3 = performConfigFetch(host, apiPort, hwidHash);

        // ════════════════════════════════════════════════
        // STAGE 4: Patch Verification
        // Connects as if checking for updates
        // Also verifies: Thread count is reasonable
        // ════════════════════════════════════════════════
        byte[] f4 = performPatchCheck(host, apiPort, hwidHash);

        // ════════════════════════════════════════════════
        // STAGE 5: ECDH Primary Authentication
        // Full challenge-response with key exchange
        // Returns double-encrypted mod payload
        // ════════════════════════════════════════════════
        EcdhResult ecdhResult = performEcdhAuth(host, port, license, bytesToHex(hwid));

        // ════════════════════════════════════════════════
        // COMPOSITE KEY DERIVATION
        // compositeKey = HMAC-SHA256(sharedSecret, f1||f2||f3||f4)
        // Then XOR with envProfile (zero if clean, corrupted if tampered)
        // ════════════════════════════════════════════════
        byte[] compositeKey = deriveCompositeKey(ecdhResult.sharedSecret, f1, f2, f3, f4);

        // Apply environment profile (anti-tamper)
        // Clean system: envProfile = all zeros → XOR = no change
        // Tampered:     envProfile ≠ 0 → key corrupted → garbage output
        for (int i = 0; i < envProfile.length && i < compositeKey.length; i++) {
            compositeKey[i] ^= envProfile[i];
        }

        // ════════════════════════════════════════════════
        // DOUBLE DECRYPTION
        // Outer: AES-256-GCM (payloadKey) — transport auth
        // Inner: AES-256-CTR (compositeKey) — always "succeeds"
        // ════════════════════════════════════════════════

        // Outer GCM decrypt → gets inner(iv + ciphertext)
        byte[] payloadKey = deriveKey(ecdhResult.sharedSecret, ecdhResult.nonce, "FROST_PAYLOAD_V2");
        byte[] innerData = aesGcmDecrypt(payloadKey, ecdhResult.outerIv,
                ecdhResult.outerCiphertext, ecdhResult.outerTag);

        // Inner CTR decrypt → mod payload (or garbage, silently)
        byte[] innerIv = Arrays.copyOfRange(innerData, 0, 16);
        byte[] innerCt = Arrays.copyOfRange(innerData, 16, innerData.length);
        byte[] modPayload = aesCtrDecrypt(compositeKey, innerIv, innerCt);

        // ════════════════════════════════════════════════
        // SESSION BRIDGE — populate for ZenyaClient's second-stage auth
        // Recover real session token by un-XOR'ing with sessionKey
        // ════════════════════════════════════════════════
        try {
            byte[] sessionKey = deriveKey(ecdhResult.sharedSecret, ecdhResult.nonce, "FROST_SESSION_V2");
            byte[] rawToken = new byte[32];
            for (int i = 0; i < 32; i++) {
                rawToken[i] = (byte)((ecdhResult.sessionToken[i] & 0xFF) ^ (sessionKey[i] & 0xFF));
            }
            // Convert raw token bytes to hex string
            String tokenHex = bytesToHex(rawToken);
            String apiBase  = "http://" + host + ":" + apiPort;
            SessionBridge.populate(tokenHex, bytesToHex(hwid), apiBase, sessionKey);
        } catch (Throwable ignored) {
            // Bridge population failure is non-fatal for the decryption path.
            // ZenyaAuth will fail silently if bridge is unpopulated.
        }

        return modPayload;
    }

    // ═══════════════════════════════════════════════════════════════
    // FRAGMENT COLLECTION — Each looks like a different service
    // ═══════════════════════════════════════════════════════════════

    /** Stage 1: CDN integrity + ClassLoader chain verification */
    private static byte[] performCdnIntegrity(String host, int port, byte[] hwidHash) throws Exception {
        String path = "/cdn-cgi/trace?h=" + bytesToHex(hwidHash);
        byte[] fragment = collectFragmentHttp(host, port, "GET", path, "cdn-edge.frostclient.net", null, "\"cf-ray\":\"", "-IAD\"", true, false);

        // LOCAL CHECK: ClassLoader parent chain depth
        // Normal: 2-3 levels. Instrumented: 5+ levels
        int depth = 0;
        ClassLoader cl = ModAuthClient.class.getClassLoader();
        while (cl != null) { cl = cl.getParent(); depth++; }
        // depth > 4 suggests injection framework
        int anomaly = Math.max(0, depth - 4) & 0xFF;
        fragment[0] ^= (byte) anomaly;

        return fragment;
    }

    /** Stage 2: Telemetry sync + Java agent detection */
    private static byte[] performTelemetrySync(String host, int port, byte[] hwidHash) throws Exception {
        String body = "{\"deviceId\":\"" + bytesToHex(hwidHash) + "\"}";
        byte[] fragment = collectFragmentHttp(host, port, "POST", "/v1/metrics", "telemetry.frostclient.net", body, "\"traceId\":\"trace_", "\"", true, false);

        // LOCAL CHECK: Loaded agent count (Instrumentation API)
        // If agents are attached, certain system properties exist
        String agentProp = System.getProperty("jdk.attach.allowAttachSelf", "");
        int agentScore = agentProp.length() > 0 ? 0xFF : 0x00;
        fragment[4] ^= (byte) agentScore;

        return fragment;
    }

    /** Stage 3: Config fetch + Stack trace depth verification */
    private static byte[] performConfigFetch(String host, int port, byte[] hwidHash) throws Exception {
        String path = "/api/v2/config?v=" + bytesToHex(hwidHash);
        byte[] fragment = collectFragmentHttp(host, port, "GET", path, "config.frostclient.net", null, "\"signature\":\"", "\"", false, true);

        // LOCAL CHECK: Stack trace should be shallow
        // Normal: ~5-10 frames. Hooked/proxied: 15+ frames
        int stackDepth = Thread.currentThread().getStackTrace().length;
        int stackAnomaly = Math.max(0, stackDepth - 15) & 0xFF;
        fragment[8] ^= (byte) stackAnomaly;

        return fragment;
    }

    /** Stage 4: Patch check + Thread count verification */
    private static byte[] performPatchCheck(String host, int port, byte[] hwidHash) throws Exception {
        String body = "{\"clientId\":\"" + bytesToHex(hwidHash) + "\"}";
        byte[] fragment = collectFragmentHttp(host, port, "POST", "/update/v1/check", "update.frostclient.net", body, "\"checksum\":\"sha256-", "\"", true, false);

        // LOCAL CHECK: Thread count should be reasonable
        // Normal Java app: 5-20 threads. Heavily instrumented: 30+
        int threadCount = Thread.activeCount();
        int threadAnomaly = Math.max(0, threadCount - 30) & 0xFF;
        fragment[12] ^= (byte) threadAnomaly;

        return fragment;
    }

    /** Raw HTTP over socket for stealth (avoids HttpURLConnection constants) */
    private static byte[] collectFragmentHttp(String host, int port, String method, String path, String hostHeader, String bodyData, String extractPrefix, String extractSuffix, boolean hexDecode, boolean base64Decode) throws Exception {
        Socket sock = new Socket(host, port);
        sock.setSoTimeout(10000);
        OutputStream out = sock.getOutputStream();
        InputStream in = sock.getInputStream();

        StringBuilder req = new StringBuilder();
        req.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
        req.append("Host: ").append(hostHeader).append("\r\n");
        req.append("Connection: close\r\n");
        if (bodyData != null) {
            req.append("Content-Type: application/json\r\n");
            req.append("Content-Length: ").append(bodyData.length()).append("\r\n\r\n");
            req.append(bodyData);
        } else {
            req.append("\r\n");
        }
        
        out.write(req.toString().getBytes("UTF-8"));
        out.flush();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int r;
        while ((r = in.read(buf)) != -1) baos.write(buf, 0, r);
        sock.close();
        
        String resp = new String(baos.toByteArray(), "UTF-8");
        int start = resp.indexOf(extractPrefix);
        if (start == -1) throw new Exception("ERR1");
        start += extractPrefix.length();
        int end = resp.indexOf(extractSuffix, start);
        if (end == -1) throw new Exception("ERR2");
        
        String extracted = resp.substring(start, end);
        if (hexDecode) return hexToBytes(extracted);
        if (base64Decode) return java.util.Base64.getDecoder().decode(extracted);
        return extracted.getBytes("UTF-8");
    }

    private static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    // ═══════════════════════════════════════════════════════════════
    // ECDH AUTHENTICATION
    // ═══════════════════════════════════════════════════════════════

    private static EcdhResult performEcdhAuth(String host, int port, String license, String hwidHex) throws Exception {
        Socket sock = new Socket(host, port);
        sock.setSoTimeout(15000);
        OutputStream out = sock.getOutputStream();
        InputStream in = sock.getInputStream();

        // Send protocol version byte to trigger ECDH handler
        out.write(CMD_AUTH);
        out.flush();

        // ---- Receive Challenge: [1:ver][65:serverPub][32:nonce][8:ts] = 106 bytes ----
        byte[] challenge = readExact(in, 106);
        byte[] serverPubRaw = Arrays.copyOfRange(challenge, 1, 66);
        byte[] nonce = Arrays.copyOfRange(challenge, 66, 98);

        // ---- Generate Client ECDH Keypair ----
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        KeyPair clientKp = kpg.generateKeyPair();

        // Extract raw uncompressed public key (65 bytes: 04||x||y)
        byte[] clientPubRaw = encodeUncompressedPoint(clientKp);

        // ---- Reconstruct Server Public Key ----
        PublicKey serverPub = decodeUncompressedPoint(serverPubRaw, clientKp);

        // ---- Compute Shared Secret ----
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(clientKp.getPrivate());
        ka.doPhase(serverPub, true);
        byte[] sharedSecret = ka.generateSecret();

        // ---- Derive Auth Key ----
        byte[] authKey = deriveKey(sharedSecret, nonce, "FROST_AUTH_V2");

        // ---- Build Auth Payload: nonce(32) + license + \x00 + hwid ----
        byte[] licenseBytes = license.getBytes("UTF-8");
        byte[] hwidBytes = hwidHex.getBytes("UTF-8");
        byte[] plaintext = new byte[32 + licenseBytes.length + 1 + hwidBytes.length];
        System.arraycopy(nonce, 0, plaintext, 0, 32);
        System.arraycopy(licenseBytes, 0, plaintext, 32, licenseBytes.length);
        plaintext[32 + licenseBytes.length] = 0x00;
        System.arraycopy(hwidBytes, 0, plaintext, 33 + licenseBytes.length, hwidBytes.length);

        // ---- AES-GCM Encrypt Auth Payload ----
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher gcm = Cipher.getInstance("AES/GCM/NoPadding");
        gcm.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(authKey, "AES"), new GCMParameterSpec(128, iv));
        byte[] encrypted = gcm.doFinal(plaintext);
        // Java GCM output = ciphertext || tag (last 16 bytes)
        byte[] ciphertext = Arrays.copyOfRange(encrypted, 0, encrypted.length - 16);
        byte[] tag = Arrays.copyOfRange(encrypted, encrypted.length - 16, encrypted.length);

        // ---- Send Auth: [65:clientPub][12:iv][2:ctLen][N:ct][16:tag] ----
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(clientPubRaw);
        baos.write(iv);
        baos.write((ciphertext.length >> 8) & 0xFF);
        baos.write(ciphertext.length & 0xFF);
        baos.write(ciphertext);
        baos.write(tag);
        out.write(baos.toByteArray());
        out.flush();

        // ---- Receive Response: [4:totalSize][...payload...] ----
        byte[] sizeBytes = readExact(in, 4);
        int totalSize = ((sizeBytes[0] & 0xFF) << 24) | ((sizeBytes[1] & 0xFF) << 16) |
                        ((sizeBytes[2] & 0xFF) << 8)  | (sizeBytes[3] & 0xFF);

        byte[] response = readExact(in, totalSize);
        sock.close();

        // Parse: [12:outerIv][N:outerCt][16:outerTag][32:sessionToken]
        byte[] outerIv = Arrays.copyOfRange(response, 0, 12);
        byte[] sessionToken = Arrays.copyOfRange(response, totalSize - 32, totalSize);
        byte[] outerTag = Arrays.copyOfRange(response, totalSize - 48, totalSize - 32);
        byte[] outerCt = Arrays.copyOfRange(response, 12, totalSize - 48);

        EcdhResult result = new EcdhResult();
        result.sharedSecret = sharedSecret;
        result.nonce = nonce;
        result.outerIv = outerIv;
        result.outerCiphertext = outerCt;
        result.outerTag = outerTag;
        result.sessionToken = sessionToken;
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // CRYPTO UTILITIES
    // ═══════════════════════════════════════════════════════════════

    /** HMAC-SHA256 key derivation matching server's deriveKey() */
    static byte[] deriveKey(byte[] sharedSecret, byte[] nonce, String context) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(sharedSecret, "HmacSHA256"));
        hmac.update(nonce);
        hmac.update(context.getBytes("UTF-8"));
        return hmac.doFinal();
    }

    /** Composite key = HMAC(sharedSecret, f1||f2||f3||f4) */
    static byte[] deriveCompositeKey(byte[] sharedSecret, byte[] f1, byte[] f2, byte[] f3, byte[] f4) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(sharedSecret, "HmacSHA256"));
        hmac.update(f1);
        hmac.update(f2);
        hmac.update(f3);
        hmac.update(f4);
        return hmac.doFinal();
    }

    /** AES-256-GCM decrypt. Throws on auth failure (outer transport layer). */
    static byte[] aesGcmDecrypt(byte[] key, byte[] iv, byte[] ciphertext, byte[] tag) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        // Java expects ciphertext || tag concatenated
        byte[] input = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, input, 0, ciphertext.length);
        System.arraycopy(tag, 0, input, ciphertext.length, tag.length);
        return cipher.doFinal(input);
    }

    /**
     * AES-256-CTR decrypt. NEVER throws. NEVER fails.
     * Wrong key → garbage output (not an exception).
     * This is THE critical anti-crack feature:
     * there is no exception to catch, no boolean to check.
     */
    static byte[] aesCtrDecrypt(byte[] key, byte[] iv, byte[] ciphertext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(ciphertext);
    }

    // ═══════════════════════════════════════════════════════════════
    // EC POINT ENCODING (Java ↔ Node.js interop)
    // ═══════════════════════════════════════════════════════════════

    /** Encode EC public key as 65-byte uncompressed point (04||x||y) */
    static byte[] encodeUncompressedPoint(KeyPair kp) {
        java.security.interfaces.ECPublicKey pub =
            (java.security.interfaces.ECPublicKey) kp.getPublic();
        java.security.spec.ECPoint w = pub.getW();

        byte[] x = w.getAffineX().toByteArray();
        byte[] y = w.getAffineY().toByteArray();

        byte[] raw = new byte[65];
        raw[0] = 0x04;

        // Right-align x into bytes 1-32 (handle BigInteger leading zero)
        int xOff = Math.max(0, x.length - 32);
        int xLen = Math.min(x.length, 32);
        System.arraycopy(x, xOff, raw, 1 + (32 - xLen), xLen);

        // Right-align y into bytes 33-64
        int yOff = Math.max(0, y.length - 32);
        int yLen = Math.min(y.length, 32);
        System.arraycopy(y, yOff, raw, 33 + (32 - yLen), yLen);

        return raw;
    }

    /** Decode 65-byte uncompressed point to Java PublicKey */
    static PublicKey decodeUncompressedPoint(byte[] raw, KeyPair localKp) throws Exception {
        byte[] xBytes = Arrays.copyOfRange(raw, 1, 33);
        byte[] yBytes = Arrays.copyOfRange(raw, 33, 65);
        BigInteger bx = new BigInteger(1, xBytes);
        BigInteger by = new BigInteger(1, yBytes);
        java.security.spec.ECPoint point = new java.security.spec.ECPoint(bx, by);

        // Get curve parameters from our own key
        java.security.interfaces.ECPublicKey localPub =
            (java.security.interfaces.ECPublicKey) localKp.getPublic();
        ECParameterSpec ecSpec = localPub.getParams();

        ECPublicKeySpec pubSpec = new ECPublicKeySpec(point, ecSpec);
        return KeyFactory.getInstance("EC").generatePublic(pubSpec);
    }

    // ═══════════════════════════════════════════════════════════════
    // I/O UTILITIES
    // ═══════════════════════════════════════════════════════════════

    static byte[] readExact(InputStream in, int len) throws IOException {
        byte[] buf = new byte[len];
        int read = 0;
        while (read < len) {
            int r = in.read(buf, read, len - read);
            if (r == -1) throw new IOException("Stream ended");
            read += r;
        }
        return buf;
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    /** ECDH auth result container — NOT a boolean */
    static class EcdhResult {
        byte[] sharedSecret;
        byte[] nonce;
        byte[] outerIv;
        byte[] outerCiphertext;
        byte[] outerTag;
        byte[] sessionToken;
    }
}
