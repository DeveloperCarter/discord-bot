package dev.carterbrown;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class CommandHandlers {
    private static final Logger logger = LoggerFactory.getLogger(CommandHandlers.class);

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> spamTask;
    private static String currentSpamTargetId;
    private static final List<String> sentMessageIds = new CopyOnWriteArrayList<>();

    private static final List<String> ALLOWED_CLEAR_CHANNEL_IDS = List.of(
            "1370867615421169684"
    );

    public static void handleSpam(MessageReceivedEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();
        String content = event.getMessage().getContentDisplay().trim();

        if (spamTask != null && !spamTask.isCancelled()) {
            channel.sendMessage("A spam session is already running. Use /stopspam first.").queue();
            return;
        }

        List<Member> mentioned = event.getMessage().getMentions().getMembers();
        String targetId;
        if (!mentioned.isEmpty()) {
            targetId = mentioned.get(0).getId();
        } else {
            String name = content.substring(6).trim();
            if (name.startsWith("@")) name = name.substring(1);
            Member memberFromName = event.getGuild()
                    .getMembersByEffectiveName(name, true)
                    .stream().findFirst().orElse(null);
            if (memberFromName == null) {
                channel.sendMessage("❌ Could not find a user named `" + name + "`. Try tagging them.").queue();
                return;
            }
            targetId = memberFromName.getId();
        }

        currentSpamTargetId = targetId;

        spamTask = scheduler.scheduleAtFixedRate(() -> {
            if (currentSpamTargetId != null) {
                channel.sendMessage("<@" + currentSpamTargetId + ">").queue(sentMsg -> {
                    if (!sentMessageIds.contains(sentMsg.getId())) {
                        sentMessageIds.add(sentMsg.getId());
                        logger.debug("Message ID {} added to sentMessageIds", sentMsg.getId());
                    }
                });
            }
        }, 0, 3, TimeUnit.SECONDS);

        channel.sendMessage("Started spamming <@" + currentSpamTargetId + "> every 3 seconds.").queue();
    }

    public static void handleStopSpam(MessageReceivedEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();

        if (spamTask != null) {
            spamTask.cancel(true);
            spamTask = null;

            for (String id : sentMessageIds) {
                channel.deleteMessageById(id).queue(
                        success -> logger.info("Deleted message: {}", id),
                        err -> logger.warn("Failed to delete message: {}", err.getMessage())
                );
            }
            sentMessageIds.clear();

            channel.sendMessage("Stopped spamming <@" + currentSpamTargetId + "> and deleted spam messages.").queue();
            currentSpamTargetId = null;
        } else {
            channel.sendMessage("No spam session running.").queue();
        }
    }

    public static void handleClearChat(MessageReceivedEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();
        if (!ALLOWED_CLEAR_CHANNEL_IDS.contains(channel.getId())) {
            channel.sendMessage("❌ This command is not allowed in this channel.").queue();
            return;
        }
        deleteAllMessages(channel, 0);
    }

    private static void deleteAllMessages(TextChannel channel, int totalDeleted) {
        channel.getHistory().retrievePast(100).queue(messages -> {
            if (messages.isEmpty()) {
                channel.sendMessage("✅ Cleared " + totalDeleted + " messages.").queue();
                return;
            }

            List<net.dv8tion.jda.api.entities.Message> deletable = messages.stream()
                    .filter(m -> m.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
                    .toList();

            channel.deleteMessages(deletable).queue(
                    success -> deleteAllMessages(channel, totalDeleted + deletable.size()),
                    error -> channel.sendMessage("❌ Error: " + error.getMessage()).queue()
            );
        });
    }

    public static void handleHelp(MessageReceivedEvent event) {
        event.getChannel().sendMessage(CommandRegistry.getHelpMessage()).queue();
    }

    public static void handleCoinFlip(MessageReceivedEvent event) {
        String result = Math.random() < 0.5 ? "🪙 Heads!" : "🪙 Tails!";
        event.getChannel().sendMessage(result).queue();
    }

    public static void handlePicker(MessageReceivedEvent event) {
        String content = event.getMessage().getContentRaw();
        String[] lines = content.replaceFirst("(?i)^/picker\\s*", "").split(",");

        if (lines.length < 2) {
            event.getChannel().sendMessage("❌ Provide at least two options.").queue();
            return;
        }
        if (lines.length > 10) {
            event.getChannel().sendMessage("❌ No more than 10 options allowed.").queue();
            return;
        }

        List<String> options = Arrays.stream(lines).map(String::trim).collect(Collectors.toList());
        TextChannel channel = event.getChannel().asTextChannel();

        // Rotation states for spinning effect
        String[] rotationStates = {"|", "/", "-", "\\"};
        int[] stateIndex = {0};

        // Send initial message
        channel.sendMessage("🎡 Preparing the spinning wheel...").queue(message -> {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            int[] spinIndex = {0}; // Track current option
            int spinCycles = 10; // Fewer cycles due to slower updates
            long baseIntervalMs = 1000; // 1 edit per second to respect rate limit
            int slowDownStart = spinCycles - 3; // Slow down in last 3 cycles

            // Create a task to update the message
            Runnable spinTask = new Runnable() {
                int cycle = 0;

                @Override
                public void run() {
                    if (cycle >= spinCycles) {
                        // Final update: show the result
                        String chosenOption = options.get(new Random().nextInt(options.size()));
                        StringBuilder finalMessage = new StringBuilder();
                        finalMessage.append("🎡 **The wheel has stopped!** 🎉\n");
                        finalMessage.append("**Chosen: ").append(chosenOption).append("**\n\n");
                        finalMessage.append("Options:\n");
                        for (int i = 0; i < options.size(); i++) {
                            finalMessage.append(i == options.indexOf(chosenOption) ? "➡️ " : "   ")
                                    .append(options.get(i)).append("\n");
                        }
                        message.editMessage(finalMessage.toString()).queue();
                        scheduler.shutdown();
                        return;
                    }

                    // Calculate interval for slow-down effect
                    long intervalMs = baseIntervalMs;
                    if (cycle >= slowDownStart) {
                        intervalMs = baseIntervalMs + (cycle - slowDownStart) * 500; // Increase by 500ms per cycle
                    }

                    // Spinning update
                    int currentIndex = spinIndex[0]++ % options.size();
                    String currentState = rotationStates[stateIndex[0]++ % rotationStates.length];
                    StringBuilder spinMessage = new StringBuilder();
                    spinMessage.append("🎡 **Spinning %s** (%d/%d)\n".formatted(currentState, cycle + 1, spinCycles));
                    spinMessage.append("Current: ").append(options.get(currentIndex)).append("\n\n");
                    spinMessage.append("Options:\n");
                    for (int i = 0; i < options.size(); i++) {
                        spinMessage.append(i == currentIndex ? "➡️ " : "   ")
                                .append(options.get(i)).append("\n");
                    }
                    message.editMessage(spinMessage.toString()).queue();

                    // Reschedule with new interval for slow-down
                    scheduler.schedule(this, intervalMs, TimeUnit.MILLISECONDS);
                    cycle++;
                }
            };

            // Start the spinning updates
            scheduler.schedule(spinTask, 0, TimeUnit.MILLISECONDS);
        });
    }
}
