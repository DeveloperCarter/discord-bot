package dev.carterbrown;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.awt.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Util {

    public static String formatPollDescription(String question, Map<String, String> choices, Map<String, Integer> votes) {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(question).append("**\n\n");
        int i = 1;
        for (Map.Entry<String, String> entry : choices.entrySet()) {
            String buttonId = entry.getKey();
            String label = entry.getValue();
            int count = votes.getOrDefault(buttonId, 0);
            sb.append("**").append(i).append(".** ").append(label).append(" — **").append(count).append(" votes**\n");
            i++;
        }
        return sb.toString();
    }

    public static String getHelpMessage(SlashCommandInteractionEvent event) {
        StringBuilder sb = new StringBuilder("🤖 **Available Commands:**\n");

        // Fetch guild-specific commands (if event is in a guild)
        List<Command> commands;
        if (event.isFromGuild()) {
            commands = Objects.requireNonNull(event.getGuild()).retrieveCommands().complete();
        } else {
            // fallback to global commands if no guild (DM)
            commands = event.getJDA().retrieveCommands().complete();
        }

        for (Command cmd : commands) {
            sb.append("`/").append(cmd.getName()).append("` - ").append(cmd.getDescription()).append("\n");
        }

        return sb.toString();
    }

   public static void deleteAllMessagesPreserving(TextChannel channel, int totalDeleted, Message messageToEdit, SlashCommandInteractionEvent event) {
        channel.getHistory().retrievePast(100).queue(messages -> {
            if (messages.isEmpty()) {
                EmbedBuilder doneEmbed = new EmbedBuilder()
                        .setTitle("✅ Deletion Complete")
                        .setDescription("Cleared **" + totalDeleted + "** messages 🧹")
                        .setColor(Color.GREEN);
                messageToEdit.editMessageEmbeds(doneEmbed.build()).queue();
                return;
            }

            List<net.dv8tion.jda.api.entities.Message> deletable = messages.stream()
                    .filter(m -> !m.getId().equals(messageToEdit.getId()))
                    .filter(m -> m.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14)))
                    .toList();

            if (deletable.isEmpty()) {
                // No deletable messages found, fetch more
                deleteAllMessagesPreserving(channel, totalDeleted, messageToEdit, event);
                return;
            }

            channel.deleteMessages(deletable).queue(
                    success -> {
                        int newTotal = totalDeleted + deletable.size();

                        EmbedBuilder progressEmbed = new EmbedBuilder()
                                .setTitle("🧹 Clearing Messages")
                                .setDescription("Deleted **" + newTotal + "** messages...")
                                .setColor(Color.ORANGE);

                        messageToEdit.editMessageEmbeds(progressEmbed.build()).queue(
                                m -> System.out.println("Edited message successfully"),
                                err -> System.err.println("Failed to edit message: " + err.getMessage())
                        );

                        deleteAllMessagesPreserving(channel, newTotal, messageToEdit, event);
                    },
                    error -> {
                        System.err.println("Failed to delete messages: " + error.getMessage());

                        EmbedBuilder errorEmbed = new EmbedBuilder()
                                .setTitle("❌ Error")
                                .setDescription("Failed while deleting messages:\n" + error.getMessage())
                                .setColor(Color.RED);

                        messageToEdit.editMessageEmbeds(errorEmbed.build()).queue();
                    }
            );
        }, error -> {
            System.err.println("Failed to retrieve history: " + error.getMessage());

            EmbedBuilder errorEmbed = new EmbedBuilder()
                    .setTitle("❌ Error")
                    .setDescription("Failed while retrieving messages:\n" + error.getMessage())
                    .setColor(Color.RED);

            messageToEdit.editMessageEmbeds(errorEmbed.build()).queue();
        });
    }


}
