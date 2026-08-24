package io.github.gonalez.znpcs.user;

import com.mojang.authlib.GameProfile;
import io.github.gonalez.znpcs.ServersNPC;
import io.github.gonalez.znpcs.cache.CacheRegistry;
import io.github.gonalez.znpcs.modern.ModernPacketBridge;
import io.github.gonalez.znpcs.npc.NPC;
import io.github.gonalez.znpcs.npc.NPCAction;
import io.github.gonalez.znpcs.npc.event.ClickType;
import io.github.gonalez.znpcs.npc.event.NPCInteractEvent;
import io.netty.channel.Channel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ZUser {
  private static final String CHANNEL_NAME = "npc_interact";
  private static final Map<UUID, ZUser> USER_MAP = new HashMap<>();

  private final Map<Integer, Long> lastClicked;
  private final List<EventService<?>> eventServices;
  private final UUID uuid;
  private final GameProfile gameProfile;
  private final Object playerConnection;

  private boolean hasPath = false;
  private long lastInteract = 0L;

  public ZUser(UUID uuid) {
    this.uuid = uuid;
    this.lastClicked = new HashMap<>();
    this.eventServices = new ArrayList<>();

    Player player = toPlayer();
    if (player == null) {
      throw new IllegalStateException("can't create player " + uuid + ": player is offline");
    }

    if (ModernPacketBridge.isModern()) {
      // 26.1+ interactions are handled by the embedded PacketEvents listener. Do not touch
      // CraftPlayer, ServerGamePacketListenerImpl, or the Netty pipeline here.
      this.gameProfile = new GameProfile(uuid, player.getName());
      this.playerConnection = null;
      return;
    }

    try {
      Object playerHandle = CacheRegistry.GET_HANDLE_PLAYER_METHOD.load().invoke(player);
      this.gameProfile = (GameProfile) CacheRegistry.GET_PROFILE_METHOD.load().invoke(playerHandle);
      this.playerConnection = CacheRegistry.PLAYER_CONNECTION_FIELD.load().get(playerHandle);
      Channel channel = (Channel) CacheRegistry.CHANNEL_FIELD.load().get(
          CacheRegistry.NETWORK_MANAGER_FIELD.load().get(this.playerConnection));
      if (channel.pipeline().names().contains(CHANNEL_NAME)) {
        channel.pipeline().remove(CHANNEL_NAME);
      }
      channel.pipeline().addAfter("decoder", CHANNEL_NAME, new NpcInteractServerHandler(this));
    } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
      throw new IllegalStateException("can't create player " + uuid, e.getCause());
    }
  }

  public UUID getUUID() {
    return this.uuid;
  }

  public GameProfile getGameProfile() {
    return this.gameProfile;
  }

  public Object getPlayerConnection() {
    return this.playerConnection;
  }

  public boolean isHasPath() {
    return this.hasPath;
  }

  public List<EventService<?>> getEventServices() {
    return this.eventServices;
  }

  public void setHasPath(boolean hasPath) {
    this.hasPath = hasPath;
  }

  public Player toPlayer() {
    return Bukkit.getPlayer(this.uuid);
  }

  /** Handles a virtual-NPC interaction for both legacy Netty injection and the 26.x PacketEvents bridge. */
  public void handleInteraction(NPC npc, ClickType clickType) {
    long lastInteractNanos = System.nanoTime() - this.lastInteract;
    if (this.lastInteract != 0L && lastInteractNanos < 1_000_000_000L) {
      return;
    }
    this.lastInteract = System.nanoTime();

    ServersNPC.SCHEDULER.scheduleSyncDelayedTask(() -> {
      Player player = toPlayer();
      if (player == null) return;

      Bukkit.getServer().getPluginManager().callEvent(new NPCInteractEvent(player, clickType, npc));
      List<NPCAction> actions = npc.getNpcPojo().getClickActions();
      if (actions == null || actions.isEmpty()) return;

      for (NPCAction npcAction : actions) {
        if (npcAction.getClickType() != ClickType.DEFAULT && clickType != npcAction.getClickType()) {
          continue;
        }
        if (npcAction.getDelay() > 0) {
          int actionId = actions.indexOf(npcAction);
          Long previous = this.lastClicked.get(actionId);
          if (previous != null && System.nanoTime() - previous < npcAction.getFixedDelay()) {
            continue;
          }
          this.lastClicked.put(actionId, System.nanoTime());
        }
        npcAction.run(this, npcAction.getAction());
      }
    }, 1);
  }

  public static ZUser find(UUID uuid) {
    return USER_MAP.computeIfAbsent(uuid, ZUser::new);
  }

  public static ZUser find(Player player) {
    return find(player.getUniqueId());
  }

  public static void unregister(Player player) {
    ZUser zUser = USER_MAP.get(player.getUniqueId());
    if (zUser == null) {
      return;
    }
    USER_MAP.remove(player.getUniqueId());
    NPC.all().stream()
        .filter(npc -> npc.getViewers().contains(zUser))
        .forEach(npc -> npc.delete(zUser));
  }
}
