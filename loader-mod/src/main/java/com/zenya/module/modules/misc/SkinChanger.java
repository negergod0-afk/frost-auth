package com.zenya.module.modules.misc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zenya.module.Category;
import com.zenya.module.Module;
import com.zenya.setting.Setting;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public final class SkinChanger extends Module {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final long DEBOUNCE_MS = 600L;

    private static final String MOJANG_PROFILE_URL  = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String SESSION_PROFILE_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Volatile skin override state
    private static volatile SkinTextures overrideSkin = null;
    private static volatile Identifier   registeredId = null;

    private final Setting<String> playerName = new Setting<>("Player Name", "");
    private final AtomicInteger generation = new AtomicInteger();

    private String lastObserved = "";
    private String lastRequested = "";
    private long lastEditTime = 0;

    public SkinChanger() {
        super("SkinChanger", Category.MISC);
        addSetting(playerName);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        lastObserved = normalize(playerName.getValue());
        lastRequested = "";
        lastEditTime = System.currentTimeMillis();
        if (!lastObserved.isEmpty()) {
            fetch(lastObserved);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        generation.incrementAndGet();
        lastRequested = "";
        clearOverride();
    }

    @Override
    public void onTick() {
        String current = normalize(playerName.getValue());
        if (!Objects.equals(current, lastObserved)) {
            lastObserved = current;
            lastEditTime = System.currentTimeMillis();
            return;
        }
        if (current.isEmpty()) {
            if (overrideSkin != null) {
                lastRequested = "";
                clearOverride();
            }
            return;
        }
        if (!Objects.equals(current, lastRequested)
                && System.currentTimeMillis() - lastEditTime >= DEBOUNCE_MS) {
            fetch(current);
        }
    }

    /**
     * Returns the override SkinTextures for the given UUID if SkinChanger is active
     * and the UUID matches the local player. Called by mixins.
     */
    public static SkinTextures getOverrideSkin(UUID uuid) {
        if (overrideSkin == null || uuid == null) return null;
        UUID localUuid = localUuid();
        return localUuid != null && localUuid.equals(uuid) ? overrideSkin : null;
    }

    // -------------------------------------------------------------------------
    // Internal fetch pipeline
    // -------------------------------------------------------------------------

    private void fetch(String name) {
        lastRequested = name;
        int gen = generation.incrementAndGet();

        CompletableFuture
            .supplyAsync(() -> fetchSkin(name), Util.getIoWorkerExecutor())
            .whenComplete((result, err) -> mc.execute(() -> {
                if (gen != generation.get() || !isEnabled()) {
                    return;
                }
                if (err != null) {
                    chat("[SkinChanger] Failed: " + rootMessage(err));
                    return;
                }
                if (result == null) {
                    chat("[SkinChanger] Skin not found for: " + name);
                    return;
                }
                applyResult(result, gen);
            }));
    }

    /** Runs off-thread: resolves UUID → texture URL → downloads PNG bytes. */
    private FetchResult fetchSkin(String name) {
        try {
            UUID uuid = resolveUuid(name);
            TextureInfo info = resolveTextureInfo(uuid);
            byte[] pngBytes = downloadBytes(info.url());
            return new FetchResult(name, pngBytes, info.skinType());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /** Runs on render thread: registers texture and sets override. */
    private void applyResult(FetchResult result, int gen) {
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(result.pngBytes()));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "skin_changer_skin", img);

            Identifier id = Identifier.of("zenya", "skins/" + result.name().toLowerCase(Locale.ROOT));

            // Destroy old texture if any
            if (registeredId != null) {
                mc.getTextureManager().destroyTexture(registeredId);
            }

            mc.getTextureManager().registerTexture(id, tex);
            registeredId = id;

            // AssetInfo.TextureAssetInfo is a record that implements AssetInfo.TextureAsset
            AssetInfo.TextureAsset bodyAsset = new AssetInfo.TextureAssetInfo(id);

            overrideSkin = new SkinTextures(bodyAsset, null, null, result.skinType(), true);
            chat("[SkinChanger] Applied skin: " + result.name());
        } catch (Exception e) {
            chat("[SkinChanger] Failed to apply texture: " + e.getMessage());
        }
    }

    private static synchronized void clearOverride() {
        if (registeredId != null && mc != null) {
            mc.getTextureManager().destroyTexture(registeredId);
            registeredId = null;
        }
        overrideSkin = null;
    }

    // -------------------------------------------------------------------------
    // API helpers
    // -------------------------------------------------------------------------

    private UUID resolveUuid(String name) throws IOException, InterruptedException {
        JsonObject obj = getJson(MOJANG_PROFILE_URL + enc(name));
        if (obj == null || !obj.has("id")) {
            throw new IOException("Player not found: " + name);
        }
        return parseUuid(obj.get("id").getAsString());
    }

    private TextureInfo resolveTextureInfo(UUID uuid) throws IOException, InterruptedException {
        String raw = uuid.toString().replace("-", "");
        JsonObject profile = getJson(SESSION_PROFILE_URL + raw);
        if (profile == null || !profile.has("properties")) {
            throw new IOException("Profile not found for UUID: " + uuid);
        }
        JsonArray props = profile.getAsJsonArray("properties");
        for (JsonElement el : props) {
            if (!el.isJsonObject()) continue;
            JsonObject prop = el.getAsJsonObject();
            if (!"textures".equalsIgnoreCase(prop.has("name") ? prop.get("name").getAsString() : "")) continue;
            if (!prop.has("value")) continue;

            String decoded = new String(Base64.getDecoder().decode(prop.get("value").getAsString()), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(decoded).getAsJsonObject();
            JsonObject textures = root.has("textures") ? root.getAsJsonObject("textures") : null;
            JsonObject skin = textures != null && textures.has("SKIN") ? textures.getAsJsonObject("SKIN") : null;
            if (skin == null || !skin.has("url")) break;

            String model = null;
            if (skin.has("metadata")) {
                JsonObject meta = skin.getAsJsonObject("metadata");
                if (meta.has("model")) model = meta.get("model").getAsString();
            }
            PlayerSkinType type = "slim".equalsIgnoreCase(model) ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
            return new TextureInfo(skin.get("url").getAsString(), type);
        }
        throw new IOException("No skin texture found in profile");
    }

    private byte[] downloadBytes(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404 || resp.statusCode() == 204) return null;
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) throw new IOException("HTTP " + resp.statusCode());
        String body = resp.body();
        if (body == null || body.isBlank()) return null;
        return JsonParser.parseString(body).getAsJsonObject();
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static UUID parseUuid(String raw) {
        String s = raw.replace("-", "");
        return UUID.fromString(s.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5"));
    }

    private static String normalize(String s) { return s == null ? "" : s.trim(); }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }

    private static UUID localUuid() {
        if (mc == null) return null;
        if (mc.player != null) return mc.player.getUuid();
        return mc.getSession() != null ? mc.getSession().getUuidOrNull() : null;
    }

    private void chat(String msg) {
        if (mc != null && mc.inGameHud != null) {
            try { mc.inGameHud.getChatHud().addMessage(Text.literal(msg)); }
            catch (Throwable ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Records
    // -------------------------------------------------------------------------

    private record TextureInfo(String url, PlayerSkinType skinType) {}
    private record FetchResult(String name, byte[] pngBytes, PlayerSkinType skinType) {}
}
