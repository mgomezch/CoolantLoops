package com.gtnewhorizons.coolantloops.common.recipe;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.coolantloops.common.block.ModBlocks;
import com.gtnewhorizons.coolantloops.common.metatileentity.ModMetaTileEntities;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;

public class ModRecipes {

    public static void init() {
        registerCraftingRecipes();
        registerAssemblerRecipes();
    }

    private static void registerCraftingRecipes() {
        // Manifold: 4 Steel Plates, 4 Steel Normal Pipes, 1 Solid Steel Casing
        ItemStack steelPlate = GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 1L);
        ItemStack steelPipe = GTOreDictUnificator.get(OrePrefixes.pipeMedium, Materials.Steel, 1L);
        ItemStack steelCasing = ItemList.Casing_SolidSteel.get(1L);

        if (ModBlocks.manifold != null && steelPlate != null && steelPipe != null && steelCasing != null) {
            GTModHandler.addCraftingRecipe(
                new ItemStack(ModBlocks.manifold, 1),
                GTModHandler.RecipeBits.NOT_REMOVABLE | GTModHandler.RecipeBits.REVERSIBLE,
                new Object[] { "PIP", "ICI", "PIP", 'P', steelPlate, 'I', steelPipe, 'C', steelCasing });
        }

        // Loop Instrument: 1 Steel Pipe, 1 Redstone Comparator, 1 Small Steel Gear
        ItemStack smallSteelGear = GTOreDictUnificator.get(OrePrefixes.gearGtSmall, Materials.Steel, 1L);
        ItemStack comparator = new ItemStack(Items.comparator);

        if (ModBlocks.loopInstrument != null && steelPipe != null && smallSteelGear != null) {
            GTModHandler.addCraftingRecipe(
                new ItemStack(ModBlocks.loopInstrument, 1),
                GTModHandler.RecipeBits.NOT_REMOVABLE | GTModHandler.RecipeBits.REVERSIBLE,
                new Object[] { " G ", " P ", " C ", 'G', smallSteelGear, 'P', steelPipe, 'C', comparator });
        }

        registerCoolantPipeCraftingRecipes();
    }

    private static final OrePrefixes[] PIPE_PREFIXES = new OrePrefixes[] {
        OrePrefixes.pipeTiny,
        OrePrefixes.pipeSmall,
        OrePrefixes.pipeMedium,
        OrePrefixes.pipeLarge,
        OrePrefixes.pipeHuge
    };

    public static int getMaterialInsulationTier(Materials mat) {
        if (mat == Materials.CastIron || mat == Materials.Steel) return 1;
        if (mat == Materials.StainlessSteel) return 2;
        if (mat == Materials.Titanium) return 3;
        if (mat == Materials.TungstenSteel) return 4;
        if (mat == Materials.Osmium) return 5;
        return 6; // Neutronium / tier > 5 repeats tier 2 (Mica)
    }

    public static ItemStack getInsulationItem(int tier) {
        ItemStack stack = null;
        switch (tier) {
            case 1:
                stack = GTModHandler.getModItem("dreamcraft", "item.AluminoSilicateWool", 1L);
                if (stack == null) stack = GTModHandler.getModItem("dreamcraft", "AluminoSilicateWool", 1L);
                if (stack == null) stack = GTOreDictUnificator.get("woolAluminoSilicate", 1L);
                if (stack == null) stack = GTOreDictUnificator.get(OrePrefixes.plate, Materials.Asbestos, 1L);
                if (stack == null) stack = new ItemStack(net.minecraft.init.Blocks.wool);
                break;
            case 2:
                stack = GTModHandler.getModItem("dreamcraft", "item.MicaInsulatorFoil", 1L);
                if (stack == null) stack = GTModHandler.getModItem("dreamcraft", "MicaInsulatorFoil", 1L);
                if (stack == null) stack = GTOreDictUnificator.get(OrePrefixes.foil, Materials.Mica, 1L);
                if (stack == null) stack = ItemList.Naquarite_Universal_Insulator_Foil.get(1L);
                if (stack == null) stack = new ItemStack(net.minecraft.init.Items.paper);
                break;
            case 3:
                stack = GTModHandler.getModItem("GalacticraftMars", "item.itemBasicAsteroids", 1L, 7);
                if (stack == null) stack = GTModHandler.getModItem("GalacticraftMars", "itemBasicAsteroids", 1L, 7);
                if (stack == null) stack = getInsulationItem(2);
                break;
            case 4:
                stack = GTModHandler.getModItem("GalaxySpace", "item.ThermalClothT2", 1L);
                if (stack == null) stack = GTModHandler.getModItem("GalaxySpace", "ThermalClothT2", 1L);
                if (stack == null) stack = getInsulationItem(2);
                break;
            case 5:
                stack = ItemList.Naquarite_Universal_Insulator_Foil.get(1L);
                if (stack == null) stack = getInsulationItem(2);
                break;
            default:
                // Tiers > 5 repeat Mica Insulator Foil
                stack = getInsulationItem(2);
                break;
        }
        return stack;
    }

    private static void registerCoolantPipeCraftingRecipes() {
        for (int m = 0; m < ModMetaTileEntities.COOLANT_PIPE_MATERIALS.length; m++) {
            Materials mat = ModMetaTileEntities.COOLANT_PIPE_MATERIALS[m];
            int tier = getMaterialInsulationTier(mat);
            ItemStack insulation = getInsulationItem(tier);

            for (int s = 0; s < ModMetaTileEntities.PIPE_THICKNESSES.length; s++) {
                OrePrefixes prefix = PIPE_PREFIXES[s];
                ItemStack originalPipe = GTOreDictUnificator.get(prefix, mat, 1L);
                if (originalPipe == null && mat == Materials.CastIron) {
                    originalPipe = GTOreDictUnificator.get(prefix, Materials.Iron, 1L);
                }
                ItemStack coolantPipe = ModMetaTileEntities.coolantPipeItems[m][s];

                if (originalPipe != null && insulation != null && coolantPipe != null) {
                    GTModHandler.addCraftingRecipe(
                        gregtech.api.util.GTUtility.copyAmount(3, coolantPipe),
                        GTModHandler.RecipeBits.NOT_REMOVABLE | GTModHandler.RecipeBits.REVERSIBLE,
                        new Object[] {
                            "Z P Z",
                            "Z P Z",
                            "Z P Z",
                            'Z', insulation,
                            'P', originalPipe
                        }
                    );
                }
            }
        }
    }

    private static void registerAssemblerRecipes() {
        // 1. Coolant Loop Pump (EV)
        if (ModMetaTileEntities.coolantPump != null) {
            ItemStack evHull = ItemList.Hull_EV.get(1L);
            ItemStack evMotor = ItemList.Electric_Motor_EV.get(2L);
            ItemStack evPump = ItemList.Electric_Pump_EV.get(2L);
            ItemStack largePipe = GTOreDictUnificator.get(OrePrefixes.pipeLarge, Materials.TungstenSteel, 1L);
            ItemStack circuit = GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 1L);

            if (evHull != null && evMotor != null && evPump != null && largePipe != null && circuit != null) {
                GTValues.RA.stdBuilder()
                    .itemInputs(evHull, evMotor, evPump, largePipe, circuit)
                    .itemOutputs(ModMetaTileEntities.coolantPump.getStackForm(1L))
                    .duration(30 * SECONDS)
                    .eut(1920) // EV tier
                    .addTo(assemblerRecipes);
            }
        }

        // 2. Pressurized Fluid Hatch (EV)
        if (ModMetaTileEntities.pressurizedFluidHatch != null) {
            ItemStack evHull = ItemList.Hull_EV.get(1L);
            ItemStack evPump = ItemList.Electric_Pump_EV.get(1L);
            ItemStack normalPipe = GTOreDictUnificator.get(OrePrefixes.pipeMedium, Materials.Titanium, 1L);
            ItemStack plate = GTOreDictUnificator.get(OrePrefixes.plate, Materials.Titanium, 1L);

            if (evHull != null && evPump != null && normalPipe != null && plate != null) {
                GTValues.RA.stdBuilder()
                    .itemInputs(evHull, evPump, normalPipe, plate)
                    .itemOutputs(ModMetaTileEntities.pressurizedFluidHatch.getStackForm(1L))
                    .duration(20 * SECONDS)
                    .eut(1920)
                    .addTo(assemblerRecipes);
            }
        }

        // 3. Pressurized Heat Exchanger Multiblock Controller
        if (ModMetaTileEntities.pressurizedHeatExchanger != null) {
            ItemStack evHull = ItemList.Hull_EV.get(1L);
            ItemStack evPump = ItemList.Electric_Pump_EV.get(2L);
            ItemStack tiPlate = GTOreDictUnificator.get(OrePrefixes.plate, Materials.Titanium, 2L);
            ItemStack circuit = GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 1L);

            if (evHull != null && evPump != null && tiPlate != null && circuit != null) {
                GTValues.RA.stdBuilder()
                    .itemInputs(evHull, evPump, tiPlate, circuit)
                    .itemOutputs(ModMetaTileEntities.pressurizedHeatExchanger.getStackForm(1L))
                    .duration(30 * SECONDS)
                    .eut(1920)
                    .addTo(assemblerRecipes);
            }
        }

        registerCoolantPipeAssemblerRecipes();
    }

    private static void registerCoolantPipeAssemblerRecipes() {
        if (assemblerRecipes == null || GTValues.RA == null) {
            return;
        }

        for (int m = 0; m < ModMetaTileEntities.COOLANT_PIPE_MATERIALS.length; m++) {
            Materials mat = ModMetaTileEntities.COOLANT_PIPE_MATERIALS[m];
            int tier = getMaterialInsulationTier(mat);
            ItemStack insulation = getInsulationItem(tier);

            for (int s = 0; s < ModMetaTileEntities.PIPE_THICKNESSES.length; s++) {
                OrePrefixes prefix = PIPE_PREFIXES[s];
                ItemStack originalPipe = GTOreDictUnificator.get(prefix, mat, 1L);
                if (originalPipe == null && mat == Materials.CastIron) {
                    originalPipe = GTOreDictUnificator.get(prefix, Materials.Iron, 1L);
                }
                ItemStack coolantPipe = ModMetaTileEntities.coolantPipeItems[m][s];

                if (originalPipe != null && insulation != null && coolantPipe != null) {
                    long eut = tier <= 5 ? GTValues.VP[tier] : GTValues.VP[6];
                    GTValues.RA.stdBuilder()
                        .itemInputs(
                            gregtech.api.util.GTUtility.copyAmount(3, originalPipe),
                            gregtech.api.util.GTUtility.copyAmount(6, insulation))
                        .itemOutputs(gregtech.api.util.GTUtility.copyAmount(3, coolantPipe))
                        .duration(15 * SECONDS)
                        .eut(eut)
                        .addTo(assemblerRecipes);
                }
            }
        }
    }
}
