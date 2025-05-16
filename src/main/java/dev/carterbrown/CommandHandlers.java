package dev.carterbrown;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.awt.Color;
import java.util.concurrent.atomic.AtomicLong;

public class CommandHandlers {
    private static final Logger logger = LoggerFactory.getLogger(CommandHandlers.class);

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private static ScheduledFuture<?> spamTask;
    private static String currentSpamTargetId;
    private static final List<String> sentMessageIds = new CopyOnWriteArrayList<>();

    private static final List<String> ALLOWED_CLEAR_CHANNEL_IDS = List.of("1370867615421169684");
    private static final Random random = new Random();

    public static final Map<String, Map<String, Integer>> pollVotes = new ConcurrentHashMap<>();
    public static final Map<String, Set<String>> pollVoters = new ConcurrentHashMap<>();
    public static final Map<String, Map<String, String>> pollChoices = new ConcurrentHashMap<>();
    public static final Map<String, String> pollQuestions = new ConcurrentHashMap<>();
    public static final Map<String, Message> pollMessages = new ConcurrentHashMap<>();
    public static final Map<String, Map<String, Set<String>>> pollVotesByChoice = new ConcurrentHashMap<>();
    private static final Map<String, ScheduledFuture<?>> pollCountdownTasks = new ConcurrentHashMap<>();


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
        }, 0, 1, TimeUnit.SECONDS);

        event.getHook().sendMessage("Started spamming <@" + currentSpamTargetId + "> every second.").queue();
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

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🧹 Clearing Messages")
                .setDescription("Starting to delete messages...")
                .setColor(Color.ORANGE);

        event.getHook().sendMessageEmbeds(embed.build()).queue(confirmationMessage -> {
            Util.deleteAllMessagesPreserving(channel, 0, confirmationMessage, event);
        });
    }


    public static void handleHelp(SlashCommandInteractionEvent event) {
        String helpMessage = Util.getHelpMessage(event);
        event.getHook().sendMessage(helpMessage).queue();
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


    public static void handlePoll(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("poll")) return;

        String question = Objects.requireNonNull(event.getOption("question")).getAsString();
        String[] choices = Objects.requireNonNull(event.getOption("choices")).getAsString().split(",");
        int duration = event.getOption("duration") != null ? event.getOption("duration").getAsInt() : 3; // Default 3 minutes

        if (choices.length > 5) {
            event.getHook().sendMessage("Please limit your poll to 5 choices.").setEphemeral(true).queue();
            return;
        }

        List<Button> buttons = new ArrayList<>();
        Map<String, Integer> voteMap = new ConcurrentHashMap<>();
        Map<String, String> choiceLabels = new HashMap<>();

        for (String choice : choices) {
            String trimmed = choice.trim();
            String buttonId = UUID.randomUUID().toString();
            voteMap.put(buttonId, 0);
            choiceLabels.put(buttonId, trimmed);
            buttons.add(Button.primary(buttonId, trimmed));
        }

        String pollId = UUID.randomUUID().toString();
        pollVotes.put(pollId, voteMap);
        pollVoters.put(pollId, ConcurrentHashMap.newKeySet());
        pollChoices.put(pollId, choiceLabels);
        pollQuestions.put(pollId, question);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Poll")
                .setDescription(Util.formatPollDescription(question, choiceLabels, voteMap))
                .setColor(0x1ABC9C)
                .setFooter("Poll ends in " + duration + " minute" + (duration > 1 ? "s" : ""));

        event.getHook().sendMessageEmbeds(embed.build())
                .addActionRow(buttons)
                .queue(sentMessage -> {
                    pollMessages.put(pollId, sentMessage);

                    final long totalSeconds = duration * 60L;
                    final AtomicLong secondsLeft = new AtomicLong(totalSeconds);

                    ScheduledFuture<?> countdownTask = scheduler.scheduleAtFixedRate(() -> {
                        long remaining = secondsLeft.getAndDecrement();

                        if (remaining < 0) {
                            // Countdown finished, cancel task and show results
                            ScheduledFuture<?> task = pollCountdownTasks.remove(pollId);
                            if (task != null) {
                                task.cancel(false);
                            }

                            // Edit message to show poll finished message
                            EmbedBuilder finishedEmbed = new EmbedBuilder()
                                    .setTitle("Poll Finished! \uD83D\uDED1")
                                    .setDescription("**" + question + "**\n\n" + "Here are the results:")
                                    .setColor(Color.RED)
                                    .setTimestamp(OffsetDateTime.now());

                            sentMessage.editMessageEmbeds(finishedEmbed.build()).queue();

                            // Send detailed results
                            showResults(sentMessage.getChannel(), pollId);
                        } else {
                            long minutes = remaining / 60;
                            long seconds = remaining % 60;

                            EmbedBuilder updatedEmbed = new EmbedBuilder()
                                    .setTitle("📊 Poll")
                                    .setDescription(Util.formatPollDescription(question, choiceLabels, voteMap))
                                    .setColor(0x1ABC9C)
                                    .setFooter(String.format("Poll ends in %d minute%s %d second%s",
                                            minutes, minutes == 1 ? "" : "s",
                                            seconds, seconds == 1 ? "" : "s"));

                            sentMessage.editMessageEmbeds(updatedEmbed.build()).queue();
                        }
                    }, 0, 1, TimeUnit.SECONDS);

                    pollCountdownTasks.put(pollId, countdownTask);
                });
    }

    private static void showResults(MessageChannel channel, String pollId) {
        Map<String, Integer> votes = pollVotes.get(pollId);
        Map<String, Set<String>> votesByChoice = pollVotesByChoice.get(pollId);
        Map<String, String> choices = pollChoices.get(pollId);
        String question = pollQuestions.get(pollId);
        Message pollMessage = pollMessages.get(pollId);

        if (votes == null || question == null || choices == null || pollMessage == null) {
            channel.sendMessage("Poll results are no longer available.").queue();
            return;
        }

        StringBuilder result = new StringBuilder("**Results for:** **" + question + "**\n\n");

        List<Map.Entry<String, Integer>> sortedVotes = new ArrayList<>(votes.entrySet());
        sortedVotes.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        int totalVotes = votes.values().stream().mapToInt(Integer::intValue).sum();

        for (Map.Entry<String, Integer> entry : sortedVotes) {
            String choiceId = entry.getKey();
            String label = choices.get(choiceId);
            int count = entry.getValue();
            double percent = totalVotes > 0 ? (count * 100.0 / totalVotes) : 0;

            result.append(String.format("**%s** — %d vote%s (%.1f%%)\n", label, count, count == 1 ? "" : "s", percent));

            if (votesByChoice != null && votesByChoice.containsKey(choiceId)) {
                Set<String> userIds = votesByChoice.get(choiceId);
                if (!userIds.isEmpty()) {
                    String voters = userIds.stream()
                            .map(id -> "<@" + id + ">")
                            .reduce((a, b) -> a + " " + b)
                            .orElse("");
                    result.append("Voters: ").append(voters).append("\n");
                }
            }
            result.append("\n");
        }

        if (totalVotes == 0) {
            result.append("_No votes were cast._");
        } else {
            result.append("Total votes: ").append(totalVotes);
        }

        EmbedBuilder finalEmbed = new EmbedBuilder()
                .setTitle("📊 Poll Ended")
                .setDescription(result.toString())
                .setColor(0x1ABC9C);

        pollMessage.editMessageEmbeds(finalEmbed.build())
                .setComponents()
                .queue(success -> {
                    pollVotes.remove(pollId);
                    pollVotesByChoice.remove(pollId);
                    pollChoices.remove(pollId);
                    pollQuestions.remove(pollId);
                    pollMessages.remove(pollId);

                    ScheduledFuture<?> task = pollCountdownTasks.remove(pollId);
                    if (task != null) task.cancel(false);
                });
    }
}