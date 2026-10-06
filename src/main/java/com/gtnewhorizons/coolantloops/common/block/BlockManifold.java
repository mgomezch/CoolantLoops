package com.gtnewhorizons.coolantloops.common.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;

/**
 * Manifold Block.
 * Used to split or merge coolant flows within a single closed loop.
 * Contiguous groups of up to 8 inline blocks face the same output direction.
 */
public class BlockManifold extends BlockContainer {

    public BlockManifold() {
        super(Material.iron);
        setBlockName("coolantloops.manifold");
        setHardness(3.0f);
        setResistance(15.0f);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityManifold();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityManifold) {
                TileEntityManifold manifold = (TileEntityManifold) te;
                if (player.isSneaking()) {
                    // Rotate facing: NORTH -> EAST -> SOUTH -> WEST -> NORTH
                    ForgeDirection curr = manifold.getFacing();
                    ForgeDirection next;
                    switch (curr) {
                        case NORTH:
                            next = ForgeDirection.EAST;
                            break;
                        case EAST:
                            next = ForgeDirection.SOUTH;
                            break;
                        case SOUTH:
                            next = ForgeDirection.WEST;
                            break;
                        default:
                            next = ForgeDirection.NORTH;
                            break;
                    }
                    manifold.setFacing(next);
                    player.addChatMessage(
                        new ChatComponentText(String.format("Manifold: Output facing set to %s", next.name())));
                } else {
                    int groupSize = manifold.findLinearGroup()
                        .size();
                    player.addChatMessage(
                        new ChatComponentText(
                            String.format(
                                "Manifold: Output = %s, Inline Group Size = %d/8 blocks, Flow = %.2f L/s, Temp = %.1f C",
                                manifold.getFacing()
                                    .name(),
                                groupSize,
                                manifold.getTotalInputFlowRate() * 1000.0,
                                manifold.getDeviceTemperatureCelsius())));
                }
            }
        }
        return true;
    }
}
