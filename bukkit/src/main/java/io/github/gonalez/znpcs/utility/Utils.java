package io.github.gonalez.znpcs.utility;

import io.github.gonalez.znpcs.ZNPConfigUtils;
import io.github.gonalez.znpcs.cache.CacheRegistry;
import io.github.gonalez.znpcs.configuration.ConfigConfiguration;
import io.github.gonalez.znpcs.user.ZUser;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadLocalRandom;

public final class Utils {
  public static final int BUKKIT_VERSION = getMinecraftMajorVersion();
  public static boolean PLACEHOLDER_SUPPORT = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

  private Utils() {}

  private static int getMinecraftMajorVersion() {
    String minecraftVersion = Bukkit.getMinecraftVersion();
    String[] parts = minecraftVersion.split("\\.");
    if (parts.length == 0) {
      throw new IllegalStateException("Unable to determine Minecraft version from " + minecraftVersion);
    }

    try {
      int first = Integer.parseInt(parts[0]);
      return first == 1 && parts.length > 1 ? Integer.parseInt(parts[1]) : first;
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("Unable to parse Minecraft version " + minecraftVersion, exception);
    }
  }

  public static void sendMessage(CommandSender commandSender, String message, Object... args) {
    commandSender.sendMessage(toColor(String.format(message, args)));
  }

  public static boolean versionNewer(int version) {
    return BUKKIT_VERSION >= version;
  }

  public static String getBukkitPackage() {
    String packageName = Bukkit.getServer().getClass().getPackage().getName();
    String prefix = "org.bukkit.craftbukkit";
    if (packageName.equals(prefix)) return "";
    if (packageName.startsWith(prefix + ".")) {
      return packageName.substring(prefix.length() + 1).split("\\.")[0];
    }
    return "";
  }

  public static String getFormattedBukkitPackage() {
    return Integer.toString(BUKKIT_VERSION);
  }

  public static String toColor(String string) {
    return ChatColor.translateAlternateColorCodes('&', string);
  }

  public static String getWithPlaceholders(String string, Player player) {
    String replaced = PLACEHOLDER_SUPPORT ? PlaceholderAPI.setPlaceholders(player, string) : string;
    return replaced.replace(ZNPConfigUtils.getConfig(ConfigConfiguration.class).replaceSymbol, " ");
  }

  public static String randomString(int length) {
    StringBuilder stringBuilder = new StringBuilder();
    for (int index = 0; index < length; index++)
      stringBuilder.append(ThreadLocalRandom.current().nextInt(0, 9));
    return stringBuilder.toString();
  }

  public static void sendTitle(Player player, String title, String subTitle) {
    player.sendTitle(toColor(title), toColor(subTitle));
  }

  public static void setValue(Object fieldInstance, String fieldName, Object value) throws NoSuchFieldException, IllegalAccessException {
    Field f = fieldInstance.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(fieldInstance, value);
  }

  public static void setValue(Object fieldInstance, Object value, Class<?> expectedType)
      throws NoSuchFieldException, IllegalAccessException {
    for (Field field : fieldInstance.getClass().getDeclaredFields()) {
      if (field.getType() == expectedType)
        setValue(fieldInstance, field.getName(), value);
    }
  }

  public static Object getValue(Object instance, String fieldName) throws NoSuchFieldException, IllegalAccessException {
    Field f = instance.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    return f.get(instance);
  }

  public static void sendPackets(ZUser user, Object... packets) {
    try {
      for (Object packet : packets) {
        if (packet != null)
          CacheRegistry.SEND_PACKET_METHOD.load().invoke(user.getPlayerConnection(), packet);
      }
    } catch (IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
      e.printStackTrace();
    }
  }
}
