package com.gtnewhorizons.coolantloops.client.renderer;

import java.util.Iterator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantBath;
import com.gtnewhorizons.coolantloops.network.PacketSyncCoolantBath;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 3D in-world client renderer for the Coolant Bath multiblock.
 *
 * Renders active items floating and rotating at their assigned virtual slot positions
 * inside the glass-covered cooling chamber, visible through the tiered glass roof.
 */
@SideOnly(Side.CLIENT)
public class CoolantBathRenderer {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.renderViewEntity == null) return;
        if (MTECoolantBath.ACTIVE_CLIENT_BATHS.isEmpty()) return;

        double interpPosX = RenderManager.instance.renderPosX;
        double interpPosY = RenderManager.instance.renderPosY;
        double interpPosZ = RenderManager.instance.renderPosZ;

        float ticks = mc.renderViewEntity.ticksExisted + event.partialTicks;

        for (Iterator<MTECoolantBath> it = MTECoolantBath.ACTIVE_CLIENT_BATHS.iterator(); it.hasNext();) {
            MTECoolantBath bath = it.next();
            IGregTechTileEntity bte = bath.getBaseMetaTileEntity();
            if (bte == null || bte.isInvalidTileEntity() || bte.getWorld() != mc.theWorld) {
                it.remove();
                continue;
            }

            PacketSyncCoolantBath.SlotSyncData[] slots = bath.clientSlots;
            if (slots == null || slots.length == 0) continue;

            for (int i = 0; i < slots.length; i++) {
                PacketSyncCoolantBath.SlotSyncData s = slots[i];
                if (s == null || s.stack == null) continue;

                int[] worldPos = bath.getSlotWorldCoordinates(s.index);
                if (worldPos == null) continue;

                double x = worldPos[0] - interpPosX;
                double y = worldPos[1] - interpPosY;
                double z = worldPos[2] - interpPosZ;

                // Distance cull if beyond 64 blocks
                if (x * x + y * y + z * z > 4096.0) continue;

                GL11.glPushMatrix();

                float bob = MathHelper.sin((ticks + s.index * 7.0f) / 16.0f) * 0.05f;
                GL11.glTranslated(x + 0.5, y + 0.25 + bob, z + 0.5);
                GL11.glRotatef((ticks * 2.0f + s.index * 23.0f) % 360.0f, 0.0f, 1.0f, 0.0f);

                boolean isBlock = s.stack.getItem() instanceof ItemBlock;
                float scale = isBlock ? 1.6f : 0.9f;
                GL11.glScalef(scale, scale, scale);

                EntityItem entityItem = new EntityItem(mc.theWorld, 0, 0, 0, s.stack);
                entityItem.hoverStart = 0;
                RenderManager.instance.renderEntityWithPosYaw(entityItem, 0, 0, 0, 0, 0);

                GL11.glPopMatrix();
            }
        }
    }
}
