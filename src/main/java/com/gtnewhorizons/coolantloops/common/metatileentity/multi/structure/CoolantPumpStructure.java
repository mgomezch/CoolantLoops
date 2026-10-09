package com.gtnewhorizons.coolantloops.common.metatileentity.multi.structure;

import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.coolantloops.common.metatileentity.CoolantStructureChannels;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.engine.CoolantPipingRegistry;
import com.gtnewhorizons.coolantloops.engine.LoopGraphCrawler;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.util.GTStructureUtility;
import mods.railcraft.common.blocks.machine.TileMultiBlock;
import mods.railcraft.common.blocks.machine.beta.EnumMachineBeta;
import mods.railcraft.common.modules.ModuleAdvancedTanks;

public final class CoolantPumpStructure {

    private CoolantPumpStructure() {}

    private static IStructureDefinition<MTECoolantPump> STRUCTURE_DEFINITION = null;

    public static Materials getMaterialForTier(int tier) {
        switch (tier) {
            case 0:
                return Materials.CastIron;
            case 1:
                return Materials.Steel;
            case 2:
                return Materials.StainlessSteel;
            case 3:
                return Materials.Titanium;
            case 4:
                return Materials.TungstenSteel;
            case 5:
                return Materials.Neutronium;
            default:
                if (tier < 0) return Materials.CastIron;
                return Materials.Neutronium;
        }
    }

    public static Materials getMaterialFromTrigger(ItemStack trigger) {
        if (trigger == null) return Materials.Steel;
        int tier;
        try {
            if (ChannelDataAccessor.hasSubChannel(trigger, "tier")) {
                tier = ChannelDataAccessor.getChannelData(trigger, "tier");
            } else {
                tier = trigger.stackSize - 1;
            }
        } catch (Throwable t) {
            tier = trigger.stackSize - 1;
        }
        return getMaterialForTier(tier);
    }

    public static int getWidthFromTrigger(ItemStack trigger) {
        if (trigger == null) return 3;
        int val = CoolantStructureChannels.STRUCTURE_WIDTH.getValueClamped(trigger, 0, 9);
        switch (val) {
            case 0:
            case 1:
            case 3:
                return 3;
            case 2:
            case 5:
                return 5;
            case 7:
                return 7;
            case 4:
            case 9:
                return 9;
            default:
                if (val <= 0) return 3;
                if (val == 6) return 7;
                if (val >= 8) return 9;
                return 3;
        }
    }

    public static int getHeightFromTrigger(ItemStack trigger) {
        if (trigger == null) return 4;
        int val = CoolantStructureChannels.STRUCTURE_HEIGHT.getValueClamped(trigger, 0, 8);
        switch (val) {
            case 0:
            case 1:
            case 4:
                return 4;
            case 2:
            case 5:
                return 5;
            case 3:
            case 6:
                return 6;
            case 7:
                return 7;
            case 8:
                return 8;
            default:
                if (val < 4) return 4;
                if (val > 8) return 8;
                return val;
        }
    }

    public static ItemStack getTankWallItem(Materials mat) {
        try {
            if (mat == Materials.CastIron && EnumMachineBeta.TANK_IRON_WALL != null) {
                return EnumMachineBeta.TANK_IRON_WALL.getItem();
            }
            if (mat == Materials.Steel && EnumMachineBeta.TANK_STEEL_WALL != null) {
                return EnumMachineBeta.TANK_STEEL_WALL.getItem();
            }
            if (mat == Materials.StainlessSteel && ModuleAdvancedTanks.STAINLESS != null
                && ModuleAdvancedTanks.STAINLESS.TANK_WALL != null) {
                return ModuleAdvancedTanks.STAINLESS.TANK_WALL.getItem();
            }
            if (mat == Materials.Titanium && ModuleAdvancedTanks.TITANIUM != null
                && ModuleAdvancedTanks.TITANIUM.TANK_WALL != null) {
                return ModuleAdvancedTanks.TITANIUM.TANK_WALL.getItem();
            }
            if (mat == Materials.TungstenSteel && ModuleAdvancedTanks.TUNGSTENSTEEL != null
                && ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_WALL != null) {
                return ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_WALL.getItem();
            }
            if (mat == Materials.Neutronium && ModuleAdvancedTanks.NEUTRONIUM != null
                && ModuleAdvancedTanks.NEUTRONIUM.TANK_WALL != null) {
                return ModuleAdvancedTanks.NEUTRONIUM.TANK_WALL.getItem();
            }
            if (mat == Materials.Aluminium && ModuleAdvancedTanks.ALUMINIUM != null
                && ModuleAdvancedTanks.ALUMINIUM.TANK_WALL != null) {
                return ModuleAdvancedTanks.ALUMINIUM.TANK_WALL.getItem();
            }
            if (EnumMachineBeta.TANK_STEEL_WALL != null) {
                return EnumMachineBeta.TANK_STEEL_WALL.getItem();
            }
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        } catch (Throwable t) {
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        }
    }

    public static ItemStack getTankValveItem(Materials mat) {
        try {
            if (mat == Materials.CastIron && EnumMachineBeta.TANK_IRON_VALVE != null) {
                return EnumMachineBeta.TANK_IRON_VALVE.getItem();
            }
            if (mat == Materials.Steel && EnumMachineBeta.TANK_STEEL_VALVE != null) {
                return EnumMachineBeta.TANK_STEEL_VALVE.getItem();
            }
            if (mat == Materials.StainlessSteel && ModuleAdvancedTanks.STAINLESS != null
                && ModuleAdvancedTanks.STAINLESS.TANK_VALVE != null) {
                return ModuleAdvancedTanks.STAINLESS.TANK_VALVE.getItem();
            }
            if (mat == Materials.Titanium && ModuleAdvancedTanks.TITANIUM != null
                && ModuleAdvancedTanks.TITANIUM.TANK_VALVE != null) {
                return ModuleAdvancedTanks.TITANIUM.TANK_VALVE.getItem();
            }
            if (mat == Materials.TungstenSteel && ModuleAdvancedTanks.TUNGSTENSTEEL != null
                && ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_VALVE != null) {
                return ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_VALVE.getItem();
            }
            if (mat == Materials.Neutronium && ModuleAdvancedTanks.NEUTRONIUM != null
                && ModuleAdvancedTanks.NEUTRONIUM.TANK_VALVE != null) {
                return ModuleAdvancedTanks.NEUTRONIUM.TANK_VALVE.getItem();
            }
            if (mat == Materials.Aluminium && ModuleAdvancedTanks.ALUMINIUM != null
                && ModuleAdvancedTanks.ALUMINIUM.TANK_VALVE != null) {
                return ModuleAdvancedTanks.ALUMINIUM.TANK_VALVE.getItem();
            }
            if (EnumMachineBeta.TANK_STEEL_VALVE != null) {
                return EnumMachineBeta.TANK_STEEL_VALVE.getItem();
            }
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        } catch (Throwable t) {
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        }
    }

    public static ItemStack getTankGaugeItem(Materials mat) {
        try {
            if (mat == Materials.CastIron && EnumMachineBeta.TANK_IRON_GAUGE != null) {
                return EnumMachineBeta.TANK_IRON_GAUGE.getItem();
            }
            if (mat == Materials.Steel && EnumMachineBeta.TANK_STEEL_GAUGE != null) {
                return EnumMachineBeta.TANK_STEEL_GAUGE.getItem();
            }
            if (mat == Materials.StainlessSteel && ModuleAdvancedTanks.STAINLESS != null
                && ModuleAdvancedTanks.STAINLESS.TANK_GAUGE != null) {
                return ModuleAdvancedTanks.STAINLESS.TANK_GAUGE.getItem();
            }
            if (mat == Materials.Titanium && ModuleAdvancedTanks.TITANIUM != null
                && ModuleAdvancedTanks.TITANIUM.TANK_GAUGE != null) {
                return ModuleAdvancedTanks.TITANIUM.TANK_GAUGE.getItem();
            }
            if (mat == Materials.TungstenSteel && ModuleAdvancedTanks.TUNGSTENSTEEL != null
                && ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_GAUGE != null) {
                return ModuleAdvancedTanks.TUNGSTENSTEEL.TANK_GAUGE.getItem();
            }
            if (mat == Materials.Neutronium && ModuleAdvancedTanks.NEUTRONIUM != null
                && ModuleAdvancedTanks.NEUTRONIUM.TANK_GAUGE != null) {
                return ModuleAdvancedTanks.NEUTRONIUM.TANK_GAUGE.getItem();
            }
            if (mat == Materials.Aluminium && ModuleAdvancedTanks.ALUMINIUM != null
                && ModuleAdvancedTanks.ALUMINIUM.TANK_GAUGE != null) {
                return ModuleAdvancedTanks.ALUMINIUM.TANK_GAUGE.getItem();
            }
            if (EnumMachineBeta.TANK_STEEL_GAUGE != null) {
                return EnumMachineBeta.TANK_STEEL_GAUGE.getItem();
            }
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        } catch (Throwable t) {
            return new ItemStack(net.minecraft.init.Items.stick, 1);
        }
    }

    public static IStructureElement<MTECoolantPump> ofCornerFrame() {
        return new IStructureElement<MTECoolantPump>() {

            private IStructureElement<MTECoolantPump> getElement(ItemStack trigger) {
                Materials mat = getMaterialFromTrigger(trigger);
                return GTStructureUtility.<MTECoolantPump>ofFrame(mat);
            }

            @Override
            public boolean check(MTECoolantPump t, World world, int x, int y, int z) {
                return GTStructureUtility.<MTECoolantPump>ofFrame(t.getTankMaterial())
                    .check(t, world, x, y, z);
            }

            @Override
            public boolean spawnHint(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger) {
                return getElement(trigger).spawnHint(t, world, x, y, z, trigger);
            }

            @Override
            public boolean placeBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger) {
                return getElement(trigger).placeBlock(t, world, x, y, z, trigger);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                IItemSource s, EntityPlayerMP actor, Consumer<IChatComponent> chatter) {
                return getElement(trigger).survivalPlaceBlock(t, world, x, y, z, trigger, s, actor, chatter);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return getElement(trigger).survivalPlaceBlock(t, world, x, y, z, trigger, env);
            }

            @Override
            public BlocksToPlace getBlocksToPlace(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return getElement(trigger).getBlocksToPlace(t, world, x, y, z, trigger, env);
            }
        };
    }

    public static IStructureElement<MTECoolantPump> ofTankWall() {
        return new IStructureElement<MTECoolantPump>() {

            private IStructureElement<MTECoolantPump> getElement(ItemStack trigger) {
                Materials mat = getMaterialFromTrigger(trigger);
                ItemStack stack = getTankWallItem(mat);
                if (stack == null || stack.getItem() == null) {
                    stack = EnumMachineBeta.TANK_STEEL_WALL != null ? EnumMachineBeta.TANK_STEEL_WALL.getItem() : null;
                }
                Block block = (stack != null && stack.getItem() != null) ? Block.getBlockFromItem(stack.getItem())
                    : null;
                if (block == null) {
                    block = Block.getBlockFromName("stone");
                }
                int meta = stack != null ? stack.getItemDamage() : 0;
                return StructureUtility.ofBlock(block, meta);
            }

            @Override
            public boolean check(MTECoolantPump t, World world, int x, int y, int z) {
                TileEntity te = world.getTileEntity(x, y, z);
                if (te instanceof TileMultiBlock) {
                    TileMultiBlock tmb = (TileMultiBlock) te;
                    Materials mat = MTECoolantPump.getRailcraftTankMaterial(tmb);
                    if (t.getTankMaterial() != null) {
                        return CoolantPipingRegistry.areMaterialsEqual(mat, t.getTankMaterial());
                    }
                    return LoopGraphCrawler.isAllowedCoolantPipeMaterial(mat);
                }
                return false;
            }

            @Override
            public boolean spawnHint(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger) {
                return getElement(trigger).spawnHint(t, world, x, y, z, trigger);
            }

            @Override
            public boolean placeBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger) {
                return getElement(trigger).placeBlock(t, world, x, y, z, trigger);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                IItemSource s, EntityPlayerMP actor, Consumer<IChatComponent> chatter) {
                return getElement(trigger).survivalPlaceBlock(t, world, x, y, z, trigger, s, actor, chatter);
            }

            @Override
            public PlaceResult survivalPlaceBlock(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return getElement(trigger).survivalPlaceBlock(t, world, x, y, z, trigger, env);
            }

            @Override
            public BlocksToPlace getBlocksToPlace(MTECoolantPump t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return getElement(trigger).getBlocksToPlace(t, world, x, y, z, trigger, env);
            }
        };
    }

    public static String[] makePumpBaseShape0(int width) {
        String[] rows = new String[width];
        for (int r = 0; r < width; r++) {
            char[] row = new char[width];
            for (int c = 0; c < width; c++) {
                if ((r == 0 || r == width - 1) && (c == 0 || c == width - 1)) {
                    row[c] = 'F';
                } else if (r == 0 && c == width / 2) {
                    row[c] = '~';
                } else {
                    row[c] = 'C';
                }
            }
            rows[r] = new String(row);
        }
        return rows;
    }

    public static String[] makePumpBaseShape1(int width) {
        String[] rows = new String[width];
        for (int r = 0; r < width; r++) {
            char[] row = new char[width];
            for (int c = 0; c < width; c++) {
                if ((r == 0 || r == width - 1) && (c == 0 || c == width - 1)) {
                    row[c] = 'F';
                } else {
                    row[c] = 'C';
                }
            }
            rows[r] = new String(row);
        }
        return rows;
    }

    public static String[] makeTankBottomShape(int width) {
        String[] rows = new String[width];
        for (int r = 0; r < width; r++) {
            char[] row = new char[width];
            for (int c = 0; c < width; c++) {
                row[c] = 'w';
            }
            rows[r] = new String(row);
        }
        return rows;
    }

    public static String[] makeTankMidShape(int width) {
        String[] rows = new String[width];
        for (int r = 0; r < width; r++) {
            char[] row = new char[width];
            for (int c = 0; c < width; c++) {
                if (r == 0 || r == width - 1 || c == 0 || c == width - 1) {
                    row[c] = 'w';
                } else {
                    row[c] = ' ';
                }
            }
            rows[r] = new String(row);
        }
        return rows;
    }

    public static String[] makeTankTopShape(int width) {
        return makeTankBottomShape(width);
    }

    public static IStructureDefinition<MTECoolantPump> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            StructureDefinition.Builder<MTECoolantPump> b = StructureDefinition.<MTECoolantPump>builder();
            for (int w : new int[] { 3, 5, 7, 9 }) {
                b.addShape(
                    "pump_" + w + "_base_0",
                    StructureUtility.transpose(new String[][] { makePumpBaseShape0(w) }))
                    .addShape(
                        "pump_" + w + "_base_1",
                        StructureUtility.transpose(new String[][] { makePumpBaseShape1(w) }))
                    .addShape(
                        "tank_" + w + "_bottom",
                        StructureUtility.transpose(new String[][] { makeTankBottomShape(w) }))
                    .addShape("tank_" + w + "_mid", StructureUtility.transpose(new String[][] { makeTankMidShape(w) }))
                    .addShape("tank_" + w + "_top", StructureUtility.transpose(new String[][] { makeTankTopShape(w) }));
            }
            Block casing = GregTechAPI.sBlockCasings4 != null ? GregTechAPI.sBlockCasings4
                : Block.getBlockFromName("stone");
            Block hintBlock = StructureLibAPI.getBlockHint() != null ? StructureLibAPI.getBlockHint()
                : Block.getBlockFromName("stone");
            b.addShape(
                "pump_3x3",
                StructureUtility.transpose(new String[][] { makePumpBaseShape0(3), makePumpBaseShape1(3) }))
                .addElement('F', ofCornerFrame())
                .addElement('w', ofTankWall())
                .addElement(
                    'C',
                    StructureUtility.ofChain(
                        GTStructureUtility.ofHatchAdder(MTECoolantPump::addBottomHatch, 48 + 2, hintBlock, 0),
                        StructureUtility.ofBlock(casing, 2)));
            STRUCTURE_DEFINITION = b.build();
        }
        return STRUCTURE_DEFINITION;
    }
}
