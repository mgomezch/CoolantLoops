package com.gtnewhorizons.coolantloops.common.metatileentity.multi.util;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Represents a single virtual processing slot within the Coolant Bath multiblock.
 * Manages item thermal exchange, durability/damage progress, and transformation state.
 */
public class CoolantBathSlot {

    public ItemStack itemStack;
    public double currentTemp; // Temperature in deg C
    public double targetTemp; // Target completion temperature in deg C
    public double startTemp; // Initial temperature at insertion in deg C
    public int initialDamage; // Damage at insertion
    public int maxDamage; // Durability limit (or 1000 for ingots)
    public double heatCapacity; // Thermal mass in J/K
    public double surfaceArea; // Convective heat transfer area in m^2
    public boolean isCoolantCell;
    public boolean isIngot;
    public boolean cooling; // true if item is hotter than target / cooling; false if heating
    public ItemStack transformedOutput;

    // Transient client-side entity item used for rendering through the glass roof
    public transient EntityItem clientEntityItem;

    public CoolantBathSlot() {}

    public CoolantBathSlot(ItemStack itemStack, double initialTemp, double targetTemp, int initialDamage, int maxDamage,
        double heatCapacity, double surfaceArea, boolean isCoolantCell, boolean isIngot, boolean cooling,
        ItemStack transformedOutput) {
        this.itemStack = itemStack;
        this.currentTemp = initialTemp;
        this.targetTemp = targetTemp;
        this.startTemp = initialTemp;
        this.initialDamage = initialDamage;
        this.maxDamage = Math.max(1, maxDamage);
        this.heatCapacity = Math.max(10.0, heatCapacity);
        this.surfaceArea = Math.max(0.01, surfaceArea);
        this.isCoolantCell = isCoolantCell;
        this.isIngot = isIngot;
        this.cooling = cooling;
        this.transformedOutput = transformedOutput;
    }

    public boolean isEmpty() {
        return itemStack == null;
    }

    /**
     * Checks if the slot item has reached its transformation or auto-ejection criteria.
     *
     * Rules:
     * - Coolant cells auto-eject when reaching max or min durability, EXCEPT if present state
     * equals insertion state (fully-hot ejects only when fully-cold; fully-cold ejects only
     * when fully-hot; intermediate ejects at either extreme).
     * - Ingots auto-eject when they have reached the target temperature and transformed.
     */
    public boolean isAutoEjectReady() {
        if (itemStack == null) return false;

        if (isCoolantCell) {
            int currentDmg = itemStack.getItemDamage();
            if (currentDmg == initialDamage) {
                return false;
            }
            int minDmg = 1;
            int maxDmg = maxDamage > 1 ? maxDamage : 10000;
            if (initialDamage >= maxDmg) {
                return currentDmg <= minDmg;
            } else if (initialDamage <= minDmg) {
                return currentDmg >= maxDmg;
            } else {
                return currentDmg <= minDmg || currentDmg >= maxDmg;
            }
        } else if (isIngot) {
            if (cooling) {
                return currentTemp <= (targetTemp + 2.0);
            } else {
                return currentTemp >= (targetTemp - 2.0);
            }
        }

        return false;
    }

    public ItemStack getCompletedOutput() {
        if (transformedOutput != null) {
            ItemStack res = transformedOutput.copy();
            res.stackSize = 1;
            return res;
        }
        return itemStack;
    }

    public double getProgressFraction() {
        if (itemStack == null) return 0.0;

        if (isCoolantCell) {
            int minDmg = 1;
            int maxDmg = maxDamage > 1 ? maxDamage : 10000;
            int curDmg = itemStack.getItemDamage();
            if (cooling) {
                return Math
                    .max(0.0, Math.min(1.0, (double) (initialDamage - curDmg) / Math.max(1, initialDamage - minDmg)));
            } else {
                return Math
                    .max(0.0, Math.min(1.0, (double) (curDmg - initialDamage) / Math.max(1, maxDmg - initialDamage)));
            }
        } else if (isIngot) {
            double totalSpan = Math.abs(targetTemp - startTemp);
            if (totalSpan <= 0.0) return 1.0;
            if (cooling) {
                return Math.max(0.0, Math.min(1.0, (startTemp - currentTemp) / totalSpan));
            } else {
                return Math.max(0.0, Math.min(1.0, (currentTemp - startTemp) / totalSpan));
            }
        }
        return 0.0;
    }

    /**
     * Updates the item's damage and NBT progress tags to match current temperature.
     */
    public void updateItemDamage() {
        if (itemStack == null) return;

        if (isCoolantCell) {
            int minDmg = 1;
            int maxDmg = maxDamage > 1 ? maxDamage : 10000;
            double frac = Math.max(0.0, Math.min(1.0, (currentTemp - 20.0) / (1000.0 - 20.0)));
            int dmg = minDmg + (int) Math.round(frac * (maxDmg - minDmg));
            dmg = Math.max(minDmg, Math.min(maxDmg, dmg));
            itemStack.setItemDamage(dmg);
        } else if (isIngot) {
            double frac = getProgressFraction();
            int maxDmg = 1000;
            int dmg = (int) Math.round(frac * maxDmg);
            if (itemStack.isItemStackDamageable()) {
                itemStack.setItemDamage(dmg);
            }
            NBTTagCompound tag = itemStack.hasTagCompound() ? itemStack.getTagCompound() : new NBTTagCompound();
            tag.setInteger("bathDamage", dmg);
            tag.setDouble("bathProgress", frac);
            tag.setDouble("bathTemperature", currentTemp);
            itemStack.setTagCompound(tag);
        }
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (itemStack != null) {
            NBTTagCompound itemTag = new NBTTagCompound();
            itemStack.writeToNBT(itemTag);
            tag.setTag("item", itemTag);
        }
        tag.setDouble("currentTemp", currentTemp);
        tag.setDouble("targetTemp", targetTemp);
        tag.setDouble("startTemp", startTemp);
        tag.setInteger("initialDamage", initialDamage);
        tag.setInteger("maxDamage", maxDamage);
        tag.setDouble("heatCapacity", heatCapacity);
        tag.setDouble("surfaceArea", surfaceArea);
        tag.setBoolean("isCoolantCell", isCoolantCell);
        tag.setBoolean("isIngot", isIngot);
        tag.setBoolean("cooling", cooling);
        if (transformedOutput != null) {
            NBTTagCompound outTag = new NBTTagCompound();
            transformedOutput.writeToNBT(outTag);
            tag.setTag("transformedOutput", outTag);
        }
        return tag;
    }

    public static CoolantBathSlot readFromNBT(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("item")) {
            return null;
        }
        CoolantBathSlot slot = new CoolantBathSlot();
        slot.itemStack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("item"));
        slot.currentTemp = tag.getDouble("currentTemp");
        slot.targetTemp = tag.getDouble("targetTemp");
        slot.startTemp = tag.hasKey("startTemp") ? tag.getDouble("startTemp")
            : (slot.cooling ? Math.max(slot.targetTemp + 50.0, slot.currentTemp) : 20.0);
        slot.initialDamage = tag.getInteger("initialDamage");
        slot.maxDamage = Math.max(1, tag.getInteger("maxDamage"));
        slot.heatCapacity = Math.max(10.0, tag.getDouble("heatCapacity"));
        slot.surfaceArea = Math.max(0.01, tag.getDouble("surfaceArea"));
        slot.isCoolantCell = tag.getBoolean("isCoolantCell");
        slot.isIngot = tag.getBoolean("isIngot");
        slot.cooling = tag.getBoolean("cooling");
        if (tag.hasKey("transformedOutput")) {
            slot.transformedOutput = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("transformedOutput"));
        }
        return slot;
    }
}
