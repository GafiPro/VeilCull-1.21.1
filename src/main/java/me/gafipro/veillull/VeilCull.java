package me.gafipro.veillull;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class VeilCull implements ModInitializer {
    public static final String MOD_ID = "veilcull";
    public static final VeilCullConfig CONFIG = new VeilCullConfig();

    private static final Map<UUID, Integer> FAKE_PINGS = new HashMap<>();
    private static int pingBroadcastTicker = 0;

    @Override
    public void onInitialize() {
        CONFIG.load();

        registerCommands();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!FAKE_PINGS.isEmpty()) {
                for (Map.Entry<UUID, Integer> entry : FAKE_PINGS.entrySet()) {
                    ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                    if (player != null) {
                        player.latency = entry.getValue();
                    }
                }

                // Vanilla periodically refreshes tab-list latency. Sending an UPDATE_LATENCY
                // packet regularly keeps the fake value visible to client-side ping displays.
                if (++pingBroadcastTicker >= 10) {
                    pingBroadcastTicker = 0;
                    for (Map.Entry<UUID, Integer> entry : FAKE_PINGS.entrySet()) {
                        ServerPlayerEntity target = server.getPlayerManager().getPlayer(entry.getKey());
                        if (target == null) {
                            continue;
                        }

                        PlayerListS2CPacket packet = new PlayerListS2CPacket(
                                java.util.EnumSet.of(PlayerListS2CPacket.Action.UPDATE_LATENCY),
                                java.util.List.of(target)
                        );

                        for (ServerPlayerEntity viewer : server.getPlayerManager().getPlayerList()) {
                            viewer.networkHandler.sendPacket(packet);
                        }
                    }
                }
            } else {
                pingBroadcastTicker = 0;
            }
        });
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
                                        ctx.getSource().getServer().getPlayerManager().broadcast(fakeMessage, false);

                                        return 1;
                                    }))));

            dispatcher.register(literal("fakejoin")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String player = StringArgumentType.getString(ctx, "player");
                                Text message = Text.translatable(
                                        "multiplayer.player.joined",
                                        Text.literal(player)
                                ).formatted(Formatting.YELLOW);

                                ctx.getSource().getServer().getPlayerManager().broadcast(message, false);
                                return 1;
                            })));

            dispatcher.register(literal("fakeleave")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                String player = StringArgumentType.getString(ctx, "player");
                                Text message = Text.translatable(
                                        "multiplayer.player.left",
                                        Text.literal(player)
                                ).formatted(Formatting.YELLOW);

                                ctx.getSource().getServer().getPlayerManager().broadcast(message, false);
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
                            .executes(ctx -> setFakeLag(ctx.getSource(), StringArgumentType.getString(ctx, "player"), 9999))
                            .then(argument("ping", IntegerArgumentType.integer(0, 1000000))
                                    .executes(ctx -> setFakeLag(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "player"),
                                            IntegerArgumentType.getInteger(ctx, "ping")
                                    )))));

            dispatcher.register(literal("interceptmsg")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(ctx -> toggleInterception(ctx.getSource()))
                    .then(argument("player", StringArgumentType.word())
                            .executes(ctx -> enableInterceptionFor(
                                    ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player")
                            ))));
        });
    }

    private static int setFakeLag(ServerCommandSource source, String name, int ping) {
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(name);
        if (target == null) {
            source.sendError(Text.literal("O jogador '" + name + "' não está online."));
            return 0;
        }

        if (ping == 0) {
            FAKE_PINGS.remove(target.getUuid());
            source.sendFeedback(() -> Text.literal("Fake lag removido de " + name + "."), false);
            return 1;
        }

        FAKE_PINGS.put(target.getUuid(), ping);
        target.latency = ping;
        source.sendFeedback(() -> Text.literal("Ping de " + name + " definido para " + ping + " ms."), false);
        return 1;
    }

    private static int toggleInterception(ServerCommandSource source) {
        ServerPlayerEntity observer = source.getPlayer();
        if (observer == null) {
            source.sendError(Text.literal("Este comando tem de ser executado por um jogador."));
            return 0;
        }

        if (CONFIG.interceptPlayer == null || CONFIG.interceptPlayer.isBlank()) {
            source.sendError(Text.literal("Ainda não existe um jogador alvo. Usa /interceptmsg <player> primeiro."));
            return 0;
        }

        if (observer.getGameProfile().getName().equalsIgnoreCase(CONFIG.interceptPlayer)) {
            source.sendError(Text.literal("O observador e o jogador interceptado não podem ser a mesma pessoa."));
            return 0;
        }

        CONFIG.interceptEnabled = !CONFIG.interceptEnabled;
        CONFIG.interceptObserver = observer.getGameProfile().getName();
        CONFIG.save();

        boolean enabled = CONFIG.interceptEnabled;
        source.sendFeedback(() -> Text.literal(
                enabled
                        ? "Interceptação ativada para " + CONFIG.interceptPlayer + "."
                        : "Interceptação desativada."
        ), false);

        return 1;
    }

    private static int enableInterceptionFor(ServerCommandSource source, String target) {
        ServerPlayerEntity observer = source.getPlayer();
        if (observer == null) {
            source.sendError(Text.literal("Este comando tem de ser executado por um jogador."));
            return 0;
        }

        if (observer.getGameProfile().getName().equalsIgnoreCase(target)) {
            source.sendError(Text.literal("O observador e o jogador interceptado não podem ser a mesma pessoa."));
            return 0;
        }

        CONFIG.interceptPlayer = target;
        CONFIG.interceptObserver = observer.getGameProfile().getName();
        CONFIG.interceptEnabled = true;
        CONFIG.save();

        source.sendFeedback(() -> Text.literal(
                "Interceptação ativada: " + target + " -> " + CONFIG.interceptObserver + "."
        ), false);

        return 1;
    }

    public static void interceptMessage(ServerPlayerEntity recipient, Text message) {
        if (!CONFIG.interceptEnabled
                || CONFIG.interceptPlayer == null
                || CONFIG.interceptPlayer.isBlank()
                || CONFIG.interceptObserver == null
                || CONFIG.interceptObserver.isBlank()) {
            return;
        }

        if (!recipient.getGameProfile().getName().equalsIgnoreCase(CONFIG.interceptPlayer)) {
            return;
        }

        if (recipient.getGameProfile().getName().equalsIgnoreCase(CONFIG.interceptObserver)) {
            return;
        }

        ServerPlayerEntity observer = recipient.getServer()
                .getPlayerManager()
                .getPlayer(CONFIG.interceptObserver);

        if (observer == null) {
            return;
        }

        observer.sendMessage(
                Text.literal("[Intercept] ").formatted(Formatting.DARK_GRAY)
                        .append(message.copy())
        );
    }

    public static void interceptChatMessage(ServerPlayerEntity recipient, Text message) {
        if (!CONFIG.interceptEnabled
                || CONFIG.interceptPlayer == null
                || CONFIG.interceptObserver == null) {
            return;
        }

        if (!recipient.getGameProfile().getName().equalsIgnoreCase(CONFIG.interceptPlayer)) {
            return;
        }

        ServerPlayerEntity observer = recipient.getServer()
                .getPlayerManager()
                .getPlayer(CONFIG.interceptObserver);

        if (observer == null || observer == recipient) {
            return;
        }

        observer.sendMessage(
                Text.literal("[Intercept] ").formatted(Formatting.DARK_GRAY)
                        .append(message.copy())
        );
    }

    public static void clearFakePing(UUID uuid) {
        FAKE_PINGS.remove(uuid);
    }
}
