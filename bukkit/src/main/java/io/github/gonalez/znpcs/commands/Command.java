package io.github.gonalez.znpcs.commands;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Command extends BukkitCommand implements CommandExecutor {
  private final Map<CommandInformation, CommandInvoker> subCommands;

  public Command(String name) {
    super(name);
    this.subCommands = new HashMap<>();
    load();
  }

  private void load() {
    for (Method method : getClass().getMethods()) {
      if (method.isAnnotationPresent(CommandInformation.class)) {
        CommandInformation cmdInfo = method.getAnnotation(CommandInformation.class);
        this.subCommands.put(cmdInfo, new CommandInvoker(this, method, cmdInfo.permission()));
      }
    }
  }

  public Set<CommandInformation> getCommands() {
    return this.subCommands.keySet();
  }

  @Override
  public boolean onCommand(
      CommandSender sender,
      org.bukkit.command.Command command,
      String label,
      String[] args) {
    return execute(sender, label, args);
  }

  @Override
  public boolean execute(CommandSender sender, String commandLabel, String[] args) {
    Optional<Map.Entry<CommandInformation, CommandInvoker>> subCommandOptional = this.subCommands.entrySet()
        .stream()
        .filter(command -> command.getKey().name().contentEquals(args.length > 0 ? args[0] : ""))
        .findFirst();
    if (!subCommandOptional.isPresent()) {
      sender.sendMessage(ChatColor.RED + "Unknown subcommand for arguments.");
      return false;
    }
    try {
      ImmutableList<String> list = ImmutableList.copyOf(args);
      Map.Entry<CommandInformation, CommandInvoker> subCommand = subCommandOptional.get();
      subCommand.getValue().execute(sender, ImmutableList.copyOf(Iterables.skip(list, 1)));
    } catch (CommandExecuteException e) {
      sender.sendMessage(ChatColor.RED + "Failed to execute command.");
      e.printStackTrace();
    } catch (CommandPermissionException e) {
      sender.sendMessage(ChatColor.RED + "No permission.");
    }
    return true;
  }
}
