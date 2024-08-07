package tallestegg.guardvillagers.client;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import tallestegg.guardvillagers.GuardVillagers;

@Mod.EventBusSubscriber(modid = GuardVillagers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class GuardSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, GuardVillagers.MODID);
    public static final RegistryObject<SoundEvent> GUARD_AMBIENT = SOUNDS.register("entity.guard.ambient", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(GuardVillagers.MODID, "entity.guard.ambient")));
    public static final RegistryObject<SoundEvent> GUARD_DEATH = SOUNDS.register("entity.guard.death", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(GuardVillagers.MODID, "entity.guard.death")));
    public static final RegistryObject<SoundEvent> GUARD_HURT = SOUNDS.register("entity.guard.hurt", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(GuardVillagers.MODID, "entity.guard.hurt")));
    public static final RegistryObject<SoundEvent> GUARD_YES = SOUNDS.register("entity.guard.yes", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(GuardVillagers.MODID, "entity.guard.yes")));
    public static final RegistryObject<SoundEvent> GUARD_NO = SOUNDS.register("entity.guard.no", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(GuardVillagers.MODID, "entity.guard.no")));
}
