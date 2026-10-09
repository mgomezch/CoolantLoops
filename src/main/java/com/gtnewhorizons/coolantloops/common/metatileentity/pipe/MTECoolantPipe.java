package com.gtnewhorizons.coolantloops.common.metatileentity.pipe;

import static gregtech.api.interfaces.metatileentity.IConnectable.CONNECTED_DOWN;
import static gregtech.api.interfaces.metatileentity.IConnectable.CONNECTED_EAST;
import static gregtech.api.interfaces.metatileentity.IConnectable.CONNECTED_NORTH;
import static gregtech.api.interfaces.metatileentity.IConnectable.CONNECTED_SOUTH;
import static gregtech.api.interfaces.metatileentity.IConnectable.CONNECTED_UP;
import static gregtech.api.interfaces.metatileentity.IConnectable.CONNECTED_WEST;
import static gregtech.api.interfaces.metatileentity.IConnectable.NO_CONNECTION;
import static net.minecraftforge.common.util.ForgeDirection.DOWN;
import static net.minecraftforge.common.util.ForgeDirection.EAST;
import static net.minecraftforge.common.util.ForgeDirection.NORTH;
import static net.minecraftforge.common.util.ForgeDirection.SOUTH;
import static net.minecraftforge.common.util.ForgeDirection.UP;
import static net.minecraftforge.common.util.ForgeDirection.WEST;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.coolantloops.engine.CoolantPipingRegistry;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaPipeEntity;
import gregtech.api.metatileentity.implementations.MTEFluidPipe;
import gregtech.api.render.ISBRContext;
import gregtech.api.render.ISBRInventoryContext;
import gregtech.api.render.ISBRWorldContext;
import gregtech.api.render.TextureFactory;

/**
 * Thermally Insulated Coolant Pipe for closed coolant loops.
 * Features beveled/cut-out corners along its 3D geometry and thermal insulation
 * cladding overlays to visually distinguish coolant pipes from regular GregTech pipes.
 */
public class MTECoolantPipe extends MTEFluidPipe {

    public static class CustomPipeIcon implements IIconContainer, Runnable {

        protected IIcon mIcon;
        protected final String mModID;
        protected final String mIconName;

        public CustomPipeIcon(final String aModID, final String aIconName) {
            this.mModID = aModID;
            this.mIconName = aIconName;
            GregTechAPI.sGTBlockIconload.add(this);
        }

        @Override
        public IIcon getIcon() {
            return this.mIcon;
        }

        @Override
        public IIcon getOverlayIcon() {
            return null;
        }

        @Override
        public net.minecraft.util.ResourceLocation getTextureFile() {
            return net.minecraft.client.renderer.texture.TextureMap.locationBlocksTexture;
        }

        @Override
        public void run() {
            this.mIcon = GregTechAPI.sBlockIcons.registerIcon(this.mModID + ":" + this.mIconName);
        }
    }

    public static final IIconContainer INSULATION_OVERLAY_ICON = new CustomPipeIcon("coolantloops", "pipe_insulation");

    private volatile ITexture[][] mInventoryTextureCache;

    public MTECoolantPipe(int aID, String aName, String aPrefixKey, float aThickNess, Materials aMaterial,
        int aCapacity, int aHeatResistance, boolean aGasProof) {
        super(aID, aName, aPrefixKey, aThickNess, aMaterial, aCapacity, aHeatResistance, aGasProof, 1);
    }

    public MTECoolantPipe(String aName, float aThickNess, Materials aMaterial, int aCapacity, int aHeatResistance,
        boolean aGasProof) {
        super(aName, aThickNess, aMaterial, aCapacity, aHeatResistance, aGasProof, 1);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECoolantPipe(mName, mThickNess, mMaterial, mCapacity, mHeatResistance, mGasProof);
    }

    @Override
    public boolean canConnect(ForgeDirection side, net.minecraft.tileentity.TileEntity tileEntity) {
        if (tileEntity == null) return false;
        if (tileEntity instanceof com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument inst) {
            // Coolant pipes cannot connect to the instrument's facing side (which is reserved for redstone output)
            return side.getOpposite() != inst.getFacing();
        }
        if (tileEntity instanceof com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice
            || tileEntity instanceof com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold) {
            return true;
        }
        if (tileEntity instanceof IGregTechTileEntity gte) {
            IMetaTileEntity mte = gte.getMetaTileEntity();
            if (mte instanceof com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid
                || mte instanceof com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice) {
                return true;
            }
        }
        return super.canConnect(side, tileEntity);
    }

    public String getSizeName() {
        if (mThickNess <= 0.251f) {
            return "Tiny";
        } else if (mThickNess <= 0.376f) {
            return "Small";
        } else if (mThickNess <= 0.501f) {
            return "Medium";
        } else if (mThickNess <= 0.751f) {
            return "Large";
        } else {
            return "Huge";
        }
    }

    @Override
    public String getLocalizedName() {
        String matName = mMaterial != null ? mMaterial.mDefaultLocalName : "Unknown";
        return String.format("%s %s Coolant Pipe", getSizeName(), matName);
    }

    @Override
    public String[] getDescription() {
        List<String> list = new ArrayList<>();
        list.add(
            EnumChatFormatting.AQUA + com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .get("gt.pipe.coolant.desc", "Thermally insulated coolant loop pipe"));
        String[] superDesc = super.getDescription();
        if (superDesc != null) {
            list.addAll(Arrays.asList(superDesc));
        }
        double maxP = CoolantPipingRegistry.getMaxPressureBar(mMaterial);
        double maxT = CoolantPipingRegistry.getMaxTemperatureCelsius(this);
        double diamCm = CoolantPipingRegistry.getDiameterForThickness(mThickNess) * 100.0;
        list.add(
            EnumChatFormatting.GRAY + com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.pipe.coolant.specs.diameter", diamCm));
        list.add(
            EnumChatFormatting.GRAY + com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.pipe.coolant.specs.pressure", maxP));
        list.add(
            EnumChatFormatting.GRAY + com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                .format("gt.pipe.coolant.specs.temperature", maxT));
        return list.toArray(new String[0]);
    }

    /**
     * Calculates the corner cut-out/bezel dimension proportional to pipe thickness.
     */
    public static float getBezelSize(float thickness) {
        return Math.max(0.02F, Math.min(0.0625F, thickness * 0.125F));
    }

    /**
     * Returns the 3 non-overlapping sub-boxes forming the beveled/cut-out cross-section along the X axis.
     */
    public static List<AxisAlignedBB> getBeveledSubBoxesX(float xMin, float xMax, float pipeMin, float pipeMax,
        float b) {
        List<AxisAlignedBB> boxes = new ArrayList<>(3);
        // Center horizontal bar (spans full Z, height T - 2b)
        boxes.add(AxisAlignedBB.getBoundingBox(xMin, pipeMin + b, pipeMin, xMax, pipeMax - b, pipeMax));
        // Top bar (width T - 2b in Z, height b)
        boxes.add(AxisAlignedBB.getBoundingBox(xMin, pipeMax - b, pipeMin + b, xMax, pipeMax, pipeMax - b));
        // Bottom bar (width T - 2b in Z, height b)
        boxes.add(AxisAlignedBB.getBoundingBox(xMin, pipeMin, pipeMin + b, xMax, pipeMin + b, pipeMax - b));
        return boxes;
    }

    /**
     * Returns the 3 non-overlapping sub-boxes forming the beveled/cut-out cross-section along the Y axis.
     */
    public static List<AxisAlignedBB> getBeveledSubBoxesY(float yMin, float yMax, float pipeMin, float pipeMax,
        float b) {
        List<AxisAlignedBB> boxes = new ArrayList<>(3);
        // Center bar (spans full Z in XZ plane, width T - 2b in X)
        boxes.add(AxisAlignedBB.getBoundingBox(pipeMin + b, yMin, pipeMin, pipeMax - b, yMax, pipeMax));
        // East bar (width b in X, height T - 2b in Z)
        boxes.add(AxisAlignedBB.getBoundingBox(pipeMax - b, yMin, pipeMin + b, pipeMax, yMax, pipeMax - b));
        // West bar (width b in X, height T - 2b in Z)
        boxes.add(AxisAlignedBB.getBoundingBox(pipeMin, yMin, pipeMin + b, pipeMin + b, yMax, pipeMax - b));
        return boxes;
    }

    /**
     * Returns the 3 non-overlapping sub-boxes forming the beveled/cut-out cross-section along the Z axis.
     */
    public static List<AxisAlignedBB> getBeveledSubBoxesZ(float zMin, float zMax, float pipeMin, float pipeMax,
        float b) {
        List<AxisAlignedBB> boxes = new ArrayList<>(3);
        // Center bar (spans full X in XY plane, height T - 2b in Y)
        boxes.add(AxisAlignedBB.getBoundingBox(pipeMin, pipeMin + b, zMin, pipeMax, pipeMax - b, zMax));
        // Top bar (width T - 2b in X, height b in Y)
        boxes.add(AxisAlignedBB.getBoundingBox(pipeMin + b, pipeMax - b, zMin, pipeMax - b, pipeMax, zMax));
        // Bottom bar (width T - 2b in X, height b in Y)
        boxes.add(AxisAlignedBB.getBoundingBox(pipeMin + b, pipeMin, zMin, pipeMax - b, pipeMin + b, zMax));
        return boxes;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity base, ForgeDirection side, int connections, int colorIndex,
        boolean connected, boolean redstoneLevel) {
        ITexture[] baseTextures = super.getTexture(base, side, connections, colorIndex, connected, redstoneLevel);
        ITexture insulation = getInsulationOverlay();
        if (insulation != null && baseTextures != null && baseTextures.length > 0) {
            ITexture[] result = new ITexture[baseTextures.length + 1];
            result[0] = baseTextures[0];
            result[1] = insulation;
            System.arraycopy(baseTextures, 1, result, 2, baseTextures.length - 1);
            return result;
        }
        return baseTextures;
    }

    protected ITexture getInsulationOverlay() {
        try {
            if (INSULATION_OVERLAY_ICON == null) return null;
            return TextureFactory.of(INSULATION_OVERLAY_ICON);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean renderInInventory(ISBRInventoryContext ctx) {
        final float tThickness = getThickness();
        final float pipeMin = (1.0F - tThickness) / 2.0F;
        final float pipeMax = 1.0F - pipeMin;
        final float b = getBezelSize(tThickness);
        final RenderBlocks renderBlocks = ctx.getRenderBlocks();

        ITexture[][] textures = mInventoryTextureCache;
        if (textures == null) {
            final IGregTechTileEntity mte = getBaseMetaTileEntity();
            textures = new ITexture[][] { getTexture(mte, DOWN, (CONNECTED_WEST | CONNECTED_EAST), -1, false, false),
                getTexture(mte, WEST, (CONNECTED_WEST | CONNECTED_EAST), -1, true, false) };
            mInventoryTextureCache = textures;
        }
        final ITexture[] sideTexture = textures[0];
        final ITexture[] endTexture = textures[1];

        // Render straight X segment with beveled corners in inventory
        renderBeveledSegmentX(
            ctx,
            renderBlocks,
            0.0F,
            1.0F,
            pipeMin,
            pipeMax,
            b,
            sideTexture,
            sideTexture,
            sideTexture,
            sideTexture,
            endTexture,
            endTexture,
            true,
            true);
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean renderInWorld(ISBRWorldContext ctx) {
        BaseMetaPipeEntity rte = (BaseMetaPipeEntity) getBaseMetaTileEntity();
        if (rte == null) return false;

        final byte aConnections = rte.getConnections();
        final float thickness = getThickness();
        if (thickness >= 1.0F) {
            ctx.renderNegativeYFacing(rte.getTextureCovered(DOWN));
            ctx.renderPositiveYFacing(rte.getTextureCovered(UP));
            ctx.renderNegativeZFacing(rte.getTextureCovered(NORTH));
            ctx.renderPositiveZFacing(rte.getTextureCovered(SOUTH));
            ctx.renderNegativeXFacing(rte.getTextureCovered(WEST));
            ctx.renderPositiveXFacing(rte.getTextureCovered(EAST));
            return true;
        }

        final float pipeMin = (1.0F - thickness) / 2.0F;
        final float pipeMax = 1.0F - pipeMin;
        final float b = getBezelSize(thickness);

        final ITexture[] textureUp = rte.getTextureUncovered(UP);
        final ITexture[] textureDown = rte.getTextureUncovered(DOWN);
        final ITexture[] textureNorth = rte.getTextureUncovered(NORTH);
        final ITexture[] textureSouth = rte.getTextureUncovered(SOUTH);
        final ITexture[] textureWest = rte.getTextureUncovered(WEST);
        final ITexture[] textureEast = rte.getTextureUncovered(EAST);

        final RenderBlocks renderBlocks = ctx.getRenderBlocks();

        switch (aConnections) {
            case NO_CONNECTION -> {
                renderBeveledCenterJoint(
                    ctx,
                    renderBlocks,
                    pipeMin,
                    pipeMax,
                    b,
                    aConnections,
                    textureDown,
                    textureUp,
                    textureNorth,
                    textureSouth,
                    textureWest,
                    textureEast);
            }
            case CONNECTED_EAST | CONNECTED_WEST -> {
                renderBeveledSegmentX(
                    ctx,
                    renderBlocks,
                    0.0F,
                    1.0F,
                    pipeMin,
                    pipeMax,
                    b,
                    textureDown,
                    textureUp,
                    textureNorth,
                    textureSouth,
                    textureWest,
                    textureEast,
                    true,
                    true);
            }
            case CONNECTED_DOWN | CONNECTED_UP -> {
                renderBeveledSegmentY(
                    ctx,
                    renderBlocks,
                    0.0F,
                    1.0F,
                    pipeMin,
                    pipeMax,
                    b,
                    textureDown,
                    textureUp,
                    textureNorth,
                    textureSouth,
                    textureWest,
                    textureEast,
                    true,
                    true);
            }
            case CONNECTED_NORTH | CONNECTED_SOUTH -> {
                renderBeveledSegmentZ(
                    ctx,
                    renderBlocks,
                    0.0F,
                    1.0F,
                    pipeMin,
                    pipeMax,
                    b,
                    textureDown,
                    textureUp,
                    textureNorth,
                    textureSouth,
                    textureWest,
                    textureEast,
                    true,
                    true);
            }
            default -> {
                // West branch
                if ((aConnections & CONNECTED_WEST) != 0) {
                    renderBeveledSegmentX(
                        ctx,
                        renderBlocks,
                        0.0F,
                        pipeMin + b,
                        pipeMin,
                        pipeMax,
                        b,
                        textureDown,
                        textureUp,
                        textureNorth,
                        textureSouth,
                        textureWest,
                        textureEast,
                        true,
                        false);
                }
                // East branch
                if ((aConnections & CONNECTED_EAST) != 0) {
                    renderBeveledSegmentX(
                        ctx,
                        renderBlocks,
                        pipeMax - b,
                        1.0F,
                        pipeMin,
                        pipeMax,
                        b,
                        textureDown,
                        textureUp,
                        textureNorth,
                        textureSouth,
                        textureWest,
                        textureEast,
                        false,
                        true);
                }
                // Down branch
                if ((aConnections & CONNECTED_DOWN) != 0) {
                    renderBeveledSegmentY(
                        ctx,
                        renderBlocks,
                        0.0F,
                        pipeMin + b,
                        pipeMin,
                        pipeMax,
                        b,
                        textureDown,
                        textureUp,
                        textureNorth,
                        textureSouth,
                        textureWest,
                        textureEast,
                        true,
                        false);
                }
                // Up branch
                if ((aConnections & CONNECTED_UP) != 0) {
                    renderBeveledSegmentY(
                        ctx,
                        renderBlocks,
                        pipeMax - b,
                        1.0F,
                        pipeMin,
                        pipeMax,
                        b,
                        textureDown,
                        textureUp,
                        textureNorth,
                        textureSouth,
                        textureWest,
                        textureEast,
                        false,
                        true);
                }
                // North branch
                if ((aConnections & CONNECTED_NORTH) != 0) {
                    renderBeveledSegmentZ(
                        ctx,
                        renderBlocks,
                        0.0F,
                        pipeMin + b,
                        pipeMin,
                        pipeMax,
                        b,
                        textureDown,
                        textureUp,
                        textureNorth,
                        textureSouth,
                        textureWest,
                        textureEast,
                        true,
                        false);
                }
                // South branch
                if ((aConnections & CONNECTED_SOUTH) != 0) {
                    renderBeveledSegmentZ(
                        ctx,
                        renderBlocks,
                        pipeMax - b,
                        1.0F,
                        pipeMin,
                        pipeMax,
                        b,
                        textureDown,
                        textureUp,
                        textureNorth,
                        textureSouth,
                        textureWest,
                        textureEast,
                        false,
                        true);
                }

                // Center junction
                renderBeveledCenterJoint(
                    ctx,
                    renderBlocks,
                    pipeMin,
                    pipeMax,
                    b,
                    aConnections,
                    textureDown,
                    textureUp,
                    textureNorth,
                    textureSouth,
                    textureWest,
                    textureEast);
            }
        }

        renderCovers(ctx, renderBlocks, rte, aConnections, thickness, pipeMin, pipeMax);
        return true;
    }

    @SideOnly(Side.CLIENT)
    private static void renderBeveledSegmentX(ISBRContext ctx, RenderBlocks rb, float xMin, float xMax, float pipeMin,
        float pipeMax, float b, ITexture[] down, ITexture[] up, ITexture[] north, ITexture[] south, ITexture[] west,
        ITexture[] east, boolean renderWestEnd, boolean renderEastEnd) {
        // 1. Center horizontal bar
        rb.setRenderBounds(xMin, pipeMin + b, pipeMin, xMax, pipeMax - b, pipeMax);
        ctx.renderNegativeZFacing(north);
        ctx.renderPositiveZFacing(south);
        ctx.renderNegativeYFacing(down);
        ctx.renderPositiveYFacing(up);
        if (renderWestEnd) ctx.renderNegativeXFacing(west);
        if (renderEastEnd) ctx.renderPositiveXFacing(east);

        // 2. Top bar
        rb.setRenderBounds(xMin, pipeMax - b, pipeMin + b, xMax, pipeMax, pipeMax - b);
        ctx.renderPositiveYFacing(up);
        ctx.renderNegativeZFacing(north);
        ctx.renderPositiveZFacing(south);
        if (renderWestEnd) ctx.renderNegativeXFacing(west);
        if (renderEastEnd) ctx.renderPositiveXFacing(east);

        // 3. Bottom bar
        rb.setRenderBounds(xMin, pipeMin, pipeMin + b, xMax, pipeMin + b, pipeMax - b);
        ctx.renderNegativeYFacing(down);
        ctx.renderNegativeZFacing(north);
        ctx.renderPositiveZFacing(south);
        if (renderWestEnd) ctx.renderNegativeXFacing(west);
        if (renderEastEnd) ctx.renderPositiveXFacing(east);
    }

    @SideOnly(Side.CLIENT)
    private static void renderBeveledSegmentY(ISBRContext ctx, RenderBlocks rb, float yMin, float yMax, float pipeMin,
        float pipeMax, float b, ITexture[] down, ITexture[] up, ITexture[] north, ITexture[] south, ITexture[] west,
        ITexture[] east, boolean renderDownEnd, boolean renderUpEnd) {
        // 1. Center bar (spans full Z in XZ plane, width T - 2b in X)
        rb.setRenderBounds(pipeMin + b, yMin, pipeMin, pipeMax - b, yMax, pipeMax);
        ctx.renderNegativeZFacing(north);
        ctx.renderPositiveZFacing(south);
        ctx.renderNegativeXFacing(west);
        ctx.renderPositiveXFacing(east);
        if (renderDownEnd) ctx.renderNegativeYFacing(down);
        if (renderUpEnd) ctx.renderPositiveYFacing(up);

        // 2. East bar (width b in X, height T - 2b in Z)
        rb.setRenderBounds(pipeMax - b, yMin, pipeMin + b, pipeMax, yMax, pipeMax - b);
        ctx.renderPositiveXFacing(east);
        ctx.renderNegativeZFacing(north);
        ctx.renderPositiveZFacing(south);
        if (renderDownEnd) ctx.renderNegativeYFacing(down);
        if (renderUpEnd) ctx.renderPositiveYFacing(up);

        // 3. West bar (width b in X, height T - 2b in Z)
        rb.setRenderBounds(pipeMin, yMin, pipeMin + b, pipeMin + b, yMax, pipeMax - b);
        ctx.renderNegativeXFacing(west);
        ctx.renderNegativeZFacing(north);
        ctx.renderPositiveZFacing(south);
        if (renderDownEnd) ctx.renderNegativeYFacing(down);
        if (renderUpEnd) ctx.renderPositiveYFacing(up);
    }

    @SideOnly(Side.CLIENT)
    private static void renderBeveledSegmentZ(ISBRContext ctx, RenderBlocks rb, float zMin, float zMax, float pipeMin,
        float pipeMax, float b, ITexture[] down, ITexture[] up, ITexture[] north, ITexture[] south, ITexture[] west,
        ITexture[] east, boolean renderNorthEnd, boolean renderSouthEnd) {
        // 1. Center bar (spans full X in XY plane, height T - 2b in Y)
        rb.setRenderBounds(pipeMin, pipeMin + b, zMin, pipeMax, pipeMax - b, zMax);
        ctx.renderNegativeXFacing(west);
        ctx.renderPositiveXFacing(east);
        ctx.renderNegativeYFacing(down);
        ctx.renderPositiveYFacing(up);
        if (renderNorthEnd) ctx.renderNegativeZFacing(north);
        if (renderSouthEnd) ctx.renderPositiveZFacing(south);

        // 2. Top bar (width T - 2b in X, height b in Y)
        rb.setRenderBounds(pipeMin + b, pipeMax - b, zMin, pipeMax - b, pipeMax, zMax);
        ctx.renderPositiveYFacing(up);
        ctx.renderNegativeXFacing(west);
        ctx.renderPositiveXFacing(east);
        if (renderNorthEnd) ctx.renderNegativeZFacing(north);
        if (renderSouthEnd) ctx.renderPositiveZFacing(south);

        // 3. Bottom bar (width T - 2b in X, height b in Y)
        rb.setRenderBounds(pipeMin + b, pipeMin, zMin, pipeMax - b, pipeMin + b, zMax);
        ctx.renderNegativeYFacing(down);
        ctx.renderNegativeXFacing(west);
        ctx.renderPositiveXFacing(east);
        if (renderNorthEnd) ctx.renderNegativeZFacing(north);
        if (renderSouthEnd) ctx.renderPositiveZFacing(south);
    }

    @SideOnly(Side.CLIENT)
    private static void renderBeveledCenterJoint(ISBRContext ctx, RenderBlocks rb, float pipeMin, float pipeMax,
        float b, byte connections, ITexture[] down, ITexture[] up, ITexture[] north, ITexture[] south, ITexture[] west,
        ITexture[] east) {
        final float x0 = pipeMin, x1 = pipeMin + b, x2 = pipeMax - b, x3 = pipeMax;
        final float y0 = pipeMin, y1 = pipeMin + b, y2 = pipeMax - b, y3 = pipeMax;
        final float z0 = pipeMin, z1 = pipeMin + b, z2 = pipeMax - b, z3 = pipeMax;

        final boolean cDown = (connections & CONNECTED_DOWN) != 0;
        final boolean cUp = (connections & CONNECTED_UP) != 0;
        final boolean cNorth = (connections & CONNECTED_NORTH) != 0;
        final boolean cSouth = (connections & CONNECTED_SOUTH) != 0;
        final boolean cWest = (connections & CONNECTED_WEST) != 0;
        final boolean cEast = (connections & CONNECTED_EAST) != 0;

        // 1. Top Plate (Y in [y2, y3]) if not connected UP
        if (!cUp) {
            rb.setRenderBounds(x1, y2, z1, x2, y3, z2);
            ctx.renderPositiveYFacing(up);
            if (!cNorth) ctx.renderNegativeZFacing(north);
            if (!cSouth) ctx.renderPositiveZFacing(south);
            if (!cWest) ctx.renderNegativeXFacing(west);
            if (!cEast) ctx.renderPositiveXFacing(east);
        }

        // 2. Bottom Plate (Y in [y0, y1]) if not connected DOWN
        if (!cDown) {
            rb.setRenderBounds(x1, y0, z1, x2, y1, z2);
            ctx.renderNegativeYFacing(down);
            if (!cNorth) ctx.renderNegativeZFacing(north);
            if (!cSouth) ctx.renderPositiveZFacing(south);
            if (!cWest) ctx.renderNegativeXFacing(west);
            if (!cEast) ctx.renderPositiveXFacing(east);
        }

        // 3. North Plate (Z in [z0, z1]) if not connected NORTH
        if (!cNorth) {
            rb.setRenderBounds(x1, y1, z0, x2, y2, z1);
            ctx.renderNegativeZFacing(north);
            if (!cUp) ctx.renderPositiveYFacing(up);
            if (!cDown) ctx.renderNegativeYFacing(down);
            if (!cWest) ctx.renderNegativeXFacing(west);
            if (!cEast) ctx.renderPositiveXFacing(east);
        }

        // 4. South Plate (Z in [z2, z3]) if not connected SOUTH
        if (!cSouth) {
            rb.setRenderBounds(x1, y1, z2, x2, y2, z3);
            ctx.renderPositiveZFacing(south);
            if (!cUp) ctx.renderPositiveYFacing(up);
            if (!cDown) ctx.renderNegativeYFacing(down);
            if (!cWest) ctx.renderNegativeXFacing(west);
            if (!cEast) ctx.renderPositiveXFacing(east);
        }

        // 5. West Plate (X in [x0, x1]) if not connected WEST
        if (!cWest) {
            rb.setRenderBounds(x0, y1, z1, x1, y2, z2);
            ctx.renderNegativeXFacing(west);
            if (!cUp) ctx.renderPositiveYFacing(up);
            if (!cDown) ctx.renderNegativeYFacing(down);
            if (!cNorth) ctx.renderNegativeZFacing(north);
            if (!cSouth) ctx.renderPositiveZFacing(south);
        }

        // 6. East Plate (X in [x2, x3]) if not connected EAST
        if (!cEast) {
            rb.setRenderBounds(x2, y1, z1, x3, y2, z2);
            ctx.renderPositiveXFacing(east);
            if (!cUp) ctx.renderPositiveYFacing(up);
            if (!cDown) ctx.renderNegativeYFacing(down);
            if (!cNorth) ctx.renderNegativeZFacing(north);
            if (!cSouth) ctx.renderPositiveZFacing(south);
        }
    }

    @SideOnly(Side.CLIENT)
    private static void renderCovers(ISBRWorldContext ctx, RenderBlocks renderBlocks, BaseMetaPipeEntity rte,
        byte aConnections, float thickness, float pipeMin, float pipeMax) {
        final boolean hasCoverDown = rte.hasCoverAtSide(DOWN);
        final boolean hasCoverUp = rte.hasCoverAtSide(UP);
        final boolean hasCoverNorth = rte.hasCoverAtSide(NORTH);
        final boolean hasCoverSouth = rte.hasCoverAtSide(SOUTH);
        final boolean hasCoverWest = rte.hasCoverAtSide(WEST);
        final boolean hasCoverEast = rte.hasCoverAtSide(EAST);

        final float coverThickness = Math.min(1.0F / 8.0F, (1.0F - thickness) / 2.0F);
        final float coverInnerMin = coverThickness;
        final float coverInnerMax = 1.0F - coverThickness;

        if (hasCoverDown) {
            final ITexture[] coverTexture = rte.getTexture(DOWN);
            renderBlocks.setRenderBounds(0.0F, 0.0F, 0.0F, 1.0F, coverInnerMin, 1.0F);
            if (!hasCoverNorth) ctx.renderNegativeZFacing(coverTexture);
            if (!hasCoverSouth) ctx.renderPositiveZFacing(coverTexture);
            if (!hasCoverWest) ctx.renderNegativeXFacing(coverTexture);
            if (!hasCoverEast) ctx.renderPositiveXFacing(coverTexture);
            ctx.renderPositiveYFacing(coverTexture);
            if ((aConnections & CONNECTED_DOWN) != 0) {
                renderBlocks.setRenderBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.0F, pipeMin);
                ctx.renderNegativeYFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, 0.0F, pipeMax, 1.0F, 0.0F, 1.0F);
                ctx.renderNegativeYFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, 0.0F, pipeMin, pipeMin, 0.0F, pipeMax);
                ctx.renderNegativeYFacing(coverTexture);
                renderBlocks.setRenderBounds(pipeMax, 0.0F, pipeMin, 1.0F, 0.0F, pipeMax);
            }
            ctx.renderNegativeYFacing(coverTexture);
        }

        if (hasCoverUp) {
            final ITexture[] coverTexture = rte.getTexture(UP);
            renderBlocks.setRenderBounds(0.0F, coverInnerMax, 0.0F, 1.0F, 1.0F, 1.0F);
            if (!hasCoverNorth) ctx.renderNegativeZFacing(coverTexture);
            if (!hasCoverSouth) ctx.renderPositiveZFacing(coverTexture);
            if (!hasCoverWest) ctx.renderNegativeXFacing(coverTexture);
            if (!hasCoverEast) ctx.renderPositiveXFacing(coverTexture);
            ctx.renderNegativeYFacing(coverTexture);
            if ((aConnections & CONNECTED_UP) != 0) {
                renderBlocks.setRenderBounds(0.0F, 1.0F, 0.0F, 1.0F, 1.0F, pipeMin);
                ctx.renderPositiveYFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, 1.0F, pipeMax, 1.0F, 1.0F, 1.0F);
                ctx.renderPositiveYFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, 1.0F, pipeMin, pipeMin, 1.0F, pipeMax);
                ctx.renderPositiveYFacing(coverTexture);
                renderBlocks.setRenderBounds(pipeMax, 1.0F, pipeMin, 1.0F, 1.0F, pipeMax);
            }
            ctx.renderPositiveYFacing(coverTexture);
        }

        if (hasCoverNorth) {
            final ITexture[] coverTexture = rte.getTexture(NORTH);
            renderBlocks.setRenderBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, coverInnerMin);
            if (!hasCoverDown) ctx.renderNegativeYFacing(coverTexture);
            if (!hasCoverUp) ctx.renderPositiveYFacing(coverTexture);
            if (!hasCoverWest) ctx.renderNegativeXFacing(coverTexture);
            if (!hasCoverEast) ctx.renderPositiveXFacing(coverTexture);
            ctx.renderPositiveZFacing(coverTexture);
            if ((aConnections & CONNECTED_NORTH) != 0) {
                renderBlocks.setRenderBounds(0.0F, 0.0F, 0.0F, 1.0F, pipeMin, 0.0F);
                ctx.renderNegativeZFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, pipeMax, 0.0F, 1.0F, 1.0F, 0.0F);
                ctx.renderNegativeZFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, pipeMin, 0.0F, pipeMin, pipeMax, 0.0F);
                ctx.renderNegativeZFacing(coverTexture);
                renderBlocks.setRenderBounds(pipeMax, pipeMin, 0.0F, 1.0F, pipeMax, 0.0F);
            }
            ctx.renderNegativeZFacing(coverTexture);
        }

        if (hasCoverSouth) {
            final ITexture[] coverTexture = rte.getTexture(SOUTH);
            renderBlocks.setRenderBounds(0.0F, 0.0F, coverInnerMax, 1.0F, 1.0F, 1.0F);
            if (!hasCoverDown) ctx.renderNegativeYFacing(coverTexture);
            if (!hasCoverUp) ctx.renderPositiveYFacing(coverTexture);
            if (!hasCoverWest) ctx.renderNegativeXFacing(coverTexture);
            if (!hasCoverEast) ctx.renderPositiveXFacing(coverTexture);
            ctx.renderNegativeZFacing(coverTexture);
            if ((aConnections & CONNECTED_SOUTH) != 0) {
                renderBlocks.setRenderBounds(0.0F, 0.0F, 1.0F, 1.0F, pipeMin, 1.0F);
                ctx.renderPositiveZFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, pipeMax, 1.0F, 1.0F, 1.0F, 1.0F);
                ctx.renderPositiveZFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, pipeMin, 1.0F, pipeMin, pipeMax, 1.0F);
                ctx.renderPositiveZFacing(coverTexture);
                renderBlocks.setRenderBounds(pipeMax, pipeMin, 1.0F, 1.0F, pipeMax, 1.0F);
            }
            ctx.renderPositiveZFacing(coverTexture);
        }

        if (hasCoverWest) {
            final ITexture[] coverTexture = rte.getTexture(WEST);
            renderBlocks.setRenderBounds(0.0F, 0.0F, 0.0F, coverInnerMin, 1.0F, 1.0F);
            if (!hasCoverDown) ctx.renderNegativeYFacing(coverTexture);
            if (!hasCoverUp) ctx.renderPositiveYFacing(coverTexture);
            if (!hasCoverNorth) ctx.renderNegativeZFacing(coverTexture);
            if (!hasCoverSouth) ctx.renderPositiveZFacing(coverTexture);
            ctx.renderPositiveXFacing(coverTexture);
            if ((aConnections & CONNECTED_WEST) != 0) {
                renderBlocks.setRenderBounds(0.0F, 0.0F, 0.0F, 0.0F, pipeMin, 1.0F);
                ctx.renderNegativeXFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, pipeMax, 0.0F, 0.0F, 1.0F, 1.0F);
                ctx.renderNegativeXFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, pipeMin, 0.0F, 0.0F, pipeMax, pipeMin);
                ctx.renderNegativeXFacing(coverTexture);
                renderBlocks.setRenderBounds(0.0F, pipeMin, pipeMax, 0.0F, pipeMax, 1.0F);
            }
            ctx.renderNegativeXFacing(coverTexture);
        }

        if (hasCoverEast) {
            final ITexture[] coverTexture = rte.getTexture(EAST);
            renderBlocks.setRenderBounds(coverInnerMax, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
            if (!hasCoverDown) ctx.renderNegativeYFacing(coverTexture);
            if (!hasCoverUp) ctx.renderPositiveYFacing(coverTexture);
            if (!hasCoverNorth) ctx.renderNegativeZFacing(coverTexture);
            if (!hasCoverSouth) ctx.renderPositiveZFacing(coverTexture);
            ctx.renderNegativeXFacing(coverTexture);
            if ((aConnections & CONNECTED_EAST) != 0) {
                renderBlocks.setRenderBounds(1.0F, 0.0F, 0.0F, 1.0F, pipeMin, 1.0F);
                ctx.renderPositiveXFacing(coverTexture);
                renderBlocks.setRenderBounds(1.0F, pipeMax, 0.0F, 1.0F, 1.0F, 1.0F);
                ctx.renderPositiveXFacing(coverTexture);
                renderBlocks.setRenderBounds(1.0F, pipeMin, 0.0F, 1.0F, pipeMax, pipeMin);
                ctx.renderPositiveXFacing(coverTexture);
                renderBlocks.setRenderBounds(1.0F, pipeMin, pipeMax, 1.0F, pipeMax, 1.0F);
            }
            ctx.renderPositiveXFacing(coverTexture);
        }
    }
}
