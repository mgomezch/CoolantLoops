package com.gtnewhorizons.coolantloops.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.coolantloops.CoolantLoops;
import com.gtnewhorizons.coolantloops.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.coolantloops.common.gui.CoolantBathGui;
import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.common.metatileentity.multi.util.CoolantBathSlot;
import com.gtnewhorizons.coolantloops.common.util.CoolantBathRegistry;
import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopDevice;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.coolantloops.network.PacketSyncCoolantBath;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * Coolant Bath Multiblock.
 *
 * Variable-size rectangular-cuboid, strictly 3 blocks tall:
 * - Layer 0 (bottom): heatproof casings, controller at front bottom center (narrow end).
 * - Layer 1 (middle): heatproof casing outer walls, coolant loop inlet above controller,
 * outlet on opposite narrow end, hollow interior (air/coolant space) for item slots.
 * - Layer 2 (top): tiered glass roof.
 *
 * Unlocked sizes:
 * - EV glass (Tier 4): 5x7 footprint, 3x5 = 15 slots.
 * - IV glass (Tier 5): 7x9 footprint, 5x7 = 35 slots.
 * - LuV glass (Tier 6): 9x11 footprint, 7x9 = 63 slots.
 */
public class MTECoolantBath extends MTEEnhancedMultiBlockBase<MTECoolantBath>
    implements ICoolantLoopDevice, ISurvivalConstructable {

    public static final int CASING_INDEX_HEAT_PROOF = 11;
    public static final Set<MTECoolantBath> ACTIVE_CLIENT_BATHS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    protected MTEHatchPressurizedFluid mPrimaryInlet = null;
    protected MTEHatchPressurizedFluid mPrimaryOutlet = null;
    protected ICoolantLoopPump mConnectedPump = null;

    protected double mCurrentCoolantTemp = 20.0;
    protected ConvectiveHeatTransferModel mConvectiveModel = null;
    public int mGlassTier = -1;
    public int mStructureTier = 0; // 1: 5x7 (15 slots), 2: 7x9 (35 slots), 3: 9x11 (63 slots)
    public int mInteriorWidth = 3;
    public int mInteriorLength = 5;

    public CoolantBathSlot[] mSlots = new CoolantBathSlot[15];
    protected boolean mNeedsSync = false;

    // Client-side synchronized slot data
    public PacketSyncCoolantBath.SlotSyncData[] clientSlots = null;
    public int clientStructureTier = 0;

    public MTECoolantBath(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTECoolantBath(String aName) {
        super(aName);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTECoolantBath(mName);
    }

    @Override
    public boolean shouldCheckMaintenance() {
        return false; // Passive thermal exchanger, no electrical breakdown
    }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        return true;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    @Override
    protected MTEMultiBlockBaseGui<?> getGui() {
        return new CoolantBathGui(this);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings1, CASING_INDEX_HEAT_PROOF),
                TextureFactory.of(
                    aActive ? Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER_ACTIVE
                        : Textures.BlockIcons.OVERLAY_FRONT_HEAT_EXCHANGER) };
        }
        return new ITexture[] { TextureFactory.of(GregTechAPI.sBlockCasings1, CASING_INDEX_HEAT_PROOF) };
    }

    public MTEHatchPressurizedFluid getPrimaryInlet() {
        return mPrimaryInlet;
    }

    public MTEHatchPressurizedFluid getPrimaryOutlet() {
        return mPrimaryOutlet;
    }

    public int getActiveItemCount() {
        int count = 0;
        if (mSlots != null) {
            for (CoolantBathSlot slot : mSlots) {
                if (slot != null && !slot.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getTotalSlotCount() {
        if (mSlots != null && (mMachine || mSlots.length != 15)) {
            return mSlots.length;
        }
        return mInteriorWidth * mInteriorLength;
    }

    // --- ICoolantLoopDevice Implementation ---

    private String cachedDeviceId;

    @Override
    public String getDeviceId() {
        if (cachedDeviceId != null) return cachedDeviceId;
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            cachedDeviceId = "coolant_bath_" + te.getXCoord() + "_" + te.getYCoord() + "_" + te.getZCoord();
            return cachedDeviceId;
        }
        return "coolant_bath_unbound";
    }

    @Override
    public double getMinorLossK() {
        // Resistance increases proportionally to active item count: baseK + itemK * N_active
        double baseK = 1.0;
        double itemK = 1.0;
        CoolantDeviceData devData = CoolantLoopDataRegistry.getDeviceData();
        if (devData != null && devData.getBath() != null) {
            baseK = devData.getBath().baseMinorLossK;
            itemK = devData.getBath().itemMinorLossK;
        }
        return baseK + itemK * getActiveItemCount();
    }

    @Override
    public long getFillVolumeLiters() {
        long perSlot = 1000L;
        CoolantDeviceData devData = CoolantLoopDataRegistry.getDeviceData();
        if (devData != null && devData.getBath() != null) {
            perSlot = devData.getBath().fillVolumePerSlotLiters;
        }
        return (long) getTotalSlotCount() * perSlot;
    }

    @Override
    public double getDeviceTemperatureCelsius() {
        return mCurrentCoolantTemp;
    }

    @Override
    public void setLoopPump(ICoolantLoopPump pump) {
        this.mConnectedPump = pump;
    }

    public ConvectiveHeatTransferModel getConvectiveModel() {
        if (mConvectiveModel != null) {
            return mConvectiveModel;
        }
        ConvectiveHeatTransferModel cfg = CoolantLoopDataRegistry.getConvectiveModel();
        return cfg != null ? cfg : ConvectiveHeatTransferModel.DEFAULT;
    }

    public void setConvectiveModel(ConvectiveHeatTransferModel aModel) {
        this.mConvectiveModel = aModel;
    }

    @Override
    public void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid,
        LoopSegment segment) {
        if (segment == null || fluid == null) return;

        this.mCurrentCoolantTemp = segment.getCurrentTemperatureCelsius();

        // Update loop segment minor loss K dynamically based on active item count
        segment.setMinorLossK(getMinorLossK());

        int activeCount = getActiveItemCount();
        if (activeCount <= 0) return;

        double velocity = segment.getArea() > 0 ? (coolantFlowRateM3s / segment.getArea()) : 0.0;
        ConvectiveHeatTransferModel conv = getConvectiveModel();

        double totalHeatToCoolantJoules = 0.0;
        for (int i = 0; i < mSlots.length; i++) {
            CoolantBathSlot slot = mSlots[i];
            if (slot == null || slot.isEmpty()) continue;

            if (slot.isCryoCell) {
                // Cryogenic boiling cell stays at boiling point while absorbing heat
                // Newton's law: Q_dot = h(v) * A * (T_cell - T_coolant)
                // When T_cell < T_coolant, Q_dot is negative (cooling the loop coolant)
                double qDotWatts = conv
                    .computeHeatTransferRate(velocity, slot.surfaceArea, slot.currentTemp, mCurrentCoolantTemp);
                double deltaEJoules = qDotWatts * dt;

                // Heat absorbed by the cryogenic cell
                double heatAbsorbed = -deltaEJoules;
                if (heatAbsorbed > 0) {
                    double remainingCapacity = Math.max(0.0, slot.heatCapacity - slot.absorbedHeatJoules);
                    double actualHeatAbsorbed = Math.min(heatAbsorbed, remainingCapacity);
                    slot.absorbedHeatJoules += actualHeatAbsorbed;
                    slot.updateItemDamage();
                    totalHeatToCoolantJoules -= actualHeatAbsorbed;
                }
            } else {
                // Newton's law: Q_dot = h(v) * A * (T_item - T_coolant)
                // Positive Q_dot means heat transfers from item into coolant
                double qDotWatts = conv
                    .computeHeatTransferRate(velocity, slot.surfaceArea, slot.currentTemp, mCurrentCoolantTemp);
                double deltaEJoules = qDotWatts * dt;

                double deltaItemTemp = -deltaEJoules / slot.heatCapacity;
                slot.currentTemp += deltaItemTemp;
                slot.updateItemDamage();

                totalHeatToCoolantJoules += deltaEJoules;
            }
        }

        // Alter coolant fluid temperature in segment
        double segmentMassKg = segment.getVolume() * fluid.getDensity();
        double fluidHeatCapacity = segmentMassKg * fluid.getSpecificHeat();
        if (fluidHeatCapacity > 0) {
            double deltaCoolantTemp = totalHeatToCoolantJoules / fluidHeatCapacity;
            double newCoolantTemp = mCurrentCoolantTemp + deltaCoolantTemp;
            segment.setCurrentTemperatureCelsius(newCoolantTemp);
            this.mCurrentCoolantTemp = newCoolantTemp;
            if (mPrimaryOutlet != null) {
                mPrimaryOutlet.setCurrentTemperatureCelsius(newCoolantTemp);
            }
        }
    }

    // --- Machine Tick Logic ---

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (!aBaseMetaTileEntity.isServerSide()) return;
        if (!mMachine) return;

        // 1. Immediately intake compatible items from input buses into empty slots
        if (mSlots != null) {
            for (int i = 0; i < mSlots.length; i++) {
                if (mSlots[i] == null) {
                    for (MTEHatchInputBus bus : mInputBusses) {
                        for (int s = 0; s < bus.getSizeInventory(); s++) {
                            ItemStack st = bus.getStackInSlot(s);
                            if (st != null && CoolantBathRegistry.isCompatibleItem(st)) {
                                ItemStack extracted = bus.decrStackSize(s, 1);
                                if (extracted != null && extracted.stackSize > 0) {
                                    mSlots[i] = CoolantBathRegistry.createSlot(extracted, mCurrentCoolantTemp);
                                    markDirty();
                                    mNeedsSync = true;
                                    break;
                                }
                            }
                        }
                        if (mSlots[i] != null) break;
                    }
                }
            }

            // 2. Auto-eject completed items to output buses
            for (int i = 0; i < mSlots.length; i++) {
                CoolantBathSlot slot = mSlots[i];
                if (slot != null && slot.isAutoEjectReady()) {
                    if (ejectSlotToOutputBus(i)) {
                        mNeedsSync = true;
                    }
                }
            }
        }

        // 3. Sync slot items and progress to clients
        if (mNeedsSync || aTick % 20 == 0) {
            syncToClients();
            mNeedsSync = false;
        }

        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().setActive(getActiveItemCount() > 0);
        }
    }

    public boolean ejectSlotToOutputBus(int slotIndex) {
        if (mSlots == null || slotIndex < 0 || slotIndex >= mSlots.length) return false;
        CoolantBathSlot slot = mSlots[slotIndex];
        if (slot == null || slot.itemStack == null) return false;

        ItemStack toStore = slot.getCompletedOutput();
        if (addOutputAtomic(toStore)) {
            mSlots[slotIndex] = null;
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * Manually ejects an item from a virtual slot to an output bus in its current state.
     */
    public boolean ejectSlot(int slotIndex) {
        if (mSlots == null || slotIndex < 0 || slotIndex >= mSlots.length) return false;
        CoolantBathSlot slot = mSlots[slotIndex];
        if (slot == null || slot.itemStack == null) return false;

        slot.updateItemDamage();
        ItemStack toStore = slot.itemStack;
        if (addOutputAtomic(toStore)) {
            mSlots[slotIndex] = null;
            markDirty();
            mNeedsSync = true;
            syncToClients();
            return true;
        }
        return false;
    }

    public void syncToClients() {
        IGregTechTileEntity bte = getBaseMetaTileEntity();
        if (bte == null || bte.getWorld() == null || !bte.isServerSide()) return;

        int count = mSlots != null ? mSlots.length : 0;
        PacketSyncCoolantBath.SlotSyncData[] syncData = new PacketSyncCoolantBath.SlotSyncData[count];
        for (int i = 0; i < count; i++) {
            PacketSyncCoolantBath.SlotSyncData s = new PacketSyncCoolantBath.SlotSyncData();
            s.index = i;
            CoolantBathSlot slot = mSlots[i];
            if (slot != null && slot.itemStack != null) {
                s.stack = slot.itemStack.copy();
                s.currentTemp = (float) slot.currentTemp;
                s.targetTemp = (float) slot.targetTemp;
                s.progress = (float) slot.getProgressFraction();
                s.isCoolantCell = slot.isCoolantCell;
                s.cooling = slot.cooling;
            } else {
                s.stack = null;
            }
            syncData[i] = s;
        }

        PacketSyncCoolantBath packet = new PacketSyncCoolantBath(
            bte.getXCoord(),
            bte.getYCoord(),
            bte.getZCoord(),
            mStructureTier,
            syncData);

        CoolantLoops.network.sendToAllAround(
            packet,
            new TargetPoint(
                bte.getWorld().provider.dimensionId,
                bte.getXCoord(),
                bte.getYCoord(),
                bte.getZCoord(),
                64));
    }

    public void receiveClientSync(int structureTier, PacketSyncCoolantBath.SlotSyncData[] slots) {
        this.clientStructureTier = structureTier;
        this.clientSlots = slots;
        this.mStructureTier = structureTier;
        this.mInteriorWidth = structureTier == 3 ? 7 : (structureTier == 2 ? 5 : 3);
        this.mInteriorLength = structureTier == 3 ? 9 : (structureTier == 2 ? 7 : 5);
        ACTIVE_CLIENT_BATHS.add(this);
    }

    /**
     * Calculates the 3D world coordinates for a specific virtual slot center.
     */
    public int[] getSlotWorldCoordinates(int slotIndex) {
        if (mInteriorWidth <= 0 || mInteriorLength <= 0
            || slotIndex < 0
            || slotIndex >= (mInteriorWidth * mInteriorLength)) {
            return null;
        }
        IGregTechTileEntity bte = getBaseMetaTileEntity();
        if (bte == null) return null;

        int col = slotIndex % mInteriorWidth;
        int row = slotIndex / mInteriorWidth;

        int xCenter = (mInteriorWidth + 2) / 2;
        int charX = 1 + col;
        int layerY = 1;
        int sliceZ = 1 + row;

        int[] abc = new int[] { charX - xCenter, layerY - 2, sliceZ - 0 };
        int[] xyz = new int[] { 0, 0, 0 };
        getExtendedFacing().getWorldOffset(abc, xyz);

        return new int[] { bte.getXCoord() + xyz[0], bte.getYCoord() + xyz[1], bte.getZCoord() + xyz[2] };
    }

    // --- Structure Validation & Hatch Adders ---

    public boolean addCasingHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte == null) return false;

        if (mte instanceof MTEHatchInputBus) {
            return addInputBusToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (mte instanceof MTEHatchOutputBus) {
            return addOutputBusToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (mte instanceof MTEHatchMaintenance) {
            return addMaintenanceToMachineList(aTileEntity, aBaseCasingIndex);
        }
        return false;
    }

    public boolean addInletHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEHatchPressurizedFluid) {
            MTEHatchPressurizedFluid ph = (MTEHatchPressurizedFluid) mte;
            if (!ph.isInput() || mPrimaryInlet != null) {
                return false;
            }
            ph.updateTexture(aBaseCasingIndex);
            mPrimaryInlet = ph;
            return true;
        }
        return false;
    }

    public boolean addOutletHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity mte = aTileEntity.getMetaTileEntity();
        if (mte instanceof MTEHatchPressurizedFluid) {
            MTEHatchPressurizedFluid ph = (MTEHatchPressurizedFluid) mte;
            if (!ph.isOutput() || mPrimaryOutlet != null) {
                return false;
            }
            ph.updateTexture(aBaseCasingIndex);
            mPrimaryOutlet = ph;
            return true;
        }
        return false;
    }

    private static IStructureDefinition<MTECoolantBath> STRUCTURE_DEFINITION = null;

    @Override
    public IStructureDefinition<MTECoolantBath> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTECoolantBath>builder()
                .addShape(
                    "bath_5x7x3",
                    StructureUtility.transpose(
                        new String[][] { { "GGGGG", "GGGGG", "GGGGG", "GGGGG", "GGGGG", "GGGGG", "GGGGG" },
                            { "CCICC", "C   C", "C   C", "C   C", "C   C", "C   C", "CCOCC" },
                            { "CC~CC", "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC", "CCCCC" } }))
                .addShape(
                    "bath_7x9x3",
                    StructureUtility.transpose(
                        new String[][] {
                            { "GGGGGGG", "GGGGGGG", "GGGGGGG", "GGGGGGG", "GGGGGGG", "GGGGGGG", "GGGGGGG", "GGGGGGG",
                                "GGGGGGG" },
                            { "CCCICCC", "C     C", "C     C", "C     C", "C     C", "C     C", "C     C", "C     C",
                                "CCCOCCC" },
                            { "CCC~CCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC", "CCCCCCC",
                                "CCCCCCC" } }))
                .addShape(
                    "bath_9x11x3",
                    StructureUtility.transpose(
                        new String[][] {
                            { "GGGGGGGGG", "GGGGGGGGG", "GGGGGGGGG", "GGGGGGGGG", "GGGGGGGGG", "GGGGGGGGG", "GGGGGGGGG",
                                "GGGGGGGGG", "GGGGGGGGG", "GGGGGGGGG", "GGGGGGGGG" },
                            { "CCCCICCCC", "C       C", "C       C", "C       C", "C       C", "C       C", "C       C",
                                "C       C", "C       C", "C       C", "CCCCOCCCC" },
                            { "CCCC~CCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC",
                                "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC", "CCCCCCCCC" } }))
                .addElement(
                    'C',
                    StructureUtility.ofChain(
                        GTStructureUtility.ofHatchAdder(MTECoolantBath::addCasingHatch, CASING_INDEX_HEAT_PROOF, 1),
                        StructureUtility.ofBlock(GregTechAPI.sBlockCasings1, CASING_INDEX_HEAT_PROOF)))
                .addElement(
                    'I',
                    GTStructureUtility.ofHatchAdder(MTECoolantBath::addInletHatch, CASING_INDEX_HEAT_PROOF, 2))
                .addElement(
                    'O',
                    GTStructureUtility.ofHatchAdder(MTECoolantBath::addOutletHatch, CASING_INDEX_HEAT_PROOF, 3))
                .addElement(' ', StructureUtility.isAir())
                .addElement(
                    'G',
                    GTStructureUtility.chainAllGlasses(-1, (te, t) -> te.mGlassTier = t, te -> te.mGlassTier))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> aErrors) {
        mPrimaryInlet = null;
        mPrimaryOutlet = null;
        mInputBusses.clear();
        mOutputBusses.clear();
        mMaintenanceHatches.clear();
        mGlassTier = -1;
        mStructureTier = 0;

        List<StructureError> errors9 = new ArrayList<>();
        List<StructureError> errors7 = new ArrayList<>();
        List<StructureError> errors5 = new ArrayList<>();

        boolean matched9 = checkPiece("bath_9x11x3", 4, 2, 0, errors9);
        boolean matched7 = false;
        boolean matched5 = false;

        if (matched9) {
            mStructureTier = 3;
            mInteriorWidth = 7;
            mInteriorLength = 9;
            if (mGlassTier < 6) { // LuV
                aErrors.add(StructureErrors.glassTierNotEnough(6));
            }
        } else {
            mPrimaryInlet = null;
            mPrimaryOutlet = null;
            mInputBusses.clear();
            mOutputBusses.clear();
            mMaintenanceHatches.clear();
            mGlassTier = -1;

            matched7 = checkPiece("bath_7x9x3", 3, 2, 0, errors7);
            if (matched7) {
                mStructureTier = 2;
                mInteriorWidth = 5;
                mInteriorLength = 7;
                if (mGlassTier < 5) { // IV
                    aErrors.add(StructureErrors.glassTierNotEnough(5));
                }
            } else {
                mPrimaryInlet = null;
                mPrimaryOutlet = null;
                mInputBusses.clear();
                mOutputBusses.clear();
                mMaintenanceHatches.clear();
                mGlassTier = -1;

                matched5 = checkPiece("bath_5x7x3", 2, 2, 0, errors5);
                if (matched5) {
                    mStructureTier = 1;
                    mInteriorWidth = 3;
                    mInteriorLength = 5;
                    if (mGlassTier < 4) { // EV
                        aErrors.add(StructureErrors.glassTierNotEnough(4));
                    }
                }
            }
        }

        if (mStructureTier == 0) {
            int intendedTier = detectIntendedTier(aBaseMetaTileEntity);
            if (intendedTier == 3) {
                aErrors.addAll(errors9);
            } else if (intendedTier == 2) {
                aErrors.addAll(errors7);
            } else {
                aErrors.addAll(errors5);
            }
            return;
        }

        if (mPrimaryInlet == null) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.missing_primary_inlet"));
        }
        if (mPrimaryOutlet == null) {
            aErrors.add(StructureErrors.of("coolantloops.structure.error.missing_primary_outlet"));
        }
        checkHasInputBus(aErrors);
        checkHasOutputBus(aErrors);
        checkOneMaintenanceHatch(aErrors);

        if (!aErrors.isEmpty()) {
            return;
        }

        int totalSlots = mInteriorWidth * mInteriorLength;
        if (mSlots == null || mSlots.length != totalSlots) {
            CoolantBathSlot[] newSlots = new CoolantBathSlot[totalSlots];
            if (mSlots != null) {
                System.arraycopy(mSlots, 0, newSlots, 0, Math.min(mSlots.length, totalSlots));
            }
            mSlots = newSlots;
        }

        final int casingIndex = CASING_INDEX_HEAT_PROOF;
        if (mPrimaryInlet != null) {
            mPrimaryInlet.updateTexture(casingIndex);
            if (getBaseMetaTileEntity() != null) {
                mPrimaryInlet.updateCraftingIcon(getMachineCraftingIcon());
            }
        }
        if (mPrimaryOutlet != null) {
            mPrimaryOutlet.updateTexture(casingIndex);
            if (getBaseMetaTileEntity() != null) {
                mPrimaryOutlet.updateCraftingIcon(getMachineCraftingIcon());
            }
        }
        for (MTEHatchMaintenance mh : mMaintenanceHatches) {
            mh.updateTexture(casingIndex);
            if (getBaseMetaTileEntity() != null) {
                mh.updateCraftingIcon(getMachineCraftingIcon());
            }
        }
        for (MTEHatchInputBus ib : mInputBusses) {
            ib.updateTexture(casingIndex);
            if (getBaseMetaTileEntity() != null) {
                ib.updateCraftingIcon(getMachineCraftingIcon());
            }
        }
        for (MTEHatchOutputBus ob : mOutputBusses) {
            ob.updateTexture(casingIndex);
            if (getBaseMetaTileEntity() != null) {
                ob.updateCraftingIcon(getMachineCraftingIcon());
            }
        }

        checkMaintenance();
        if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork()) {
            aBaseMetaTileEntity.enableWorking();
        }
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return checkMachine_TT(aBaseMetaTileEntity);
    }

    protected int detectIntendedTier(IGregTechTileEntity aBaseMetaTileEntity) {
        if (aBaseMetaTileEntity == null) return 1;
        net.minecraft.world.World world = aBaseMetaTileEntity.getWorld();
        if (world == null) return 1;
        ForgeDirection front = aBaseMetaTileEntity.getFrontFacing();
        ForgeDirection right = (front != null && front != ForgeDirection.UNKNOWN) ? front.getRotation(ForgeDirection.UP)
            : ForgeDirection.WEST;
        int x = aBaseMetaTileEntity.getXCoord();
        int y = aBaseMetaTileEntity.getYCoord();
        int z = aBaseMetaTileEntity.getZCoord();

        if (!world.isAirBlock(x + right.offsetX * 4, y, z + right.offsetZ * 4)
            || !world.isAirBlock(x - right.offsetX * 4, y, z - right.offsetZ * 4)) {
            return 3;
        }
        if (!world.isAirBlock(x + right.offsetX * 3, y, z + right.offsetZ * 3)
            || !world.isAirBlock(x - right.offsetX * 3, y, z - right.offsetZ * 3)) {
            return 2;
        }
        return 1;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece("bath_5x7x3", stackSize, hintsOnly, 2, 2, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece("bath_5x7x3", stackSize, 2, 2, 0, elementBudget, env, false, true);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Coolant Bath")
            .addInfo("Passive convective thermal treatment bath for ingots and coolant cells")
            .addInfo("3 blocks tall, variable footprint (5x7 EV, 7x9 IV, 9x11 LuV)")
            .addInfo("Transfers heat between loop coolant and items in parallel virtual slots")
            .addInfo("No electricity use, no recipes: operates purely by Newton's law of cooling")
            .addSeparator()
            .beginStructureBlock(5, 3, 7, false)
            .addController("Front bottom center")
            .addCasingInfoMin("Heatproof Machine Casings", 30, false)
            .addOtherStructurePart("Tiered Glass Roof", "Top layer (EV+ for 5x7, IV+ for 7x9, LuV+ for 9x11)")
            .addOtherStructurePart("Pressurized Inlet Hatch", "Middle layer, right above controller")
            .addOtherStructurePart("Pressurized Outlet Hatch", "Middle layer, opposite narrow end")
            .addInputBus("Any heatproof casing block", 1)
            .addOutputBus("Any heatproof casing block", 1)
            .toolTipFinisher("Coolant Loops");
        return tt;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("structureTier", mStructureTier);
        aNBT.setInteger("glassTier", mGlassTier);
        aNBT.setDouble("currentCoolantTemp", mCurrentCoolantTemp);

        if (mSlots != null) {
            NBTTagList slotList = new NBTTagList();
            for (int i = 0; i < mSlots.length; i++) {
                if (mSlots[i] != null && !mSlots[i].isEmpty()) {
                    NBTTagCompound sTag = mSlots[i].writeToNBT();
                    sTag.setInteger("slotIndex", i);
                    slotList.appendTag(sTag);
                }
            }
            aNBT.setTag("bathSlots", slotList);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        mStructureTier = aNBT.getInteger("structureTier");
        mGlassTier = aNBT.getInteger("glassTier");
        mCurrentCoolantTemp = aNBT.getDouble("currentCoolantTemp");

        mInteriorWidth = mStructureTier == 3 ? 7 : (mStructureTier == 2 ? 5 : 3);
        mInteriorLength = mStructureTier == 3 ? 9 : (mStructureTier == 2 ? 7 : 5);
        int totalSlots = mInteriorWidth * mInteriorLength;
        mSlots = new CoolantBathSlot[totalSlots];

        if (aNBT.hasKey("bathSlots", Constants.NBT.TAG_LIST)) {
            NBTTagList slotList = aNBT.getTagList("bathSlots", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < slotList.tagCount(); i++) {
                NBTTagCompound sTag = slotList.getCompoundTagAt(i);
                int idx = sTag.getInteger("slotIndex");
                if (idx >= 0 && idx < totalSlots) {
                    mSlots[idx] = CoolantBathSlot.readFromNBT(sTag);
                }
            }
        }
    }
}
