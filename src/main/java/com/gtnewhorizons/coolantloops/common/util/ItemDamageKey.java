package com.gtnewhorizons.coolantloops.common.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Hashable value key representing an (Item, damage) pair, eliminating
 * string allocations and parsing when indexing items in registries.
 */
public final class ItemDamageKey {

    private final Item item;
    private final int damage;

    public ItemDamageKey(Item item, int damage) {
        this.item = item;
        this.damage = damage;
    }

    public static ItemDamageKey of(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        return new ItemDamageKey(stack.getItem(), stack.getItemDamage());
    }

    public static ItemDamageKey of(Item item, int damage) {
        if (item == null) {
            return null;
        }
        return new ItemDamageKey(item, damage);
    }

    public Item getItem() {
        return item;
    }

    public int getDamage() {
        return damage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ItemDamageKey that = (ItemDamageKey) o;
        return damage == that.damage && item == that.item;
    }

    @Override
    public int hashCode() {
        return 31 * (item != null ? item.hashCode() : 0) + damage;
    }

    @Override
    public String toString() {
        String name = (item != null && item.delegate != null && item.delegate.name() != null) ? item.delegate.name()
            : String.valueOf(item);
        return name + ":" + damage;
    }
}
