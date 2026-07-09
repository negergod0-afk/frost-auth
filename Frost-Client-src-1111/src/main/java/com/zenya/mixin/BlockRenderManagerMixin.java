package com.zenya.mixin;

import com.zenya.module.modules.render.NoRender;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockRenderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockRenderManager.class)
public class BlockRenderManagerMixin {

    // Chunk meshing in 1.21+ calls getModel(BlockState) to look up the baked model per block,
    // then renders that model at the block's world position. Swap the input state here and
    // the chunk mesher bakes the disguised model in place of the original. This is the path
    // that actually controls visual chunk geometry.
    @ModifyVariable(method = "getModel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockState zenya$antileakSwapModel(BlockState original) {
        return NoRender.filterBlockState(original);
    }

    // Also catch the direct render path (item frames, world preview, etc) so the disguise
    // looks consistent everywhere blocks show up.
    @ModifyVariable(method = "renderBlock", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private BlockState zenya$antileakSwapRender(BlockState original) {
        return NoRender.filterBlockState(original);
    }
}
