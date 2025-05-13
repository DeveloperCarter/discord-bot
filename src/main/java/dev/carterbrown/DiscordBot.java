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
import static dev.carterbrown.CommandRegistry.*;

public class DiscordBot extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DiscordBot.class);
    private static final Dotenv dotenv = Dotenv.configure()
            .directory(System.getProperty("user.dir"))
            .ignoreIfMissing()
            .load();

    private static final String DISCORD_TOKEN = dotenv.get("DISCORD_TOKEN");
    private static final String CHANNEL_ID = dotenv.get("CHANNEL_ID");
    private static final String GMAIL_USER = dotenv.get("GMAIL_USER");
    private static final String GMAIL_PASS = dotenv.get("GMAIL_PASS");
    private static final String SMS_TO = dotenv.get("SMS_TO");
    private static final String ALLOWED_ROLE_NAME = "Bot";

    public static void main(String[] args) throws LoginException {
        registerCommands();
        JDABuilder.createDefault(DISCORD_TOKEN)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new DiscordBot())
                .build();
        logger.info("Bot started.");
    }

    private static void registerCommands() {
        register("/spam", "Start spamming the mentioned user every 3 seconds.", CommandHandlers::handleSpam);
        register("/stopspam", "Stop the ongoing spam and delete the spam messages.", CommandHandlers::handleStopSpam);
        register("/clearchat", "Clear all recent messages in this channel (only allowed channels).", CommandHandlers::handleClearChat);
        register("/cf", "Flip a coin.", CommandHandlers::handleCoinFlip);
        register("/picker", "Pick one item from a list of up to 10 newline-separated options.", CommandHandlers::handlePicker);
        register("/helpdb", "Show this list of commands.", CommandHandlers::handleHelp);
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
