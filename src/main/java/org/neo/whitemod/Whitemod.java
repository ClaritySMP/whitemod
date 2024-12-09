package org.neo.whitemod;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class Whitemod extends JavaPlugin {

    private FileConfiguration config;

    @Override
    public void onEnable() {
        // Load the configuration
        createDefaultConfig();
        config = getConfig();

        String discordToken = config.getString("bot-token");
        if (discordToken == null || discordToken.isEmpty()) {
            getLogger().severe("Discord bot token is missing! Please add it to config.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize Discord bot
        try {
            JDABuilder.createDefault(discordToken)
                    .addEventListeners(new DiscordBot(this)) // Pass plugin instance to the bot
                    .build();
            getLogger().info("Discord bot has been successfully started.");
        } catch (Exception e) {
            getLogger().severe("Failed to start Discord bot: " + e.getMessage());
        }

        if (config.getBoolean("commands.whitelist.enabled", true)) {
            getLogger().info("Whitelist command enabled.");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Whitemod plugin disabled.");
    }

    // Create default config.yml
    private void createDefaultConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
            getLogger().info("Default config.yml created.");
        }
        reloadConfig();
    }

    public class DiscordBot extends ListenerAdapter {

        private final Whitemod plugin;

        public DiscordBot(Whitemod plugin) {
            this.plugin = plugin;
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            if (event.getGuild() == null) {
                return; // Only process guild events
            }

            FileConfiguration config = plugin.getConfig();
            String guildId = config.getString("guild-id");
            if (!event.getGuild().getId().equals(guildId)) {
                event.reply("This command is not available in this guild.").setEphemeral(true).queue();
                return;
            }

            if (config.getBoolean("check-permissions", true)) {
                boolean hasPermission = event.getMember().getRoles().stream()
                        .map(Role::getName)
                        .anyMatch(role -> config.getStringList("allowed-roles").contains(role));
                if (!hasPermission) {
                    event.reply(config.getString("messages.no-permission", "You don't have permission to use this command.")).setEphemeral(true).queue();
                    return;
                }
            }

            if (event.getName().equals("whitelist")) {
                String username = event.getOption(config.getString("commands.whitelist.option-name", "username")).getAsString();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        ConsoleCommandSender console = Bukkit.getServer().getConsoleSender();
                        Bukkit.getServer().dispatchCommand(console, "whitelist add " + username);
                        event.reply(config.getString("messages.whitelist-success", "Successfully added %username% to the Minecraft whitelist!").replace("%username%", username)).queue();
                    } catch (Exception e) {
                        event.reply(config.getString("messages.whitelist-failure", "Failed to add %username% to the whitelist. Please try again.").replace("%username%", username)).queue();
                        e.printStackTrace();
                    }
                });
            }
        }
    }
}
