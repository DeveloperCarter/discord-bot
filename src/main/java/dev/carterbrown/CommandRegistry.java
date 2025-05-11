package dev.carterbrown;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.*;

public class CommandRegistry {
    private static final Map<String, Command> commands = new LinkedHashMap<>();

    public static void register(String name, String description, Command.CommandHandler handler) {
        commands.put(name.toLowerCase(), new Command(name, description, handler));
    }

    public static Command get(String name) {
        return commands.get(name.toLowerCase());
    }

    public static Collection<Command> all() {
        return commands.values();
    }

    public static String getHelpMessage() {
        StringBuilder sb = new StringBuilder("🤖 **Available Commands:**\n");
        for (Command cmd : commands.values()) {
            sb.append(cmd.name()).append(" - ").append(cmd.description()).append("\n");
        }
        return sb.toString();
    }

    public record Command(String name, String description, CommandHandler handler) {
        @FunctionalInterface
        public interface CommandHandler {
            void execute(MessageReceivedEvent event);
        }
    }
}
