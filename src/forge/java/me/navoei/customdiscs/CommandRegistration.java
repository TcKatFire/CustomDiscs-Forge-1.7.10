package me.navoei.customdiscs;

import net.minecraft.command.CommandHandler;
import net.minecraft.command.ICommand;

import java.util.logging.Logger;

public final class CommandRegistration {
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");
    private static final String ROOT_COMMAND = "customdiscs";

    private CommandRegistration() {
    }

    public static boolean register(CommandHandler commandHandler) {
        ICommand existing = (ICommand) commandHandler.getCommands().get(ROOT_COMMAND);
        if (existing instanceof CustomDiscsCommand) {
            return true;
        }
        if (existing != null) {
            LOGGER.warning("Cannot register /" + ROOT_COMMAND + ": already owned by "
                    + existing.getClass().getName() + " (primary name /"
                    + existing.getCommandName() + ").");
            return false;
        }
        commandHandler.registerCommand(new CustomDiscsCommand());
        return true;
    }
}
