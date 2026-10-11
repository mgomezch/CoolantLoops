package com.gtnewhorizons.coolantloops.client.renderer;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.gtnewhorizons.coolantloops.common.block.BlockManifold;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;

import codechicken.lib.vec.Rotation;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.GTMod;
import gregtech.api.GregTechAPI;
import gregtech.api.util.GTUtility;
import gregtech.common.config.Client;

/**
 * Renders the GregTech-style 3x3 wrench interaction overlay grid and connection indicators
 * for Coolant Loop Manifolds, and triggers render updates when the soldering iron occlusion
 * transparency state toggles.
 */
@SideOnly(Side.CLIENT)
public class ManifoldHighlightRenderer {

    private static final int[][] GRID_SWITCH_TABLE = new int[][] { { 0, 5, 3, 1, 2, 4 }, // DOWN
        { 5, 0, 1, 3, 2, 4 }, // UP
        { 1, 3, 0, 5, 2, 4 }, // NORTH
        { 3, 1, 5, 0, 2, 4 }, // SOUTH
        { 4, 2, 3, 1, 0, 5 }, // WEST
        { 2, 4, 3, 1, 5, 0 }, // EAST
    };

    private boolean lastHideThings = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        boolean hideThings = GTMod.clientProxy()
            .shouldHideThings();
        if (hideThings != lastHideThings) {
            lastHideThings = hideThings;
            int px = MathHelper.floor_double(mc.thePlayer.posX);
            int py = MathHelper.floor_double(mc.thePlayer.posY);
            int pz = MathHelper.floor_double(mc.thePlayer.posZ);
            mc.theWorld.markBlockRangeForRenderUpdate(px - 64, py - 64, pz - 64, px + 64, py + 64, pz + 64);
        }
    }

    @SubscribeEvent
    public void onDrawBlockHighlight(DrawBlockHighlightEvent event) {
        if (event.target == null || event.target.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }

        final World world = event.player.worldObj;
        final Block block = world.getBlock(event.target.blockX, event.target.blockY, event.target.blockZ);
        if (!(block instanceof BlockManifold)) {
            return;
        }

        final TileEntity te = world.getTileEntity(event.target.blockX, event.target.blockY, event.target.blockZ);
        if (!(te instanceof TileEntityManifold manifold)) {
            return;
        }

        if (GTUtility.isStackInList(event.currentItem, GregTechAPI.sWrenchList)) {
            drawManifoldGrid(event, block, manifold);
        }
    }

    private static void drawManifoldGrid(DrawBlockHighlightEvent event, Block block, TileEntityManifold manifold) {
        event.setCanceled(true);

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);

        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(0);
        GL11.glLineWidth(calculateLineWidth());

        MovingObjectPosition target = event.target;
        EntityPlayer player = event.player;
        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double) event.partialTicks;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double) event.partialTicks;
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double) event.partialTicks;

        int red = Client.blockoverlay.red;
        int green = Client.blockoverlay.green;
        int blue = Client.blockoverlay.blue;
        int alpha = Client.blockoverlay.alpha;

        final World world = player.worldObj;

        // Draw outer selection bounding box
        if (block.getMaterial() != Material.air) {
            block.setBlockBoundsBasedOnState(world, target.blockX, target.blockY, target.blockZ);
            AxisAlignedBB box = block
                .getSelectedBoundingBoxFromPool(world, target.blockX, target.blockY, target.blockZ);
            box = box.expand(0.002D, 0.002D, 0.002D);
            box = box.getOffsetBoundingBox(-camX, -camY, -camZ);

            Tessellator tess = Tessellator.instance;
            tess.startDrawing(GL11.GL_LINE_STRIP);
            tess.setColorRGBA(red, green, blue, alpha);
            tess.addVertex(box.minX, box.minY, box.minZ);
            tess.addVertex(box.maxX, box.minY, box.minZ);
            tess.addVertex(box.maxX, box.minY, box.maxZ);
            tess.addVertex(box.minX, box.minY, box.maxZ);
            tess.addVertex(box.minX, box.minY, box.minZ);
            tess.draw();

            tess.startDrawing(GL11.GL_LINE_STRIP);
            tess.setColorRGBA(red, green, blue, alpha);
            tess.addVertex(box.minX, box.maxY, box.minZ);
            tess.addVertex(box.maxX, box.maxY, box.minZ);
            tess.addVertex(box.maxX, box.maxY, box.maxZ);
            tess.addVertex(box.minX, box.maxY, box.maxZ);
            tess.addVertex(box.minX, box.maxY, box.minZ);
            tess.draw();

            tess.startDrawing(GL11.GL_LINES);
            tess.setColorRGBA(red, green, blue, alpha);
            tess.addVertex(box.minX, box.minY, box.minZ);
            tess.addVertex(box.minX, box.maxY, box.minZ);
            tess.addVertex(box.maxX, box.minY, box.minZ);
            tess.addVertex(box.maxX, box.maxY, box.minZ);
            tess.addVertex(box.maxX, box.minY, box.maxZ);
            tess.addVertex(box.maxX, box.maxY, box.maxZ);
            tess.addVertex(box.minX, box.minY, box.maxZ);
            tess.addVertex(box.minX, box.maxY, box.maxZ);
            tess.draw();
        }

        // Translate to face center and rotate
        GL11.glTranslated(target.blockX - (int) camX, target.blockY - (int) camY, target.blockZ - (int) camZ);
        GL11.glTranslated(0.5D - (camX - (int) camX), 0.5D - (camY - (int) camY), 0.5D - (camZ - (int) camZ));
        final int tSideHit = target.sideHit;
        Rotation.sideRotations[tSideHit].glApply();
        GL11.glTranslated(0.0D, -0.502D, 0.0D);

        // Draw 3x3 grid lines
        final Tessellator tess = Tessellator.instance;
        tess.startDrawing(GL11.GL_LINES);
        tess.setColorRGBA(red, green, blue, alpha);
        tess.addVertex(+.50D, .0D, -.25D);
        tess.addVertex(-.50D, .0D, -.25D);
        tess.addVertex(+.50D, .0D, +.25D);
        tess.addVertex(-.50D, .0D, +.25D);
        tess.addVertex(+.25D, .0D, -.50D);
        tess.addVertex(+.25D, .0D, +.50D);
        tess.addVertex(-.25D, .0D, -.50D);
        tess.addVertex(-.25D, .0D, +.50D);

        // Draw connection indicators
        int tConnections = manifold.getConnectionsMask() & 0x3F;
        if (tConnections != 0) {
            for (ForgeDirection tSide : ForgeDirection.VALID_DIRECTIONS) {
                if ((tConnections & (1 << tSide.ordinal())) != 0) {
                    switch (GRID_SWITCH_TABLE[target.sideHit][tSide.ordinal()]) {
                        case 0 -> {
                            // Center
                            tess.addVertex(+.25D, .0D, +.25D);
                            tess.addVertex(-.25D, .0D, -.25D);
                            tess.addVertex(-.25D, .0D, +.25D);
                            tess.addVertex(+.25D, .0D, -.25D);
                        }
                        case 1 -> {
                            // Bottom edge
                            tess.addVertex(-.25D, .0D, +.50D);
                            tess.addVertex(+.25D, .0D, +.25D);
                            tess.addVertex(-.25D, .0D, +.25D);
                            tess.addVertex(+.25D, .0D, +.50D);
                        }
                        case 2 -> {
                            // Left edge
                            tess.addVertex(-.50D, .0D, -.25D);
                            tess.addVertex(-.25D, .0D, +.25D);
                            tess.addVertex(-.50D, .0D, +.25D);
                            tess.addVertex(-.25D, .0D, -.25D);
                        }
                        case 3 -> {
                            // Top edge
                            tess.addVertex(-.25D, .0D, -.50D);
                            tess.addVertex(+.25D, .0D, -.25D);
                            tess.addVertex(-.25D, .0D, -.25D);
                            tess.addVertex(+.25D, .0D, -.50D);
                        }
                        case 4 -> {
                            // Right edge
                            tess.addVertex(+.50D, .0D, -.25D);
                            tess.addVertex(+.25D, .0D, +.25D);
                            tess.addVertex(+.50D, .0D, +.25D);
                            tess.addVertex(+.25D, .0D, -.25D);
                        }
                        case 5 -> {
                            // All 4 corners (opposite side)
                            tess.addVertex(+.50D, .0D, +.50D);
                            tess.addVertex(+.25D, .0D, +.25D);
                            tess.addVertex(+.50D, .0D, +.25D);
                            tess.addVertex(+.25D, .0D, +.50D);

                            tess.addVertex(+.50D, .0D, -.50D);
                            tess.addVertex(+.25D, .0D, -.25D);
                            tess.addVertex(+.50D, .0D, -.25D);
                            tess.addVertex(+.25D, .0D, -.50D);

                            tess.addVertex(-.50D, .0D, +.50D);
                            tess.addVertex(-.25D, .0D, +.25D);
                            tess.addVertex(-.50D, .0D, +.25D);
                            tess.addVertex(-.25D, .0D, +.50D);

                            tess.addVertex(-.50D, .0D, -.50D);
                            tess.addVertex(-.25D, .0D, -.25D);
                            tess.addVertex(-.50D, .0D, -.25D);
                            tess.addVertex(-.25D, .0D, -.50D);
                        }
                    }
                }
            }
        }
        tess.draw();

        GL20.glUseProgram(program);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static float calculateLineWidth() {
        final float baseHeight = 1080F;
        return Client.blockoverlay.lineWidth * (Minecraft.getMinecraft().displayHeight / baseHeight);
    }
}
