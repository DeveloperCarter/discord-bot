package dev.carterbrown;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

public class CommandHandlers {
    private static final Logger logger = LoggerFactory.getLogger(CommandHandlers.class);

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static ScheduledFuture<?> spamTask;
    private static String currentSpamTargetId;
    private static final List<String> sentMessageIds = new CopyOnWriteArrayList<>();

    private static final List<String> ALLOWED_CLEAR_CHANNEL_IDS = List.of("1370867615421169684");
    private static final Random random = new Random();

    public static void handleSpam(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();
        OptionMapping targetOption = event.getOption("target");

        if (targetOption == null) {
            event.getHook().sendMessage("❌ Please specify a target user.").queue();
            return;
        }

        User target = targetOption.getAsUser();
        String targetId = target.getId();

        if (spamTask != null && !spamTask.isCancelled()) {
            event.getHook().sendMessage("A spam session is already running. Use /stopspam first.").queue();
            return;
        }

        currentSpamTargetId = targetId;

        spamTask = scheduler.scheduleAtFixedRate(() -> {
            if (currentSpamTargetId != null) {
                channel.sendMessage("<@" + currentSpamTargetId + ">").queue(sentMsg -> {
                    sentMessageIds.add(sentMsg.getId());
                });
            }
        }, 0, 3, TimeUnit.SECONDS);

        event.getHook().sendMessage("Started spamming <@" + currentSpamTargetId + "> every 3 seconds.").queue();
    }

    public static void handleStopSpam(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();

        if (spamTask != null) {
            spamTask.cancel(true);
            spamTask = null;

            event.getHook().sendMessage("Stopping spam and cleaning up messages...").queue(msg -> {
                final Iterator<String> iterator = sentMessageIds.iterator();
                final Runnable deleteTask = new Runnable() {
                    @Override
                    public void run() {
                        if (iterator.hasNext()) {
                            String id = iterator.next();
                            channel.deleteMessageById(id).queue(
                                    success -> logger.info("Deleted message: {}", id),
                                    err -> logger.warn("Failed to delete message: {}", err.getMessage())
                            );
                        } else {
                            currentSpamTargetId = null;
                            sentMessageIds.clear();
                            msg.editMessage("✅ Spam stopped and messages deleted.").queue();
                            return;
                        }
                        scheduler.schedule(this, 250, TimeUnit.MILLISECONDS);
                    }
                };
                scheduler.schedule(deleteTask, 0, TimeUnit.MILLISECONDS);
            });

        } else {
            event.getHook().sendMessage("No spam session running.").queue();
        }
    }


    public static void handleClearChat(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();

        if (!ALLOWED_CLEAR_CHANNEL_IDS.contains(channel.getId())) {
            event.getHook().sendMessage("❌ This command is not allowed in this channel.").queue();
            return;
        }

        event.getHook().sendMessage("🧹 Clearing messages...").queue(confirmationMessage -> {
            deleteAllMessagesPreserving(channel, 0, event, confirmationMessage.getId());
        });
    }


    private static void deleteAllMessagesPreserving(TextChannel channel, int totalDeleted, SlashCommandInteractionEvent event, String preserveMessageId) {
        channel.getHistory().retrievePast(100).queue(messages -> {
            if (messages.isEmpty()) {
                event.getHook().editOriginal("✅ Cleared " + totalDeleted + " messages.").queue();
                return;
            }

            List<net.dv8tion.jda.api.entities.Message> deletable = messages.stream()
                    .filter(m -> !m.getId().equals(preserveMessageId)) // Do not delete the bot's own message
                    .filter(m -> m.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
                    .toList();

            if (deletable.isEmpty()) {
                // Nothing to delete this round — fetch more
                deleteAllMessagesPreserving(channel, totalDeleted, event, preserveMessageId);
                return;
            }

            channel.deleteMessages(deletable).queue(
                    success -> deleteAllMessagesPreserving(channel, totalDeleted + deletable.size(), event, preserveMessageId),
                    error -> {
                        logger.error("Failed to delete messages: {}", error.getMessage());
                        event.getHook().editOriginal("❌ Error while deleting messages: " + error.getMessage()).queue();
                    }
            );
        }, error -> {
            logger.error("Failed to retrieve history: {}", error.getMessage());
            event.getHook().editOriginal("❌ Error while retrieving messages: " + error.getMessage()).queue();
        });
    }


    public static void handleHelp(SlashCommandInteractionEvent event) {
        event.getHook().sendMessage(getHelpMessage()).queue();
    }

    public static String getHelpMessage() {
        StringBuilder sb = new StringBuilder("🤖 **Available Commands:**\n");

        sb.append("`/help` - Displays a list of available commands.\n")
                .append("`/coinflip` - Flips a coin (Heads/Tails).\n")
                .append("`/picker <options>` - Randomly picks an option from a comma-separated list.\n")
                .append("`/clearchat` - Clears messages in allowed channels.\n")
                .append("`/stopspam` - Stops an ongoing spam session.\n")
                .append("`/spam <target>` - Starts a spam session targeting a user.\n");

        return sb.toString();
    }

    public static void handleCoinFlip(SlashCommandInteractionEvent event) {
        String result = random.nextBoolean() ? "🪙 Heads!" : "🪙 Tails!";
        event.getHook().sendMessage(result).queue();
    }

    public static void handlePicker(SlashCommandInteractionEvent event) {
        OptionMapping optionsOption = event.getOption("options");

        if (optionsOption == null) {
            event.getHook().sendMessage("❌ Please provide a comma-separated list of options.").queue();
            return;
        }

        String[] options = optionsOption.getAsString().split(",");

        if (options.length < 2) {
            event.getHook().sendMessage("❌ Provide at least two options.").queue();
            return;
        }

        if (options.length > 10) {
            event.getHook().sendMessage("❌ No more than 10 options allowed.").queue();
            return;
        }

        List<String> trimmedOptions = Arrays.stream(options).map(String::trim).toList();
        TextChannel channel = event.getChannel().asTextChannel();

        channel.sendMessage("🎡 Preparing the spinning wheel...").queue(message -> {
            int spinCycles = 10;
            long baseIntervalMs = 1000;
            int slowDownStart = spinCycles - 3;
            int[] spinIndex = {0};
            int[] cycle = {0};

            Runnable spinTask = new Runnable() {
                @Override
                public void run() {
                    if (cycle[0] >= spinCycles) {
                        String chosen = trimmedOptions.get(random.nextInt(trimmedOptions.size()));
                        StringBuilder finalMessage = new StringBuilder("🎡 **The wheel has stopped!** 🎉\n");
                        finalMessage.append("**Chosen: ").append(chosen).append("**\n\nOptions:\n");
                        for (String opt : trimmedOptions) {
                            finalMessage.append(opt.equals(chosen) ? "➡️ " : "   ").append(opt).append("\n");
                        }
                        message.editMessage(finalMessage.toString()).queue();
                        return;
                    }

                    int currentIndex = spinIndex[0]++ % trimmedOptions.size();
                    StringBuilder spinMsg = new StringBuilder("🎡 **Spinning ")
                            .append(new String[]{"|", "/", "-", "\\"}[cycle[0] % 4])
                            .append("** (").append(cycle[0] + 1).append("/").append(spinCycles).append(")\n")
                            .append("Current: ").append(trimmedOptions.get(currentIndex)).append("\n\nOptions:\n");

                    for (int i = 0; i < trimmedOptions.size(); i++) {
                        spinMsg.append(i == currentIndex ? "➡️ " : "   ").append(trimmedOptions.get(i)).append("\n");
                    }

                    message.editMessage(spinMsg.toString()).queue();
                    long delay = baseIntervalMs + Math.max(0, (cycle[0] - slowDownStart) * 500L);
                    cycle[0]++;
                    scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
                }
            };

            scheduler.schedule(spinTask, 0, TimeUnit.MILLISECONDS);
        });
    }
}