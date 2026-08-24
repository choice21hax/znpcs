package io.github.gonalez.znpcs.modern;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.gonalez.znpcs.user.ZUser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Packet-only armor-stand holograms for 26.x. */
public final class ModernHologramBridge {
  private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

  private ModernHologramBridge() {}

  public static void spawn(ZUser user, int entityId, UUID uuid, String text, Location location, boolean visible) {
    Player player = user.toPlayer();
    if (player == null) return;

    PacketEvents.getAPI().getPlayerManager().sendPacket(player,
        new WrapperPlayServerSpawnEntity(entityId, Optional.of(uuid), EntityTypes.ARMOR_STAND,
            vector(location), 0F, 0F, 0F, 0, Optional.of(new Vector3d())));
    updateText(user, entityId, text, visible);
  }

  public static void updateText(ZUser user, int entityId, String text, boolean visible) {
    Player player = user.toPlayer();
    if (player == null) return;

    List<EntityData<?>> metadata = new ArrayList<>();
    metadata.add(new EntityData<>(0, EntityDataTypes.BYTE, (byte) 0x20)); // invisible
    if (visible) {
      Component component = LEGACY.deserialize(text == null ? "" : text);
      metadata.add(new EntityData<>(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, Optional.of(component)));
      metadata.add(new EntityData<>(3, EntityDataTypes.BOOLEAN, true));
    } else {
      metadata.add(new EntityData<>(3, EntityDataTypes.BOOLEAN, false));
    }
    metadata.add(new EntityData<>(5, EntityDataTypes.BOOLEAN, true)); // no gravity
    metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 0x10)); // marker, no hitbox

    PacketEvents.getAPI().getPlayerManager().sendPacket(player,
        new WrapperPlayServerEntityMetadata(entityId, metadata));
  }

  public static void teleport(Iterable<ZUser> users, int entityId, Location location) {
    EntityPositionData data = new EntityPositionData(
        vector(location), new Vector3d(0, 0, 0), 0F, 0F);
    for (ZUser user : users) {
      Player player = user.toPlayer();
      if (player == null) continue;
      PacketEvents.getAPI().getPlayerManager().sendPacket(player,
          new WrapperPlayServerEntityTeleport(entityId, data, RelativeFlag.NONE, false));
    }
  }

  public static void destroy(ZUser user, int entityId) {
    Player player = user.toPlayer();
    if (player == null) return;
    PacketEvents.getAPI().getPlayerManager().sendPacket(player,
        new WrapperPlayServerDestroyEntities(entityId));
  }

  private static Vector3d vector(Location location) {
    return new Vector3d(location.getX(), location.getY(), location.getZ());
  }
}
