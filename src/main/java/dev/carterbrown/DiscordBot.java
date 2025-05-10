package dev.carterbrown;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

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

    public static void main(String[] args) throws LoginException {
        JDABuilder.createDefault(DISCORD_TOKEN)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new DiscordBot())
                .build();
        logger.info("Listening for messages in channel ID: {}", CHANNEL_ID);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) return;

        Member member = event.getMember();
        if (member == null || member.getRoles().stream().noneMatch(role -> role.getName().equalsIgnoreCase(ALLOWED_ROLE_NAME))) {
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        String content = event.getMessage().getContentDisplay().trim();
        logger.info("Message received: {}", content);

        if (channel.getId().equals(CHANNEL_ID) && content.toLowerCase().contains("raid")) {
            sendText(content + " Time: " + System.currentTimeMillis());
        }

        if (content.startsWith("/spam ")) {
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
                channel.sendMessage("<@" + currentSpamTargetId + ">").queue(sentMsg -> sentMessageIds.add(sentMsg.getId()));
            }, 0, 3, TimeUnit.SECONDS);

            channel.sendMessage("Started spamming <@" + currentSpamTargetId + "> every 3 seconds.").queue();
            return;
        }

        if (content.equalsIgnoreCase("/stopspam")) {
            if (spamTask != null) {
                spamTask.cancel(true);
                spamTask = null;

                for (String id : sentMessageIds) {
                    channel.deleteMessageById(id).queue(null, err -> logger.warn("Failed to delete message: {}", err.getMessage()));
                }
                sentMessageIds.clear();

                channel.sendMessage("Stopped spamming <@" + currentSpamTargetId + "> and deleted spam messages.").queue();
                currentSpamTargetId = null;
            } else {
                channel.sendMessage("No spam session running.").queue();
            }
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