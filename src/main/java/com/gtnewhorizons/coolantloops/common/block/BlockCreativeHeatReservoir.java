package com.gtnewhorizons.coolantloops.common.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityCreativeHeatReservoir;

/**
 * Creative Heat Reservoir Block.
 * Shift-right clicking with empty hand cycles preset temperatures (100C, 300C, 600C, 1000C, 20C).
 */
public class BlockCreativeHeatReservoir extends BlockContainer {

    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    private net.minecraft.util.IIcon mIcon;

    public BlockCreativeHeatReservoir() {
        super(Material.iron);
        setBlockName("coolantloops.creative_heat_reservoir");
        setHardness(2.0f);
        setResistance(10.0f);
        setStepSound(soundTypeMetal);
        setCreativeTab(CreativeTabs.tabRedstone);
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public void registerBlockIcons(net.minecraft.client.renderer.texture.IIconRegister reg) {
        this.blockIcon = reg.registerIcon("coolantloops:creative_heat_reservoir");
        this.mIcon = this.blockIcon;
    }

    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public net.minecraft.util.IIcon getIcon(int side, int meta) {
        return this.mIcon != null ? this.mIcon : this.blockIcon;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityCreativeHeatReservoir();
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityCreativeHeatReservoir) {
                TileEntityCreativeHeatReservoir res = (TileEntityCreativeHeatReservoir) te;
                if (player.isSneaking()) {
                    // Cycle temperatures: 20 -> 100 -> 300 -> 600 -> 1000 -> 20
                    double current = res.getDeviceTemperatureCelsius();
                    double next;
                    if (current < 50.0) next = 100.0;
                    else if (current < 200.0) next = 300.0;
                    else if (current < 450.0) next = 600.0;
                    else if (current < 800.0) next = 1000.0;
                    else next = 20.0;

                    res.setTargetTemperatureCelsius(next);
                    player.addChatMessage(
                        new ChatComponentText(
                            String.format("Creative Heat Reservoir: Temperature set to %.1f C", next)));
                } else {
                    player.addChatMessage(
                        new ChatComponentText(
                            String.format(
                                "Creative Heat Reservoir: Current Temp = %.1f C, Area = %.1f m^2 (Sneak-click to cycle temp)",
                                res.getDeviceTemperatureCelsius(),
                                res.getHeatTransferArea())));
                }
            }
        }
        return true;
    }
}
