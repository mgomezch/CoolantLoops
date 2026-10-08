package com.gtnewhorizons.coolantloops.client.renderer;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;

import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class RenderBlockManifold implements ISimpleBlockRenderingHandler {

    public static int renderID = -1;

    public static void init() {
        renderID = RenderingRegistry.getNextAvailableRenderId();
        RenderingRegistry.registerBlockHandler(new RenderBlockManifold());
    }

    @Override
    public int getRenderId() {
        return renderID;
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public boolean renderWorldBlock(IBlockAccess world, int x, int y, int z, Block block, int modelId,
        RenderBlocks renderer) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityManifold)) {
            renderer.setRenderBounds(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
            return renderer.renderStandardBlock(block, x, y, z);
        }

        TileEntityManifold manifold = (TileEntityManifold) te;
        List<AxisAlignedBB> boxes = manifold.getComponentBoundingBoxes(0, 0, 0);
        for (AxisAlignedBB box : boxes) {
            renderer.setRenderBounds(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            renderer.renderStandardBlock(block, x, y, z);
        }
        return true;
    }

    @Override
    public void renderInventoryBlock(Block block, int metadata, int modelID, RenderBlocks renderer) {
        Tessellator tessellator = Tessellator.instance;

        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // Central plate in XY plane (Z normal)
        renderer.setRenderBounds(0.125, 0.125, 0.3125, 0.875, 0.875, 0.6875);
        renderInvBox(renderer, block, metadata, tessellator);

        // Input/output nozzles along Z normal (smaller cuboids)
        renderer.setRenderBounds(0.25, 0.25, 0.0, 0.75, 0.75, 0.3125);
        renderInvBox(renderer, block, metadata, tessellator);

        renderer.setRenderBounds(0.25, 0.25, 0.6875, 0.75, 0.75, 1.0);
        renderInvBox(renderer, block, metadata, tessellator);

        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
        block.setBlockBoundsForItemRender();
    }

    private static void renderInvBox(RenderBlocks renderer, Block block, int meta, Tessellator tessellator) {
        tessellator.startDrawingQuads();
        tessellator.setNormal(0.0F, -1.0F, 0.0F);
        renderer.renderFaceYNeg(block, 0.0D, 0.0D, 0.0D, block.getIcon(0, meta));
        tessellator.setNormal(0.0F, 1.0F, 0.0F);
        renderer.renderFaceYPos(block, 0.0D, 0.0D, 0.0D, block.getIcon(1, meta));
        tessellator.setNormal(0.0F, 0.0F, -1.0F);
        renderer.renderFaceZNeg(block, 0.0D, 0.0D, 0.0D, block.getIcon(2, meta));
        tessellator.setNormal(0.0F, 0.0F, 1.0F);
        renderer.renderFaceZPos(block, 0.0D, 0.0D, 0.0D, block.getIcon(3, meta));
        tessellator.setNormal(-1.0F, 0.0F, 0.0F);
        renderer.renderFaceXNeg(block, 0.0D, 0.0D, 0.0D, block.getIcon(4, meta));
        tessellator.setNormal(1.0F, 0.0F, 0.0F);
        renderer.renderFaceXPos(block, 0.0D, 0.0D, 0.0D, block.getIcon(5, meta));
        tessellator.draw();
    }
}
