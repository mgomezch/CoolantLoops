package com.gtnewhorizons.coolantloops.common.block;

import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityLoopInstrument;

/**
 * In-Line Coolant Loop Instrument Block.
 * Emits redstone signal (0-15) based on monitored coolant properties.
 */
public class BlockLoopInstrument extends BlockContainer {

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
    public boolean canProvidePower() {
        return true;
    }

    @Override
    public int isProvidingWeakPower(IBlockAccess world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof TileEntityLoopInstrument) {
            return ((TileEntityLoopInstrument) te).getRedstoneOutput();
        }
        return 0;
    }

    @Override
    public int isProvidingStrongPower(IBlockAccess world, int x, int y, int z, int side) {
        return isProvidingWeakPower(world, x, y, z, side);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX,
        float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntityLoopInstrument) {
                TileEntityLoopInstrument inst = (TileEntityLoopInstrument) te;
                if (player.isSneaking()) {
                    // Cycle type: FLOW_METER -> THERMOMETER -> DISSOLVED_GAS_SENSOR -> FLOW_METER
                    TileEntityLoopInstrument.InstrumentType curr = inst.getType();
                    TileEntityLoopInstrument.InstrumentType next;
                    switch (curr) {
                        case FLOW_METER:
                            next = TileEntityLoopInstrument.InstrumentType.THERMOMETER;
                            break;
                        case THERMOMETER:
                            next = TileEntityLoopInstrument.InstrumentType.DISSOLVED_GAS_SENSOR;
                            break;
                        default:
                            next = TileEntityLoopInstrument.InstrumentType.FLOW_METER;
                            break;
                    }
                    inst.setType(next);
                    player.addChatMessage(
                        new ChatComponentText(String.format("Instrument: Mode set to %s", next.name())));
                } else {
                    player.addChatMessage(
                        new ChatComponentText(
                            String.format(
                                "Instrument (%s): Output Redstone = %d/15, Temp = %.1f C, Flow = %.2f L/s",
                                inst.getType()
                                    .name(),
                                inst.getRedstoneOutput(),
                                inst.getDeviceTemperatureCelsius(),
                                inst.getCurrentFlowRateLitersPerSecond())));
                }
            }
        }
        return true;
    }
}
