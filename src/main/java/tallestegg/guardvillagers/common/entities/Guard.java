package tallestegg.guardvillagers.common.entities;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.serialization.Dynamic;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.ToolActions;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import org.jetbrains.annotations.NotNull;
import tallestegg.guardvillagers.GuardItems;
import tallestegg.guardvillagers.GuardLootTables;
import tallestegg.guardvillagers.client.GuardSounds;
import tallestegg.guardvillagers.common.entities.ai.goals.*;
import tallestegg.guardvillagers.configuration.GuardConfig;
import tallestegg.guardvillagers.networking.GuardOpenInventoryPacket;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public class Guard extends PathfinderMob implements CrossbowAttackMob, RangedAttackMob, NeutralMob, ContainerListener, ReputationEventHandler {
    protected static final EntityDataAccessor<Optional<UUID>> OWNER_UNIQUE_ID = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final UUID MODIFIER_UUID = UUID.fromString("5CD17E52-A79A-43D3-A529-90FDE04B181E");
    private static final AttributeModifier USE_ITEM_SPEED_PENALTY = new AttributeModifier(MODIFIER_UUID, "Use item speed penalty", -0.25D, AttributeModifier.Operation.ADD_VALUE);
    private static final EntityDataAccessor<Optional<BlockPos>> GUARD_POS = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Boolean> PATROLLING = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> GUARD_VARIANT = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RUNNING_TO_EAT = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CHARGING_STATE = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> KICKING = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> FOLLOWING = SynchedEntityData.defineId(Guard.class, EntityDataSerializers.BOOLEAN);
    private static final Map<Pose, EntityDimensions> SIZE_BY_POSE = ImmutableMap.<Pose, EntityDimensions>builder()
            .put(Pose.SLEEPING, SLEEPING_DIMENSIONS)
            .put(Pose.FALL_FLYING, EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F))
            .put(Pose.SWIMMING, EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F))
            .put(Pose.SPIN_ATTACK, EntityDimensions.scalable(0.6F, 0.6F).withEyeHeight(0.4F))
            .put(
                    Pose.CROUCHING,
                    EntityDimensions.scalable(0.6F, 1.5F)
                            .withEyeHeight(1.27F)
                            .withAttachments(EntityAttachments.builder().attach(EntityAttachment.VEHICLE, new Vec3(0.0, 0.6, 0.0)
                            ))).put(Pose.DYING, EntityDimensions.fixed(0.2F, 0.2F).withEyeHeight(1.62F))
            .build();
    private static final UniformInt angerTime = TimeUtil.rangeOfSeconds(20, 39);
    private static final Map<EquipmentSlot, ResourceKey<LootTable>> EQUIPMENT_SLOT_ITEMS = Util.make(Maps.newHashMap(), (slotItems) -> {
        slotItems.put(EquipmentSlot.MAINHAND, GuardLootTables.GUARD_MAIN_HAND);
        slotItems.put(EquipmentSlot.OFFHAND, GuardLootTables.GUARD_OFF_HAND);
        slotItems.put(EquipmentSlot.HEAD, GuardLootTables.GUARD_HELMET);
        slotItems.put(EquipmentSlot.CHEST, GuardLootTables.GUARD_CHEST);
        slotItems.put(EquipmentSlot.LEGS, GuardLootTables.GUARD_LEGGINGS);
        slotItems.put(EquipmentSlot.FEET, GuardLootTables.GUARD_FEET);
    });
    private final GossipContainer gossips = new GossipContainer();
    public long lastGossipTime;
    public long lastGossipDecayTime;
    public SimpleContainer guardInventory = new SimpleContainer(6);
    public int kickTicks;
    public int shieldCoolDown;
    public int kickCoolDown;
    public boolean interacting;
    protected boolean spawnWithArmor;
    private int remainingPersistentAngerTime;
    private UUID persistentAngerTarget;

    public Guard(EntityType<? extends Guard> type, Level world) {
        super(type, world);
        this.guardInventory.addListener(this);
        this.setPersistenceRequired();
        if (GuardConfig.COMMON.GuardsOpenDoors.get())
            ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(true);
    }

    public static int slotToInventoryIndex(EquipmentSlot slot) {
        return switch (slot) {
            case CHEST -> 1;
            case FEET -> 3;
            case HEAD -> 0;
            case LEGS -> 2;
            default -> 0;
        };
    }

    /**
     * Credit - SmellyModder for Biome Specific Textures
     */
    public static int getRandomTypeForBiome(LevelAccessor world, BlockPos pos) {
        VillagerType type = VillagerType.byBiome(world.getBiome(pos));
        if (type == VillagerType.SNOW) return 6;
        else if (type == VillagerType.TAIGA) return 5;
        else if (type == VillagerType.JUNGLE) return 4;
        else if (type == VillagerType.SWAMP) return 3;
        else if (type == VillagerType.SAVANNA) return 2;
        else if (type == VillagerType.DESERT) return 1;
        else return 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, GuardConfig.COMMON.healthModifier.get()).add(Attributes.MOVEMENT_SPEED, GuardConfig.COMMON.speedModifier.get()).add(Attributes.ATTACK_DAMAGE, 1.0D).add(Attributes.FOLLOW_RANGE, GuardConfig.COMMON.followRangeModifier.get());
    }

    @Override
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor worldIn, @NotNull DifficultyInstance difficultyIn, @NotNull MobSpawnType reason, @Nullable SpawnGroupData spawnDataIn) {
        this.setPersistenceRequired();
        int type = !GuardConfig.COMMON.guardVariantRandomSpawning.get() ?
        getRandomTypeForBiome(level(), this.blockPosition()) : this.random.nextInt(6);
        if (spawnDataIn instanceof GuardData) {
            type = ((GuardData) spawnDataIn).variantData;
            spawnDataIn = new GuardData(type);
        }
        this.setGuardVariant(type);
        RandomSource randomsource = worldIn.getRandom();
        this.populateDefaultEquipmentSlots(randomsource, difficultyIn);
        return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn);
    }

    @Override
    protected void doPush(Entity entityIn) {
        if (entityIn instanceof PathfinderMob living) {
            boolean attackTargets = living.getTarget() instanceof Villager || living.getTarget() instanceof IronGolem || living.getTarget() instanceof Guard;
            if (attackTargets) this.setTarget(living);
        }
        super.doPush(entityIn);
    }

    @Nullable
    public BlockPos getPatrolPos() {
        return this.entityData.get(GUARD_POS).orElse(null);
    }

    @Nullable
    public void setPatrolPos(BlockPos position) {
        this.entityData.set(GUARD_POS, Optional.ofNullable(position));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return GuardSounds.GUARD_AMBIENT.value();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return GuardSounds.GUARD_HURT.value();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return GuardSounds.GUARD_DEATH.value();
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHitIn) {
        for (int i = 0; i < this.guardInventory.getContainerSize(); ++i) {
            ItemStack itemstack = this.guardInventory.getItem(i);
            RandomSource randomsource = level().getRandom();
            if (!itemstack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(itemstack) && randomsource.nextFloat() < GuardConfig.COMMON.chanceToDropEquipment.get().floatValue())
                this.spawnAtLocation(itemstack);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        UUID uuid = compound.hasUUID("Owner") ? compound.getUUID("Owner") : null;
        if (uuid != null) {
            try {
                this.setOwnerId(uuid);
            } catch (Throwable throwable) {
                this.setOwnerId(null);
            }
        }
        this.setGuardVariant(compound.getInt("Type"));
        this.kickTicks = compound.getInt("KickTicks");
        this.setFollowing(compound.getBoolean("Following"));
        this.interacting = compound.getBoolean("Interacting");
        this.setPatrolling(compound.getBoolean("Patrolling"));
        this.shieldCoolDown = compound.getInt("KickCooldown");
        this.kickCoolDown = compound.getInt("ShieldCooldown");
        this.lastGossipDecayTime = compound.getLong("LastGossipDecay");
        this.lastGossipTime = compound.getLong("LastGossipTime");
        this.spawnWithArmor = compound.getBoolean("SpawnWithArmor");
        if (compound.contains("PatrolPosX")) {
            int x = compound.getInt("PatrolPosX");
            int y = compound.getInt("PatrolPosY");
            int z = compound.getInt("PatrolPosZ");
            this.entityData.set(GUARD_POS, Optional.of(new BlockPos(x, y, z)));
        }
        ListTag listtag = compound.getList("Gossips", 10);
        this.gossips.update(new Dynamic<>(NbtOps.INSTANCE, listtag));
        ListTag listnbt = compound.getList("Inventory", 9);
        for (int i = 0; i < listnbt.size(); ++i) {
            CompoundTag compoundnbt = listnbt.getCompound(i);
            int j = compoundnbt.getByte("Slot") & 255;
            ItemStack stack = ItemStack.parseOptional(this.registryAccess(), compoundnbt);
            if (!stack.isEmpty())
                this.guardInventory.setItem(j, stack);
            else
                listtag.add(new CompoundTag());
        }
        if (compound.contains("ArmorItems", 9)) {
            ListTag armorItems = compound.getList("ArmorItems", 10);
            for (int i = 0; i < this.armorItems.size(); ++i) {
                ItemStack stack = ItemStack.parseOptional(this.registryAccess(), armorItems.getCompound(i));
                if (!stack.isEmpty()) {
                    int index = Guard.slotToInventoryIndex(Mob.getEquipmentSlotForItem(ItemStack.parse(this.registryAccess(), armorItems.getCompound(i)).orElse(ItemStack.EMPTY)));
                    this.guardInventory.setItem(index, stack);
                } else {
                    listtag.add(new CompoundTag());
                }
            }
            if (compound.contains("HandItems", 9)) {
                ListTag handItems = compound.getList("HandItems", 10);
                for (int i = 0; i < this.handItems.size(); ++i) {
                    int handSlot = i == 0 ? 5 : 4;
                    if (!ItemStack.parseOptional(this.registryAccess(), handItems.getCompound(i)).isEmpty())
                        this.guardInventory.setItem(handSlot, ItemStack.parseOptional(this.registryAccess(), handItems.getCompound(i)));
                    else
                        listtag.add(new CompoundTag());
                }
                if (!level().isClientSide) this.readPersistentAngerSaveData(level(), compound);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Type", this.getGuardVariant());
        compound.putInt("KickTicks", this.kickTicks);
        compound.putInt("ShieldCooldown", this.shieldCoolDown);
        compound.putInt("KickCooldown", this.kickCoolDown);
        compound.putBoolean("Following", this.isFollowing());
        compound.putBoolean("Interacting", this.interacting);
        compound.putBoolean("Patrolling", this.isPatrolling());
        compound.putBoolean("SpawnWithArmor", this.spawnWithArmor);
        compound.putLong("LastGossipTime", this.lastGossipTime);
        compound.putLong("LastGossipDecay", this.lastGossipDecayTime);
        if (this.getOwnerId() != null) {
            compound.putUUID("Owner", this.getOwnerId());
        }
        ListTag listnbt = new ListTag();
        for (int i = 0; i < this.guardInventory.getContainerSize(); ++i) {
            ItemStack itemstack = this.guardInventory.getItem(i);
            if (!itemstack.isEmpty()) {
                CompoundTag compoundnbt = new CompoundTag();
                compoundnbt.putByte("Slot", (byte) i);
                listnbt.add(itemstack.save(this.registryAccess(), compoundnbt));
            } else {
                listnbt.add(new CompoundTag());
            }
        }
        compound.put("Inventory", listnbt);
        if (this.getPatrolPos() != null) {
            compound.putInt("PatrolPosX", this.getPatrolPos().getX());
            compound.putInt("PatrolPosY", this.getPatrolPos().getY());
            compound.putInt("PatrolPosZ", this.getPatrolPos().getZ());
        }
        compound.put("Gossips", this.gossips.store(NbtOps.INSTANCE));
        this.addPersistentAngerSaveData(compound);
    }

    private void maybeDecayGossip() {
        long i = level().getGameTime();
        if (this.lastGossipDecayTime == 0L) {
            this.lastGossipDecayTime = i;
        } else if (i >= this.lastGossipDecayTime + 24000L) {
            this.gossips.decay();
            this.lastGossipDecayTime = i;
        }
    }

    @Override
    protected void completeUsingItem() {
        if (this.isUsingItem()) {
            InteractionHand interactionhand = this.getUsedItemHand();
            if (!this.useItem.equals(this.getItemInHand(interactionhand))) {
                this.releaseUsingItem();
            } else {
                if (!this.useItem.isEmpty() && this.isUsingItem()) {
                    this.triggerItemUseEffects(this.useItem, 16);
                    ItemStack copy = this.useItem.copy();
                    ItemStack itemstack = EventHooks.onItemUseFinish(this, copy, getUseItemRemainingTicks(), this.useItem.finishUsingItem(this.level(), this));
                    if (itemstack != this.useItem) {
                        this.setItemInHand(interactionhand, itemstack);
                    }
                    if (!(this.useItem.getUseAnimation() == UseAnim.EAT)) this.useItem.shrink(1);
                    this.stopUsingItem();
                }

            }
        }
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot pSlot) {
        return switch (pSlot) {
            case HEAD -> this.guardInventory.getItem(0);
            case CHEST -> this.guardInventory.getItem(1);
            case LEGS -> this.guardInventory.getItem(2);
            case FEET -> this.guardInventory.getItem(3);
            case OFFHAND -> this.guardInventory.getItem(4);
            case MAINHAND -> this.guardInventory.getItem(5);
            default -> ItemStack.EMPTY;
        };
    }

    public GossipContainer getGossips() {
        return this.gossips;
    }

    public int getPlayerReputation(Player player) {
        return this.gossips.getReputation(player.getUUID(), (gossipType) -> true);
    }

    @Nullable
    public LivingEntity getOwner() {
        try {
            UUID uuid = this.getOwnerId();
            boolean heroOfTheVillage = uuid != null && level().getPlayerByUUID(uuid) != null && Objects.requireNonNull(level().getPlayerByUUID(uuid)).hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
            return uuid == null || (level().getPlayerByUUID(uuid) != null && (!heroOfTheVillage && GuardConfig.COMMON.followHero.get()) || !GuardConfig.COMMON.followHero.get() && level().getPlayerByUUID(uuid) == null) ? null : level().getPlayerByUUID(uuid);
        } catch (IllegalArgumentException illegalargumentexception) {
            return null;
        }
    }

    public boolean isOwner(LivingEntity entityIn) {
        return entityIn == this.getOwner();
    }

    @Nullable
    public UUID getOwnerId() {
        return this.entityData.get(OWNER_UNIQUE_ID).orElse(null);
    }

    public void setOwnerId(@Nullable UUID p_184754_1_) {
        this.entityData.set(OWNER_UNIQUE_ID, Optional.ofNullable(p_184754_1_));
    }

    @Override
    public boolean doHurtTarget(Entity entityIn) {
        if (this.isKicking()) {
            ((LivingEntity) entityIn).knockback(1.0F, Mth.sin(this.getYRot() * ((float) Math.PI / 180F)), (-Mth.cos(this.getYRot() * ((float) Math.PI / 180F))));
            this.kickTicks = 10;
            level().broadcastEntityEvent(this, (byte) 4);
            this.lookAt(entityIn, 90.0F, 90.0F);
        }
        ItemStack hand = this.getMainHandItem();
        hand.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
        return super.doHurtTarget(entityIn);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) {
            this.kickTicks = 10;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean isImmobile() {
        return this.interacting || super.isImmobile();
    }

    @Override
    public void die(DamageSource source) {
        if ((level().getDifficulty() == Difficulty.NORMAL || level().getDifficulty() == Difficulty.HARD) && source.getEntity() instanceof Zombie && EventHooks.canLivingConvert((LivingEntity) source.getEntity(), EntityType.ZOMBIE_VILLAGER, (timer) -> {
        })) {
            ZombieVillager zombieguard = this.convertTo(EntityType.ZOMBIE_VILLAGER, true);
            if (level().getDifficulty() != Difficulty.HARD && this.random.nextBoolean() || zombieguard == null) {
                return;
            }
            if (!this.isSilent()) level().levelEvent(null, 1026, this.blockPosition(), 0);
            this.discard();
        }
        super.die(source);
    }

    @Override
    public ItemStack eat(Level world, ItemStack stack) {
        if (stack.getUseAnimation() == UseAnim.EAT) {
            this.heal((float) stack.getItem().getFoodProperties(stack, this).nutrition());
        }
        world.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5F, world.random.nextFloat() * 0.1F + 0.9F);
        super.eat(world, stack);
        return stack;
    }

    @Override
    public void aiStep() {
        if (this.kickTicks > 0) --this.kickTicks;
        if (this.kickCoolDown > 0) --this.kickCoolDown;
        if (this.shieldCoolDown > 0) --this.shieldCoolDown;
        if (this.getHealth() < this.getMaxHealth() && this.tickCount % 200 == 0) {
            this.heal(GuardConfig.COMMON.amountOfHealthRegenerated.get().floatValue());
        }
        if (spawnWithArmor) {
            for (EquipmentSlot equipmentslottype : EquipmentSlot.values()) {
                for (ItemStack stack : this.getItemsFromLootTable(equipmentslottype, (ServerLevel) this.level())) {
                    this.setItemSlot(equipmentslottype, stack);
                    this.setShieldBanners(this.getOffhandItem());
                }
            }
            this.spawnWithArmor = false;
        }
        if (!level().isClientSide) this.updatePersistentAnger((ServerLevel) level(), true);
        this.updateSwingTime();
        super.aiStep();
    }

    @Override
    public void tick() {
        this.maybeDecayGossip();
        super.tick();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return SIZE_BY_POSE.getOrDefault(pose, EntityDimensions.scalable(0.6F, 1.95F));
    }

    @Override
    protected void blockUsingShield(LivingEntity entityIn) {
        super.blockUsingShield(entityIn);
        this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 1.0F);
        if (entityIn.getMainHandItem().canDisableShield(this.useItem, this, entityIn)) this.disableShield(true);
    }

    @Override
    protected void hurtCurrentlyUsedShield(float damage) {
        if (this.useItem.canPerformAction(ToolActions.SHIELD_BLOCK)) {
            if (damage >= 3.0F) {
                int i = 1 + Mth.floor(damage);
                InteractionHand hand = this.getUsedItemHand();
                this.useItem.hurtAndBreak(i, this, LivingEntity.getSlotForHand(hand));
                if (this.useItem.isEmpty()) {
                    if (hand == InteractionHand.MAIN_HAND) {
                        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    } else {
                        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    }
                    this.useItem = ItemStack.EMPTY;
                    this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + level().random.nextFloat() * 0.4F);
                }
            }
        }
    }

    @Override
    public void startUsingItem(InteractionHand hand) {
        super.startUsingItem(hand);
        ItemStack itemstack = this.getItemInHand(hand);
        if (itemstack.canPerformAction(ToolActions.SHIELD_BLOCK)) {
            AttributeInstance modifiableattributeinstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
            modifiableattributeinstance.removeModifier(USE_ITEM_SPEED_PENALTY);
            modifiableattributeinstance.addTransientModifier(USE_ITEM_SPEED_PENALTY);
        }
    }

    @Override
    public void stopUsingItem() {
        super.stopUsingItem();
        if (this.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(USE_ITEM_SPEED_PENALTY))
            this.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(USE_ITEM_SPEED_PENALTY);
    }

    public void disableShield(boolean increase) {
        float chance = 0.25F + (float) EnchantmentHelper.getBlockEfficiency(this) * 0.05F;
        if (increase) chance += 0.75;
        if (this.random.nextFloat() < chance) {
            this.shieldCoolDown = 100;
            this.stopUsingItem();
            this.level().broadcastEntityEvent(this, (byte) 30);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder data) {
        super.defineSynchedData(data);
        data.define(GUARD_VARIANT, 0);
        data.define(DATA_CHARGING_STATE, false);
        data.define(KICKING, false);
        data.define(OWNER_UNIQUE_ID, Optional.empty());
        data.define(FOLLOWING, false);
        data.define(GUARD_POS, Optional.empty());
        data.define(PATROLLING, false);
        data.define(RUNNING_TO_EAT, false);
    }

    public boolean isCharging() {
        return this.entityData.get(DATA_CHARGING_STATE);
    }

    public void setChargingCrossbow(boolean charging) {
        this.entityData.set(DATA_CHARGING_STATE, charging);
    }

    public boolean isKicking() {
        return this.entityData.get(KICKING);
    }

    public void setKicking(boolean kicking) {
        this.entityData.set(KICKING, kicking);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource source, DifficultyInstance instance) {
        this.handDropChances[EquipmentSlot.MAINHAND.getIndex()] = 100.0F;
        this.handDropChances[EquipmentSlot.OFFHAND.getIndex()] = 100.0F;
        this.spawnWithArmor = true;
    }

    public List<ItemStack> getItemsFromLootTable(EquipmentSlot slot, ServerLevel level) {
        if (EQUIPMENT_SLOT_ITEMS.containsKey(slot)) {
            ServerLevel serverlevel = (ServerLevel) this.level();
            LootTable loot = serverlevel.getServer().reloadableRegistries().getLootTable(EQUIPMENT_SLOT_ITEMS.get(slot));
            LootParams lootcontext$builder = (new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, this).create(GuardLootTables.SLOT));
            return loot.getRandomItems(lootcontext$builder);
        }
        return Collections.singletonList(ItemStack.EMPTY);
    }


    public int getGuardVariant() {
        return this.entityData.get(GUARD_VARIANT);
    }

    public void setGuardVariant(int typeId) {
        this.entityData.set(GUARD_VARIANT, typeId);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new KickGoal(this));
        this.goalSelector.addGoal(0, new GuardEatFoodGoal(this));
        this.goalSelector.addGoal(0, new RaiseShieldGoal(this));
        this.goalSelector.addGoal(1, new GuardRunToEatGoal(this));
        this.goalSelector.addGoal(3, new RangedCrossbowAttackPassiveGoal<>(this, 1.0D, 8.0F));
        this.goalSelector.addGoal(3, new RangedBowAttackGoal(this, 0.5D, 20, 15.0F) {
            @Override
            public boolean canUse() {
                return Guard.this.getTarget() != null && this.isBowInMainhand() && !Guard.this.isEating() && !Guard.this.isBlocking();
            }

            protected boolean isBowInMainhand() {
                return Guard.this.getMainHandItem().getItem() instanceof BowItem;
            }

            @Override
            public void tick() {
                super.tick();
                if (Guard.this.isPatrolling()) {
                    Guard.this.getNavigation().stop();
                    Guard.this.getMoveControl().strafe(0.0F, 0.0F);
                }
            }

            @Override
            public boolean canContinueToUse() {
                return (this.canUse() || !Guard.this.getNavigation().isDone()) && this.isBowInMainhand();
            }
        });
        this.goalSelector.addGoal(3, new GuardMeleeGoal(this, 0.8D, true));
        this.goalSelector.addGoal(4, new FollowHeroGoal(this));
        if (GuardConfig.COMMON.GuardsRunFromPolarBears.get())
            this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, PolarBear.class, 12.0F, 1.0D, 1.2D));
        this.goalSelector.addGoal(4, new MoveBackToVillageGoal(this, 0.5D, false));
        if (GuardConfig.COMMON.GuardsOpenDoors.get())
            this.goalSelector.addGoal(4, new GuardInteractDoorGoal(this, true));
        if (GuardConfig.COMMON.GuardFormation.get())
            this.goalSelector.addGoal(6, new FollowShieldGuards(this)); // phalanx
        if (GuardConfig.COMMON.ClericHealing.get()) this.goalSelector.addGoal(6, new RunToClericGoal(this));
        if (GuardConfig.COMMON.armorersRepairGuardArmor.get())
            this.goalSelector.addGoal(6, new ArmorerRepairGuardArmorGoal(this));
        this.goalSelector.addGoal(5, new WalkBackToCheckPointGoal(this, 0.5D));
        this.goalSelector.addGoal(5, new GolemRandomStrollInVillageGoal(this, 0.5D));
        this.goalSelector.addGoal(5, new MoveThroughVillageGoal(this, 0.5D, false, 4, () -> false));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, AbstractVillager.class, 8.0F));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new GuardLookAtAndStopMovingWhenBeingTheInteractionTarget(this));
        this.targetSelector.addGoal(2, (new HurtByTargetGoal(this, Guard.class, IronGolem.class)).setAlertOthers());
        this.targetSelector.addGoal(3, new HeroHurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new HeroHurtTargetGoal(this));
        this.targetSelector.addGoal(5, new DefendVillageGuardGoal(this));
        if (GuardConfig.COMMON.AttackAllMobs.get()) {
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, true, this::canAttack));
        } else {
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Ravager.class, true)); // To make witches and ravagers have a priority than other mobs this has to be done
            this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Witch.class, true));
            this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Raider.class, true));
            this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Zombie.class, true, (mob) -> !(mob instanceof ZombifiedPiglin)));
        }
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, true, (mob) -> GuardConfig.COMMON.MobWhiteList.get().contains(mob.getEncodeId())));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        this.shieldCoolDown = 8;
        if (this.getMainHandItem().getItem() instanceof CrossbowItem) this.performCrossbowAttack(this, 1.6F);
        if (this.getMainHandItem().getItem() instanceof BowItem) {
            ItemStack itemstack = this.getProjectile(this.getItemInHand(GuardItems.getHandWith(this, item -> item instanceof BowItem)));
            ItemStack hand = this.getMainHandItem();
            AbstractArrow abstractarrowentity = ProjectileUtil.getMobArrow(this, itemstack, distanceFactor);
            abstractarrowentity = ((BowItem) this.getMainHandItem().getItem()).customArrow(abstractarrowentity, itemstack);
            int powerLevel = itemstack.getEnchantmentLevel(Enchantments.POWER);
            if (powerLevel > 0)
                abstractarrowentity.setBaseDamage(abstractarrowentity.getBaseDamage() + (double) powerLevel * 0.5D + 0.5D);
            int punchLevel = itemstack.getEnchantmentLevel(Enchantments.PUNCH);
            if (punchLevel > 0) abstractarrowentity.setKnockback(punchLevel);
            if (itemstack.getEnchantmentLevel(Enchantments.FLAME) > 0)
                abstractarrowentity.setRemainingFireTicks(100);
            double d0 = target.getX() - this.getX();
            double d1 = target.getY(0.3333333333333333D) - abstractarrowentity.getY();
            double d2 = target.getZ() - this.getZ();
            double d3 = Mth.sqrt((float) (d0 * d0 + d2 * d2));
            abstractarrowentity.shoot(d0, d1 + d3 * (double) 0.2F, d2, 1.6F, (float) (14 - level().getDifficulty().getId() * 4));
            this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
            level().addFreshEntity(abstractarrowentity);
            hand.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
        }
    }

    @Override
    public void performCrossbowAttack(LivingEntity p_32337_, float p_32338_) {
        InteractionHand interactionhand = ProjectileUtil.getWeaponHoldingHand(p_32337_, item -> item instanceof CrossbowItem);
        ItemStack itemstack = p_32337_.getItemInHand(interactionhand);
        if (itemstack.getItem() instanceof CrossbowItem crossbowitem) {
            crossbowitem.performShooting(
                    p_32337_.level(), p_32337_, interactionhand, itemstack, p_32338_, 1.0F, null
            );
        }

        this.onCrossbowAttackPerformed();
    }

    @Override
    public void setItemSlot(EquipmentSlot slotIn, ItemStack stack) {
        super.setItemSlot(slotIn, stack);
        switch (slotIn) {
            case CHEST:
                if (this.guardInventory.getItem(1).isEmpty())
                    this.guardInventory.setItem(1, this.armorItems.get(slotIn.getIndex()));
                break;
            case FEET:
                if (this.guardInventory.getItem(3).isEmpty())
                    this.guardInventory.setItem(3, this.armorItems.get(slotIn.getIndex()));
                break;
            case HEAD:
                if (this.guardInventory.getItem(0).isEmpty())
                    this.guardInventory.setItem(0, this.armorItems.get(slotIn.getIndex()));
                break;
            case LEGS:
                if (this.guardInventory.getItem(2).isEmpty())
                    this.guardInventory.setItem(2, this.armorItems.get(slotIn.getIndex()));
                break;
            case MAINHAND:
                this.guardInventory.setItem(5, this.handItems.get(slotIn.getIndex()));
                break;
            case OFFHAND:
                this.guardInventory.setItem(4, this.handItems.get(slotIn.getIndex()));
                break;
        }
    }

    @Override
    public ItemStack getProjectile(ItemStack shootable) {
        if (shootable.getItem() instanceof ProjectileWeaponItem) {
            Predicate<ItemStack> predicate = ((ProjectileWeaponItem) shootable.getItem()).getSupportedHeldProjectiles();
            ItemStack itemstack = ProjectileWeaponItem.getHeldProjectile(this, predicate);
            return itemstack.isEmpty() ? new ItemStack(Items.ARROW) : itemstack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    public int getKickTicks() {
        return this.kickTicks;
    }

    public boolean isFollowing() {
        return this.entityData.get(FOLLOWING);
    }

    public void setFollowing(boolean following) {
        this.entityData.set(FOLLOWING, following);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return target instanceof Enemy
                && !GuardConfig.COMMON.MobBlackList.get().contains(target.getEncodeId())
                && !target.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
                && !this.isOwner(target)
                && !(target instanceof TamableAnimal tameable
                        && tameable.isTame()
                        && !(tameable.getTarget() instanceof Villager)
                        && !(tameable.getTarget() instanceof Guard))
                && super.canAttack(target);
    }

    @Override
    public void rideTick() {
        super.rideTick();
        if (this.getVehicle() instanceof PathfinderMob) {
            PathfinderMob creatureentity = (PathfinderMob) this.getVehicle();
            this.yBodyRot = creatureentity.yBodyRot;
        }
    }

    @Override
    public void onCrossbowAttackPerformed() {
        this.noActionTime = 0;
    }

    @Override
    public void setTarget(LivingEntity entity) {
        if (entity != null && (GuardConfig.COMMON.MobBlackList.get().contains(entity.getEncodeId()) || entity.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) || this.isOwner(entity)))
            return;
        super.setTarget(entity);
    }

    public void gossip(Villager villager, long gameTime) {
        if ((gameTime < this.lastGossipTime || gameTime >= this.lastGossipTime + 1200L) && (gameTime < villager.lastGossipTime || gameTime >= villager.lastGossipTime + 1200L)) {
            this.gossips.transferFrom(villager.getGossips(), this.random, 10);
            this.lastGossipTime = gameTime;
            villager.lastGossipTime = gameTime;
        }
    }


    @Override
    protected void blockedByShield(LivingEntity entityIn) {
        if (this.isKicking()) {
            this.setKicking(false);
        }
        super.blockedByShield(this);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean configValues = player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) && GuardConfig.COMMON.giveGuardStuffHOTV.get() || player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) && GuardConfig.COMMON.setGuardPatrolHotv.get() || player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) && GuardConfig.COMMON.giveGuardStuffHOTV.get() && GuardConfig.COMMON.setGuardPatrolHotv.get() || this.getPlayerReputation(player) >= GuardConfig.COMMON.reputationRequirement.get() || player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) && !GuardConfig.COMMON.giveGuardStuffHOTV.get() && !GuardConfig.COMMON.setGuardPatrolHotv.get() || this.getOwnerId() != null && this.getOwnerId().equals(player.getUUID());
        boolean inventoryRequirements = !player.isSecondaryUseActive();
        if (inventoryRequirements) {
            if (this.getTarget() != player && this.isEffectiveAi() && configValues) {
                if (player instanceof ServerPlayer) {
                    this.openGui((ServerPlayer) player);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.CONSUME;
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void onReputationEventFrom(ReputationEventType reputationEventType, Entity entity) {
    }

    @Override
    public void containerChanged(Container invBasic) {
    }

    @Override
    protected void hurtArmor(DamageSource damageSource, float damage) {
        if (damage >= 0.0F) {
            damage = damage / 4.0F;
            if (damage < 1.0F) {
                damage = 1.0F;
            }
            for (int i = 0; i < this.guardInventory.getContainerSize(); ++i) {
                ItemStack itemstack = this.guardInventory.getItem(i);
                if ((!damageSource.is(DamageTypes.ON_FIRE) || !itemstack.getItem().components().has(DataComponents.FIRE_RESISTANT)) && itemstack.getItem() instanceof ArmorItem) {
                    int j = i;
                    itemstack.hurtAndBreak((int) damage, this, EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, j));
                }
            }
        }
    }

    @Override
    public void thunderHit(ServerLevel p_241841_1_, LightningBolt p_241841_2_) {
        if (p_241841_1_.getDifficulty() != Difficulty.PEACEFUL && EventHooks.canLivingConvert(this, EntityType.WITCH, (timer) -> {
        })) {
            Witch witchentity = EntityType.WITCH.create(p_241841_1_);
            if (witchentity == null) return;
            witchentity.copyPosition(this);
            witchentity.finalizeSpawn(p_241841_1_, p_241841_1_.getCurrentDifficultyAt(witchentity.blockPosition()), MobSpawnType.CONVERSION, null);
            witchentity.setNoAi(this.isNoAi());
            witchentity.setCustomName(this.getCustomName());
            witchentity.setCustomNameVisible(this.isCustomNameVisible());
            witchentity.setPersistenceRequired();
            p_241841_1_.addFreshEntityWithPassengers(witchentity);
            this.discard();
        } else {
            super.thunderHit(p_241841_1_, p_241841_2_);
        }
    }

    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(UUID arg0) {
        this.persistentAngerTarget = arg0;
    }

    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int arg0) {
        this.remainingPersistentAngerTime = arg0;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(angerTime.sample(random));
    }

    public void openGui(ServerPlayer player) {
        this.setOwnerId(player.getUUID());
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        this.interacting = true;
        player.nextContainerCounter();
        player.connection.send(new GuardOpenInventoryPacket(player.containerCounter, this.guardInventory.getContainerSize(), this.getId()));
        player.containerMenu = new GuardContainer(player.containerCounter, player.getInventory(), this.guardInventory, this);
        player.initMenu(player.containerMenu);
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, player.containerMenu));
    }


    public boolean isEating() {
        return GuardEatFoodGoal.isConsumable(this.getUseItem()) && this.isUsingItem();
    }

    public boolean isPatrolling() {
        return this.entityData.get(PATROLLING);
    }

    public void setPatrolling(boolean patrolling) {
        this.entityData.set(PATROLLING, patrolling);
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem item) {
        return item instanceof BowItem || item instanceof CrossbowItem || super.canFireProjectileWeapon(item);
    }

    protected void setShieldBanners(ItemStack shieldStack) {
   /*     ItemStack itemstack = new ItemStack(Items.WHITE_BANNER);
        CompoundTag compoundtag = new CompoundTag();
        List<Holder.Reference<BannerPattern>> bannerPatterns = BuiltInRegistries.BANNER_PATTERN.holders().toList();
        List<DyeColor> dyeList = Arrays.stream(DyeColor.values()).toList();
        List<Pair<Holder<BannerPattern>, DyeColor>> patterns = new ArrayList<>();
        for (int bannerPatternsMade = 0; bannerPatternsMade < this.random.nextInt(2); ++bannerPatternsMade) {
            patterns.add(Pair.of(bannerPatterns.get(this.random.nextInt(bannerPatterns.size())), dyeList.get(this.random.nextInt(dyeList.size()))));
        }
        BannerPattern.Builder builder = new BannerPattern.Builder();
        patterns.forEach(builder::addPattern);
        compoundtag.put("Patterns", builder.toListTag());
        CompoundTag compoundtag1 = compoundtag == null ? new CompoundTag() : compoundtag.copy();
        compoundtag1.putInt("Base", ((BannerItem)itemstack.getItem()).getColor().getId());
        BlockItem.setBlockEntityData(shieldStack, BlockEntityType.BANNER, compoundtag1); */
    }

    public static class GuardData implements SpawnGroupData {
        public final int variantData;

        public GuardData(int type) {
            this.variantData = type;
        }
    }

    public static class DefendVillageGuardGoal extends TargetGoal {
        private final Guard guard;
        private LivingEntity villageAggressorTarget;

        public DefendVillageGuardGoal(Guard guardIn) {
            super(guardIn, true, true);
            this.guard = guardIn;
            this.setFlags(EnumSet.of(Flag.TARGET, Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            AABB axisalignedbb = this.guard.getBoundingBox().inflate(10.0D, 8.0D, 10.0D);
            List<Villager> list = guard.level().getEntitiesOfClass(Villager.class, axisalignedbb);
            List<Player> list1 = guard.level().getEntitiesOfClass(Player.class, axisalignedbb);
            for (Villager villager : list) {
                for (Player player : list1) {
                    int i = villager.getPlayerReputation(player);
                    if (i <= GuardConfig.COMMON.reputationRequirementToBeAttacked.get()) {
                        this.villageAggressorTarget = player;
                        if (villageAggressorTarget.getTeam() != null && guard.getTeam() != null && guard.getTeam().isAlliedTo(villageAggressorTarget.getTeam()))
                            return false;
                    }
                }
            }
            return villageAggressorTarget != null && !villageAggressorTarget.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) && !this.villageAggressorTarget.isSpectator() && !((Player) this.villageAggressorTarget).isCreative();
        }

        @Override
        public void start() {
            this.guard.setTarget(this.villageAggressorTarget);
            super.start();
        }
    }

    public static class FollowHeroGoal extends Goal {
        public final Guard guard;

        public FollowHeroGoal(Guard mob) {
            this.guard = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public void tick() {
            if (guard.getOwner() != null && guard.getOwner().distanceTo(guard) > 3.0D) {
                guard.getNavigation().moveTo(guard.getOwner(), 0.7D);
                guard.getLookControl().setLookAt(guard.getOwner());
            } else {
                guard.getNavigation().stop();
            }
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public boolean canUse() {
            return guard.isFollowing() && guard.getOwner() != null;
        }

        @Override
        public void stop() {
            this.guard.getNavigation().stop();
        }
    }

    public static class GuardMeleeGoal extends MeleeAttackGoal {
        private static final double DEFAULT_ATTACK_REACH = Math.sqrt(2.04F) - (double) 0.6F;
        public final Guard guard;

        public GuardMeleeGoal(Guard guard, double speedIn, boolean useLongMemory) {
            super(guard, speedIn, useLongMemory);
            this.guard = guard;
        }

        @Override
        public boolean canUse() {
            return !(mob.isHolding(is -> is.getItem() instanceof CrossbowItem) || mob.isHolding(is -> is.getItem() instanceof BowItem)) && this.guard.getTarget() != null && !this.guard.isEating() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && this.guard.getTarget() != null;
        }

        @Override
        public void tick() {
            LivingEntity target = guard.getTarget();
            if (target != null) {
                if (target.distanceTo(guard) <= 2.5D && !guard.isBlocking()) {
                    guard.getMoveControl().strafe(-2.0F, 0.0F);
                    guard.lookAt(target, 30.0F, 30.0F);
                }
                if (path != null && target.distanceTo(guard) <= 2.0D) guard.getNavigation().stop();
                super.tick();
            }
        }

        @Override
        protected void checkAndPerformAttack(LivingEntity enemy) {
            if (canPerformAttack(enemy)) {
                this.resetAttackCooldown();
                this.guard.stopUsingItem();
                if (guard.shieldCoolDown == 0) this.guard.shieldCoolDown = 8;
                this.guard.swing(InteractionHand.MAIN_HAND);
                this.guard.doHurtTarget(enemy);
            }
        }

        @Override
        protected boolean canPerformAttack(LivingEntity mob) {
            return this.isTimeToAttack() && this.mobHitBox(this.mob).inflate(0.65).intersects(this.mobHitBox(mob)) && this.mob.getSensing().hasLineOfSight(mob);
        }

        protected AABB mobHitBox(LivingEntity mob) {
            Entity entity = mob.getVehicle();
            AABB aabb;
            if (entity != null) {
                AABB aabb1 = entity.getBoundingBox();
                AABB aabb2 = mob.getBoundingBox();
                aabb = new AABB(Math.min(aabb2.minX, aabb1.minX), aabb2.minY, Math.min(aabb2.minZ, aabb1.minZ), Math.max(aabb2.maxX, aabb1.maxX), aabb2.maxY, Math.max(aabb2.maxZ, aabb1.maxZ));
            } else {
                aabb = mob.getBoundingBox();
            }

            return aabb.inflate(DEFAULT_ATTACK_REACH, 0.0D, DEFAULT_ATTACK_REACH);
        }
    }
}