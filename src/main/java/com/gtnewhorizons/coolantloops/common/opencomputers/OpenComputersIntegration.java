package com.gtnewhorizons.coolantloops.common.opencomputers;

import cpw.mods.fml.common.Loader;
import li.cil.oc.api.Driver;

public class OpenComputersIntegration {

    public static void register() {
        if (Loader.isModLoaded("OpenComputers")) {
            try {
                Driver.add(new DriverLoopInstrument());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
