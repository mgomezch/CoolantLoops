package com.gtnewhorizons.coolantloops.common.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;
import com.gtnewhorizons.modularui.api.UIInfos;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Pressurized Instrumentation Computer Block.
 * Emits redstone signal (0-15) and 8-bit ProjectRed bundled signal (0-255)
 * strictly from its facing direction.
 */
public class BlockLoopInstrument extends BlockContainer {

    @SideOnly(Side.CLIENT)
    private IIcon iconSide;
    @SideOnly(Side.CLIENT)
    private IIcon iconTop;
    @SideOnly(Side.CLIENT)
    private IIcon iconFront;

    public BlockLoopInstrument() {
        super(Material.iron);
        setBlockName("coolantloops.instrument");
        setHardness(2.0f);
        setResistance(10.0f);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityLoopInstrument();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        this.iconSide = register.registerIcon("coolantloops:instrument_side");
        this.iconTop = register.registerIcon("coolantloops:instrument_top");
        this.iconFront = register.registerIcon("coolantloops:instrument_front");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityLoopInstrument inst) {
            if (side == inst.getFacing()
                .ordinal()) {
                return iconFront;
            }
        }
        if (side == ForgeDirection.UP.ordinal()) {
            return iconTop;
        }
        return iconSide;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        if (side == ForgeDirection.UP.ordinal()) {
            return iconTop;
        }
        if (side == ForgeDirection.NORTH.ordinal()) {
            return iconFront;
        }
        return iconSide;
    }

    @Override
    public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z);
        int meta = world.getBlockMetadata(x, y, z);
        if (meta >= 2 && meta <= 5) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityLoopInstrument inst) {
                inst.setFacing(ForgeDirection.getOrientation(meta));
            }
        }
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack itemStack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityLoopInstrument inst && entity != null) {
            int l = MathHelper.floor_double((double) (entity.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
            ForgeDirection facing = switch (l) {
                case 0 -> ForgeDirection.SOUTH;
                case 1 -> ForgeDirection.WEST;
                case 2 -> ForgeDirection.NORTH;
                case 3 -> ForgeDirection.EAST;
                default -> ForgeDirection.NORTH;
            };
            inst.setFacing(facing);
        }
    }

    @Override
    public boolean canProvidePower() {
        return true;
    }

    @Override
    public int isProvidingWeakPower(IBlockAccess world, int x, int y, int z, int side) {
        if (side < 0 || side > 5) {
            return 0;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityLoopInstrument inst) {
            // Neighbor on 'side' queries with side of neighbor, which is opposite of this block's face
            ForgeDirection queriedFace = ForgeDirection.getOrientation(side)
                .getOpposite();
            if (queriedFace == inst.getFacing()) {
                return inst.getRedstoneOutput();
            }
        }
        return 0;
    }

    @Override
    public int isProvidingStrongPower(IBlockAccess world, int x, int y, int z, int side) {
        return isProvidingWeakPower(world, x, y, z, side);
    }

    public static boolean isScrewdriver(ItemStack stack) {
        if (stack == null) return false;
        for (int id : OreDictionary.getOreIDs(stack)) {
            if ("craftingToolScrewdriver".equals(OreDictionary.getOreName(id))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWrench(ItemStack stack) {
        if (stack == null) return false;
        for (int id : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(id);
            if ("craftingToolWrench".equals(name) || "wrench".equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean rotateBlock(World worldObj, int x, int y, int z, ForgeDirection axis) {
        TileEntity te = worldObj.getTileEntity(x, y, z);
        if (te instanceof TileEntityLoopInstrument inst) {
            ForgeDirection cur = inst.getFacing();
            ForgeDirection next = cur.getRotation(axis != null ? axis : ForgeDirection.UP);
            if (next == cur || next == ForgeDirection.UNKNOWN) {
                next = switch (cur) {
                    case NORTH -> ForgeDirection.EAST;
                    case EAST -> ForgeDirection.SOUTH;
                    case SOUTH -> ForgeDirection.WEST;
                    case WEST -> ForgeDirection.NORTH;
                    default -> ForgeDirection.NORTH;
                };
            }
            inst.setFacing(next);
            return true;
        }
        return false;
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityLoopInstrument inst)) {
            return false;
        }

        ItemStack held = player != null ? player.getHeldItem() : null;
        if (held != null) {
            if (isWrench(held)) {
                if (!world.isRemote) {
                    ForgeDirection clickedSide = ForgeDirection.getOrientation(side);
                    ForgeDirection nextFacing;
                    if (clickedSide == inst.getFacing()) {
                        nextFacing = switch (inst.getFacing()) {
                            case NORTH -> ForgeDirection.EAST;
                            case EAST -> ForgeDirection.SOUTH;
                            case SOUTH -> ForgeDirection.WEST;
                            case WEST -> ForgeDirection.NORTH;
                            case UP -> ForgeDirection.DOWN;
                            case DOWN -> ForgeDirection.NORTH;
                            default -> ForgeDirection.NORTH;
                        };
                    } else {
                        nextFacing = clickedSide;
                    }
                    inst.setFacing(nextFacing);
                    world.markBlockForUpdate(x, y, z);
                    world.notifyBlocksOfNeighborChange(x, y, z, this);
                    player.addChatMessage(
                        new ChatComponentText(
                            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization
                                .format("coolantloops.chat.instrument.facing", nextFacing.name())));
                }
                return true;
            }
            if (isScrewdriver(held)) {
                if (!world.isRemote) {
                    inst.cycleMetric(player.isSneaking() ? -1 : 1);
                    player.addChatMessage(
                        new ChatComponentText(
                            com.gtnewhorizons.coolantloops.common.util.CoolantLocalization.format(
                                "coolantloops.chat.instrument.tracked_metric",
                                inst.getTrackedMetric()
                                    .getDisplayName())));
                }
                return true;
            }
        }

        if (!world.isRemote && player != null) {
            UIInfos.TILE_MODULAR_UI.open(player, world, x, y, z);
        }
        return true;
    }
}
