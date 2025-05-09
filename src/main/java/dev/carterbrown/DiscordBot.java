package dev.carterbrown;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import java.util.EnumSet;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.util.*;

public class DiscordBot extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(DiscordBot.class);

    private static final Dotenv dotenv = Dotenv.load();

    private static final String DISCORD_TOKEN = dotenv.get("DISCORD_TOKEN");
    private static final String CHANNEL_ID = dotenv.get("CHANNEL_ID");
    private static final String GMAIL_USER = dotenv.get("GMAIL_USER");
    private static final String GMAIL_PASS = dotenv.get("GMAIL_PASS");
    private static final String SMS_TO = dotenv.get("SMS_TO");

    public static void main(String[] args) throws LoginException {
        JDABuilder.createDefault(DISCORD_TOKEN)
                .enableIntents(EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                .addEventListeners(new DiscordBot())
                .build();
        logger.info("Listening for messages in channel ID: {}", CHANNEL_ID);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;
        logger.info("Message received: {}", event.getMessage().getContentDisplay());

        TextChannel channel = event.getChannel().asTextChannel();

        if (channel.getId().equals(CHANNEL_ID)) {
            String content = event.getMessage().getContentDisplay();
            if (content.toLowerCase().contains("raid")) {
                logger.info("Trigger word detected. Sending text.");
                sendText("GETTING RAIDED");
            }
        }
    }

    public static void sendText(String messageBody) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(GMAIL_USER, GMAIL_PASS);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(GMAIL_USER));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(SMS_TO));
            message.setSubject("");
            message.setText(messageBody);
            Transport.send(message);
            logger.info("Sent text.");
        } catch (MessagingException e) {
            logger.error(e.toString());
        }
    }
}
