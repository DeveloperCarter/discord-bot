package dev.carterbrown;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cdimascio.dotenv.Dotenv;

import java.util.Date;
import java.util.EnumSet;
import java.util.Properties;

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
                    new Date().toString(),
                    messageLink
            );

            sendText(emailBody);
            logger.info("\uD83D\uDEA8 Raid alert email triggered by {} in #{}", username, channelName);
        }
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
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(GMAIL_USER));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(SMS_TO));
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
            event.deferReply().queue();

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
            logger.error("\u274C Unexpected error in slash command", e);
            if (event.isAcknowledged()) {
                event.getHook().sendMessage("An error occurred.").queue();
            } else {
                event.reply("An error occurred.").queue();
            }
        }
    }
}