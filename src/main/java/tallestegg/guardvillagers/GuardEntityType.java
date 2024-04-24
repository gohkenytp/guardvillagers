package tallestegg.guardvillagers;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import tallestegg.guardvillagers.entities.Guard;

@Mod.EventBusSubscriber(modid = GuardVillagers.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class GuardEntityType {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, GuardVillagers.MODID);
    public static final RegistryObject<EntityType<Guard>> GUARD = ENTITIES.register("guard", () -> EntityType.Builder.of(Guard::new, MobCategory.MISC).sized(0.6F, 1.90F).setShouldReceiveVelocityUpdates(true).build(GuardVillagers.MODID + "guard"));
}
