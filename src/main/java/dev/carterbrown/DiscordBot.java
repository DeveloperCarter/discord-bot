package dev.carterbrown;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

public class DiscordBot extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DiscordBot.class);

    public static void main(String[] args) {
        String token = System.getenv("DISCORD_BOT_TOKEN");
        if (token == null || token.isBlank()) {
            logger.error("❌ DISCORD_BOT_TOKEN environment variable not set.");
            System.exit(1);
        }

        try {
            JDA jda = JDABuilder.createDefault(token, EnumSet.of(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.MESSAGE_CONTENT // Required for message content access
                    ))
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE)
                    .setActivity(Activity.watching("you 👀"))
                    .addEventListeners(new DiscordBot())
                    .build();

            jda.awaitReady();
            logger.info("✅ Bot is online as {}", jda.getSelfUser().getAsTag());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down bot...");
                jda.shutdownNow();
            }));

        } catch (InterruptedException e) {
            logger.error("Startup interrupted.", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Failed to start bot.", e);
            System.exit(1);
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        event.getJDA().updateCommands().addCommands(
                Commands.slash("spam", "Start spamming a user")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.USER, "target", "User to spam", true),
                Commands.slash("stopspam", "Stop spamming and delete messages"),
                Commands.slash("clearchat", "Clears messages (restricted to certain channels)"),
                Commands.slash("help", "List available commands"),
                Commands.slash("coinflip", "Flip a coin"),
                Commands.slash("picker", "Randomly pick one option")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "options", "Comma-separated list of options", true)
        ).queue();
        logger.info("Commands registered successfully.");
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        try {
            event.deferReply().queue(); // Defer once at the top

            switch (event.getName()) {
                case "spam" -> CommandHandlers.handleSpam(event);
                case "stopspam" -> CommandHandlers.handleStopSpam(event);
                case "clearchat" -> CommandHandlers.handleClearChat(event);
                case "help" -> CommandHandlers.handleHelp(event);
                case "coinflip" -> CommandHandlers.handleCoinFlip(event);
                case "picker" -> CommandHandlers.handlePicker(event);
                default -> event.getHook().sendMessage("Unknown command.").queue();
            }
        } catch (Exception e) {
            logger.error("❌ Unexpected error in slash command", e);
            if (event.isAcknowledged()) {
                event.getHook().sendMessage("An error occurred.").queue();
            } else {
                event.reply("An error occurred.").queue();
            }
        }
    }
}