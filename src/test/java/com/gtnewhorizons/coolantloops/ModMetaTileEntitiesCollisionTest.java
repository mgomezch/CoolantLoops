package com.gtnewhorizons.coolantloops;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.common.metatileentity.ModMetaTileEntities;

import gregtech.api.enums.MetaTileEntityIDs;

public class ModMetaTileEntitiesCollisionTest {

    @Test
    public void testNoCollisionWithGregTechAndTecTechMetaTileEntityIDs() {
        Set<Integer> gtIds = new HashSet<>();
        for (MetaTileEntityIDs idEnum : MetaTileEntityIDs.values()) {
            gtIds.add(idEnum.ID);
        }

        Set<Integer> coolantLoopIds = new HashSet<>();
        coolantLoopIds.add(ModMetaTileEntities.PUMP_ID);
        coolantLoopIds.add(ModMetaTileEntities.PRESSURIZED_HATCH_ID);
        coolantLoopIds.add(ModMetaTileEntities.HEAT_EXCHANGER_ID);
        coolantLoopIds.add(ModMetaTileEntities.DEGASSER_ID);

        int totalPipes = ModMetaTileEntities.COOLANT_PIPE_MATERIALS.length * ModMetaTileEntities.PIPE_THICKNESSES.length;
        for (int i = 0; i < totalPipes; i++) {
            coolantLoopIds.add(ModMetaTileEntities.COOLANT_PIPE_START_ID + i);
        }

        for (int id : coolantLoopIds) {
            assertFalse(
                gtIds.contains(id),
                "CoolantLoops MTE ID " + id + " collides with a core GT/TecTech MetaTileEntityID enum!"
            );
        }

        // Also verify no overlap with ModularNuclear IDs (32100..32115)
        for (int mnId = 32100; mnId <= 32115; mnId++) {
            assertFalse(
                coolantLoopIds.contains(mnId),
                "CoolantLoops MTE ID collides with ModularNuclear ID " + mnId
            );
        }

        assertTrue(coolantLoopIds.size() >= 39, "Expected at least 39 distinct CoolantLoops MTE IDs");
    }
}
