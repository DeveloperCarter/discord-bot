package dev.carterbrown;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.util.*;
import java.util.concurrent.*;
import static dev.carterbrown.CommandRegistry.*;

public class DiscordBot extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DiscordBot.class);
    private static final Dotenv dotenv = Dotenv.load();

    private static final String DISCORD_TOKEN = dotenv.get("DISCORD_TOKEN");
    private static final String CHANNEL_ID = dotenv.get("CHANNEL_ID");
    private static final String GMAIL_USER = dotenv.get("GMAIL_USER");
    private static final String GMAIL_PASS = dotenv.get("GMAIL_PASS");
    private static final String SMS_TO = dotenv.get("SMS_TO");

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> spamTask;
    private static String currentSpamTargetId;
    private static final List<String> sentMessageIds = new CopyOnWriteArrayList<>();
    private static final String ALLOWED_ROLE_NAME = "Bot";

    private static final List<String> ALLOWED_CLEAR_CHANNEL_IDS = List.of(
            "1370867615421169684"
    );

    public static void main(String[] args) throws LoginException {
        registerCommands();
        JDABuilder.createDefault(DISCORD_TOKEN)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new DiscordBot())
                .build();
        logger.info("Bot started.");
    }

    private static void registerCommands() {
        register("/spam", "Start spamming the mentioned user every 3 seconds.", DiscordBot::handleSpam);
        register("/stopspam", "Stop the ongoing spam and delete the spam messages.", DiscordBot::handleStopSpam);
        register("/clearchat", "Clear all recent messages in this channel (only allowed channels).", DiscordBot::handleClearChat);
        register("/cf", "Flip a coin.", DiscordBot::handleCoinFlip);
        register("/picker", "Pick one item from a list of up to 10 newline-separated options.", DiscordBot::handlePicker);
        register("/helpdb", "Show this list of commands.", DiscordBot::handleHelp);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        Member member = event.getMember();
        if (member == null || member.getRoles().stream().noneMatch(role -> role.getName().equalsIgnoreCase(ALLOWED_ROLE_NAME))) {
            return;
        }

        String content = event.getMessage().getContentDisplay().trim();
        String cmd = content.split(" ")[0].toLowerCase();
        CommandRegistry.Command command = get(cmd);

        if (command != null) {
            command.handler().execute(event);
        }

        TextChannel channel = event.getChannel().asTextChannel();
        if (channel.getId().equals(CHANNEL_ID) && content.toLowerCase().contains("raid")) {
            sendText(content + " Time: " + System.currentTimeMillis());
        }
    }

    // ==== Command handlers ====

    public static void handleSpam(MessageReceivedEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();
        String content = event.getMessage().getContentDisplay().trim();

        // Check if spam task is already running
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

        // Create a new spam task
        spamTask = scheduler.scheduleAtFixedRate(() -> {
            if (currentSpamTargetId != null) {
                channel.sendMessage("<@" + currentSpamTargetId + ">").queue(sentMsg -> {
                    // Only add message ID if it's not already in the list
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

        // Cancel the spam task if it exists
        if (spamTask != null) {
            spamTask.cancel(true);
            spamTask = null;

            // Delete all spam messages sent during the spam task
            for (String id : sentMessageIds) {
                channel.deleteMessageById(id).queue(
                        success -> logger.info("Successfully deleted message: {}", id),
                        err -> {
                            if (err.getMessage().contains("Unknown Message")) {
                                logger.warn("Message with ID {} could not be deleted: Unknown message", id);
                            } else {
                                logger.warn("Failed to delete message with ID {}: {}", id, err.getMessage());
                            }
                        }
                );
            }
            sentMessageIds.clear();

            channel.sendMessage("Stopped spamming <@" + currentSpamTargetId + "> and deleted spam messages.").queue();
            currentSpamTargetId = null;
        } else {
            channel.sendMessage("No spam session running.").queue();
        }
    }

    private static void handleClearChat(MessageReceivedEvent event) {
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
                channel.sendMessage("✅ Cleared a total of " + totalDeleted + " messages.").queue();
                return;
            }

            List<net.dv8tion.jda.api.entities.Message> deletable = messages.stream()
                    .filter(m -> m.getTimeCreated().isAfter(java.time.OffsetDateTime.now().minusDays(14)))
                    .toList();

            channel.deleteMessages(deletable).queue(
                    success -> deleteAllMessages(channel, totalDeleted + deletable.size()),
                    error -> channel.sendMessage("❌ Error during deletion: " + error.getMessage()).queue()
            );
        });
    }

    private static void handleHelp(MessageReceivedEvent event) {
        event.getChannel().sendMessage(getHelpMessage()).queue();
    }

    public static void handleCoinFlip(MessageReceivedEvent event) {
        String result = Math.random() < 0.5 ? "🪙 Heads!" : "🪙 Tails!";
        event.getChannel().sendMessage(result).queue();
    }

    public static void handlePicker(MessageReceivedEvent event) {
        String content = event.getMessage().getContentRaw(); // Raw to preserve newlines
        String[] lines = content.replaceFirst("(?i)^/pi\\s*", "").split("\\R+");

        if (lines.length < 2) {
            event.getChannel().sendMessage("❌ Please provide at least two options, each on a new line.").queue();
            return;
        }

        if (lines.length > 10) {
            event.getChannel().sendMessage("❌ Please provide no more than 10 options.").queue();
            return;
        }

        String choice = lines[new Random().nextInt(lines.length)].trim();
        event.getChannel().sendMessage("🎯 I choose: **" + choice + "**").queue();
    }


    // ==== Text alert ====

    public static void sendText(String messageBody) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        System.setProperty("https.protocols", "TLSv1.2");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(GMAIL_USER, GMAIL_PASS);
            }
        });

        try {
            jakarta.mail.Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(GMAIL_USER));
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(SMS_TO));
            message.setSubject("Raid Alert");
            message.setText(messageBody);
            Transport.send(message);
            logger.info("Sent text.");
        } catch (MessagingException e) {
            logger.error(e.toString());
        }
    }
}
