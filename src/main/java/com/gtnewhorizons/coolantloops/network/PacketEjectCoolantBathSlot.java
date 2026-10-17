package com.gtnewhorizons.coolantloops.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantBath;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

public class PacketEjectCoolantBathSlot implements IMessage {

    public int x, y, z;
    public int slotIndex;

    public PacketEjectCoolantBathSlot() {}

    public PacketEjectCoolantBathSlot(int x, int y, int z, int slotIndex) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.slotIndex = slotIndex;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(slotIndex);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.slotIndex = buf.readInt();
    }

    public static class Handler implements IMessageHandler<PacketEjectCoolantBathSlot, IMessage> {

        @Override
        public IMessage onMessage(PacketEjectCoolantBathSlot msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) return null;

            World world = player.worldObj;
            if (world == null) return null;

            TileEntity te = world.getTileEntity(msg.x, msg.y, msg.z);
            if (te instanceof IGregTechTileEntity) {
                IGregTechTileEntity gte = (IGregTechTileEntity) te;
                if (gte.getMetaTileEntity() instanceof MTECoolantBath) {
                    MTECoolantBath bath = (MTECoolantBath) gte.getMetaTileEntity();
                    bath.ejectSlot(msg.slotIndex);
                }
            }
            return null;
        }
    }
}
