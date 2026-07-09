package frost.loader.mod;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * FROST MIXIN PLUGIN
 *
 * Gates mixin application: all mixins in this mod are the REAL
 * Frost Client mixin implementations (from the Frost Client source).
 * They are always applied — the auth check happens in the ClientModInitializer.
 *
 * If auth fails, ZenyaClient.onInitializeClient() is never called so
 * modules/features never initialize, even though the mixin hooks are wired.
 * This is intentional: the mixin hooks are inert without module state.
 */
public class FrostMixinPlugin implements IMixinConfigPlugin {

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) { return true; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, org.objectweb.asm.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
