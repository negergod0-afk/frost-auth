package com.zenya.utils;

import com.zenya.module.Module;
import com.zenya.module.ModuleManager;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ConfigStore {

    private static final String EXT = ".txt";
    private static final Pattern SAFE = Pattern.compile("[^A-Za-z0-9_\\- ]");

    private ConfigStore() {}

    public static Path configsDir() {
        Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("zenya_configs");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        return dir;
    }

    private static Path liveConfig() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("zenya_config.txt");
    }

    public static String sanitize(String name) {
        if (name == null) return "";
        return SAFE.matcher(name.trim()).replaceAll("_");
    }

    public static List<String> list() {
        List<String> out = new ArrayList<>();
        Path dir = configsDir();
        if (!Files.isDirectory(dir)) return out;
        try {
            Files.list(dir).forEach(p -> {
                String fn = p.getFileName().toString();
                if (fn.endsWith(EXT)) out.add(fn.substring(0, fn.length() - EXT.length()));
            });
        } catch (IOException ignored) {}
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static boolean saveAs(String name) {
        String safe = sanitize(name);
        if (safe.isEmpty()) return false;
        ModuleManager.INSTANCE.saveConfig();
        Path src = liveConfig();
        Path dst = configsDir().resolve(safe + EXT);
        try {
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean load(String name) {
        Path src = configsDir().resolve(sanitize(name) + EXT);
        if (!Files.isRegularFile(src)) return false;
        return applyFromPath(src);
    }

    public static boolean delete(String name) {
        try {
            return Files.deleteIfExists(configsDir().resolve(sanitize(name) + EXT));
        } catch (IOException e) {
            return false;
        }
    }

    public static String generateShareCode() {
        try {
            ModuleManager.INSTANCE.saveConfig();
            // Diff-only payload: skip everything that's still at its default. Cuts the
            // typical share code from many KB down to a few hundred chars.
            byte[] data = ModuleManager.INSTANCE.buildDiffPayload();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bos)) {
                gz.write(data);
            }
            return "XCFG2-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Decodes either the new gzipped {@code XCFG2-} format or the legacy uncompressed
     * {@code XCFG-} format and applies the config locally.
     */
    public static boolean redeemShareCode(String code) {
        if (code == null) return false;
        String c = code.trim();
        if (c.isEmpty()) return false;

        try {
            byte[] data;
            if (c.startsWith("XCFG2-")) {
                byte[] gz = Base64.getUrlDecoder().decode(c.substring(6));
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(
                        new java.io.ByteArrayInputStream(gz))) {
                    in.transferTo(bos);
                }
                data = bos.toByteArray();
            } else if (c.startsWith("XCFG-")) {
                data = Base64.getUrlDecoder().decode(c.substring(5));
            } else {
                return false;
            }
            return saveAndApply(data);
        } catch (IllegalArgumentException | IOException e) {
            return false;
        }
    }

    private static boolean saveAndApply(byte[] data) {
        try {
            Path tmp = configsDir().resolve(".__shared_tmp" + EXT);
            Files.write(tmp, data);
            boolean ok = applyFromPath(tmp);
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            return ok;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean applyFromPath(Path src) {
        Map<String, Boolean> before = snapshotEnabled();
        try {
            Files.copy(src, liveConfig(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return false;
        }
        ModuleManager.INSTANCE.loadConfig();
        Map<String, Boolean> after = snapshotEnabled();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            boolean was = before.getOrDefault(m.getName(), false);
            boolean now = after.getOrDefault(m.getName(), false);
            if (was == now) continue;
            try {
                if (now) m.onEnable();
                else m.onDisable();
            } catch (Throwable ignored) {}
        }
        ModuleManager.INSTANCE.saveConfig();
        return true;
    }

    private static Map<String, Boolean> snapshotEnabled() {
        Map<String, Boolean> map = new HashMap<>();
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            map.put(m.getName(), m.isEnabled());
        }
        return map;
    }

    public static String readClipboard() {
        try {
            return MinecraftClient.getInstance().keyboard.getClipboard();
        } catch (Throwable e) {
            return "";
        }
    }

    public static void writeClipboard(String s) {
        try {
            MinecraftClient.getInstance().keyboard.setClipboard(s == null ? "" : s);
        } catch (Throwable ignored) {}
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
