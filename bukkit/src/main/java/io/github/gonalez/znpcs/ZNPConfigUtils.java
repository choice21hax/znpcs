package io.github.gonalez.znpcs;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import io.github.gonalez.znpcs.configuration.ConfigConfiguration;
import io.github.gonalez.znpcs.configuration.Configuration;
import io.github.gonalez.znpcs.configuration.ConversationsConfiguration;
import io.github.gonalez.znpcs.configuration.DataConfiguration;
import io.github.gonalez.znpcs.configuration.GsonConfigurationIndex;
import io.github.gonalez.znpcs.configuration.MessagesConfiguration;
import io.github.gonalez.znpcs.configuration.WritableConfigurationIndex;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.ReflectiveOperationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class ZNPConfigUtils {

  static final ImmutableMap<Class<? extends Configuration>, String> PLUGIN_CONFIGURATIONS =
      ImmutableMap.of(
          ConfigConfiguration.class, "config",
          MessagesConfiguration.class, "messages",
          DataConfiguration.class, "data",
          ConversationsConfiguration.class, "conversations");

  private static final AtomicReference<WritableConfigurationIndex> CONFIG_INDEX_REF =
      new AtomicReference<>();

  static final Map<Class<? extends Configuration>, Configuration> knownConfigs =
      new LinkedHashMap<>();

  private ZNPConfigUtils() {}

  private static void setupConfigs(WritableConfigurationIndex configurationIndex) {
    knownConfigs.clear();
    for (Class<? extends Configuration> configType : PLUGIN_CONFIGURATIONS.keySet()) {
      try {
        Configuration configuration = configurationIndex.createConfiguration(configType);
        knownConfigs.put(configType, configuration);
      } catch (IOException exception) {
        throw new IllegalStateException("Failed to load plugin configuration " + configType.getSimpleName(), exception);
      }
    }
  }

  static void setConfigurationManager(WritableConfigurationIndex configurationIndex) {
    CONFIG_INDEX_REF.set(Preconditions.checkNotNull(configurationIndex));
    setupConfigs(configurationIndex);
  }

  public static void rewriteConfigs(Predicate<Configuration> shouldSavePredicate) {
    WritableConfigurationIndex configurationIndex = CONFIG_INDEX_REF.get();
    if (configurationIndex == null) {
      throw new IllegalStateException("Configuration index has not been initialized");
    }

    for (Configuration configuration : knownConfigs.values()) {
      if (shouldSavePredicate.apply(configuration)) {
        try {
          configurationIndex.writeConfiguration(configuration);
        } catch (IOException exception) {
          throw new IllegalStateException(
              "Failed to save plugin configuration " + configuration.getClass().getSimpleName(), exception);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends Configuration> T getConfig(Class<T> configType) {
    Configuration configuration = knownConfigs.get(configType);
    if (configuration != null) {
      return (T) configuration;
    }
    throw new NullPointerException("Not a plugin config: " + configType);
  }

  static class PluginConfigConfigurationFormat extends GsonConfigurationIndex {
    private final Path pluginFolder;

    PluginConfigConfigurationFormat(Path pluginFolder, Gson gson) {
      super(gson);
      this.pluginFolder = Preconditions.checkNotNull(pluginFolder);
      try {
        Files.createDirectories(pluginFolder);
      } catch (IOException exception) {
        throw new IllegalStateException("Failed to create plugin data directory " + pluginFolder, exception);
      }
    }

    @Override
    public <T extends Configuration> T createConfiguration(Class<T> type) throws IOException {
      Path path = getConfigFilePath(type);
      if (Files.notExists(path)) {
        T configuration;
        try {
          Constructor<T> constructor = type.getDeclaredConstructor();
          constructor.setAccessible(true);
          configuration = constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
          throw new IOException("Failed to create default configuration " + type.getName(), exception);
        }
        writeConfiguration(configuration);
        return configuration;
      }
      return super.createConfiguration(type);
    }

    @Override
    public Path getConfigFilePath(Class<? extends Configuration> configurationClass) {
      String configName = PLUGIN_CONFIGURATIONS.get(configurationClass);
      if (configName == null) {
        throw new IllegalArgumentException("Not a plugin config: " + configurationClass);
      }
      return pluginFolder.resolve(configName + ".json");
    }
  }
}
