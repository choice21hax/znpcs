package io.github.gonalez.znpcs.npc.hologram;

import io.github.gonalez.znpcs.UnexpectedCallException;
import io.github.gonalez.znpcs.ZNPConfigUtils;
import io.github.gonalez.znpcs.cache.CacheRegistry;
import io.github.gonalez.znpcs.configuration.ConfigConfiguration;
import io.github.gonalez.znpcs.modern.ModernHologramBridge;
import io.github.gonalez.znpcs.modern.ModernPacketBridge;
import io.github.gonalez.znpcs.npc.NPC;
import io.github.gonalez.znpcs.npc.hologram.replacer.LineReplacer;
import io.github.gonalez.znpcs.user.ZUser;
import io.github.gonalez.znpcs.utility.Utils;
import org.bukkit.Location;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Hologram {
  private static final String WHITESPACE = " ";
  private static final boolean NEW_METHOD = (Utils.BUKKIT_VERSION > 12);

  private final List<HologramLine> hologramLines = new ArrayList<>();
  private final NPC npc;
  private Location currentLocation;
  private double currentEntityHeight;

  public Hologram(NPC npc) {
    this.npc = npc;
  }

  public void createHologram() {
    npc.getViewers().forEach(this::delete);
    hologramLines.clear();

    if (ModernPacketBridge.isModern()) {
      for (String line : npc.getNpcPojo().getHologramLines()) {
        hologramLines.add(new HologramLine(
            line.replace(ZNPConfigUtils.getConfig(ConfigConfiguration.class).replaceSymbol, WHITESPACE),
            null,
            ModernPacketBridge.reserveEntityId(),
            ModernPacketBridge.reserveUuid()));
      }
      setLocation(npc.getLocation(), currentEntityHeight);
      npc.getViewers().forEach(this::spawn);
      return;
    }

    try {
      double y = 0;
      final Location location = npc.getLocation();
      for (String line : npc.getNpcPojo().getHologramLines()) {
        boolean visible = !line.equalsIgnoreCase("%space%");
        Object armorStand = CacheRegistry.ENTITY_CONSTRUCTOR.load().newInstance(
            CacheRegistry.GET_HANDLE_WORLD_METHOD.load().invoke(location.getWorld()),
            location.getX(), (location.getY() - 0.15) + y, location.getZ());
        if (visible) {
          CacheRegistry.SET_CUSTOM_NAME_VISIBLE_METHOD.load().invoke(armorStand, true);
          updateLine(line, armorStand, null);
        }
        CacheRegistry.SET_INVISIBLE_METHOD.load().invoke(armorStand, true);
        hologramLines.add(new HologramLine(
            line.replace(ZNPConfigUtils.getConfig(ConfigConfiguration.class).replaceSymbol, WHITESPACE),
            armorStand,
            (Integer) CacheRegistry.GET_ENTITY_ID.load().invoke(armorStand),
            UUID.randomUUID()));
        y += ZNPConfigUtils.getConfig(ConfigConfiguration.class).lineSpacing;
      }
      setLocation(location, 0);
      npc.getPackets().flushCache("getHologramSpawnPacket");
      npc.getViewers().forEach(this::spawn);
    } catch (ReflectiveOperationException operationException) {
      throw new UnexpectedCallException(operationException);
    }
  }

  public void spawn(ZUser user) {
    if (ModernPacketBridge.isModern()) {
      for (int i = 0; i < hologramLines.size(); i++) {
        HologramLine hologramLine = hologramLines.get(i);
        boolean visible = !hologramLine.line.equalsIgnoreCase("%space%");
        String text = visible ? Utils.toColor(LineReplacer.makeAll(user, hologramLine.line)) : "";
        ModernHologramBridge.spawn(user, hologramLine.id, hologramLine.uuid, text,
            locationForLine(i), visible);
      }
      return;
    }

    hologramLines.forEach(hologramLine -> {
      try {
        Object entityPlayerPacketSpawn = npc.getPackets().getProxyInstance()
            .getHologramSpawnPacket(hologramLine.armorStand);
        Utils.sendPackets(user, entityPlayerPacketSpawn);
      } catch (ReflectiveOperationException operationException) {
        delete(user);
      }
    });
  }

  public void delete(ZUser user) {
    if (ModernPacketBridge.isModern()) {
      hologramLines.forEach(line -> ModernHologramBridge.destroy(user, line.id));
      return;
    }

    hologramLines.forEach(hologramLine -> {
      try {
        Utils.sendPackets(user, npc.getPackets().getProxyInstance().getDestroyPacket(hologramLine.id));
      } catch (ReflectiveOperationException operationException) {
        throw new UnexpectedCallException(operationException);
      }
    });
  }

  public void updateNames(ZUser user) {
    if (ModernPacketBridge.isModern()) {
      for (HologramLine hologramLine : hologramLines) {
        boolean visible = !hologramLine.line.equalsIgnoreCase("%space%");
        String text = visible ? Utils.toColor(LineReplacer.makeAll(user, hologramLine.line)) : "";
        ModernHologramBridge.updateText(user, hologramLine.id, text, visible);
      }
      return;
    }

    for (HologramLine hologramLine : hologramLines) {
      try {
        updateLine(hologramLine.line, hologramLine.armorStand, user);
        Object metaData = npc.getPackets().getProxyInstance().getMetadataPacket(
            hologramLine.id, hologramLine.armorStand);
        Utils.sendPackets(user, metaData);
      } catch (ReflectiveOperationException operationException) {
        throw new UnexpectedCallException(operationException);
      }
    }
  }

  public void updateLocation() {
    if (ModernPacketBridge.isModern()) {
      for (int i = 0; i < hologramLines.size(); i++) {
        ModernHologramBridge.teleport(npc.getViewers(), hologramLines.get(i).id, locationForLine(i));
      }
      return;
    }

    hologramLines.forEach(hologramLine -> {
      try {
        Object packet = CacheRegistry.PACKET_PLAY_OUT_ENTITY_TELEPORT_CONSTRUCTOR.load().newInstance(
            hologramLine.armorStand);
        npc.getViewers().forEach(player -> Utils.sendPackets(player, packet));
      } catch (ReflectiveOperationException operationException) {
        throw new UnexpectedCallException(operationException);
      }
    });
  }

  public void setLocation(Location location, double height) {
    this.currentLocation = location.clone();
    this.currentEntityHeight = height;

    if (ModernPacketBridge.isModern()) {
      updateLocation();
      return;
    }

    location = location.clone().add(0, height, 0);
    try {
      double y = npc.getNpcPojo().getHologramHeight();
      for (HologramLine hologramLine : hologramLines) {
        CacheRegistry.SET_LOCATION_METHOD.load().invoke(hologramLine.armorStand,
            location.getX(), (location.getY() - 0.15) + y,
            location.getZ(), location.getYaw(), location.getPitch());
        y += ZNPConfigUtils.getConfig(ConfigConfiguration.class).lineSpacing;
      }
      updateLocation();
    } catch (ReflectiveOperationException operationException) {
      throw new UnexpectedCallException(operationException);
    }
  }

  private Location locationForLine(int index) {
    Location base = currentLocation != null ? currentLocation.clone() : npc.getLocation().clone();
    double spacing = ZNPConfigUtils.getConfig(ConfigConfiguration.class).lineSpacing;
    double y = currentEntityHeight + npc.getNpcPojo().getHologramHeight() - 0.15 + (index * spacing);
    return base.add(0, y, 0);
  }

  private void updateLine(String line, Object armorStand, @Nullable ZUser user)
      throws InvocationTargetException, IllegalAccessException {
    if (NEW_METHOD) {
      CacheRegistry.SET_CUSTOM_NAME_NEW_METHOD.load().invoke(
          armorStand,
          CacheRegistry.CRAFT_CHAT_MESSAGE_METHOD.load().invoke(null, LineReplacer.makeAll(user, line)));
    } else {
      CacheRegistry.SET_CUSTOM_NAME_OLD_METHOD.load().invoke(armorStand, LineReplacer.makeAll(user, line));
    }
  }

  private static class HologramLine {
    private final String line;
    private final Object armorStand;
    private final int id;
    private final UUID uuid;

    protected HologramLine(String line, Object armorStand, int id, UUID uuid) {
      this.line = line;
      this.armorStand = armorStand;
      this.id = id;
      this.uuid = uuid;
    }
  }
}
