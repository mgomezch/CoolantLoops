package com.gtnewhorizons.coolantloops.common.util;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.tileentity.TileEntityManifold;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;

public class ItemDamageKeyTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            cpw.mods.fml.common.Loader mockLoader = Mockito.mock(cpw.mods.fml.common.Loader.class);
            Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(Mockito.mock(cpw.mods.fml.common.ICrashCallable.class));
            java.lang.reflect.Field f = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable ignored) {}
        try {
            Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}
        try {
            TileEntity.addMapping(TileEntityManifold.class, "coolantloops.manifold");
        } catch (Throwable ignored) {}
    }

    @Test
    public void testItemDamageKeyBasics() {
        Item itemA = Items.iron_ingot != null ? Items.iron_ingot : Mockito.mock(Item.class);
        Item itemB = Items.gold_ingot != null ? Items.gold_ingot : Mockito.mock(Item.class);

        ItemDamageKey key1 = new ItemDamageKey(itemA, 0);
        ItemDamageKey key2 = new ItemDamageKey(itemA, 0);
        ItemDamageKey key3 = new ItemDamageKey(itemB, 0);
        ItemDamageKey key4 = new ItemDamageKey(itemA, 1);

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, key3);
        assertNotEquals(key1, key4);
        assertNotEquals(key1, null);
        assertNotEquals(key1, "string");

        assertSame(itemA, key1.getItem());
        assertEquals(0, key1.getDamage());
        assertNotNull(key1.toString());

        ItemStack stack = new ItemStack(itemA, 1, 0);
        ItemDamageKey fromStack = ItemDamageKey.of(stack);
        assertEquals(key1, fromStack);

        assertNull(ItemDamageKey.of((ItemStack) null));
        assertNull(ItemDamageKey.of((Item) null, 0));
    }

    @Test
    public void testLoopSegmentDirectPosition() {
        LoopSegment seg = new LoopSegment("pipe_10_20_30", 1.0, 0.1, 0.00005, 0.0, 100.0, 1200.0, 20.0);
        // Initially, position is not set
        assertFalse(seg.hasPosition(), "Segment without explicit setPosition must return false");
        assertEquals(0, seg.getBlockX());
        assertEquals(0, seg.getBlockY());
        assertEquals(0, seg.getBlockZ());

        // Set position explicitly
        seg.setPosition(10, 20, 30);
        assertTrue(seg.hasPosition());
        assertEquals(10, seg.getBlockX());
        assertEquals(20, seg.getBlockY());
        assertEquals(30, seg.getBlockZ());
        assertEquals(new ChunkCoordinates(10, 20, 30), seg.getCoordinates());

        // Test instantiation with direct coordinates
        LoopSegment segWithCoords = new LoopSegment(
            "pipe_direct",
            10,
            20,
            30,
            1.0,
            0.1,
            0.00005,
            0.0,
            100.0,
            1200.0,
            20.0);
        assertTrue(segWithCoords.hasPosition());
        assertEquals(10, segWithCoords.getBlockX());
        assertEquals(20, segWithCoords.getBlockY());
        assertEquals(30, segWithCoords.getBlockZ());
        assertEquals(new ChunkCoordinates(10, 20, 30), segWithCoords.getCoordinates());

        // Test instantiation with ChunkCoordinates
        LoopSegment segWithCc = new LoopSegment(
            "pipe_cc",
            new ChunkCoordinates(5, 6, 7),
            1.0,
            0.1,
            0.00005,
            0.0,
            100.0,
            1200.0,
            20.0);
        assertTrue(segWithCc.hasPosition());
        assertEquals(5, segWithCc.getBlockX());
        assertEquals(6, segWithCc.getBlockY());
        assertEquals(7, segWithCc.getBlockZ());
        assertEquals(new ChunkCoordinates(5, 6, 7), segWithCc.getCoordinates());

        segWithCc.setPosition((ChunkCoordinates) null);
        assertFalse(segWithCc.hasPosition());
        assertNull(segWithCc.getCoordinates());
    }

    @Test
    public void testBlockPosCoordParsingAndToString() {
        LoopGraphCrawler.BlockPosCoord coord1 = LoopGraphCrawler.BlockPosCoord.parse("pump:10:20:30");
        assertNotNull(coord1);
        assertEquals(10, coord1.x);
        assertEquals(20, coord1.y);
        assertEquals(30, coord1.z);
        assertEquals("pump:10:20:30", coord1.toString());

        LoopGraphCrawler.BlockPosCoord coord2 = LoopGraphCrawler.BlockPosCoord.parse("pump_15_25_35");
        assertNotNull(coord2);
        assertEquals(15, coord2.x);
        assertEquals(25, coord2.y);
        assertEquals(35, coord2.z);

        assertNull(LoopGraphCrawler.BlockPosCoord.parse(null));
        assertNull(LoopGraphCrawler.BlockPosCoord.parse(""));
        assertNull(LoopGraphCrawler.BlockPosCoord.parse("invalid"));
        assertNull(LoopGraphCrawler.BlockPosCoord.parse("pump:notanumber:20:30"));

        LoopGraphCrawler.BlockPosCoord sameCoord = new LoopGraphCrawler.BlockPosCoord(10, 20, 30);
        assertEquals(coord1, sameCoord);
        assertEquals(coord1.hashCode(), sameCoord.hashCode());
    }

    @Test
    public void testManifoldPumpPositionAndNbt() {
        TileEntityManifold mf = new TileEntityManifold();
        assertNull(mf.getActivePumpPos());
        assertNull(mf.getActivePumpId());

        LoopGraphCrawler.BlockPosCoord pumpPos = new LoopGraphCrawler.BlockPosCoord(100, 64, -200);
        mf.setActivePumpPos(pumpPos);
        assertEquals(pumpPos, mf.getActivePumpPos());
        assertEquals("pump:100:64:-200", mf.getActivePumpId());

        // Test NBT serialization round-trip
        NBTTagCompound nbt = new NBTTagCompound();
        mf.writeToNBT(nbt);
        assertTrue(nbt.hasKey("pumpX"));
        assertEquals(100, nbt.getInteger("pumpX"));
        assertEquals(64, nbt.getInteger("pumpY"));
        assertEquals(-200, nbt.getInteger("pumpZ"));

        TileEntityManifold mfRestored = new TileEntityManifold();
        mfRestored.readFromNBT(nbt);
        assertNotNull(mfRestored.getActivePumpPos());
        assertEquals(100, mfRestored.getActivePumpPos().x);
        assertEquals(64, mfRestored.getActivePumpPos().y);
        assertEquals(-200, mfRestored.getActivePumpPos().z);
        assertEquals("pump:100:64:-200", mfRestored.getActivePumpId());

        // Test legacy NBT string fallback
        NBTTagCompound legacyNbt = new NBTTagCompound();
        legacyNbt.setString("pumpId", "pump:42:50:60");
        TileEntityManifold mfLegacy = new TileEntityManifold();
        mfLegacy.readFromNBT(legacyNbt);
        assertNotNull(mfLegacy.getActivePumpPos());
        assertEquals(42, mfLegacy.getActivePumpPos().x);
        assertEquals(50, mfLegacy.getActivePumpPos().y);
        assertEquals(60, mfLegacy.getActivePumpPos().z);
    }
}
