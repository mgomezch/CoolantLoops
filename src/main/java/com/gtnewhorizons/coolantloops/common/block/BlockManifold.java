package com.gtnewhorizons.coolantloops.common.block;

import java.util.List;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.coolantloops.client.renderer.RenderBlockManifold;
import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;

import gregtech.api.enums.SoundResource;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTUtility;

/**
 * Manifold Block.
 * Used to split or merge coolant flows within a single closed loop.
 * A 3D non-full block with an orientable "connect-to-other-manifold-blocks" plane
 * and two "connect-to-input-or-output" normal directions.
 * Shift-rightclick with a wrench changes the plane orientation.
 * Right-click with a wrench toggles explicit connections on individual faces.
 */
public class BlockManifold extends BlockContainer {

    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private net.minecraft.util.IIcon mIcon;

    public BlockManifold() {
        super(Material.iron);
        setBlockName("coolantloops.manifold");
        setHardness(3.0f);
        setResistance(15.0f);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    @Override
    public int getRenderType() {
        return RenderBlockManifold.renderID >= 0 ? RenderBlockManifold.renderID : 0;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister reg) {
        this.blockIcon = reg.registerIcon("coolantloops:manifold");
        this.mIcon = this.blockIcon;
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.IIcon getIcon(int side, int meta) {
        return this.mIcon != null ? this.mIcon : this.blockIcon;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        TileEntityManifold te = new TileEntityManifold();
        if (meta > 0 && meta < ForgeDirection.VALID_DIRECTIONS.length) {
            te.setFacing(ForgeDirection.getOrientation(meta));
        }
        return te;
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack itemStack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityManifold manifold && entity != null) {
            int l = MathHelper.floor_double((double) (entity.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
            ForgeDirection facing = switch (l) {
                case 0 -> ForgeDirection.SOUTH;
                case 1 -> ForgeDirection.WEST;
                case 2 -> ForgeDirection.NORTH;
                case 3 -> ForgeDirection.EAST;
                default -> ForgeDirection.NORTH;
            };
            manifold.setFacing(facing);
        }
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB mask, List list, Entity entity) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityManifold manifold) {
            List<AxisAlignedBB> boxes = manifold.getComponentBoundingBoxes(x, y, z);
            for (AxisAlignedBB box : boxes) {
                if (mask.intersectsWith(box)) {
                    list.add(box);
                }
            }
        } else {
            super.addCollisionBoxesToList(world, x, y, z, mask, list, entity);
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        setBlockBoundsBasedOnState(world, x, y, z);
        return super.getCollisionBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        setBlockBoundsBasedOnState(world, x, y, z);
        return super.getSelectedBoundingBoxFromPool(world, x, y, z);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityManifold manifold) {
            AxisAlignedBB union = manifold.getUnionBoundingBox();
            setBlockBounds(
                (float) union.minX, (float) union.minY, (float) union.minZ,
                (float) union.maxX, (float) union.maxY, (float) union.maxZ);
        } else {
            setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityManifold manifold)) {
            return false;
        }

        ItemStack held = player != null ? player.getHeldItem() : null;

        if (BlockLoopInstrument.isWrench(held)) {
            if (!world.isRemote) {
                ForgeDirection clickedSide = ForgeDirection.getOrientation(side);
                if (player.isSneaking()) {
                    // Shift-rightclick with wrench: Orient main plane
                    ForgeDirection newFacing = clickedSide;
                    if (manifold.getPlaneAxis() == TileEntityManifold.getAxisForDirection(clickedSide)) {
                        newFacing = manifold.getNextFacing();
                    }
                    manifold.setFacing(newFacing);
                    world.markBlockForUpdate(x, y, z);
                    world.notifyBlocksOfNeighborChange(x, y, z, this);
                    player.addChatMessage(new ChatComponentText(String.format(
                        "Manifold: Plane oriented to %s (Normal: %s)",
                        manifold.getPlaneName(),
                        manifold.getFacing().name())));
                } else {
                    // Regular rightclick with wrench: Toggle connection on wrenching side
                    ForgeDirection wrenchingSide = GTUtility.determineWrenchingSide(clickedSide, hitX, hitY, hitZ);
                    boolean connected = manifold.toggleConnectionWithNeighbor(wrenchingSide);
                    world.markBlockForUpdate(x, y, z);
                    world.notifyBlocksOfNeighborChange(x, y, z, this);
                    player.addChatMessage(new ChatComponentText(String.format(
                        "Manifold: %s connection %s",
                        wrenchingSide.name(),
                        connected ? "CONNECTED" : "DISCONNECTED")));
                }
                GTModHandler.damageOrDechargeItem(held, 1, 1000, player);
                GTUtility.sendSoundToPlayers(world, SoundResource.GTCEU_OP_WRENCH, 1.0F, 1.0F, x, y, z);
            }
            return true;
        }

        if (!world.isRemote) {
            int groupSize = manifold.findLinearGroup().size();
            player.addChatMessage(new ChatComponentText(String.format(
                "Manifold: Plane = %s, Normal = %s, Inline Group Size = %d/8 blocks, Flow = %.2f L/s, Temp = %.1f \u00B0C",
                manifold.getPlaneName(),
                manifold.getFacing().name(),
                groupSize,
                manifold.getTotalInputFlowRate() * 1000.0,
                manifold.getDeviceTemperatureCelsius())));
        }
        return true;
    }
}
