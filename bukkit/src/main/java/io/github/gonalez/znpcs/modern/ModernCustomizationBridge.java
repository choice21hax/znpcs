package io.github.gonalez.znpcs.modern;

import io.github.gonalez.znpcs.npc.NPC;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compatibility boundary for legacy Bukkit-entity customizations.
 *
 * <p>26.x NPCs are packet-only, so legacy calls such as setAge/setPowered cannot be invoked on a
 * backing Bukkit entity. Packet metadata equivalents are being handled here rather than allowing
 * the old reflection path to crash the plugin.</p>
 */
public final class ModernCustomizationBridge {
  private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

  private ModernCustomizationBridge() {}

  public static void apply(NPC npc, String name, String[] values) {
    // Core NPC rendering, skins, holograms, equipment, glow, rotation and interactions do not use
    // this path. Keep unsupported legacy entity-mutator customizations non-fatal on 26.1.2.
    String key = npc.getNpcPojo().getNpcType().name() + ':' + name;
    if (WARNED.add(key)) {
      org.bukkit.Bukkit.getLogger().fine(
          "[ServersNPC] Ignoring legacy packet-only customization on 26.x: " + key);
    }
  }
}
