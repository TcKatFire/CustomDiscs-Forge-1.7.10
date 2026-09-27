package me.navoei.customdiscs;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CustomDiscsCommand extends CommandBase {
    private static final Logger LOGGER = Logger.getLogger("CustomDiscs");
    private static final String COMMAND_NAME = "customdiscs";
    private static final String[] SUBCOMMANDS = {
            "create", "download", "range", "goatcooldown", "revert",
            "setmodel", "revertmodel", "reload"
    };

    public CustomDiscsCommand() {
    }

    @Override
    public String getCommandName() {
        return COMMAND_NAME;
    }

    @Override
    public List getCommandAliases() {
        return Collections.emptyList();
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/customdiscs <create|download|reload|revert|range|goatcooldown|setmodel|revertmodel>";
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] arguments) {
        if (arguments.length == 1) {
            return matching(arguments[0], Arrays.asList(SUBCOMMANDS));
        }
        if (arguments.length == 2 && "create".equalsIgnoreCase(arguments[0])) {
            return audioFileCompletions(arguments[1]);
        }
        return Collections.emptyList();
    }

    static List<String> audioFileCompletions(String prefix) {
        Path root = MusicFiles.getMusicDirectory().toPath().toAbsolutePath().normalize();
        int maxDepth = "none".equals(ModConfig.subdirectoryDepth) ? 1
                : "single".equals(ModConfig.subdirectoryDepth) ? 2 : Integer.MAX_VALUE;
        List<String> candidates = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(root, maxDepth)) {
            files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .forEach(path -> {
                        String name = root.relativize(path).toString().replace(File.separatorChar, '/');
                        if (name.matches(".*\\s+.*")) {
                            return;
                        }
                        try {
                            if (MusicFiles.resolve(name).isFile()
                                    && name.toLowerCase(Locale.ROOT)
                                    .startsWith(prefix.toLowerCase(Locale.ROOT))) {
                                candidates.add(name);
                            }
                        } catch (IOException ignored) {
                            // Ignore files that are not valid audio names or exceed configured depth.
                        }
                    });
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Could not list CustomDiscs audio for command completion.", e);
        }
        Collections.sort(candidates);
        return candidates;
    }

    private static List<String> matching(String prefix, List<String> options) {
        List<String> matches = new ArrayList<String>();
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                matches.add(option);
            }
        }
        return matches;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) throws CommandException {
        if (arguments.length == 0) {
            sender.addChatMessage(new ChatComponentText("CustomDiscs: create, download, reload, revert, range, goatcooldown, setmodel, revertmodel"));
            return;
        }
        String command = arguments[0].toLowerCase();
        if ("download".equals(command)) {
            download(sender, arguments);
            return;
        }
        EntityPlayer player = getCommandSenderAsPlayer(sender);
        if ("create".equals(command)) {
            create(player, arguments);
        } else if ("range".equals(command)) {
            setRange(player, arguments);
        } else if ("goatcooldown".equals(command)) {
            setCooldown(player, arguments);
        } else if ("revert".equals(command)) {
            revert(player);
        } else if ("setmodel".equals(command)) {
            setModel(player, arguments);
        } else if ("revertmodel".equals(command)) {
            revertModel(player);
        } else if ("reload".equals(command)) {
            reload(player);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void create(EntityPlayer player, String[] args) throws CommandException {
        if (args.length < 3) {
            throw new WrongUsageException("/customdiscs create <filename> <title>");
        }
        ItemStack held = player.getHeldItem();
        AudioCategory category = ItemData.category(held);
        if (category == null) {
            tell(player, "not-holding-correct-item");
            return;
        }
        if ((category == AudioCategory.MUSIC_DISC && !ModConfig.musicDiscEnabled)
                || (category == AudioCategory.HORN && !ModConfig.customHornEnabled)
                || (category == AudioCategory.PLAYER_HEAD && !ModConfig.customHeadEnabled)) {
            tell(player, category == AudioCategory.MUSIC_DISC ? "custom-music-disabled"
                    : category == AudioCategory.HORN ? "custom-horn-disabled" : "custom-head-disabled");
            return;
        }
        String filename = args[1];
        String title = join(args, 2);
        try {
            File file = MusicFiles.resolve(filename);
            if (!file.isFile()) {
                tell(player, "file-not-found");
                return;
            }
            long maximumBytes = (long) ModConfig.maximumDownloadSizeMb * 1024L * 1024L;
            if (file.length() < 1L || file.length() > maximumBytes) {
                tell(player, "file-too-large", "%max_download_size%",
                        Integer.toString(ModConfig.maximumDownloadSizeMb));
                return;
            }
            ItemData.setCustom(held, filename, title);
            tell(player, "create-filename", "%filename%", filename);
            tell(player, "create-custom-name", "%custom_name%", title);
        } catch (IOException e) {
            player.addChatMessage(new ChatComponentText("\u00a7c" + e.getMessage()));
        }
    }

    private void download(final ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3) {
            throw new WrongUsageException("/customdiscs download <url> <filename.extension>");
        }
        final String url = args[1];
        final String filename = args[2];
        tell(sender, "downloading-file");
        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String saved = AudioDownloader.download(url, filename);
                    tellServerThread(sender, "successful-download", "%file_path%",
                            "config/customdiscs/musicdata/" + saved);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "CustomDiscs audio download failed.", e);
                    tellServerThread(sender, "download-error");
                }
            }
        }, "CustomDiscs-Download");
        worker.setDaemon(true);
        worker.start();
    }

    private void setRange(EntityPlayer player, String[] args) throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException("/customdiscs range <blocks>");
        }
        ItemStack held = requireCustomItem(player);
        if (held == null) {
            return;
        }
        AudioCategory category = ItemData.category(held);
        float maximum = maximumRange(category);
        float range;
        try {
            range = Float.parseFloat(args[1]);
        } catch (NumberFormatException e) {
            throw new WrongUsageException("/customdiscs range <blocks>");
        }
        if (range < 1.0F || range > maximum || Float.isInfinite(range) || Float.isNaN(range)) {
            tell(player, "invalid-range", "%range_value%", Float.toString(maximum));
            return;
        }
        ItemData.getOrCreate(held).setFloat(ItemData.RANGE, range);
        tell(player, "create-custom-range", "%custom_range%", Float.toString(range));
    }

    private void setCooldown(EntityPlayer player, String[] args) throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException("/customdiscs goatcooldown <ticks>");
        }
        ItemStack held = player.getHeldItem();
        if (!ItemData.isHorn(held) || !ItemData.isCustom(held)) {
            tell(player, "not-holding-custom-goathorn");
            return;
        }
        int ticks;
        try {
            ticks = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            throw new WrongUsageException("/customdiscs goatcooldown <ticks>");
        }
        if (ticks < 1 || ticks > ModConfig.hornMaximumCooldownTicks) {
            tell(player, "invalid-cooldown", "%cooldown_value%",
                    Integer.toString(ModConfig.hornMaximumCooldownTicks));
            return;
        }
        ItemData.getOrCreate(held).setInteger(ItemData.COOLDOWN, ticks);
        tell(player, "create-custom-goat-cooldown", "%custom_goat_cooldown%", Integer.toString(ticks));
    }

    private void revert(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (!ItemData.isCustom(held)) {
            tell(player, "revert-not-custom");
            return;
        }
        ItemData.clearCustom(held);
        tell(player, "revert-success");
    }

    private void setModel(EntityPlayer player, String[] args) throws CommandException {
        if (!ModConfig.modelSelectionEnabled) {
            tell(player, "custom-model-data-disabled");
            return;
        }
        ItemStack held = requireCustomItem(player);
        if (held == null) {
            return;
        }
        if (args.length == 1) {
            ModelSelector.open((net.minecraft.entity.player.EntityPlayerMP) player);
            return;
        }
        if (args.length != 2) {
            throw new WrongUsageException("/customdiscs setmodel [id]");
        }
        int id;
        try {
            id = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            id = ModelRegistry.findValue(held, args[1]);
        }
        if (id < 1) {
            tell(player, "set-model-invalid");
            return;
        }
        ItemData.getOrCreate(held).setInteger(ItemData.MODEL, id);
        tell(player, "set-model-success");
    }

    private void revertModel(EntityPlayer player) throws CommandException {
        if (!ModConfig.modelSelectionEnabled) {
            tell(player, "custom-model-data-disabled");
            return;
        }
        ItemStack held = requireCustomItem(player);
        if (held == null) {
            return;
        }
        if (ItemData.getModel(held) == 0) {
            tell(player, "revert-model-not-set");
            return;
        }
        ItemData.clearModel(held);
        tell(player, "revert-model-success");
    }

    private void reload(EntityPlayer player) {
        ModConfig.reload();
        ModelRegistry.reload();
        Localization.reload();
        tell(player, "reload-success");
    }

    private ItemStack requireCustomItem(EntityPlayer player) {
        ItemStack held = player.getHeldItem();
        if (!ItemData.isCustom(held)) {
            tell(player, "not-holding-correct-item");
            return null;
        }
        return held;
    }

    private float maximumRange(AudioCategory category) {
        if (category == AudioCategory.MUSIC_DISC) {
            return ModConfig.musicDiscMaximumRange;
        }
        if (category == AudioCategory.HORN) {
            return ModConfig.hornMaximumRange;
        }
        return ModConfig.headMaximumRange;
    }

    private String join(String[] values, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < values.length; i++) {
            if (i > start) {
                result.append(' ');
            }
            result.append(values[i]);
        }
        String value = result.toString();
        if (value.length() > 1 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private void tell(EntityPlayer player, String key) {
        player.addChatMessage(new ChatComponentText(format(Localization.forPlayer(player, "prefix"))
                + format(Localization.forPlayer(player, key))));
    }

    private void tell(EntityPlayer player, String key, String token, String value) {
        String message = Localization.forPlayer(player, key).replace(token, value);
        player.addChatMessage(new ChatComponentText(format(Localization.forPlayer(player, "prefix"))
                + format(message)));
    }

    private void tell(ICommandSender sender, String key) {
        sender.addChatMessage(new ChatComponentText(format(
                languageMessage(sender, "prefix") + languageMessage(sender, key))));
    }

    private void tell(ICommandSender sender, String key, String token, String value) {
        String message = languageMessage(sender, key).replace(token, value);
        sender.addChatMessage(new ChatComponentText(format(
                languageMessage(sender, "prefix") + message)));
    }

    private void tellServerThread(final ICommandSender sender, final String key) {
        runOnServerThread(new Runnable() {
            @Override
            public void run() {
                tell(sender, key);
            }
        });
    }

    private void tellServerThread(final ICommandSender sender, final String key,
                                  final String token, final String value) {
        runOnServerThread(new Runnable() {
            @Override
            public void run() {
                tell(sender, key, token, value);
            }
        });
    }

    private void runOnServerThread(Runnable task) {
        ServerTaskQueue.enqueue(task);
    }

    private String languageMessage(ICommandSender sender, String key) {
        return sender instanceof EntityPlayer
                ? Localization.forPlayer((EntityPlayer) sender, key)
                : Localization.defaultMessage(key);
    }

    private String format(String message) {
        return message.replace('&', '\u00a7');
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(net.minecraft.command.ICommandSender sender) {
        return true;
    }
}
