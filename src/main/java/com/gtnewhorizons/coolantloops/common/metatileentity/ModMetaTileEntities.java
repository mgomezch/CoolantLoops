package com.gtnewhorizons.coolantloops.common.metatileentity;

import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTECoolantPump;

public class ModMetaTileEntities {

    public static MTECoolantPump coolantPump;
    public static MTEHatchPressurizedFluid pressurizedFluidHatch;
    public static com.gtnewhorizons.coolantloops.common.metatileentity.multi.MTEPressurizedHeatExchanger pressurizedHeatExchanger;

    public static final int PUMP_ID = 15100;
    public static final int PRESSURIZED_HATCH_ID = 15101;
    public static final int HEAT_EXCHANGER_ID = 15102;

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
    }
}
