package me.navoei.customdiscs.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import me.navoei.customdiscs.ItemData;
import me.navoei.customdiscs.ModelRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

@SideOnly(Side.CLIENT)
public final class CustomModelItemRenderer implements IItemRenderer {
    @Override
    public boolean handleRenderType(ItemStack stack, ItemRenderType type) {
        int value = ItemData.getModel(stack);
        if (value < 1) {
            return false;
        }
        String path = ModelRegistry.texturePath(stack, value);
        if (path == null) {
            return false;
        }
        try {
            Minecraft.getMinecraft().getResourceManager().getResource(
                    new ResourceLocation("customdiscs", path));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack stack, ItemRendererHelper helper) {
        return false;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack stack, Object... data) {
        int value = ItemData.getModel(stack);
        String path = ModelRegistry.texturePath(stack, value);
        if (path == null) {
            return;
        }
        TextureManager textures = Minecraft.getMinecraft().getTextureManager();
        textures.bindTexture(new ResourceLocation("customdiscs", path));
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(-0.5D, -0.5D, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(0.5D, -0.5D, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(0.5D, 0.5D, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(-0.5D, 0.5D, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
        GL11.glDisable(GL11.GL_BLEND);
    }
}
