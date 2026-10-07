package me.gafipro.veilcull;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VeilCull implements ModInitializer {
    public static final String MOD_ID = "veilcull";
    public static final VeilCullConfig CONFIG = new VeilCullConfig();

    private static final int RANDOM_PING_MIN = 7000;
    private static final int RANDOM_PING_MAX = 9800;
    private static final int PING_UPDATE_INTERVAL_TICKS = 10;

    private static final Map<UUID, FakeLagState> FAKE_LAGS = new HashMap<>();
    private static final Set<UUID> VANISHED_PLAYERS = new java.util.HashSet<>();
    private static final Map<UUID, Boolean> PREVIOUS_INVISIBLE_STATE = new HashMap<>();

    private static final Set<Object> INTERCEPTED_THIS_TICK =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private static int pingBroadcastTicker = 0;

    @Override
    public void onInitialize() {
        CONFIG.load();
        registerCommands();

        ServerTickEvents.START_SERVER_TICK.register(server -> INTERCEPTED_THIS_TICK.clear());

        ServerTickEvents.END_SERVER_TICK.register(VeilCull::tickFakeLag);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                clearPlayerState(handler.player.getUuid()));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                hideVanishedPlayersFrom(handler.player));
    }

    private static void tickFakeLag(MinecraftServer server) {
        if (FAKE_LAGS.isEmpty()) {
            pingBroadcastTicker = 0;
            return;
        }

        List<ServerPlayerEntity> fakeLagPlayers = new ArrayList<>();

        for (Map.Entry<UUID, FakeLagState> entry : FAKE_LAGS.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());

            if (player == null) {
                continue;
            }

            FakeLagState state = entry.getValue();

            fakeLagPlayers.add(player);
        }

        if (++pingBroadcastTicker < PING_UPDATE_INTERVAL_TICKS || fakeLagPlayers.isEmpty()) {
            return;
        }

        pingBroadcastTicker = 0;

        for (FakeLagState state : FAKE_LAGS.values()) {
            if (state.random) {
                state.ping = randomPing();
            }
        }

        PlayerListS2CPacket packet = new PlayerListS2CPacket(
                EnumSet.of(PlayerListS2CPacket.Action.UPDATE_LATENCY),
                fakeLagPlayers
        );

        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            viewer.networkHandler.sendPacket(packet);
        }
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("sendmsg")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                            .then(argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String player = StringArgumentType.getString(ctx, "player");
                                        String message = StringArgumentType.getString(ctx, "message");

                                        Text fakeMessage = Text.literal("<" + player + "> ")
                                                .append(Text.literal(message));

                                        ctx.getSource().getServer().getPlayerManager()
                                                .broadcast(fakeMessage, false);

                                        return 1;
                                    }))));

            dispatcher.register(literal("fakejoin")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerCommandSource source = ctx.getSource();
                                String playerName = StringArgumentType.getString(ctx, "player");
                                Text message = Text.translatable(
                                        "multiplayer.player.joined",
                                        Text.literal(playerName)
                                ).formatted(Formatting.YELLOW);

                                source.getServer().getPlayerManager().broadcast(message, false);

                                ServerPlayerEntity target = source.getServer().getPlayerManager()
                                        .getPlayer(playerName);
                                if (target != null && isVanished(target)) {
                                    setVanished(source.getServer(), target, false);
                                }

                                return 1;
                            })));

            dispatcher.register(literal("fakeleave")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerCommandSource source = ctx.getSource();
                                String playerName = StringArgumentType.getString(ctx, "player");
                                Text message = Text.translatable(
                                        "multiplayer.player.left",
                                        Text.literal(playerName)
                                ).formatted(Formatting.YELLOW);

                                source.getServer().getPlayerManager().broadcast(message, false);

                                ServerPlayerEntity target = source.getServer().getPlayerManager()
                                        .getPlayer(playerName);
                                if (target != null) {
                                    setVanished(source.getServer(), target, true);
                                }

                                return 1;
                            })));

            dispatcher.register(literal("fakeadvancement")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                            .then(argument("text", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String player = StringArgumentType.getString(ctx, "player");
                                        String title = StringArgumentType.getString(ctx, "text");

                                        Text message = Text.literal(player + " discovered ")
                                                .append(Text.literal(title).formatted(Formatting.GREEN));

                                        ctx.getSource().getServer().getPlayerManager().broadcast(message, false);
                                        return 1;
                                    }))));

            dispatcher.register(literal("fakedeath")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String player = StringArgumentType.getString(ctx, "player");
                                Text message = Text.translatable(
                                        "death.attack.generic",
                                        Text.literal(player)
                                );

                                ctx.getSource().getServer().getPlayerManager().broadcast(message, false);
                                return 1;
                            })));

            dispatcher.register(literal("fakelag")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                            .executes(ctx -> enableRandomFakeLag(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player")
                            ))
                            .then(argument("ping", IntegerArgumentType.integer(0, 1000000))
                                    .executes(ctx -> setFixedOrDisableFakeLag(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "player"),
                                            IntegerArgumentType.getInteger(ctx, "ping")
                                    )))));

            dispatcher.register(literal("interceptmsg")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> toggleGlobalInterception(ctx.getSource())));

            dispatcher.register(literal("vanish")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerCommandSource source = ctx.getSource();
                                String playerName = StringArgumentType.getString(ctx, "player");
                                ServerPlayerEntity target = source.getServer().getPlayerManager()
                                        .getPlayer(playerName);

                                if (target == null) {
                                    source.sendError(Text.literal(
                                            "O jogador '" + playerName + "' não está online."
                                    ));
                                    return 0;
                                }

                                boolean newState = !isVanished(target);
                                setVanished(source.getServer(), target, newState);

                                source.sendFeedback(() -> Text.literal(
                                        target.getGameProfile().getName()
                                                + (newState ? " entrou em vanish." : " saiu do vanish.")
                                ), false);

                                return 1;
                            }))
                    .executes(ctx -> {
                        ServerPlayerEntity target = ctx.getSource().getPlayer();

                        if (target == null) {
                            ctx.getSource().sendError(Text.literal(
                                    "Este comando tem de ser executado por um jogador ou com um alvo."
                            ));
                            return 0;
                        }

                        boolean newState = !isVanished(target);
                        setVanished(ctx.getSource().getServer(), target, newState);

                        ctx.getSource().sendFeedback(() -> Text.literal(
                                newState ? "Vanish ativado." : "Vanish desativado."
                        ), false);

                        return 1;
                    }));
        });
    }

    private static int enableRandomFakeLag(ServerCommandSource source, String name) {
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(name);

        if (target == null) {
            source.sendError(Text.literal("O jogador '" + name + "' não está online."));
            return 0;
        }

        FakeLagState state = new FakeLagState(true, randomPing());
        FAKE_LAGS.put(target.getUuid(), state);

        sendLatencyUpdate(source.getServer(), target);

        source.sendFeedback(() -> Text.literal(
                "Fake lag de " + name + " ativado: ping a variar entre "
                        + RANDOM_PING_MIN + " e " + RANDOM_PING_MAX + " ms."
        ), false);

        return 1;
    }

    private static int setFixedOrDisableFakeLag(ServerCommandSource source, String name, int ping) {
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(name);

        if (target == null) {
            source.sendError(Text.literal("O jogador '" + name + "' não está online."));
            return 0;
        }

        if (ping == 0) {
            FAKE_LAGS.remove(target.getUuid());
            sendLatencyUpdate(source.getServer(), target);

            source.sendFeedback(
                    () -> Text.literal("Fake lag removido de " + name + "."),
                    false
            );
            return 1;
        }

        FAKE_LAGS.put(target.getUuid(), new FakeLagState(false, ping));
        sendLatencyUpdate(source.getServer(), target);

        source.sendFeedback(() -> Text.literal(
                "Ping falso de " + name + " definido para " + ping + " ms."
        ), false);

        return 1;
    }

    private static void sendLatencyUpdate(MinecraftServer server, ServerPlayerEntity target) {
        PlayerListS2CPacket packet = new PlayerListS2CPacket(
                EnumSet.of(PlayerListS2CPacket.Action.UPDATE_LATENCY),
                List.of(target)
        );

        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            viewer.networkHandler.sendPacket(packet);
        }
    }

    private static int toggleGlobalInterception(ServerCommandSource source) {
        ServerPlayerEntity observer = source.getPlayer();

        if (observer == null) {
            source.sendError(Text.literal("Este comando tem de ser executado por um jogador."));
            return 0;
        }

        CONFIG.interceptEnabled = !CONFIG.interceptEnabled;

        if (CONFIG.interceptEnabled) {
            CONFIG.interceptObserver = observer.getGameProfile().getName();

            source.sendFeedback(() -> Text.literal(
                    "Interceptação GLOBAL ativada. As mensagens recebidas pelos jogadores serão copiadas para "
                            + CONFIG.interceptObserver + "."
            ), false);
        } else {
            source.sendFeedback(
                    () -> Text.literal("Interceptação GLOBAL desativada."),
                    false
            );
        }

        CONFIG.save();
        return 1;
    }

    public static void interceptMessage(ServerPlayerEntity recipient, Text message) {
        if (!CONFIG.interceptEnabled || CONFIG.interceptObserver == null
                || CONFIG.interceptObserver.isBlank()) {
            return;
        }

        if (recipient.getGameProfile().getName().equalsIgnoreCase(CONFIG.interceptObserver)) {
            return;
        }

        if (!INTERCEPTED_THIS_TICK.add(message)) {
            return;
        }

        ServerPlayerEntity observer = findObserver(recipient.getServer());

        if (observer == null || observer == recipient) {
            return;
        }

        observer.sendMessage(
                Text.literal("[Intercept] ").formatted(Formatting.DARK_GRAY)
                        .append(message.copy())
        );
    }

    public static void interceptProfilelessChatMessage(
            ServerPlayerEntity recipient,
            Text message,
            MessageType.Parameters params
    ) {
        if (!CONFIG.interceptEnabled || CONFIG.interceptObserver == null
                || CONFIG.interceptObserver.isBlank()) {
            return;
        }

        if (recipient.getGameProfile().getName().equalsIgnoreCase(CONFIG.interceptObserver)) {
            return;
        }

        if (!INTERCEPTED_THIS_TICK.add(message)) {
            return;
        }

        ServerPlayerEntity observer = findObserver(recipient.getServer());

        if (observer == null || observer == recipient) {
            return;
        }

        Text decoratedMessage = params.applyChatDecoration(message);

        observer.sendMessage(
                Text.literal("[Intercept] ").formatted(Formatting.DARK_GRAY)
                        .append(decoratedMessage.copy())
        );
    }

    public static void interceptChatMessage(
            ServerPlayerEntity recipient,
            Object messageIdentity,
            Text decoratedMessage
    ) {
        if (!CONFIG.interceptEnabled || CONFIG.interceptObserver == null
                || CONFIG.interceptObserver.isBlank()) {
            return;
        }

        if (recipient.getGameProfile().getName().equalsIgnoreCase(CONFIG.interceptObserver)) {
            return;
        }

        if (!INTERCEPTED_THIS_TICK.add(messageIdentity)) {
            return;
        }

        ServerPlayerEntity observer = findObserver(recipient.getServer());

        if (observer == null || observer == recipient) {
            return;
        }

        observer.sendMessage(
                Text.literal("[Intercept] ").formatted(Formatting.DARK_GRAY)
                        .append(decoratedMessage.copy())
        );
    }

    private static ServerPlayerEntity findObserver(MinecraftServer server) {
        if (CONFIG.interceptObserver == null || CONFIG.interceptObserver.isBlank()) {
            return null;
        }

        return server.getPlayerManager().getPlayer(CONFIG.interceptObserver);
    }

    public static Integer getFakePing(UUID uuid) {
        FakeLagState state = FAKE_LAGS.get(uuid);
        return state == null ? null : state.ping;
    }

    private static int randomPing() {
        return ThreadLocalRandom.current().nextInt(
                RANDOM_PING_MIN,
                RANDOM_PING_MAX + 1
        );
    }

    public static boolean isVanished(ServerPlayerEntity player) {
        return VANISHED_PLAYERS.contains(player.getUuid());
    }

    private static void setVanished(MinecraftServer server, ServerPlayerEntity target, boolean vanished) {
        UUID uuid = target.getUuid();

        if (vanished) {
            if (!VANISHED_PLAYERS.add(uuid)) {
                return;
            }

            PREVIOUS_INVISIBLE_STATE.put(uuid, target.isInvisible());
            target.setInvisible(true);

            PlayerRemoveS2CPacket removePacket =
                    new PlayerRemoveS2CPacket(List.of(uuid));

            for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
                if (viewer != target) {
                    viewer.networkHandler.sendPacket(removePacket);
                }
            }
            return;
        }

        if (!VANISHED_PLAYERS.remove(uuid)) {
            return;
        }

        boolean wasInvisible = PREVIOUS_INVISIBLE_STATE
                .remove(uuid, Boolean.TRUE);

        if (!wasInvisible) {
            target.setInvisible(false);
        }

        PlayerListS2CPacket addPacket = new PlayerListS2CPacket(
                PlayerListS2CPacket.Action.ADD_PLAYER,
                target
        );

        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
            if (viewer != target) {
                viewer.networkHandler.sendPacket(addPacket);
            }
        }
    }

    private static void hideVanishedPlayersFrom(ServerPlayerEntity joiningPlayer) {
        if (VANISHED_PLAYERS.isEmpty()) {
            return;
        }

        for (UUID vanishedUuid : VANISHED_PLAYERS) {
            if (!joiningPlayer.getUuid().equals(vanishedUuid)) {
                joiningPlayer.networkHandler.sendPacket(
                        new PlayerRemoveS2CPacket(List.of(vanishedUuid))
                );
            }
        }
    }

    private static void clearPlayerState(UUID uuid) {
        FAKE_LAGS.remove(uuid);
        VANISHED_PLAYERS.remove(uuid);
        PREVIOUS_INVISIBLE_STATE.remove(uuid);
    }

    private static final class FakeLagState {
        private final boolean random;
        private int ping;

        private FakeLagState(boolean random, int ping) {
            this.random = random;
            this.ping = ping;
        }
    }
}
