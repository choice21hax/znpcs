package io.github.gonalez.znpcs.npc;

import io.github.gonalez.znpcs.modern.ModernCustomizationBridge;
import io.github.gonalez.znpcs.modern.ModernPacketBridge;
import org.bukkit.entity.EntityType;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/** List of entity types supported by ZNPCs. */
public enum NPCType {
    PLAYER(0),
    ARMOR_STAND(0, "setSmall", "setArms"),
    CREEPER(-0.15, "setPowered"),
    BAT(-0.5, "setAwake"),
    BLAZE(0),
    CAVE_SPIDER(-1),
    COW(-0.25, "setAge"),
    CHICKEN(-1, "setAge"),
    ENDER_DRAGON(1.5),
    ENDERMAN(0.7),
    ENDERMITE(-1.5),
    GHAST(3),
    IRON_GOLEM(0.75),
    GIANT(11),
    GUARDIAN(-0.7),
    HORSE(0, "setStyle", "setAge", "setColor", "setVariant"),
    LLAMA(0, "setAge"),
    MAGMA_CUBE(-1.25, "setSize"),
    MUSHROOM_COW(-0.25, "setAge"),
    OCELOT(-1, "setCatType", "setAge"),
    PARROT(-1.5, "setVariant"),
    PIG(-1, "setAge"),
    PANDA(-0.6, "setAge", "setMainGene", "setHiddenGene"),
    RABBIT(-1, "setRabbitType"),
    POLAR_BEAR(-0.5),
    SHEEP(-0.5, "setAge", "setSheared", "setColor"),
    SILVERFISH(-1.5),
    SNOWMAN(0, "setHasPumpkin", "setDerp"),
    SKELETON(0),
    SHULKER(0),
    SLIME(-1.25, "setSize"),
    SPIDER(-1),
    SQUID(-1),
    VILLAGER(0, "setProfession", "setVillagerType", "setAge"),
    WITCH(0.5),
    WITHER(1.75),
    ZOMBIE(0, "setBaby"),
    WOLF(-1, "setSitting", "setTamed", "setAngry", "setAge", "setCollarColor"),
    FOX(-1, "setFoxType", "setSitting", "setSleeping", "setAge", "setCrouching"),
    BEE(-1, "setAnger", "setHasNectar", "setHasStung"),
    TURTLE(-1),
    WARDEN(1),
    AXOLOTL(-1, "setVariant", "setAge"),
    GOAT(-0.5, "setScreamingGoat", "setAge");

    private final double holoHeight;
    private final CustomizationLoader customizationLoader;
    private final EntityType bukkitEntityType;

    NPCType(double holoHeight, String... methods) {
        this.holoHeight = holoHeight;
        EntityType resolved = null;
        try {
            resolved = EntityType.valueOf(name());
        } catch (IllegalArgumentException ignored) {
            // The wire mapping used by ModernPacketBridge is authoritative on 26.x.
        }
        this.bukkitEntityType = resolved;
        this.customizationLoader = resolved == null ? null : new CustomizationLoader(resolved, Arrays.asList(methods));
    }

    public double getHoloHeight() {
        return holoHeight;
    }

    @Deprecated
    public Constructor<?> getConstructor() {
        return null;
    }

    @Deprecated
    public Object getNmsEntityType() {
        return null;
    }

    public EntityType getBukkitEntityType() {
        return bukkitEntityType;
    }

    public CustomizationLoader getCustomizationLoader() {
        return customizationLoader;
    }

    public static Object[] arrayToPrimitive(String[] strings, Method method) {
        Class<?>[] methodParameterTypes = method.getParameterTypes();
        Object[] newArray = new Object[methodParameterTypes.length];
        for (int i = 0; i < methodParameterTypes.length; i++) {
            TypeProperty typeProperty = TypeProperty.forType(methodParameterTypes[i]);
            if (typeProperty != null) {
                newArray[i] = typeProperty.getFunction().apply(strings[i]);
            }
        }
        return newArray;
    }

    public void updateCustomization(NPC npc, String name, String[] values) {
        if (ModernPacketBridge.isModern()) {
            ModernCustomizationBridge.apply(npc, name, values);
            return;
        }
        if (customizationLoader == null || !customizationLoader.contains(name) || npc.getBukkitEntity() == null) {
            return;
        }
        try {
            Method method = customizationLoader.getMethods().get(name);
            method.invoke(npc.getBukkitEntity(), arrayToPrimitive(values, method));
            npc.updateMetadata(npc.getViewers());
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("can't invoke method: " + name, e);
        }
    }
}
