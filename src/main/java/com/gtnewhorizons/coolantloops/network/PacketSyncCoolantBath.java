package com.gtnewhorizons.coolantloops.network;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantBath;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import io.netty.buffer.ByteBuf;

public class PacketSyncCoolantBath implements IMessage {

    public int x, y, z;
    public int structureTier;
    public int slotCount;
    public SlotSyncData[] slots;

    public static class SlotSyncData {

        public int index;
        public ItemStack stack;
        public float currentTemp;
        public float targetTemp;
        public float progress;
        public boolean isCoolantCell;
        public boolean cooling;
    }

    public PacketSyncCoolantBath() {}

    public PacketSyncCoolantBath(int x, int y, int z, int structureTier, SlotSyncData[] slots) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.structureTier = structureTier;
        this.slots = slots != null ? slots : new SlotSyncData[0];
        this.slotCount = this.slots.length;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        buf.writeInt(structureTier);
        buf.writeInt(slotCount);
        for (int i = 0; i < slotCount; i++) {
            SlotSyncData s = slots[i];
            buf.writeInt(s.index);
            ByteBufUtils.writeItemStack(buf, s.stack);
            buf.writeFloat(s.currentTemp);
            buf.writeFloat(s.targetTemp);
            buf.writeFloat(s.progress);
            buf.writeBoolean(s.isCoolantCell);
            buf.writeBoolean(s.cooling);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.structureTier = buf.readInt();
        this.slotCount = buf.readInt();
        this.slots = new SlotSyncData[slotCount];
        for (int i = 0; i < slotCount; i++) {
            SlotSyncData s = new SlotSyncData();
            s.index = buf.readInt();
            s.stack = ByteBufUtils.readItemStack(buf);
            s.currentTemp = buf.readFloat();
            s.targetTemp = buf.readFloat();
            s.progress = buf.readFloat();
            s.isCoolantCell = buf.readBoolean();
            s.cooling = buf.readBoolean();
            slots[i] = s;
        }
    }

    public static class Handler implements IMessageHandler<PacketSyncCoolantBath, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketSyncCoolantBath msg, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null) return null;

            TileEntity te = mc.theWorld.getTileEntity(msg.x, msg.y, msg.z);
            if (te instanceof IGregTechTileEntity) {
                IGregTechTileEntity gte = (IGregTechTileEntity) te;
                if (gte.getMetaTileEntity() instanceof MTECoolantBath) {
                    MTECoolantBath bath = (MTECoolantBath) gte.getMetaTileEntity();
                    bath.receiveClientSync(msg.structureTier, msg.slots);
                }
            }
            return null;
        }
    }
}
