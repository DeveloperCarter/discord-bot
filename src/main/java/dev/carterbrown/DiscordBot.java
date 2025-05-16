package dev.carterbrown;

import jakarta.mail.Session;
import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Transport;
import jakarta.mail.MessagingException;

import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.InternetAddress;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cdimascio.dotenv.Dotenv;

import net.dv8tion.jda.api.entities.Message;

import java.util.*;

public class DiscordBot extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DiscordBot.class);
    private static final Dotenv dotenv = Dotenv.configure()
            .directory(System.getProperty("user.dir"))
            .ignoreIfMissing()
            .load();
    private static final String GMAIL_USER = dotenv.get("GMAIL_USER");
    private static final String GMAIL_PASS = dotenv.get("GMAIL_PASS");
    private static final String SMS_TO = dotenv.get("SMS_TO");

    public static void main(String[] args) {
        String token = System.getenv("DISCORD_BOT_TOKEN");
        if (token == null || token.isBlank()) {
            logger.error("\u274C DISCORD_BOT_TOKEN environment variable not set.");
            System.exit(1);
        }

        try {
            JDA jda = JDABuilder.createDefault(token, EnumSet.of(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.MESSAGE_CONTENT
                    ))
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE)
                    .setActivity(Activity.watching("you \ud83d\udc40"))
                    .addEventListeners(new DiscordBot())
                    .build();

            jda.awaitReady();
            logger.info("\u2705 Bot is online as {}", jda.getSelfUser().getAsTag());

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
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        String content = event.getMessage().getContentDisplay().toLowerCase();

        if (content.contains("getting raided")) {
            Member member = event.getMember();
            String username = (member != null) ? member.getEffectiveName() : event.getAuthor().getName();
            String channelName = event.getChannel().getName();
            String emailBody = getString(event, username, channelName);

            sendText(emailBody);
            logger.info("\uD83D\uDEA8 Raid alert email triggered by {} in #{}", username, channelName);
        }
    }


    @NotNull
    private static String getString(MessageReceivedEvent event, String username, String channelName) {
        String messageLink = "https://discord.com/channels/" +
                event.getGuild().getId() + "/" +
                event.getChannel().getId() + "/" +
                event.getMessageId();

        String emailBody = String.format("""
            \uD83D\uDEA8 RAID ALERT DETECTED \uD83D\uDEA8

            User: %s
            Channel: #%s
            Message: %s
            Time: %s
            Link: %s
            """,
                username,
                channelName,
                event.getMessage().getContentDisplay(),
                new Date(),
                messageLink
        );
        return emailBody;
    }


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


    @Override
    public void onReady(ReadyEvent event) {
        String pgeaId = "886359157110874132";
        Guild pgea = event.getJDA().getGuildById(pgeaId);

        List<CommandData> commands = List.of(
                Commands.slash("spam", "Start spamming a user")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.USER, "target", "User to spam", true),
                Commands.slash("stopspam", "Stop spamming and delete messages"),
                Commands.slash("clearchat", "Clears messages (restricted to certain channels)"),
                Commands.slash("help", "List available commands"),
                Commands.slash("coinflip", "Flip a coin"),
                Commands.slash("picker", "Randomly pick one option")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "choices", "Comma-separated list of choices", true),
                Commands.slash("poll", "Create a poll.")
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "question", "Your poll question.", true)
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "choices", "Comma-separated list of choices", true)
                        .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.INTEGER, "duration", "Poll duration in minutes (optional)", false)
        );

        if (pgea != null) {
            pgea.updateCommands().addCommands(commands)
                    .queue(
                            success -> logger.info("✅ Guild commands registered for PGEA."),
                            error -> logger.error("❌ Failed to register guild commands", error)
                    );
            event.getJDA().updateCommands().addCommands().queue(
                    success -> logger.info("✅ Cleared all global commands."),
                    error -> logger.error("❌ Failed to clear global commands", error)
            );
        } else {
            event.getJDA().updateCommands().addCommands(commands)
                    .queue(
                            success -> logger.info("✅ Global commands registered."),
                            error -> logger.error("❌ Failed to register global commands", error)
                    );
        }
    }


    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        try {
            event.deferReply().queue();

            switch (event.getName()) {
                case "spam" -> CommandHandlers.handleSpam(event);
                case "stopspam" -> CommandHandlers.handleStopSpam(event);
                case "clearchat" -> CommandHandlers.handleClearChat(event);
                case "help" -> CommandHandlers.handleHelp(event);
                case "coinflip" -> CommandHandlers.handleCoinFlip(event);
                case "picker" -> CommandHandlers.handlePicker(event);
                case "poll" -> CommandHandlers.handlePoll(event);
                default -> event.getHook().sendMessage("Unknown command.").queue();
            }
        } catch (Exception e) {
            logger.error("\u274C Unexpected error in slash command", e);
            if (event.isAcknowledged()) {
                event.getHook().sendMessage("An error occurred.").queue();
            } else {
                event.reply("An error occurred.").queue();
            }
        }
    }


    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getButton().getId();
        String userId = event.getUser().getId();

        String pollId = null;
        for (Map.Entry<String, Map<String, Integer>> entry : CommandHandlers.pollVotes.entrySet()) {
            if (entry.getValue().containsKey(buttonId)) {
                pollId = entry.getKey();
                break;
            }
        }

        if (pollId == null) {
            event.reply("This vote button is no longer valid.").setEphemeral(true).queue();
            return;
        }

        Set<String> voters = CommandHandlers.pollVoters.get(pollId);
        if (voters.contains(userId)) {
            event.reply("You've already voted in this poll!").setEphemeral(true).queue();
            return;
        }

        voters.add(userId);
        Map<String, Integer> votes = CommandHandlers.pollVotes.get(pollId);
        votes.computeIfPresent(buttonId, (k, v) -> v + 1);

        Message pollMessage = CommandHandlers.pollMessages.get(pollId);
        if (pollMessage != null) {
            Map<String, String> choices = CommandHandlers.pollChoices.get(pollId);
            String question = CommandHandlers.pollQuestions.get(pollId);

            EmbedBuilder updatedEmbed = new EmbedBuilder()
                    .setTitle("📊 Poll")
                    .setDescription(Util.formatPollDescription(question, choices, votes))
                    .setColor(0x1ABC9C)
                    .setFooter(Objects.requireNonNull(pollMessage.getEmbeds().get(0).getFooter()).getText());

            // Keep buttons enabled
            List<Button> buttons = new ArrayList<>();
            for (String id : choices.keySet()) {
                buttons.add(Button.primary(id, choices.get(id)));
            }

            pollMessage.editMessageEmbeds(updatedEmbed.build())
                    .setComponents(ActionRow.of(buttons.toArray(new Button[0])))
                    .queue();
        }

        event.deferEdit().queue();
    }
}