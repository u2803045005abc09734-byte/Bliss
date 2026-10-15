package dev.blissgems.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashMap;
import java.util.List;

public final class BlissGemsFabric implements ModInitializer {
    public static final String MOD_ID = "blissgemsfabric";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Map<String, Gem> GEMS = new LinkedHashMap<>();
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private static long tickCounter = 0L;

    static {
        registerGem("astra", "Astra", Items.ENDER_EYE);
        registerGem("fire", "Fire", Items.BLAZE_POWDER);
        registerGem("flux", "Flux", Items.PRISMARINE_CRYSTALS);
        registerGem("life", "Life", Items.GHAST_TEAR);
        registerGem("puff", "Puff", Items.FEATHER);
        registerGem("speed", "Speed", Items.SUGAR);
        registerGem("strength", "Strength", Items.BLAZE_ROD);
        registerGem("wealth", "Wealth", Items.EMERALD);
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("bliss")
                .executes(context -> showHelp(context.getSource()))
                .then(Commands.literal("help").executes(context -> showHelp(context.getSource())))
                .then(Commands.literal("info").executes(context -> showInfo(context.getSource())))
                .then(Commands.literal("give")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_MODERATOR))
                    .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("gem", StringArgumentType.word())
                            .then(Commands.argument("tier", IntegerArgumentType.integer(1, 2))
                                .executes(context -> giveGem(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "player"),
                                    StringArgumentType.getString(context, "gem"),
                                    IntegerArgumentType.getInteger(context, "tier")
                                ))))))
                .then(Commands.literal("ability")
                    .then(Commands.argument("slot", StringArgumentType.word())
                        .executes(context -> activateAbility(
                            context.getSource(),
                            StringArgumentType.getString(context, "slot")
                        )))));
        });

        ServerTickEvents.END_SERVER_TICK.register(BlissGemsFabric::onServerTick);
        LOGGER.info("BlissGems Fabric Port initialized for Minecraft 26.3.");
    }

    private static void registerGem(String id, String displayName, Item icon) {
        GEMS.put(id, new Gem(id, displayName, icon));
    }

    private static int showHelp(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6§lBlissGems Fabric §7— command guide"), false);
        source.sendSuccess(() -> Component.literal("§e/bliss info §7— inspect the gem in your hand"), false);
        source.sendSuccess(() -> Component.literal("§e/bliss ability primary|secondary §7— activate a gem ability"), false);
        source.sendSuccess(() -> Component.literal("§e/bliss give <player> <gem> <1|2> §7— give a gem (moderator)"), false);
        source.sendSuccess(() -> Component.literal("§7Gems: astra, fire, flux, life, puff, speed, strength, wealth"), false);
        return 1;
    }

    private static int showInfo(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        HeldGem held = findHeldGem(player);
        if (held == null) {
            player.sendSystemMessage(Component.literal("§cHold a Bliss Gem in your main hand or offhand first."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§6" + held.gem().displayName() + " Gem §7— Tier " + held.tier()));
        player.sendSystemMessage(Component.literal("§7Passive effects are applied while the gem is held."));
        return 1;
    }

    private static int giveGem(CommandSourceStack source, String playerName, String gemId, int tier) {
        Gem gem = GEMS.get(gemId.toLowerCase());
        if (gem == null) {
            source.sendFailure(Component.literal("Unknown gem. Use: astra, fire, flux, life, puff, speed, strength, wealth."));
            return 0;
        }

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Player not found or not online: " + playerName));
            return 0;
        }

        ItemStack stack = new ItemStack(gem.icon(), 1);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Bliss Gem: " + gem.displayName() + " [T" + tier + "]"));
        target.getInventory().add(stack);
        target.sendSystemMessage(Component.literal("§6You received the " + gem.displayName() + " Gem §7(Tier " + tier + ")"));
        source.sendSuccess(() -> Component.literal("Gave " + gem.displayName() + " Gem T" + tier + " to " + playerName + "."), true);
        return 1;
    }

    private static int activateAbility(CommandSourceStack source, String slot) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String normalizedSlot = slot.toLowerCase();
        if (!normalizedSlot.equals("primary") && !normalizedSlot.equals("secondary")) {
            player.sendSystemMessage(Component.literal("§cChoose primary or secondary: /bliss ability primary"));
            return 0;
        }

        HeldGem held = findHeldGem(player);
        if (held == null) {
            player.sendSystemMessage(Component.literal("§cHold a Bliss Gem in your main hand or offhand first."));
            return 0;
        }

        long now = player.level().getGameTime();
        String cooldownKey = player.getUUID() + ":" + held.gem().id() + ":" + normalizedSlot;
        long readyAt = COOLDOWNS.getOrDefault(cooldownKey, 0L);
        if (now < readyAt) {
            player.sendSystemMessage(Component.literal("§cAbility cooldown: " + ((readyAt - now + 19) / 20) + "s"));
            return 0;
        }

        applyAbility(player, held.gem(), held.tier(), normalizedSlot.equals("secondary"));
        int cooldownSeconds = normalizedSlot.equals("secondary") ? 60 : 30;
        COOLDOWNS.put(cooldownKey, now + cooldownSeconds * 20L);
        player.sendSystemMessage(Component.literal("§6" + held.gem().displayName() + " §7" + normalizedSlot + " ability activated."));
        return 1;
    }

    private static void applyAbility(ServerPlayer player, Gem gem, int tier, boolean secondary) {
        int duration = 20 * (secondary ? 8 : 10);
        int amplifier = tier == 2 ? 1 : 0;
        switch (gem.id()) {
            case "astra" -> {
                addEffect(player, MobEffects.INVISIBILITY, duration, 0);
                addEffect(player, MobEffects.RESISTANCE, duration, secondary ? amplifier : 0);
            }
            case "fire" -> {
                addEffect(player, MobEffects.FIRE_RESISTANCE, duration * 2, 0);
                if (!secondary) addEffect(player, MobEffects.STRENGTH, duration, amplifier);
            }
            case "flux" -> {
                if (secondary) {
                    player.setDeltaMovement(player.getDeltaMovement().add(player.getLookAngle().scale(tier == 2 ? 1.8 : 1.3)));
                } else {
                    addEffect(player, MobEffects.HASTE, duration, amplifier);
                    addEffect(player, MobEffects.SPEED, duration, 0);
                }
            }
            case "life" -> {
                player.heal(tier == 2 ? 10.0F : 6.0F);
                addEffect(player, MobEffects.REGENERATION, duration, amplifier);
                if (secondary) addEffect(player, MobEffects.ABSORPTION, duration, amplifier);
            }
            case "puff" -> {
                if (secondary) {
                    addEffect(player, MobEffects.SLOW_FALLING, 20 * 12, 0);
                }
                player.setDeltaMovement(player.getDeltaMovement().add(0.0, tier == 2 ? 1.35 : 0.95, 0.0));
            }
            case "speed" -> {
                addEffect(player, MobEffects.SPEED, duration, secondary ? amplifier : (tier == 2 ? 2 : 1));
                if (secondary) addEffect(player, MobEffects.JUMP_BOOST, duration, amplifier);
            }
            case "strength" -> {
                addEffect(player, MobEffects.STRENGTH, duration, secondary ? 0 : amplifier);
                if (secondary) addEffect(player, MobEffects.RESISTANCE, duration, amplifier);
            }
            case "wealth" -> {
                if (secondary) {
                    player.getInventory().add(new ItemStack(Items.EMERALD, tier == 2 ? 3 : 1));
                } else {
                    addEffect(player, MobEffects.LUCK, duration * 2, amplifier);
                }
            }
            default -> LOGGER.warn("No ability implementation for gem {}", gem.id());
        }
    }

    private static void addEffect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                                  int durationTicks, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, durationTicks, amplifier));
    }

    private static void onServerTick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % 20L != 0L) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
                HeldGem held = parseGem(stack);
                if (held != null) applyPassive(player, held.gem(), held.tier());
            }
        }

        // Avoid holding cooldown entries for players who have been offline for a long time.
        if (COOLDOWNS.size() > 4096) COOLDOWNS.clear();
    }

    private static void applyPassive(ServerPlayer player, Gem gem, int tier) {
        int amplifier = tier == 2 ? 1 : 0;
        switch (gem.id()) {
            case "astra" -> addEffect(player, MobEffects.RESISTANCE, 40, amplifier == 1 ? 1 : 0);
            case "fire" -> addEffect(player, MobEffects.FIRE_RESISTANCE, 40, 0);
            case "flux" -> addEffect(player, MobEffects.HASTE, 40, amplifier);
            case "life" -> addEffect(player, MobEffects.REGENERATION, 40, 0);
            case "puff" -> addEffect(player, MobEffects.SLOW_FALLING, 40, 0);
            case "speed" -> addEffect(player, MobEffects.SPEED, 40, amplifier);
            case "strength" -> addEffect(player, MobEffects.STRENGTH, 40, 0);
            case "wealth" -> addEffect(player, MobEffects.LUCK, 40, amplifier);
            default -> { }
        }
    }

    private static HeldGem findHeldGem(ServerPlayer player) {
        HeldGem main = parseGem(player.getMainHandItem());
        return main != null ? main : parseGem(player.getOffhandItem());
    }

    private static HeldGem parseGem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.has(DataComponents.CUSTOM_NAME)) return null;
        String customName = stack.getHoverName().getString();
        for (Gem gem : GEMS.values()) {
            String prefix = "Bliss Gem: " + gem.displayName() + " [T";
            if (customName.startsWith(prefix) && customName.endsWith("]")) {
                String tierText = customName.substring(prefix.length(), customName.length() - 1);
                try {
                    int tier = Integer.parseInt(tierText);
                    if (tier == 1 || tier == 2) return new HeldGem(gem, tier);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private record Gem(String id, String displayName, Item icon) { }
    private record HeldGem(Gem gem, int tier) { }
}
