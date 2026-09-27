package me.navoei.customdiscs;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandHandler;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.event.world.NoteBlockEvent;
import cpw.mods.fml.common.network.NetworkCheckHandler;
import cpw.mods.fml.relauncher.Side;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class PlaybackLifecycleSmokeTest {
    private PlaybackLifecycleSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        verifyHeadNoteSuppression();
        verifyHeadDropMetadata();
        verifyJukeboxScanDecisions();
        verifyCommandAccess();
        verifyCommandRegistrationCollisions();
        verifyProtocolCompatibility();
        verifyPlaybackMessageWireFormat();
        verifyAudioChunkWireFormat();
        verifyClientboundDiscriminatorContract();
        verifyLoginDoesNotReplayActiveTracks();
        verifyRangeConfigMigration();
        verifyCommandCompletions();
    }

    private static void verifyHeadNoteSuppression() {
        NoteBlockEvent.Play customHeadEvent = noteEvent();
        NBTTagCompound headData = new NBTTagCompound();
        headData.setString(ItemData.FILE, "test.wav");
        AtomicInteger stoppedSources = new AtomicInteger();
        if (!ServerEventHandler.prepareHeadNote(customHeadEvent, headData, new Runnable() {
            @Override
            public void run() {
                if (customHeadEvent.isCanceled()) {
                    throw new AssertionError("Existing playback must stop before note cancellation.");
                }
                stoppedSources.incrementAndGet();
            }
        })
                || !customHeadEvent.isCanceled()) {
            throw new AssertionError("Custom head note must suppress the vanilla note.");
        }

        NoteBlockEvent.Play repeatedEvent = noteEvent();
        if (!ServerEventHandler.prepareHeadNote(repeatedEvent, headData, new Runnable() {
            @Override
            public void run() {
                if (repeatedEvent.isCanceled()) {
                    throw new AssertionError("Repeated playback must stop before note cancellation.");
                }
                stoppedSources.incrementAndGet();
            }
        }) || !repeatedEvent.isCanceled() || stoppedSources.get() != 2) {
            throw new AssertionError("Each custom-head trigger must stop the source before replay.");
        }

        NoteBlockEvent.Play ordinaryEvent = noteEvent();
        if (ServerEventHandler.prepareHeadNote(ordinaryEvent, null, new Runnable() {
            @Override
            public void run() {
                stoppedSources.incrementAndGet();
            }
        }) || ordinaryEvent.isCanceled() || stoppedSources.get() != 3) {
            throw new AssertionError("An ordinary note must remain uncanceled.");
        }

        NoteBlockEvent.Play malformedEvent = noteEvent();
        if (ServerEventHandler.prepareHeadNote(malformedEvent, new NBTTagCompound(), new Runnable() {
            @Override
            public void run() {
                stoppedSources.incrementAndGet();
            }
        }) || malformedEvent.isCanceled() || stoppedSources.get() != 4) {
            throw new AssertionError("Head data without an audio filename is not custom audio.");
        }
    }

    private static NoteBlockEvent.Play noteEvent() {
        return new NoteBlockEvent.Play(null, 1, 2, 3, 0, 0, 0) {
            @Override
            public boolean isCancelable() {
                return true;
            }
        };
    }

    private static void verifyHeadDropMetadata() {
        NBTTagCompound data = new NBTTagCompound();
        data.setString(ItemData.FILE, "head.wav");
        NBTTagCompound root = ServerEventHandler.withHeadData(null, data);
        if (!"head.wav".equals(root
                .getCompoundTag("customdiscs").getString(ItemData.FILE))) {
            throw new AssertionError("Custom head metadata must be copied to skull drops.");
        }
        data.setString(ItemData.FILE, "changed.wav");
        if (!"head.wav".equals(root.getCompoundTag("customdiscs").getString(ItemData.FILE))) {
            throw new AssertionError("A dropped skull must receive a metadata copy.");
        }
    }

    private static void verifyJukeboxScanDecisions() {
        if (!ServerEventHandler.shouldStartJukebox(true, false, false)) {
            throw new AssertionError("An available idle custom jukebox remains startable.");
        }
        if (ServerEventHandler.shouldStartJukebox(true, true, false)
                || ServerEventHandler.shouldStartJukebox(true, false, true)) {
            throw new AssertionError("An active or completed jukebox must not restart.");
        }
        if (ServerEventHandler.shouldStartJukebox(true, false, false, false, false)
                || ServerEventHandler.shouldStartJukebox(true, false, false, true, true)
                || !ServerEventHandler.shouldStartJukebox(true, false, false, true, false)) {
            throw new AssertionError("Existing jukebox records must be baselined on load; new insertions start.");
        }
        if (!ServerEventHandler.shouldStopJukebox(false, true)
                || ServerEventHandler.shouldStopJukebox(false, false)
                || ServerEventHandler.shouldStopJukebox(true, true)) {
            throw new AssertionError("The scan must stop only an active non-custom jukebox.");
        }
    }

    private static void verifyCommandAccess() {
        CustomDiscsCommand command = new CustomDiscsCommand();
        if (command.getRequiredPermissionLevel() != 0
                || !"customdiscs".equals(command.getCommandName())
                || !command.getCommandAliases().isEmpty()
                || !command.canCommandSenderUseCommand(null)) {
            throw new AssertionError("Only /customdiscs should be registered, accessible to all players.");
        }
    }

    private static void verifyCommandRegistrationCollisions() {
        CommandHandler commands = new CommandHandler();
        if (!CommandRegistration.register(commands)
                || !(commands.getCommands().get("customdiscs") instanceof CustomDiscsCommand)
                || commands.getCommands().containsKey("cd")
                || commands.getCommands().containsKey("customdisc")) {
            throw new AssertionError("Registration must expose only the /customdiscs root.");
        }

        CommandHandler occupied = new CommandHandler();
        occupied.registerCommand(new RestrictedCommand("customdiscs"));
        Object owner = occupied.getCommands().get("customdiscs");
        if (CommandRegistration.register(occupied)
                || occupied.getCommands().get("customdiscs") != owner) {
            throw new AssertionError("Registration must not take over another mod's command.");
        }
    }

    private static void verifyRangeConfigMigration() {
        if (ModConfig.migratedRange(16.0F, 0) != 63.0F
                || ModConfig.migratedRange(32.0F, 0) != 32.0F
                || ModConfig.migratedRange(16.0F, 1) != 16.0F) {
            throw new AssertionError("Only legacy default ranges should migrate to 63 blocks.");
        }
    }

    private static void verifyProtocolCompatibility() throws Exception {
        if (!NetworkProtocol.isCompatible(
                Collections.singletonMap(CustomDiscsMod.MOD_ID, NetworkProtocol.MOD_VERSION))
                || NetworkProtocol.isCompatible(
                Collections.singletonMap(CustomDiscsMod.MOD_ID, "0.1.0"))
                || NetworkProtocol.isCompatible(Collections.<String, String>emptyMap())
                || !CustomDiscsMod.class.getMethod("checkNetworkCompatibility",
                java.util.Map.class, Side.class).isAnnotationPresent(NetworkCheckHandler.class)) {
            throw new AssertionError("Only matching CustomDiscs protocol versions may connect.");
        }
        String matchDiagnostic = NetworkProtocol.handshakeDiagnostic(
                "CLIENT", NetworkProtocol.MOD_VERSION);
        String mismatchDiagnostic = NetworkProtocol.handshakeDiagnostic("SERVER", "0.1.0");
        String missingDiagnostic = NetworkProtocol.handshakeDiagnostic("CLIENT", null);
        if (!"remote mod version is absent".equals(NetworkProtocol.mismatchReason(null))
                || !NetworkProtocol.mismatchReason(NetworkProtocol.MOD_VERSION)
                .contains("wire protocol 3 is not separately advertised")
                || !NetworkProtocol.mismatchReason("0.3.0").contains("expected 0.3.1")
                || NetworkProtocol.safeVersion("0.3.1\nforged-log-line").contains("\n")
                || !matchDiagnostic.contains("side=CLIENT local_mod_version=0.3.1")
                || !matchDiagnostic.contains("expected_wire_protocol=3 remote_mod_version=0.3.1 result=ACCEPT")
                || !mismatchDiagnostic.contains("remote_mod_version=0.1.0 result=REJECT")
                || !mismatchDiagnostic.contains("reason=mod version mismatch (expected 0.3.1, received 0.1.0)")
                || !missingDiagnostic.contains("remote_mod_version=<absent> result=REJECT")
                || NetworkProtocol.handshakeDiagnostic("CLIENT\nforged", NetworkProtocol.MOD_VERSION)
                .contains("\n")) {
            throw new AssertionError("Handshake diagnostics must distinguish and safely report outcomes.");
        }
    }

    private static void verifyPlaybackMessageWireFormat() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        for (byte source : new byte[]{PlaybackMessage.BLOCK_SOURCE, PlaybackMessage.ENTITY_SOURCE}) {
            PlaybackMessage encoded = PlaybackMessage.start(
                    java.util.UUID.randomUUID(), source, 0, 1.0D, 2.0D, 3.0D, 4,
                    AudioCategory.MUSIC_DISC, 63.0F, 1.0F, hash, "wav", "test");
            io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
            try {
                buffer.writeByte(NetworkProtocol.PLAYBACK_MESSAGE_ID);
                encoded.toBytes(buffer);
                String expectedHeader = String.format(
                        "action=0x00 source=0x%02x category=0x00", source & 0xff);
                if (buffer.getUnsignedByte(0) != NetworkProtocol.PLAYBACK_MESSAGE_ID
                        || buffer.getUnsignedByte(18) != (source & 0xff)
                        || buffer.getUnsignedByte(19) != AudioCategory.MUSIC_DISC.getId()
                        || !expectedHeader.equals(PlaybackMessage.rawHeader(buffer, 1))) {
                    throw new AssertionError("Playback serializer header bytes do not match the wire contract.");
                }
                PlaybackMessage decoded = new PlaybackMessage();
                decoded.fromBytes(buffer.slice(1, buffer.readableBytes() - 1));
                if (decoded.sourceType != source
                        || decoded.category != AudioCategory.MUSIC_DISC.getId()) {
                    throw new AssertionError("Playback source/category wire fields decoded incorrectly.");
                }
            } finally {
                buffer.release();
            }
        }

        PlaybackMessage invalidSource = PlaybackMessage.start(
                java.util.UUID.randomUUID(), (byte) 2, 0, 1.0D, 2.0D, 3.0D, 4,
                AudioCategory.MUSIC_DISC, 63.0F, 1.0F, hash, "wav", "test");
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            invalidSource.toBytes(buffer);
            PlaybackMessage decoded = new PlaybackMessage();
            decoded.fromBytes(buffer);
            if (decoded.decodeError == null) {
                throw new AssertionError("Unknown playback source must be rejected, not misdecoded.");
            }
            if (!"action=0x00 source=0x02 category=0x00".equals(decoded.decodeDiagnostic)) {
                throw new AssertionError("Malformed packet diagnostics must capture bounded raw header bytes.");
            }
        } finally {
            buffer.release();
        }

        io.netty.buffer.ByteBuf truncated = io.netty.buffer.Unpooled.buffer();
        try {
            truncated.writeByte(PlaybackMessage.START);
            PlaybackMessage decoded = new PlaybackMessage();
            decoded.fromBytes(truncated);
            if (decoded.decodeError == null) {
                throw new AssertionError("Truncated playback packets must be rejected.");
            }
        } finally {
            truncated.release();
        }
    }

    private static void verifyClientboundDiscriminatorContract() throws Exception {
        int[] ids = {
                NetworkProtocol.PLAYBACK_MESSAGE_ID,
                NetworkProtocol.AUDIO_REQUEST_MESSAGE_ID,
                NetworkProtocol.AUDIO_FINISHED_MESSAGE_ID,
                NetworkProtocol.AUDIO_CHUNK_MESSAGE_ID,
                NetworkProtocol.MODEL_SELECT_MESSAGE_ID,
                NetworkProtocol.MODEL_LIST_MESSAGE_ID,
                NetworkProtocol.CLIENT_LANGUAGE_MESSAGE_ID,
                NetworkProtocol.AUDIO_STARTED_MESSAGE_ID
        };
        java.util.Set<Integer> unique = new java.util.HashSet<Integer>();
        for (int id : ids) {
            unique.add(id);
        }
        if (unique.size() != ids.length
                || !java.lang.reflect.Modifier.isPublic(NetworkBootstrap.class
                .getMethod("registerClientboundDiscriminators").getModifiers())) {
            throw new AssertionError("Every packet needs a unique discriminator and server-side outbound registration.");
        }
    }

    private static void verifyAudioChunkWireFormat() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        AudioChunkMessage sent = new AudioChunkMessage(hash, "mp3", 0, 1, 3,
                new byte[]{1, 2, 3});
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            buffer.writeByte(NetworkProtocol.AUDIO_CHUNK_MESSAGE_ID);
            sent.toBytes(buffer);
            if (buffer.getUnsignedByte(0) != 3
                    || buffer.getUnsignedShort(1) != 64
                    || buffer.getUnsignedByte(3) != '0'
                    || buffer.getUnsignedByte(4) != '1') {
                throw new AssertionError("Audio chunk payload and clientbound discriminator contract changed.");
            }
            AudioChunkMessage received = new AudioChunkMessage();
            received.fromBytes(buffer.slice(1, buffer.readableBytes() - 1));
            if (!hash.equals(received.hash) || !"mp3".equals(received.extension)
                    || received.data.length != 3 || received.data[2] != 3) {
                throw new AssertionError("Audio chunk serializer roundtrip failed.");
            }
        } finally {
            buffer.release();
        }
    }

    private static void verifyLoginDoesNotReplayActiveTracks() throws Exception {
        try {
            ServerEventHandler.class.getDeclaredMethod("onPlayerLogin",
                    cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent.class);
            throw new AssertionError("Login must not replay already-active playback tracks.");
        } catch (NoSuchMethodException expected) {
            // Login synchronization is intentionally absent to prevent malformed-packet reconnect loops.
        }
        try {
            PlaybackService.class.getDeclaredMethod("sendActiveTo",
                    net.minecraft.entity.player.EntityPlayerMP.class);
            throw new AssertionError("Active-track resynchronization must not be available on login.");
        } catch (NoSuchMethodException expected) {
            // No server path may replay active playback to a reconnecting client.
        }
    }

    private static void verifyCommandCompletions() throws IOException {
        Path temporary = Files.createTempDirectory("customdiscs-tab-test");
        String previousDepth = ModConfig.subdirectoryDepth;
        try {
            MusicFiles.initialize(temporary.toFile());
            Path root = MusicFiles.getMusicDirectory().toPath();
            Files.write(root.resolve("ambient.wav"), new byte[]{1});
            Files.write(root.resolve("ignored.txt"), new byte[]{1});
            Files.createDirectories(root.resolve("one"));
            Files.write(root.resolve("one/chime.flac"), new byte[]{1});
            Files.createDirectories(root.resolve("one/two"));
            Files.write(root.resolve("one/two/deep.mp3"), new byte[]{1});

            CustomDiscsCommand command = new CustomDiscsCommand();
            if (!command.addTabCompletionOptions(null, new String[]{"c"}).contains("create")
                    || !command.addTabCompletionOptions(null,
                    new String[]{"create", "a"}).contains("ambient.wav")) {
                throw new AssertionError("Command and valid audio filename completion must work.");
            }
            if (CustomDiscsCommand.audioFileCompletions("").contains("ignored.txt")
                    || CustomDiscsCommand.audioFileCompletions("").contains("one/chime.flac")) {
                throw new AssertionError("Completions must honor audio extensions and disabled subdirectories.");
            }
            ModConfig.subdirectoryDepth = "single";
            if (!CustomDiscsCommand.audioFileCompletions("").contains("one/chime.flac")
                    || CustomDiscsCommand.audioFileCompletions("").contains("one/two/deep.mp3")) {
                throw new AssertionError("Completions must honor the configured subdirectory depth.");
            }
        } finally {
            ModConfig.subdirectoryDepth = previousDepth;
            try (Stream<Path> paths = Files.walk(temporary)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    private static final class RestrictedCommand extends CommandBase {
        private final String name;

        private RestrictedCommand(String name) {
            this.name = name;
        }

        @Override
        public String getCommandName() {
            return name;
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "";
        }

        @Override
        public void processCommand(ICommandSender sender, String[] arguments)
                throws CommandException {
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }
    }
}
