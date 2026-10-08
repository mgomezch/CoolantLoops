package com.gtnewhorizons.coolantloops.common.metatileentity;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.coolantloops.common.metatileentity.pipe.MTECoolantPipe;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;

public class ModMetaTileEntities {

    public static MTECoolantPump coolantPump;
    public static MTEHatchPressurizedFluid pressurizedFluidHatch;
    public static com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedHeatExchanger pressurizedHeatExchanger;
    public static com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedDegasser pressurizedDegasser;

    public static final int PUMP_ID = 15370;
    public static final int PRESSURIZED_HATCH_ID = 15371;
    public static final int HEAT_EXCHANGER_ID = 15372;
    public static final int DEGASSER_ID = 15373;
    public static final int COOLANT_PIPE_START_ID = 15400;

    public static final Materials[] COOLANT_PIPE_MATERIALS = new Materials[] {
        Materials.CastIron,
        Materials.Steel,
        Materials.StainlessSteel,
        Materials.Titanium,
        Materials.TungstenSteel,
        Materials.Osmium,
        Materials.Neutronium
    };

    public static final float[] PIPE_THICKNESSES = new float[] {
        0.25F,  // Tiny
        0.375F, // Small
        0.50F,  // Medium
        0.75F,  // Large
        0.875F  // Huge
    };

    public static final String[] SIZE_NAMES = new String[] {
        "Tiny", "Small", "Medium", "Large", "Huge"
    };

    public static final int[] BASE_CAPACITIES = new int[] {
        180,    // CastIron
        240,    // Steel
        360,    // StainlessSteel
        480,    // Titanium
        600,    // TungstenSteel
        1200,   // Osmium
        16800   // Neutronium
    };

    public static final int[] HEAT_RESISTANCES = new int[] {
        2250,   // CastIron
        2500,   // Steel
        3000,   // StainlessSteel
        5000,   // Titanium
        7500,   // TungstenSteel
        10000,  // Osmium
        1000000 // Neutronium
    };

    public static final MTECoolantPipe[][] coolantPipes = new MTECoolantPipe[COOLANT_PIPE_MATERIALS.length][PIPE_THICKNESSES.length];
    public static final ItemStack[][] coolantPipeItems = new ItemStack[COOLANT_PIPE_MATERIALS.length][PIPE_THICKNESSES.length];

    public static void init() {
        coolantPump = new MTECoolantPump(PUMP_ID, "multimachine.coolantpump", "Coolant Loop Pump");
        pressurizedFluidHatch = new MTEHatchPressurizedFluid(
            PRESSURIZED_HATCH_ID,
            "hatch.pressurizedfluid",
            "Pressurized Fluid Hatch",
            4 // EV Tier
        );
        pressurizedHeatExchanger = new com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedHeatExchanger(
            HEAT_EXCHANGER_ID,
            "multimachine.pressurizedheatexchanger",
            "Pressurized Heat Exchanger");
        pressurizedDegasser = new com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedDegasser(
            DEGASSER_ID,
            "multimachine.pressurizeddegasser",
            "Pressurized Degasser");

        int id = COOLANT_PIPE_START_ID;
        for (int m = 0; m < COOLANT_PIPE_MATERIALS.length; m++) {
            Materials mat = COOLANT_PIPE_MATERIALS[m];
            int baseCap = BASE_CAPACITIES[m];
            int heatRes = HEAT_RESISTANCES[m];

            for (int s = 0; s < PIPE_THICKNESSES.length; s++) {
                float thick = PIPE_THICKNESSES[s];
                int cap;
                if (s == 0) cap = Math.max(1, baseCap / 6);
                else if (s == 1) cap = Math.max(1, baseCap / 3);
                else if (s == 2) cap = baseCap;
                else if (s == 3) cap = baseCap * 2;
                else cap = baseCap * 4;

                String internalName = "coolantpipe." + mat.mName.toLowerCase() + "." + SIZE_NAMES[s].toLowerCase();
                MTECoolantPipe pipe = new MTECoolantPipe(id, internalName, "gt.oreprefix.material_fluid_pipe", thick, mat, cap, heatRes, true);
                coolantPipes[m][s] = pipe;
                coolantPipeItems[m][s] = pipe.getStackForm(1L);
                id++;
            }
        }
    }

    public static int getMaterialIndex(Materials mat) {
        if (mat == null) return -1;
        for (int i = 0; i < COOLANT_PIPE_MATERIALS.length; i++) {
            if (COOLANT_PIPE_MATERIALS[i] == mat || COOLANT_PIPE_MATERIALS[i].mName.equalsIgnoreCase(mat.mName)) {
                return i;
            }
        }
        if (mat == Materials.Iron || mat == Materials.WroughtIron) {
            return 0; // CastIron/Iron group
        }
        return -1;
    }

    public static ItemStack getCoolantPipe(Materials mat, int sizeIndex) {
        int mIdx = getMaterialIndex(mat);
        if (mIdx >= 0 && sizeIndex >= 0 && sizeIndex < PIPE_THICKNESSES.length) {
            return coolantPipeItems[mIdx][sizeIndex];
        }
        return null;
    }

    public static boolean isCoolantPipe(IMetaTileEntity mte) {
        return mte instanceof MTECoolantPipe;
    }
}
