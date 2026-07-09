package com.zenya.module;

import com.zenya.module.modules.StubModule;
import com.zenya.module.modules.client.Hud;
import com.zenya.module.modules.client.Themes;
import com.zenya.module.modules.client.ZenyaPlus;
import com.zenya.module.modules.client.ThemeChanger;
import com.zenya.module.modules.client.ConfigManager;
import com.zenya.module.modules.render.BlockESP;
import com.zenya.module.modules.render.Freecam;
import com.zenya.module.modules.render.LightESP;
import com.zenya.module.modules.misc.AutoMine;
import com.zenya.module.modules.misc.Freelook;
import com.zenya.module.modules.misc.SwingSpeed;
import com.zenya.setting.BlocksSetting;
import com.zenya.setting.MobsSetting;
import com.zenya.setting.Setting;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ModuleManager {
    private static final String CONFIG_HEADER = "ZENYA_CONFIG_V2";
    private static final String MODULE_PREFIX = "MODULE";
    private static final String SETTING_PREFIX = "SETTING";
    private static final String HUDPOS_PREFIX = "HUDPOS";

    public static final ModuleManager INSTANCE = new ModuleManager();

    private final List<Module> modules = new ArrayList<>();
    private final EnumMap<Category, List<Module>> modulesByCategory = new EnumMap<>(Category.class);
    private boolean initialized = false;
    private boolean loadingConfig = false;

    public static Module loadPayloadModule(String fqcn, String displayName, Category category) {
        try {
            Class<?> cls = Class.forName(fqcn);
            return (Module) cls.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            com.zenya.ZenyaClient.LOGGER.warn("Direct load failed for {}: {} - using stub", fqcn, e.getMessage());
            return new StubModule(displayName, category);
        }
    }

    public void init() {
        if (initialized) {
            return;
        }
        modules.add(new ZenyaPlus());
        modules.add(new Hud());
        modules.add(Themes.getInstance());
        modules.add(new ThemeChanger());
        modules.add(new ConfigManager());

        modules.add(loadPayloadModule("com.zenya.module.modules.render.PlayerESP",   "Player ESP",   Category.RENDER));
        modules.add(new BlockESP());
        modules.add(loadPayloadModule("com.zenya.module.modules.render.StorageESP",  "Storage ESP",  Category.RENDER));
        modules.add(new com.zenya.module.modules.render.HandView());
        modules.add(new com.zenya.module.modules.render.PistonESP());
        modules.add(new Freecam());
        modules.add(loadPayloadModule("com.zenya.module.modules.render.FullBright", "FullBright",  Category.RENDER));
        modules.add(new com.zenya.module.modules.render.StashNotifier());
        modules.add(new LightESP());
        modules.add(new com.zenya.module.modules.render.CameraTweaks());

        modules.add(new com.zenya.module.modules.combat.HoverTotem());
        modules.add(new com.zenya.module.modules.combat.AutoInvTotem());
        modules.add(new com.zenya.module.modules.combat.AnchorMacro());
        modules.add(new com.zenya.module.modules.combat.AutoCrystal());
        modules.add(new com.zenya.module.modules.combat.Triggerbot());
        modules.add(new com.zenya.module.modules.combat.AimAssist());
        modules.add(new com.zenya.module.modules.combat.AutoMace());
        modules.add(new com.zenya.module.modules.combat.MaceSwap());
        modules.add(new com.zenya.module.modules.combat.SafeAnchor());

        modules.add(new com.zenya.module.modules.combat.AutoHitCrystal());
        modules.add(new com.zenya.module.modules.combat.DoubleAnchor());
        modules.add(new com.zenya.module.modules.combat.ShieldBreaker());
        modules.add(new com.zenya.module.modules.combat.AutoWTap());
        modules.add(new com.zenya.module.modules.combat.Hitboxes());
        modules.add(new com.zenya.module.modules.combat.ElytraSwap());
        modules.add(new com.zenya.module.modules.combat.CrystalOptimizer());
        modules.add(new com.zenya.module.modules.combat.AutoPotRefill());

        modules.add(new SwingSpeed());
        modules.add(new Freelook());
        modules.add(new AutoMine());
        modules.add(new com.zenya.module.modules.misc.FastBridge());
        modules.add(new com.zenya.module.modules.misc.TridentFly());
        modules.add(new com.zenya.module.modules.misc.AutoFireworks());
        modules.add(loadPayloadModule("com.zenya.module.modules.misc.FastPlace",       "Fast Place",      Category.MISC));
        modules.add(loadPayloadModule("com.zenya.module.modules.misc.AutoTool",        "Auto Tool",       Category.MISC));
        modules.add(loadPayloadModule("com.zenya.module.modules.misc.AutoLog",         "AutoLog",         Category.MISC));
        modules.add(loadPayloadModule("com.zenya.module.modules.misc.Sprint",          "Sprint",          Category.MISC));
        modules.add(loadPayloadModule("com.zenya.module.modules.misc.WeatherNotifier", "WeatherNotifier", Category.MISC));
        modules.add(loadPayloadModule("com.zenya.module.modules.misc.SpotifyHUD",    "SpotifyHUD",    Category.MISC));
        modules.add(new com.zenya.module.modules.misc.NameProtect());
        modules.add(loadPayloadModule("com.zenya.module.modules.misc.SkinChanger",   "SkinChanger",   Category.MISC));
        modules.add(new com.zenya.module.modules.misc.NameTags());


        modules.add(new com.zenya.module.modules.smps.SpawnerTags());
        modules.add(new com.zenya.module.modules.smps.PlayerChunkFinder());
        modules.add(new com.zenya.module.modules.smps.SeedChunkFinder());
        modules.add(new com.zenya.module.modules.smps.SusChunkFinder());

        modules.add(new com.zenya.module.modules.donut.FakeStats());

        modules.add(loadPayloadModule("com.zenya.module.modules.donut.FakePay",        "FakePay",        Category.DONUT));
        modules.add(new com.zenya.module.modules.donut.RegionMap());
        modules.add(loadPayloadModule("com.zenya.module.modules.donut.DeltaSensor",       "Delta Sensor",      Category.DONUT));
        modules.add(new com.zenya.module.modules.donut.ChunkFinder());
        modules.add(new com.zenya.module.modules.render.CustomCrosshair());
        modules.add(new com.zenya.module.modules.render.JumpCircle());
        modules.add(loadPayloadModule("com.zenya.module.modules.render.AmethystESP", "AmethystESP", Category.RENDER));

        rebuildCategoryCache();
        initialized = true;
        loadConfig();
    }

    public void onSettingChanged() {
        if (!initialized || loadingConfig) {
            return;
        }
        saveConfig();
    }

    public byte[] buildDiffPayload() {
        StringBuilder sb = new StringBuilder();
        sb.append(CONFIG_HEADER).append('\n');
        for (Module module : modules) {
            boolean moduleEnabledDiffers = module.isEnabled();
            boolean bindDiffers = module.getBind() != 0;
            int activationKey = module instanceof ActivatableModule am ? am.getActivationKey() : 0;
            boolean activationDiffers = activationKey != 0;

            List<Setting<?>> changedSettings = new ArrayList<>();
            for (Setting<?> s : module.getSettings()) {
                Object cur = s.getValue();
                Object def = s.getDefaultValue();
                if (!java.util.Objects.equals(cur, def)) changedSettings.add(s);
            }
            if (!moduleEnabledDiffers && !bindDiffers && !activationDiffers && changedSettings.isEmpty()) {
                continue;
            }

            sb.append(MODULE_PREFIX).append('\t')
              .append(encode(module.getName())).append('\t')
              .append(module.getBind()).append('\t')
              .append(activationKey).append('\t')
              .append(module.isEnabled()).append('\n');

            for (Setting<?> s : changedSettings) {
                String serialized = serializeSettingValue(s);
                if (serialized == null) continue;
                sb.append(SETTING_PREFIX).append('\t')
                  .append(encode(module.getName())).append('\t')
                  .append(encode(s.getName())).append('\t')
                  .append(encode(serialized)).append('\n');
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public void saveConfig() {
        if (!initialized || loadingConfig) {
            return;
        }

        Path configFile = getConfigPath();
        try {
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                writer.write(CONFIG_HEADER);
                writer.newLine();

                for (Module module : modules) {
                    writer.write(MODULE_PREFIX);
                    writer.write('\t');
                    writer.write(encode(module.getName()));
                    writer.write('\t');
                    writer.write(Integer.toString(module.getBind()));
                    writer.write('\t');
                    int activationKey = module instanceof ActivatableModule activatableModule
                            ? activatableModule.getActivationKey()
                            : 0;
                    writer.write(Integer.toString(activationKey));
                    writer.write('\t');
                    writer.write(Boolean.toString(module.isEnabled()));
                    writer.newLine();

                    for (Setting<?> setting : module.getSettings()) {
                        String serialized = serializeSettingValue(setting);
                        if (serialized == null) {
                            continue;
                        }

                        writer.write(SETTING_PREFIX);
                        writer.write('\t');
                        writer.write(encode(module.getName()));
                        writer.write('\t');
                        writer.write(encode(setting.getName()));
                        writer.write('\t');
                        writer.write(encode(serialized));
                        writer.newLine();
                    }
                }

                for (com.zenya.module.modules.client.Hud.HudElement el : com.zenya.module.modules.client.Hud.HudElement.values()) {
                    int[] pos = com.zenya.module.modules.client.Hud.getElementPos(el);
                    writer.write(HUDPOS_PREFIX);
                    writer.write('\t');
                    writer.write(el.name());
                    writer.write('\t');
                    writer.write(Integer.toString(pos[0]));
                    writer.write('\t');
                    writer.write(Integer.toString(pos[1]));
                    writer.newLine();
                }
            }
        } catch (IOException ignored) {
        }
    }

    public void loadConfig() {
        Path configFile = getConfigPath();
        if (!Files.exists(configFile)) {
            return;
        }

        loadingConfig = true;
        try {
            List<String> lines = Files.readAllLines(configFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.isBlank() || CONFIG_HEADER.equals(line)) {
                    continue;
                }

                if (line.startsWith(MODULE_PREFIX + "\t")) {
                    loadModuleLine(line);
                    continue;
                }

                if (line.startsWith(SETTING_PREFIX + "\t")) {
                    loadSettingLine(line);
                    continue;
                }

                if (line.startsWith(HUDPOS_PREFIX + "\t")) {
                    loadHudPosLine(line);
                    continue;
                }

                loadLegacyModuleLine(line);
            }
        } catch (IOException ignored) {
        } finally {
            loadingConfig = false;
        }
    }

    private void loadHudPosLine(String line) {
        try {
            String[] parts = line.split("\t");
            if (parts.length < 4) return;
            com.zenya.module.modules.client.Hud.HudElement el = com.zenya.module.modules.client.Hud.HudElement.valueOf(parts[1]);
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            com.zenya.module.modules.client.Hud.setElementPos(el, x, y);
        } catch (Exception ignored) {
        }
    }

    public boolean isLoadingConfig() {
        return loadingConfig;
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModulesInCategory(Category category) {
        List<Module> categoryModules = modulesByCategory.get(category);
        return categoryModules == null ? List.of() : categoryModules;
    }

    public Module getModuleByName(String name) {
        String searchName = name;
        if (name.equalsIgnoreCase("Zenya+") || name.equalsIgnoreCase("Xenon")) {
            searchName = "Frost+";
        }
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(searchName)) {
                return module;
            }
        }
        return null;
    }

    public void onTick() {
        final List<Module> mods = modules;
        final int n = mods.size();
        for (int i = 0; i < n; i++) {
            Module module = mods.get(i);
            if (module.isEnabled()) {
                try {
                    module.onTick();
                } catch (Throwable t) {
                    com.zenya.ZenyaClient.LOGGER.warn("Disabling module {} after tick failure", module.getName(), t);
                    module.setEnabled(false);
                }
            }
        }
    }

    public void onRender(MatrixStack matrices, float tickDelta) {
        final List<Module> mods = modules;
        final int n = mods.size();
        for (int i = 0; i < n; i++) {
            Module module = mods.get(i);
            if (module.isEnabled()) {
                try {
                    module.onRender(matrices, tickDelta);
                } catch (Throwable t) {
                    com.zenya.ZenyaClient.LOGGER.warn("Disabling module {} after render failure", module.getName(), t);
                    module.setEnabled(false);
                }
            }
        }
    }

    public void onWorldChange() {
        final List<Module> mods = modules;
        final int n = mods.size();
        for (int i = 0; i < n; i++) {
            Module module = mods.get(i);
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onWorldChange();
            } catch (Throwable t) {
                com.zenya.ZenyaClient.LOGGER.warn("Disabling module {} after world-change failure", module.getName(), t);
                module.setEnabled(false);
            }
        }
    }

    public void onPacketReceive(net.minecraft.network.packet.Packet<?> packet) {
        final List<Module> mods = modules;
        final int n = mods.size();
        for (int i = 0; i < n; i++) {
            Module module = mods.get(i);
            if (module.isEnabled()) {
                try {
                    module.onPacketReceive(packet);
                } catch (Throwable t) {
                    com.zenya.ZenyaClient.LOGGER.warn("Disabling module {} after packet receive failure", module.getName(), t);
                    module.setEnabled(false);
                }
            }
        }
    }

    public boolean onPacketSend(net.minecraft.network.packet.Packet<?> packet) {
        boolean cancel = false;
        final List<Module> mods = modules;
        final int n = mods.size();
        for (int i = 0; i < n; i++) {
            Module module = mods.get(i);
            if (!module.isEnabled()) {
                continue;
            }
            try {
                cancel |= module.onPacketSend(packet);
            } catch (Exception ignored) {
            }
        }
        return cancel;
    }

    private Path getConfigPath() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("zenya_config.txt");
    }

    private void loadModuleLine(String line) {
        String[] parts = line.split("\t", 5);
        if (parts.length < 5) {
            return;
        }

        Module module = getModuleByName(decode(parts[1]));
        if (module == null) {
            return;
        }

        try {
            module.applyBind(Integer.parseInt(parts[2]));
            if (module instanceof ActivatableModule activatableModule) {
                activatableModule.applyActivationKey(Integer.parseInt(parts[3]));
            }
            module.applyEnabled(Boolean.parseBoolean(parts[4]));
        } catch (Exception ignored) {
        }
    }

    private void loadSettingLine(String line) {
        String[] parts = line.split("\t", 4);
        if (parts.length < 4) {
            return;
        }

        Module module = getModuleByName(decode(parts[1]));
        if (module == null) {
            return;
        }

        Setting<?> setting = getSettingByName(module, decode(parts[2]));
        if (setting == null) {
            return;
        }

        applySettingValue(setting, decode(parts[3]));
    }

    private void loadLegacyModuleLine(String line) {
        String[] parts = line.split(":", 4);
        if (parts.length < 2) {
            return;
        }

        Module module = getModuleByName(parts[0]);
        if (module == null) {
            return;
        }

        try {
            if (parts.length >= 2) {
                module.applyBind(Integer.parseInt(parts[1]));
            }
            if (parts.length >= 3) {
                if (module instanceof ActivatableModule activatableModule) {
                    activatableModule.applyActivationKey(Integer.parseInt(parts[2]));
                }
            }
            if (parts.length >= 4) {
                module.applyEnabled(Boolean.parseBoolean(parts[3]));
            }
        } catch (Exception ignored) {
        }
    }

    private Setting<?> getSettingByName(Module module, String settingName) {
        for (Setting<?> setting : module.getSettings()) {
            if (setting.matchesName(settingName)) {
                return setting;
            }
        }
        return null;
    }

    private String serializeSettingValue(Setting<?> setting) {
        Object value = setting.getValue();
        if (setting instanceof BlocksSetting blocksSetting) {
            return serializeBlocks(blocksSetting);
        }
        if (setting instanceof MobsSetting mobsSetting) {
            return serializeMobs(mobsSetting);
        }
        if (setting instanceof com.zenya.setting.StorageBlocksSetting sbs) {
            return serializeStorageBlocks(sbs);
        }
        if (value instanceof Boolean boolValue) {
            return Boolean.toString(boolValue);
        }
        if (value instanceof Float floatValue) {
            return Float.toString(floatValue);
        }
        if (value instanceof Integer intValue) {
            return Integer.toString(intValue);
        }
        if (value instanceof Double doubleValue) {
            return Double.toString(doubleValue);
        }
        if (value instanceof String stringValue) {
            return stringValue;
        }
        if (value instanceof Color colorValue) {
            return colorValue.getRed() + "," + colorValue.getGreen() + "," + colorValue.getBlue() + "," + colorValue.getAlpha();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void applySettingValue(Setting<?> setting, String serialized) {
        Object value = setting.getValue();

        try {
            if (setting instanceof BlocksSetting blocksSetting) {
                deserializeBlocks(blocksSetting, serialized);
                return;
            }
            if (setting instanceof MobsSetting mobsSetting) {
                mobsSetting.setValue(deserializeMobs(serialized));
                return;
            }
            if (setting instanceof com.zenya.setting.StorageBlocksSetting sbs) {
                deserializeStorageBlocks(sbs, serialized);
                return;
            }
            if (value instanceof Boolean) {
                ((Setting<Boolean>) setting).setValue(Boolean.parseBoolean(serialized));
                return;
            }
            if (value instanceof Float) {
                ((Setting<Float>) setting).setValue(Float.parseFloat(serialized));
                return;
            }
            if (value instanceof Integer) {
                int parsed = Math.round(Float.parseFloat(serialized));
                ((Setting<Integer>) setting).setValue(parsed);
                return;
            }
            if (value instanceof Double) {
                ((Setting<Double>) setting).setValue(Double.parseDouble(serialized));
                return;
            }
            if (value instanceof String) {
                ((Setting<String>) setting).setValue(serialized);
                return;
            }
            if (value instanceof Color) {
                String[] colorParts = serialized.split(",", 4);
                if (colorParts.length == 4) {
                    Color color = new Color(
                            Integer.parseInt(colorParts[0]),
                            Integer.parseInt(colorParts[1]),
                            Integer.parseInt(colorParts[2]),
                            Integer.parseInt(colorParts[3])
                    );
                    ((Setting<Color>) setting).setValue(color);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void applyDeferredSetting(Module module, String settingName, String serializedValue) {
        Setting<?> setting = getSettingByName(module, settingName);
        if (setting != null) {
            applySettingValue(setting, serializedValue);
        }
    }

    private String serializeBlocks(BlocksSetting setting) {
        StringBuilder builder = new StringBuilder();
        for (Block block : setting.getSelectedBlocks()) {
            Identifier id = Registries.BLOCK.getId(block);
            if (id == null) continue;
            if (!builder.isEmpty()) builder.append(',');
            builder.append(id);
        }
        builder.append('|');
        boolean first = true;
        for (java.util.Map.Entry<Block, Color> entry : setting.getColors().entrySet()) {
            Identifier id = Registries.BLOCK.getId(entry.getKey());
            if (id == null) continue;
            if (!first) builder.append(';');
            Color c = entry.getValue();
            builder.append(id).append(':')
                   .append(c.getRed()).append(',')
                   .append(c.getGreen()).append(',')
                   .append(c.getBlue()).append(',')
                   .append(c.getAlpha());
            first = false;
        }
        return builder.toString();
    }

    private void deserializeBlocks(BlocksSetting setting, String serialized) {
        if (serialized == null || serialized.isBlank()) return;
        int pipe = serialized.indexOf('|');
        String selectionPart = pipe < 0 ? serialized : serialized.substring(0, pipe);
        String colorPart = pipe < 0 ? "" : serialized.substring(pipe + 1);

        if (!colorPart.isBlank()) {
            for (String entry : colorPart.split(";")) {
                int colon = entry.indexOf(':');
                if (colon <= 0) continue;
                String blockId = entry.substring(0, colon).trim();
                String[] rgba = entry.substring(colon + 1).split(",");
                if (rgba.length < 4) continue;
                try {
                    Block block = Registries.BLOCK.get(Identifier.tryParse(blockId));
                    if (block != null) {
                        setting.setColor(block, new Color(
                                Integer.parseInt(rgba[0].trim()),
                                Integer.parseInt(rgba[1].trim()),
                                Integer.parseInt(rgba[2].trim()),
                                Integer.parseInt(rgba[3].trim())));
                    }
                } catch (Exception ignored) {}
            }
        }

        LinkedHashSet<Block> selection = new LinkedHashSet<>();
        if (!selectionPart.isBlank()) {
            for (String rawId : selectionPart.split(",")) {
                Block b = Registries.BLOCK.get(Identifier.tryParse(rawId.trim()));
                if (b != null) selection.add(b);
            }
        }
        setting.setValue(selection);
    }

    private String serializeMobs(MobsSetting setting) {
        StringBuilder builder = new StringBuilder();
        for (EntityType<?> type : setting.getSelectedMobs()) {
            Identifier id = Registries.ENTITY_TYPE.getId(type);
            if (id == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(',');
            }
            builder.append(id);
        }
        return builder.toString();
    }

    private Set<EntityType<?>> deserializeMobs(String serialized) {
        LinkedHashSet<EntityType<?>> mobs = new LinkedHashSet<>();
        if (serialized == null || serialized.isBlank()) {
            return mobs;
        }

        for (String rawId : serialized.split(",")) {
            String mobId = rawId.trim();
            if (mobId.isEmpty()) {
                continue;
            }

            Identifier identifier = Identifier.tryParse(mobId);
            if (identifier == null) {
                continue;
            }

            EntityType<?> type = Registries.ENTITY_TYPE.get(identifier);
            if (type != null) {
                mobs.add(type);
            }
        }

        return mobs;
    }

    private String serializeStorageBlocks(com.zenya.setting.StorageBlocksSetting setting) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String key : setting.getSelected()) {
            if (!first) sb.append(',');
            sb.append(key);
            first = false;
        }
        sb.append('|');
        first = true;
        for (java.util.Map.Entry<String, Color> entry : setting.getColorsSnapshot().entrySet()) {
            if (!first) sb.append(';');
            Color c = entry.getValue();
            sb.append(entry.getKey()).append(':')
              .append(c.getRed()).append(',')
              .append(c.getGreen()).append(',')
              .append(c.getBlue()).append(',')
              .append(c.getAlpha());
            first = false;
        }
        return sb.toString();
    }

    private void deserializeStorageBlocks(com.zenya.setting.StorageBlocksSetting setting, String serialized) {
        if (serialized == null) return;
        int pipe = serialized.indexOf('|');
        String selectedPart = pipe < 0 ? serialized : serialized.substring(0, pipe);
        String colorsPart = pipe < 0 ? "" : serialized.substring(pipe + 1);

        if (!colorsPart.isBlank()) {
            java.util.LinkedHashMap<String, Color> loaded = new java.util.LinkedHashMap<>();
            for (String entry : colorsPart.split(";")) {
                int colon = entry.indexOf(':');
                if (colon <= 0) continue;
                String key = entry.substring(0, colon).trim();
                String[] parts = entry.substring(colon + 1).split(",", 4);
                if (parts.length < 4) continue;
                try {
                    loaded.put(key, new Color(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()),
                            Integer.parseInt(parts[3].trim())));
                } catch (NumberFormatException ignored) {}
            }
            setting.restoreColors(loaded);
        }

        java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>();
        if (!selectedPart.isBlank()) {
            for (String key : selectedPart.split(",")) {
                String trimmed = key.trim();
                if (!trimmed.isEmpty() && setting.findEntry(trimmed) != null) {
                    selected.add(trimmed);
                }
            }
        }
        setting.setValue(selected);
    }

    private void rebuildCategoryCache() {
        modulesByCategory.clear();
        for (Category category : Category.values()) {
            modulesByCategory.put(category, new ArrayList<>());
        }
        for (Module module : modules) {
            modulesByCategory.computeIfAbsent(module.getCategory(), ignored -> new ArrayList<>()).add(module);
        }
        for (Category category : Category.values()) {
            modulesByCategory.put(category, Collections.unmodifiableList(new ArrayList<>(modulesByCategory.get(category))));
        }
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }
}
