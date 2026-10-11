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
        if (gregtech.GTMod.GT.isClientSide() && gregtech.GTMod.clientProxy()
            .shouldHideThings()) {
            final float t = 0.0625F;
            final float min = (1.0F - t) / 2.0F;
            final float max = 1.0F - min;

            // Center small hub
            renderer.setRenderBounds(min, min, min, max, max, max);
            renderer.renderStandardBlock(block, x, y, z);

            // Thin wire branches to connected sides
            if (manifold.isConnected(net.minecraftforge.common.util.ForgeDirection.WEST)) {
                renderer.setRenderBounds(0.0F, min, min, min, max, max);
                renderer.renderStandardBlock(block, x, y, z);
            }
            if (manifold.isConnected(net.minecraftforge.common.util.ForgeDirection.EAST)) {
                renderer.setRenderBounds(max, min, min, 1.0F, max, max);
                renderer.renderStandardBlock(block, x, y, z);
            }
            if (manifold.isConnected(net.minecraftforge.common.util.ForgeDirection.DOWN)) {
                renderer.setRenderBounds(min, 0.0F, min, max, min, max);
                renderer.renderStandardBlock(block, x, y, z);
            }
            if (manifold.isConnected(net.minecraftforge.common.util.ForgeDirection.UP)) {
                renderer.setRenderBounds(min, max, min, max, 1.0F, max);
                renderer.renderStandardBlock(block, x, y, z);
            }
            if (manifold.isConnected(net.minecraftforge.common.util.ForgeDirection.NORTH)) {
                renderer.setRenderBounds(min, min, 0.0F, max, max, min);
                renderer.renderStandardBlock(block, x, y, z);
            }
            if (manifold.isConnected(net.minecraftforge.common.util.ForgeDirection.SOUTH)) {
                renderer.setRenderBounds(min, min, max, max, max, 1.0F);
                renderer.renderStandardBlock(block, x, y, z);
            }
            return true;
        }

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
