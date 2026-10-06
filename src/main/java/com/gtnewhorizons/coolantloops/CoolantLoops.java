package com.gtnewhorizons.coolantloops;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
    modid = CoolantLoops.MODID,
    name = CoolantLoops.MODNAME,
    version = Tags.VERSION,
    dependencies = "required-after:gregtech;after:Railcraft")
public class CoolantLoops {

    public static final String MODID = "coolantloops";
    public static final String MODNAME = "Coolant Loops";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance(MODID)
    public static CoolantLoops instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOG.info("Pre-initializing Coolant Loops thermodynamic engine...");
        com.gtnewhorizons.coolantloops.common.block.ModBlocks.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOG.info("Initializing Coolant Loops...");
        com.gtnewhorizons.coolantloops.common.metatileentity.ModMetaTileEntities.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        LOG.info("Post-initializing Coolant Loops...");
    }
}
