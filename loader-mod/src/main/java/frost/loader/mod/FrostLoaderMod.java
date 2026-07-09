package frost.loader.mod;

import com.zenya.ZenyaClient;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.List;

/**
 * FROST LOADER — Fabric Client Mod Entrypoint
 *
 * SECURITY ARCHITECTURE:
 * ─────────────────────────────────────────────────────────────────────────────
 * ✗ No booleans — no "isValid", no "authenticated", no "success"
 * ✗ No if/else on auth result — ClassLoader gets garbage or real bytes
 * ✗ No empty calls — every method does real crypto feeding into the key
 * ✗ No meaningful error — all failures are silent / identically shaped
 *
 * Anti-tamper (debug, timing, VM, integrity) XOR-corrupts the decryption
 * key instead of branching.  Wrong key → garbage mod payload → ClassFormatError.
 * There is nothing to patch.
 *
 * Flow: envProfile → HWID → 4 HTTP fragment auths → ECDH TCP auth
 *       → double-decrypt → in-memory load → ZenyaClient.onInitializeClient()
 *
 * INJECTION NOTE: INJECTED_LICENSE and INJECTED_DISCORD_ID are exactly 64
 * bytes each.  The Discord bot binary-patches these two fields inside
 * frost/loader/mod/FrostLoaderMod.class before sending the JAR to the user.
 */
public class FrostLoaderMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("FrostLoader");

    // ═══════════════════════════════════════════════════════════════════════
    // Injected per-user by the Discord bot (MUST stay exactly 64 chars each)
    // ═══════════════════════════════════════════════════════════════════════
    private static final String INJECTED_LICENSE    = "Frost+Monthly-testing123                                        ";
    private static final String INJECTED_DISCORD_ID = "000000000000000000                                              ";

    // ── Server coordinates (also binary-patchable — padded to fixed width) ──
    // AUTH_HOST is padded to 64 chars; ports padded to 8 chars.
    // The Discord bot / inject script patches these the same way as the license.
    // In production these point at your Railway TCP+HTTP service.
    private static final String INJECTED_AUTH_HOST = "127.0.0.1                                                       ";
    private static final String INJECTED_TCP_PORT  = "4000    ";
    private static final String INJECTED_API_PORT  = "3000    ";

    // Parsed at init — trim removes the padding injected above.
    private static final String AUTH_HOST = INJECTED_AUTH_HOST.trim();
    private static final int    AUTH_PORT = Integer.parseInt(INJECTED_TCP_PORT.trim());
    private static final int    API_PORT  = Integer.parseInt(INJECTED_API_PORT.trim());

    // ── Gate: set to true only when auth fully passes ────────────────────
    // Intentionally NOT a boolean in the security-sensitive path —
    // the ClassLoader itself is the gate (garbage bytes → ClassFormatError).
    // This field is only for the mod-init sequencing after a successful load.
    static volatile boolean authPassed = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[Frost] Initializing loader…");

        // ── PHASE 1: Environment profiling (branchless, no booleans) ─────
        byte[] envKey = new byte[16];
        profileDebugAgents(envKey);
        profileExecutionTiming(envKey);
        profileVirtualization(envKey);
        profileClassIntegrity(envKey);

        // ── PHASE 2: Hardware fingerprint ────────────────────────────────
        byte[] hwid = HwidUtil.generate();

        // ── PHASE 3: Server auth (4 HTTP fragments + 1 ECDH) ────────────
        // Returns either a valid JAR payload or cryptographic garbage.
        // No exception is thrown on auth failure; the ClassLoader handles it.
        byte[] modPayload;
        try {
            modPayload = ModAuthClient.authenticate(
                INJECTED_LICENSE.trim(), hwid, envKey,
                AUTH_HOST, AUTH_PORT, API_PORT
            );
        } catch (Throwable t) {
            // Network failure, server offline, etc. → silent fail.
            LOGGER.warn("[Frost] Auth unavailable.");
            return;
        }

        // ── PHASE 4: Load real mod into memory ────────────────────────────
        // No disk write. No boolean check. If payload is garbage →
        // FabricMemoryLoader throws ClassFormatError (JVM-internal).
        // SessionBridge was populated by ModAuthClient.authenticate() above.
        try {
            FabricMemoryLoader loader = new FabricMemoryLoader(
                modPayload, FrostLoaderMod.class.getClassLoader()
            );
            Class<?> realClient = loader.loadClass("com.zenya.ZenyaClient");
            authPassed = true;
            // Delegate full initialization to the real mod's entrypoint.
            realClient.getMethod("onInitializeClient").invoke(
                realClient.getDeclaredConstructor().newInstance()
            );
            LOGGER.info("[Frost] Client initialized.");
        } catch (Throwable t) {
            // Garbage payload → natural ClassFormatError → silent fail.
            LOGGER.warn("[Frost] Load failed (auth mismatch).");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANTI-TAMPER: Debug Agent Detection
    // Scans JVM input args for JDWP / agentlib byte patterns.
    // Branchless: ((x-1)>>>31) = 1 iff x == 0 — no if/else.
    // Detected → envKey[0-3] corrupted → wrong composite key.
    // ═══════════════════════════════════════════════════════════════════════
    static void profileDebugAgents(byte[] envKey) {
        List<String> argList = ManagementFactory.getRuntimeMXBean().getInputArguments();
        byte[] argBytes = argList.toString().toLowerCase().getBytes();

        byte[][] targets = {
            {106, 100, 119, 112},                     // "jdwp"
            {97, 103, 101, 110, 116, 108, 105, 98},   // "agentlib"
            {100, 101, 98, 117, 103},                  // "debug"
            {115, 117, 115, 112, 101, 110, 100}        // "suspend"
        };

        int detected = 0;
        for (byte[] target : targets) {
            for (int i = 0; i <= argBytes.length - target.length; i++) {
                int mismatch = 0;
                for (int j = 0; j < target.length; j++) mismatch |= (argBytes[i + j] ^ target[j]);
                detected |= (int)((((long)mismatch - 1L) >>> 63) & 1L);
            }
        }
        envKey[0] ^= (byte)(detected * 0x37);
        envKey[1] ^= (byte)(detected * 0xAE);
        envKey[2] ^= (byte)(detected * 0x5C);
        envKey[3] ^= (byte)(detected * 0x91);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANTI-TAMPER: Execution Timing
    // Branchless: elapsed >> 29 ≈ elapsed / 537M ns.
    // Normal (~5 ms): 5_000_000 >> 29 = 0.  Debugging (~2 s): = 3.
    // ═══════════════════════════════════════════════════════════════════════
    static void profileExecutionTiming(byte[] envKey) {
        long t1 = System.nanoTime();
        int sink = 0x12345678;
        for (int i = 0; i < 200_000; i++) {
            sink = (sink ^ (i * 0x5DEECE66DL > 0 ? i : sink)) + 0xB;
            sink = Integer.rotateLeft(sink, 13);
        }
        long elapsed = System.nanoTime() - t1;
        int anomaly = (int)(elapsed >> 29) & 0xFF;
        envKey[4] ^= (byte)(anomaly);
        envKey[5] ^= (byte)(anomaly * 0x7B);
        envKey[6] ^= (byte)(anomaly * 0x3D);
        envKey[7] ^= (byte)(sink & 0x00); // sink used → not eliminated; XOR 0 = no-op
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANTI-TAMPER: VM / Hypervisor Detection
    // Checks system properties for VMware, VirtualBox, QEMU, etc.
    // ═══════════════════════════════════════════════════════════════════════
    static void profileVirtualization(byte[] envKey) {
        String sysInfo = (
            safe(System.getProperty("os.name")) +
            safe(System.getenv("PROCESSOR_IDENTIFIER")) +
            safe(System.getProperty("java.vm.name"))
        ).toLowerCase();

        byte[][] vmSigs = {
            {118,109,119,97,114,101},           // "vmware"
            {118,105,114,116,117,97,108},        // "virtual"
            {113,101,109,117},                   // "qemu"
            {120,101,110},                       // "xen"
            {104,121,112,101,114,118},           // "hyperv"
            {118,98,111,120}                     // "vbox"
        };

        byte[] sysBytes = sysInfo.getBytes();
        int vmScore = 0;
        for (byte[] sig : vmSigs) {
            for (int i = 0; i <= sysBytes.length - sig.length; i++) {
                int mismatch = 0;
                for (int j = 0; j < sig.length; j++) mismatch |= (sysBytes[i + j] ^ sig[j]);
                vmScore |= (int)((((long)mismatch - 1L) >>> 63) & 1L);
            }
        }
        envKey[8]  ^= (byte)(vmScore * 0x4A);
        envKey[9]  ^= (byte)(vmScore * 0xC3);
        envKey[10] ^= (byte)(vmScore * 0x17);
        envKey[11] ^= (byte)(vmScore * 0x8F);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ANTI-TAMPER: Class Integrity
    // Verifies that all loader classes are present and method count is stable.
    // ═══════════════════════════════════════════════════════════════════════
    static void profileClassIntegrity(byte[] envKey) {
        String[] required = {
            "frost.loader.mod.ModAuthClient",
            "frost.loader.mod.HwidUtil",
            "frost.loader.mod.FabricMemoryLoader"
        };
        int missing = 0;
        for (String cls : required) {
            try { Class.forName(cls, false, FrostLoaderMod.class.getClassLoader()); }
            catch (Throwable t) { missing++; }
        }
        // Expected method count: onInitializeClient + 4 profile + safe = 6
        int drift = Math.abs(FrostLoaderMod.class.getDeclaredMethods().length - 6);
        int damage = missing + drift;
        envKey[12] ^= (byte)(damage * 0xB7);
        envKey[13] ^= (byte)(damage * 0x2E);
        envKey[14] ^= (byte)(damage * 0x63);
        envKey[15] ^= (byte)(damage * 0xD4);
    }

    static String safe(String s) { return s != null ? s : ""; }
}
