package com.zenya.module;

import com.zenya.ZenyaClient;
import com.zenya.auth.ZenyaAuth;
import com.zenya.auth.PayloadLoader;
import com.zenya.setting.Setting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.packet.Packet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxy module for server-delivered (payload) modules. Defers fetching the real
 * class bytes from the auth-server until the user first toggles or opens the
 * settings of this module. Forwards all lifecycle calls to the loaded instance.
 */
public class LazyPayloadModule extends Module {

    private final String fqcn;
    private Module real = null;
    private boolean loadFailed = false;
    private final Map<String, String> deferredSettings = new LinkedHashMap<>();

    public LazyPayloadModule(String fqcn, String displayName, Category category) {
        super(displayName, category);
        this.fqcn = fqcn;
    }

    public boolean isLoaded() { return real != null; }
    public Module  getRealOrNull() { return real; }
    public String  getFqcn() { return fqcn; }

    public void deferSetting(String name, String serializedValue) {
        if (real != null) {
            ModuleManager.INSTANCE.applyDeferredSetting(real, name, serializedValue);
        } else {
            deferredSettings.put(name, serializedValue);
        }
    }

    /** Called from ModuleManager.unloadPayloads on heartbeat fail. */
    public synchronized void unload() {
        if (real != null) {
            try { real.applyEnabled(false); real.onDisable(); } catch (Throwable ignored) {}
            real = null;
        }
        loadFailed = true;
        applyEnabled(false);
    }

    private synchronized void ensureLoaded() {
        if (real != null || loadFailed) return;
        if (!ZenyaAuth.isAuthenticated()) {
            loadFailed = true;
            return;
        }
        try {
            real = (Module) PayloadLoader.getInstance().instantiate(fqcn);
            int bind = getBind();
            if (bind != 0) real.applyBind(bind);
            if (!deferredSettings.isEmpty()) {
                for (Map.Entry<String, String> e : deferredSettings.entrySet()) {
                    ModuleManager.INSTANCE.applyDeferredSetting(real, e.getKey(), e.getValue());
                }
                deferredSettings.clear();
            }
            ZenyaClient.LOGGER.info("[Payload] Lazy-loaded: {}", fqcn);
        } catch (Throwable t) {
            // Include the full chain — payload class init can throw NoClassDefFoundError /
            // ExceptionInInitializerError / InvocationTargetException whose getMessage()
            // is null. We need the cause+stacktrace to actually debug.
            loadFailed = true;
            ZenyaClient.LOGGER.error("[Payload] Lazy load failed for {}: {} ({})",
                    fqcn, t.getClass().getSimpleName(),
                    t.getMessage() != null ? t.getMessage()
                            : (t.getCause() != null
                                ? t.getCause().getClass().getSimpleName() + ":" + t.getCause().getMessage()
                                : "no message"),
                    t);
        }
    }

    @Override
    public List<Setting<?>> getSettings() {
        ensureLoaded();
        return real != null ? real.getSettings() : Collections.emptyList();
    }

    @Override
    public void onEnable() {
        com.zenya.auth.ZenyaAuth.quickTamperCheck();  // scattered check #3
        ensureLoaded();
        if (loadFailed || real == null) { applyEnabled(false); return; }
        real.applyEnabled(true);
        real.onEnable();
    }

    @Override
    public void onDisable() {
        if (real == null) return;
        real.applyEnabled(false);
        real.onDisable();
    }

    @Override
    public void onTick() { if (real != null && real.isEnabled()) real.onTick(); }

    @Override
    public void onRender(MatrixStack matrices, float tickDelta) {
        if (real != null && real.isEnabled()) real.onRender(matrices, tickDelta);
    }

    @Override
    public void onPacketReceive(Packet<?> packet) {
        if (real != null && real.isEnabled()) real.onPacketReceive(packet);
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        return real != null && real.isEnabled() && real.onPacketSend(packet);
    }
}
