package me.navoei.customdiscs.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import me.navoei.customdiscs.AudioCategory;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.event.CommandEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class ClientDiscsCommand extends CommandBase {
    private static final String[] SERVER_SUBCOMMANDS = {
            "create", "download", "range", "goatcooldown", "revert",
            "setmodel", "revertmodel", "reload"
    };
    private static final String[] VOLUME_CATEGORIES = {"disc", "head", "horn"};
    private static final String[] VOLUME_LEVELS = {"0", "25", "50", "75", "100"};

    @Override
    public String getCommandName() {
        return "customdiscs";
    }

    @Override
    public List getCommandAliases() {
        return Collections.emptyList();
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/customdiscs volume <disc|head|horn> <0-100>";
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] arguments) {
        if (arguments.length == 1) {
            List<String> commands = new ArrayList<String>();
            Collections.addAll(commands, SERVER_SUBCOMMANDS);
            commands.add("volume");
            return matching(arguments[0], commands);
        }
        if (arguments.length == 2 && isVolumeCommand(arguments)) {
            List<String> categories = new ArrayList<String>();
            Collections.addAll(categories, VOLUME_CATEGORIES);
            return matching(arguments[1], categories);
        }
        if (arguments.length == 3 && isVolumeCommand(arguments)
                && isVolumeCategory(arguments[1])) {
            List<String> levels = new ArrayList<String>();
            Collections.addAll(levels, VOLUME_LEVELS);
            return matching(arguments[2], levels);
        }
        return Collections.emptyList();
    }

    @SubscribeEvent
    public void routeCommand(CommandEvent event) {
        if (event.command == this && shouldForwardToServer(event.parameters)) {
            event.setCanceled(true);
        }
    }

    static boolean isVolumeCommand(String[] arguments) {
        return arguments.length > 0 && "volume".equalsIgnoreCase(arguments[0]);
    }

    private static boolean isVolumeCategory(String value) {
        for (String category : VOLUME_CATEGORIES) {
            if (category.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> matching(String prefix, List<String> options) {
        List<String> matches = new ArrayList<String>();
        for (String option : options) {
            if (option.regionMatches(true, 0, prefix, 0, prefix.length())) {
                matches.add(option);
            }
        }
        return matches;
    }

    static boolean shouldForwardToServer(String[] arguments) {
        return !isVolumeCommand(arguments);
    }

    static AudioCategory parseCategory(String value) {
        try {
            return ClientAudioSettings.parseCategory(value);
        } catch (IllegalArgumentException e) {
            throw new WrongUsageException("/customdiscs volume <disc|head|horn> <0-100>");
        }
    }

    static int parsePercent(String value) {
        try {
            return ClientAudioSettings.parsePercent(value);
        } catch (IllegalArgumentException e) {
            throw new WrongUsageException("/customdiscs volume <disc|head|horn> <0-100>");
        }
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) throws CommandException {
        if (arguments.length != 3 || !isVolumeCommand(arguments)) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        AudioCategory category = parseCategory(arguments[1]);
        int percentage = parsePercent(arguments[2]);
        ClientAudioSettings.setPercent(category, percentage);
        sender.addChatMessage(new ChatComponentText("CustomDiscs "
                + arguments[1].toLowerCase(java.util.Locale.ROOT)
                + " volume: " + percentage + "%"));
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }
}
