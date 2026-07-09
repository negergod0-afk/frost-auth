package frost.loader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Enumeration;

/**
 * FROST HARDWARE ID — Multi-Source Fingerprint
 *
 * Generates a SHA-256 hash from multiple hardware sources:
 * - OS name, version, arch
 * - Computer name
 * - Processor identifier + architecture
 * - MAC addresses (all NICs)
 * - Disk serial number (Windows)
 * - Motherboard serial (Windows)
 * - User home directory path
 *
 * HWID is deterministic: same machine always produces same hash.
 * Different machines produce different hashes.
 * Impossible to reverse (SHA-256 is one-way).
 */
public class HwidUtil {

    public static byte[] generate() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // ── System Properties ──
            feed(digest, System.getProperty("os.name"));
            feed(digest, System.getProperty("os.version"));
            feed(digest, System.getProperty("os.arch"));
            feed(digest, System.getProperty("user.home"));
            feed(digest, System.getProperty("user.name"));

            // ── Environment Variables ──
            feed(digest, System.getenv("COMPUTERNAME"));
            feed(digest, System.getenv("PROCESSOR_IDENTIFIER"));
            feed(digest, System.getenv("PROCESSOR_ARCHITECTURE"));
            feed(digest, System.getenv("PROCESSOR_REVISION"));
            feed(digest, System.getenv("NUMBER_OF_PROCESSORS"));

            // ── MAC Addresses (all network interfaces) ──
            try {
                Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
                while (nics != null && nics.hasMoreElements()) {
                    NetworkInterface nic = nics.nextElement();
                    byte[] mac = nic.getHardwareAddress();
                    if (mac != null && mac.length > 0) {
                        digest.update(mac);
                    }
                }
            } catch (Exception ignored) {}

            // ── Windows: Disk Serial via WMIC ──
            String diskSerial = execCommand("wmic diskdrive get SerialNumber");
            feed(digest, diskSerial);

            // ── Windows: Motherboard Serial via WMIC ──
            String mbSerial = execCommand("wmic baseboard get SerialNumber");
            feed(digest, mbSerial);

            // ── Windows: BIOS Serial ──
            String biosSerial = execCommand("wmic bios get SerialNumber");
            feed(digest, biosSerial);

            // ── Runtime: Available processors + max memory (structural fingerprint) ──
            digest.update((byte) Runtime.getRuntime().availableProcessors());
            long maxMem = Runtime.getRuntime().maxMemory();
            for (int i = 0; i < 8; i++) {
                digest.update((byte) ((maxMem >> (i * 8)) & 0xFF));
            }

            return digest.digest();

        } catch (Exception e) {
            // Fallback: use whatever we can
            try {
                return MessageDigest.getInstance("SHA-256")
                    .digest(("fallback-" + System.getProperty("os.name") +
                             System.getenv("COMPUTERNAME")).getBytes(StandardCharsets.UTF_8));
            } catch (Exception fatal) {
                return new byte[32]; // This will cause auth to fail (empty HWID)
            }
        }
    }

    private static void feed(MessageDigest md, String value) {
        if (value != null && !value.isEmpty()) {
            md.update(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String execCommand(String command) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (!os.contains("win")) return ""; // WMIC is Windows-only

            Process proc = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream()));

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line.trim());
            }
            proc.waitFor();
            return sb.toString().replaceAll("\\s+", "");
        } catch (Exception e) {
            return "";
        }
    }
}
