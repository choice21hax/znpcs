package io.github.gonalez.znpcs.modern;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import io.github.gonalez.znpcs.ServersNPC;
import io.github.gonalez.znpcs.npc.FunctionFactory;
import io.github.gonalez.znpcs.npc.ItemSlot;
import io.github.gonalez.znpcs.npc.NPC;
import io.github.gonalez.znpcs.npc.NPCType;
import io.github.gonalez.znpcs.npc.event.ClickType;
import io.github.gonalez.znpcs.user.ZUser;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PacketEvents-backed protocol implementation for Minecraft/Paper 26.x.
 *
 * <p>This deliberately avoids constructing server-side NMS entities. The old ZNPCs reflection
 * layer depended on obfuscated/renamed internals and is not viable on 26.1+, where Mojang ships
 * an unobfuscated server. PacketEvents owns the protocol-version details instead.</p>
 */
public final class ModernPacketBridge {
  private static final AtomicInteger ENTITY_IDS = new AtomicInteger(1_500_000_000);

  private static ServersNPC plugin;
  private static PacketEventsAPI<Plugin> api;
  private static boolean loaded;
  private static boolean initialized;

  private ModernPacketBridge() {}

  public static boolean isModern() {
    return io.github.gonalez.znpcs.utility.Utils.BUKKIT_VERSION >= 26;
  }

  public static void load(ServersNPC owningPlugin) {
    if (!isModern() || loaded) return;
    plugin = owningPlugin;
    api = SpigotPacketEventsBuilder.build(owningPlugin);
    PacketEvents.setAPI(api);
    api.getSettings().checkForUpdates(false);
    api.load();
    loaded = true;
  }

  public static void init() {
    if (!isModern() || initialized) return;
    if (!loaded) throw new IllegalStateException("ModernPacketBridge.load() must run during onLoad");
    api.init();
    api.getEventManager().registerListener(new InteractionListener(), PacketListenerPriority.MONITOR);
    initialized = true;
    plugin.getLogger().info("Using embedded PacketEvents " + api.getVersion() + " protocol bridge for Minecraft "
        + api.getServerManager().getVersion() + ".");
  }

  public static void terminate() {
    if (!loaded) return;
    try {
      api.terminate();
    } finally {
      initialized = false;
      loaded = false;
      api = null;
      plugin = null;
    }
  }

  public static int reserveEntityId() {
    return ENTITY_IDS.getAndIncrement();
  }

  public static UUID reserveUuid() {
    return UUID.randomUUID();
  }

  public static void spawn(NPC npc, ZUser user) {
    Player viewer = user.toPlayer();
    if (viewer == null) return;

    EntityType type = entityType(npc.getNpcPojo().getNpcType());
    if (type == EntityTypes.PLAYER) {
      addPlayerInfo(viewer, npc);
      createTeam(viewer, npc);
    }

    Location location = npc.getLocation();
    send(viewer, new WrapperPlayServerSpawnEntity(
        npc.getEntityID(), Optional.of(npc.getUUID()), type,
        vector(location), location.getPitch(), location.getYaw(), location.getYaw(),
        0, Optional.of(new Vector3d())));
    send(viewer, new WrapperPlayServerEntityHeadLook(npc.getEntityID(), location.getYaw()));
    sendMetadata(viewer, npc);
    sendEquipment(viewer, npc);

    if (type == EntityTypes.PLAYER) {
      plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
        if (viewer.isOnline()) removePlayerInfo(viewer, npc);
      }, 60L);
    }
  }

  public static void destroy(NPC npc, ZUser user) {
    Player viewer = user.toPlayer();
    if (viewer == null) return;
    send(viewer, new WrapperPlayServerDestroyEntities(npc.getEntityID()));
    if (npc.getNpcPojo().getNpcType() == NPCType.PLAYER) {
      removePlayerInfo(viewer, npc);
      removeTeam(viewer, npc);
    }
  }

  public static void teleport(NPC npc) {
    Location location = npc.getLocation();
    EntityPositionData data = new EntityPositionData(
        vector(location), new Vector3d(0, 0, 0), location.getYaw(), location.getPitch());
    for (ZUser user : npc.getViewers()) {
      Player player = user.toPlayer();
      if (player == null) continue;
      send(player, new WrapperPlayServerEntityTeleport(npc.getEntityID(), data, RelativeFlag.NONE, false));
      send(player, new WrapperPlayServerEntityHeadLook(npc.getEntityID(), location.getYaw()));
    }
  }

  public static void rotate(NPC npc, ZUser user, float yaw, float pitch) {
    if (user != null) {
      Player player = user.toPlayer();
      if (player != null) sendRotation(player, npc, yaw, pitch);
      return;
    }
    for (ZUser viewer : npc.getViewers()) {
      Player player = viewer.toPlayer();
      if (player != null) sendRotation(player, npc, yaw, pitch);
    }
  }

  public static void sendMetadata(NPC npc, Iterable<ZUser> users) {
    for (ZUser user : users) {
      Player player = user.toPlayer();
      if (player != null) sendMetadata(player, npc);
    }
  }

  public static void sendEquipment(NPC npc, ZUser user) {
    Player player = user.toPlayer();
    if (player != null) sendEquipment(player, npc);
  }

  private static void addPlayerInfo(Player viewer, NPC npc) {
    UserProfile profile = new UserProfile(npc.getUUID(), Integer.toString(npc.getEntityID()));
    String texture = npc.getNpcPojo().getSkin();
    if (texture != null && !texture.isEmpty()) {
      profile.setTextureProperties(Collections.singletonList(
          new TextureProperty("textures", texture, emptyToNull(npc.getNpcPojo().getSignature()))));
    }

    WrapperPlayServerPlayerInfoUpdate.PlayerInfo info =
        new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
            profile, false, 1, GameMode.CREATIVE, Component.empty(), null);
    send(viewer, new WrapperPlayServerPlayerInfoUpdate(
        EnumSet.of(
            WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED,
            WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME),
        info));
  }

  private static void removePlayerInfo(Player viewer, NPC npc) {
    send(viewer, new WrapperPlayServerPlayerInfoRemove(npc.getUUID()));
  }

  private static void createTeam(Player viewer, NPC npc) {
    String team = teamName(npc);
    send(viewer, new WrapperPlayServerTeams(
        team,
        WrapperPlayServerTeams.TeamMode.CREATE,
        new WrapperPlayServerTeams.ScoreBoardTeamInfo(
            Component.empty(), null, null,
            WrapperPlayServerTeams.NameTagVisibility.NEVER,
            WrapperPlayServerTeams.CollisionRule.NEVER,
            NamedTextColor.WHITE,
            WrapperPlayServerTeams.OptionData.NONE)));
    send(viewer, new WrapperPlayServerTeams(
        team,
        WrapperPlayServerTeams.TeamMode.ADD_ENTITIES,
        (WrapperPlayServerTeams.ScoreBoardTeamInfo) null,
        Integer.toString(npc.getEntityID())));
  }

  private static void removeTeam(Player viewer, NPC npc) {
    send(viewer, new WrapperPlayServerTeams(
        teamName(npc), WrapperPlayServerTeams.TeamMode.REMOVE,
        (WrapperPlayServerTeams.ScoreBoardTeamInfo) null));
  }

  private static String teamName(NPC npc) {
    return "znpc_" + npc.getNpcPojo().getId();
  }

  private static void sendMetadata(Player viewer, NPC npc) {
    List<EntityData<?>> metadata = new ArrayList<>();
    byte entityFlags = 0;
    if (FunctionFactory.isTrue(npc, "glow")) entityFlags |= 0x40;
    if (entityFlags != 0) {
      metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, entityFlags));
    }
    if (npc.getNpcPojo().getNpcType() == NPCType.PLAYER) {
      metadata.add(new EntityData<>(16, EntityDataTypes.BYTE, (byte) 0x7F));
    }
    if (!metadata.isEmpty()) {
      send(viewer, new WrapperPlayServerEntityMetadata(npc.getEntityID(), metadata));
    }
  }

  private static void sendEquipment(Player viewer, NPC npc) {
    if (npc.getNpcPojo().getNpcEquip().isEmpty()) return;
    List<Equipment> equipment = new ArrayList<>();
    npc.getNpcPojo().getNpcEquip().forEach((slot, item) -> {
      if (item != null) {
        equipment.add(new Equipment(equipmentSlot(slot), SpigotConversionUtil.fromBukkitItemStack(item)));
      }
    });
    if (!equipment.isEmpty()) {
      send(viewer, new WrapperPlayServerEntityEquipment(npc.getEntityID(), equipment));
    }
  }

  private static EquipmentSlot equipmentSlot(ItemSlot slot) {
    return switch (slot) {
      case HAND -> EquipmentSlot.MAIN_HAND;
      case OFFHAND -> EquipmentSlot.OFF_HAND;
      case BOOTS -> EquipmentSlot.BOOTS;
      case LEGGINGS -> EquipmentSlot.LEGGINGS;
      case CHESTPLATE -> EquipmentSlot.CHEST_PLATE;
      case HELMET -> EquipmentSlot.HELMET;
    };
  }

  private static void sendRotation(Player player, NPC npc, float yaw, float pitch) {
    send(player, new WrapperPlayServerEntityHeadLook(npc.getEntityID(), yaw));
    send(player, new WrapperPlayServerEntityRotation(npc.getEntityID(), yaw, pitch, true));
  }

  private static Vector3d vector(Location location) {
    return new Vector3d(location.getX(), location.getY(), location.getZ());
  }

  private static void send(Player player, PacketWrapper<?> packet) {
    api.getPlayerManager().sendPacket(player, packet);
  }

  private static String emptyToNull(String value) {
    return value == null || value.isEmpty() ? null : value;
  }

  private static EntityType entityType(NPCType type) {
    return switch (type) {
      case PLAYER -> EntityTypes.PLAYER;
      case ARMOR_STAND -> EntityTypes.ARMOR_STAND;
      case CREEPER -> EntityTypes.CREEPER;
      case BAT -> EntityTypes.BAT;
      case BLAZE -> EntityTypes.BLAZE;
      case CAVE_SPIDER -> EntityTypes.CAVE_SPIDER;
      case COW -> EntityTypes.COW;
      case CHICKEN -> EntityTypes.CHICKEN;
      case ENDER_DRAGON -> EntityTypes.ENDER_DRAGON;
      case ENDERMAN -> EntityTypes.ENDERMAN;
      case ENDERMITE -> EntityTypes.ENDERMITE;
      case GHAST -> EntityTypes.GHAST;
      case IRON_GOLEM -> EntityTypes.IRON_GOLEM;
      case GIANT -> EntityTypes.GIANT;
      case GUARDIAN -> EntityTypes.GUARDIAN;
      case HORSE -> EntityTypes.HORSE;
      case LLAMA -> EntityTypes.LLAMA;
      case MAGMA_CUBE -> EntityTypes.MAGMA_CUBE;
      case MUSHROOM_COW -> EntityTypes.MOOSHROOM;
      case OCELOT -> EntityTypes.OCELOT;
      case PARROT -> EntityTypes.PARROT;
      case PIG -> EntityTypes.PIG;
      case PANDA -> EntityTypes.PANDA;
      case RABBIT -> EntityTypes.RABBIT;
      case POLAR_BEAR -> EntityTypes.POLAR_BEAR;
      case SHEEP -> EntityTypes.SHEEP;
      case SILVERFISH -> EntityTypes.SILVERFISH;
      case SNOWMAN -> EntityTypes.SNOW_GOLEM;
      case SKELETON -> EntityTypes.SKELETON;
      case SHULKER -> EntityTypes.SHULKER;
      case SLIME -> EntityTypes.SLIME;
      case SPIDER -> EntityTypes.SPIDER;
      case SQUID -> EntityTypes.SQUID;
      case VILLAGER -> EntityTypes.VILLAGER;
      case WITCH -> EntityTypes.WITCH;
      case WITHER -> EntityTypes.WITHER;
      case ZOMBIE -> EntityTypes.ZOMBIE;
      case WOLF -> EntityTypes.WOLF;
      case FOX -> EntityTypes.FOX;
      case BEE -> EntityTypes.BEE;
      case TURTLE -> EntityTypes.TURTLE;
      case WARDEN -> EntityTypes.WARDEN;
      case AXOLOTL -> EntityTypes.AXOLOTL;
      case GOAT -> EntityTypes.GOAT;
    };
  }

  private static final class InteractionListener implements PacketListener {
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
      if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
      if (!(event.getPlayer() instanceof Player player)) return;

      WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
      NPC npc = NPC.all().stream()
          .filter(candidate -> candidate.getEntityID() == packet.getEntityId())
          .findFirst().orElse(null);
      if (npc == null) return;

      ClickType clickType = switch (packet.getAction()) {
        case ATTACK -> ClickType.LEFT;
        case INTERACT, INTERACT_AT -> ClickType.RIGHT;
      };
      ZUser.find(player).handleInteraction(npc, clickType);
    }
  }
}
