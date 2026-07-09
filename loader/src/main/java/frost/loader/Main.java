package frost.loader;

import java.lang.management.ManagementFactory;
import java.security.MessageDigest;
import java.util.List;

/**
 * FROST LOADER — Entry Point
 *
 * SECURITY ARCHITECTURE:
 * ───────────────────────
 * ✗ No booleans — no "isValid", no "success", no "authenticated"
 * ✗ No if/else on auth result — ClassLoader either works or doesn't
 * ✗ No empty calls — every method does real crypto that feeds into the key
 * ✗ No meaningful error messages — all failures look identical
 *
 * Anti-tamper checks (debug, timing, VM, integrity) silently corrupt
 * the decryption key instead of branching. Wrong key = garbage mod.
 * There is nothing to patch.
 *
 * Flow: envProfile → HWID → 4 fragment auths → ECDH auth → double decrypt → load
 */
public class Main {

    // ═══════════════════════════════════════════════
    // Injected by Discord bot build process per-user (Must be exactly 64 chars for binary patch)
    // ═══════════════════════════════════════════════
    private static final String INJECTED_LICENSE =    "Frost+Monthly-testing123                                        ";
    private static final String INJECTED_DISCORD_ID = "000000000000000000                                              ";

    // Server connection (Railway URL in production)
    private static final String AUTH_HOST = "127.0.0.1";
    private static final int AUTH_PORT = 4000;
    private static final int API_PORT = 3000;

    public static void main(String[] args) throws Throwable {
        // ═══════════════════════════════════════════════
        // PHASE 1: Environment Profiling
        // Each check XORs bytes into envKey.
        // Clean system: envKey = all zeros → key unaffected
        // Compromised:  envKey ≠ zero → key corrupted → garbage mod
        // NO BRANCHES. NO BOOLEANS. Pure arithmetic.
        // ═══════════════════════════════════════════════
        byte[] envKey = new byte[16];

        profileDebugAgents(envKey);     // envKey[0-3]  — JDWP / agent detection
        profileExecutionTiming(envKey); // envKey[4-7]  — single-step detection
        profileVirtualization(envKey);  // envKey[8-11] — VM artifact detection
        profileClassIntegrity(envKey);  // envKey[12-15]— class tamper detection

        // ═══════════════════════════════════════════════
        // PHASE 2: Hardware Fingerprint
        // Multi-source, SHA-256 hashed
        // ═══════════════════════════════════════════════
        byte[] hwid = HwidUtil.generate();

        // ═══════════════════════════════════════════════
        // PHASE 3: 5-Stage Server Authentication
        // 4 fragment auths (fake "microservices") + 1 ECDH auth
        // ALL 5 required — skip any = wrong composite key = garbage
        // Returns raw bytes: either valid JAR or garbage
        // ═══════════════════════════════════════════════
        byte[] modPayload = AuthClient.authenticate(
            INJECTED_LICENSE, hwid, envKey, AUTH_HOST, AUTH_PORT, API_PORT
        );

        // ═══════════════════════════════════════════════
        // PHASE 4: Load into Memory
        // NO BOOLEAN CHECK. If modPayload is garbage:
        //   → ClassFormatError / ZipException (JVM internal)
        //   → NOT a patchable branch
        //   → Game simply doesn't start
        // ═══════════════════════════════════════════════
        new MemoryClassLoader(modPayload)
            .loadClass("frost.client.FrostClient")
            .getMethod("initialize")
            .invoke(null);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANTI-TAMPER: Debug Agent Detection
    // Scans JVM input arguments for "jdwp" and "agentlib" byte patterns
    // Uses branchless unsigned-shift trick: ((x-1) >>> 31) = 1 iff x == 0
    // If detected: envKey[0-3] get corrupted → wrong composite key
    // ═══════════════════════════════════════════════════════════════════════
    static void profileDebugAgents(byte[] envKey) {
        List<String> argList = ManagementFactory.getRuntimeMXBean().getInputArguments();
        String allArgs = argList.toString().toLowerCase();
        byte[] argBytes = allArgs.getBytes();

        byte[][] targets = {
            {106, 100, 119, 112},                         // "jdwp"
            {97, 103, 101, 110, 116, 108, 105, 98},      // "agentlib"
            {100, 101, 98, 117, 103},                     // "debug"
            {115, 117, 115, 112, 101, 110, 100}           // "suspend"
        };

        int detected = 0;
        for (byte[] target : targets) {
            for (int i = 0; i <= argBytes.length - target.length; i++) {
                int mismatch = 0;
                for (int j = 0; j < target.length; j++) {
                    mismatch |= (argBytes[i + j] ^ target[j]);
                }
                // Branchless: if mismatch==0, match found → set bit
                detected |= (int)((((long)mismatch - 1L) >>> 63) & 1L);
            }
        }

        // detected: 0 = clean, 1+ = debugger found
        // Multiply to spread corruption across 4 bytes
        envKey[0] ^= (byte)(detected * 0x37);
        envKey[1] ^= (byte)(detected * 0xAE);
        envKey[2] ^= (byte)(detected * 0x5C);
        envKey[3] ^= (byte)(detected * 0x91);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANTI-TAMPER: Execution Timing Check
    // Measures time for deterministic work. Debugging slows execution.
    // Normal: < 100ms. Debugging: > 500ms.
    // Branchless: shift elapsed nanoseconds right by 29 (~537ms threshold)
    // If slow: envKey[4-7] corrupted → wrong key
    // ═══════════════════════════════════════════════════════════════════════
    static void profileExecutionTiming(byte[] envKey) {
        long t1 = System.nanoTime();

        // Deterministic busywork (prevents dead-code elimination via 'sink')
        int sink = 0x12345678;
        for (int i = 0; i < 200000; i++) {
            sink = (sink ^ (i * 0x5DEECE66DL > 0 ? i : sink)) + 0xB;
            sink = Integer.rotateLeft(sink, 13);
        }

        long elapsed = System.nanoTime() - t1;

        // Branchless anomaly: elapsed >> 29 ≈ elapsed / 537M ns
        // Normal (~5ms): 5_000_000 >> 29 = 0
        // Debugging (~2s): 2_000_000_000 >> 29 = 3
        int anomaly = (int)(elapsed >> 29) & 0xFF;

        envKey[4] ^= (byte)(anomaly);
        envKey[5] ^= (byte)(anomaly * 0x7B);
        envKey[6] ^= (byte)(anomaly * 0x3D);
        envKey[7] ^= (byte)(sink & 0x00); // sink used → not eliminated. XOR 0 = no-op
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANTI-TAMPER: VM Detection
    // Checks environment variables and system properties for VM artifacts
    // If VM detected: envKey[8-11] corrupted
    // ═══════════════════════════════════════════════════════════════════════
    static void profileVirtualization(byte[] envKey) {
        String sysInfo = (
            safe(System.getProperty("os.name")) +
            safe(System.getenv("PROCESSOR_IDENTIFIER")) +
            safe(System.getProperty("java.vm.name"))
        ).toLowerCase();

        byte[][] vmSigs = {
            {118, 109, 119, 97, 114, 101},           // "vmware"
            {118, 105, 114, 116, 117, 97, 108},      // "virtual"
            {113, 101, 109, 117},                     // "qemu"
            {120, 101, 110},                          // "xen"
            {104, 121, 112, 101, 114, 118},           // "hyperv"
            {118, 98, 111, 120}                       // "vbox"
        };

        byte[] sysBytes = sysInfo.getBytes();
        int vmScore = 0;
        for (byte[] sig : vmSigs) {
            for (int i = 0; i <= sysBytes.length - sig.length; i++) {
                int mismatch = 0;
                for (int j = 0; j < sig.length; j++) {
                    mismatch |= (sysBytes[i + j] ^ sig[j]);
                }
                vmScore |= (int)((((long)mismatch - 1L) >>> 63) & 1L);
            }
        }

        envKey[8]  ^= (byte)(vmScore * 0x4A);
        envKey[9]  ^= (byte)(vmScore * 0xC3);
        envKey[10] ^= (byte)(vmScore * 0x17);
        envKey[11] ^= (byte)(vmScore * 0x8F);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANTI-TAMPER: Class Integrity Check
    // Verifies that critical loader classes exist and haven't been stripped
    // If classes missing: envKey[12-15] corrupted
    // ═══════════════════════════════════════════════════════════════════════
    static void profileClassIntegrity(byte[] envKey) {
        String[] requiredClasses = {
            "frost.loader.AuthClient",
            "frost.loader.HwidUtil",
            "frost.loader.MemoryClassLoader"
        };

        int missing = 0;
        for (String cls : requiredClasses) {
            try {
                Class.forName(cls, false, Main.class.getClassLoader());
            } catch (Throwable t) {
                missing++;
            }
        }

        // Also check method count as structural fingerprint
        int methodCount = Main.class.getDeclaredMethods().length;
        // Expected: main + 4 profile methods + safe = 6
        int countDrift = Math.abs(methodCount - 6);

        int integrityDamage = missing + countDrift;

        envKey[12] ^= (byte)(integrityDamage * 0xB7);
        envKey[13] ^= (byte)(integrityDamage * 0x2E);
        envKey[14] ^= (byte)(integrityDamage * 0x63);
        envKey[15] ^= (byte)(integrityDamage * 0xD4);
    }

    static String safe(String s) { return s != null ? s : ""; }
}
