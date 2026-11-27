package kr.simtaehyeon.reality;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class RealityMinecraft implements ModInitializer {
    public static final String MOD_ID = "reality_minecraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final Map<UUID, SurvivalState> STATES = new HashMap<>();
    private static long tickCounter = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("REALITY MINECRAFT 0.1.0 is starting.");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                SurvivalState state = STATES.computeIfAbsent(player.getUUID(), id -> new SurvivalState());
                updateSurvival(player, state);
                checkStonecutter(player);
            }
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return true;
            if (state.is(net.minecraft.tags.BlockTags.LOGS) && serverPlayer.getMainHandItem().isEmpty()) {
                hurt(serverPlayer, 1.0F);
                serverPlayer.displayClientMessage(Component.literal("§c맨손으로 나무를 캐다가 손을 다쳤다!"), true);
            }
            return true;
        });
    }

    private static void updateSurvival(ServerPlayer player, SurvivalState state) {
        if (player.isCreative() || player.isSpectator()) return;
        if (tickCounter % 20 != 0) return;

        state.thirst = Math.max(0, state.thirst - 1);
        state.fatigue = Math.min(100, state.fatigue + 1);
        if (player.isInWaterOrRain()) state.temperature = Math.max(0, state.temperature - 1);
        else if (player.level().isDay()) state.temperature = Math.min(100, state.temperature + 1);
        else state.temperature = Math.max(0, state.temperature - 1);

        if (state.thirst == 0 && tickCounter % 100 == 0) hurt(player, 1.0F);
        if (state.fatigue >= 100 && tickCounter % 100 == 0) {
            player.displayClientMessage(Component.literal("§e너무 피곤하다... 잠시 쉬는 것이 좋겠다."), true);
        }
        String bar = "§b갈증 " + state.thirst + " §7| §e피로 " + state.fatigue
                + " §7| §c체온 " + state.temperature;
        player.displayClientMessage(Component.literal(bar), true);
    }

    private static void checkStonecutter(ServerPlayer player) {
        if (!player.onGround() || tickCounter % 20 != 0) return;
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.15, player.getZ());
        BlockState block = player.level().getBlockState(below);
        if (block.is(Blocks.STONECUTTER)) {
            hurt(player, 1.0F);
            player.displayClientMessage(Component.literal("§c석재 절단기에 올라서서 발을 다쳤다!"), true);
        }
    }

    private static void hurt(ServerPlayer player, float amount) {
        if (player.level() instanceof ServerLevel level) {
            player.hurtServer(level, player.damageSources().generic(), amount);
        }
    }

    private static final class SurvivalState {
        int thirst = 100;
        int fatigue = 0;
        int temperature = 50;
    }
}
