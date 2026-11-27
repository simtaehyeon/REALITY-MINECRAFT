package kr.simtaehyeon.reality;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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
                serverPlayer.sendSystemMessage(Component.literal("§c맨손으로 나무를 캐다가 손을 다쳤다!"));
            }
            return true;
        });
    }

    private static void updateSurvival(ServerPlayer player, SurvivalState state) {
        if (player.isCreative() || player.isSpectator() || tickCounter % 20 != 0) return;
        state.thirst = Math.max(0, state.thirst - 1);
        state.fatigue = Math.min(100, state.fatigue + 1);
        boolean wet = player.isInWaterOrRain();
        if (wet) state.temperature = Math.max(0, state.temperature - 1);
        else if (player.level().isBrightOutside()) state.temperature = Math.min(100, state.temperature + 1);
        else state.temperature = Math.max(0, state.temperature - 1);
        updateHeat(player, state, wet);
        if (state.thirst == 0 && tickCounter % 100 == 0) hurt(player, 1.0F);
        if (state.fatigue >= 100 && tickCounter % 100 == 0)
            player.sendSystemMessage(Component.literal("§e너무 피곤하다... 잠시 쉬는 것이 좋겠다."));
        String bar = "§b갈증 " + state.thirst + " §7| §e피로 " + state.fatigue
                + " §7| §c체온 " + state.temperature + " §7| §6열 " + state.heat;
        player.sendSystemMessage(Component.literal(bar));
    }

    // 열원과의 거리, 노출 시간, 직접 접촉, 방어구 착용 여부를 매초 계산한다.
    private static void updateHeat(ServerPlayer player, SurvivalState state, boolean wet) {
        BlockPos origin = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        double strongestHeat = 0.0;
        boolean touching = false;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -3, -4), origin.offset(4, 3, 4))) {
            double heat = heatValue(player.level().getBlockState(pos).getBlock());
            if (heat <= 0.0) continue;
            double distance = Math.sqrt(pos.distToCenterSqr(player.position()));
            if (distance <= 1.15) touching = true;
            strongestHeat = Math.max(strongestHeat, heat / (1.0 + distance * distance));
        }
        double protection = 1.0 - getHeatArmorProtection(player) * 0.12;
        double exposure = strongestHeat * protection;
        if (touching) exposure = Math.max(exposure, 8.0 * protection);
        if (wet) exposure *= 0.65;

        if (exposure > 0.35) {
            state.heat = Math.min(100, state.heat + Math.max(1, (int) Math.ceil(exposure)));
            state.temperature = Math.min(100, state.temperature + Math.max(1, (int) Math.ceil(exposure / 2.0)));
        } else state.heat = Math.max(0, state.heat - 3);

        if (touching || state.heat >= 70) {
            state.burnTicks += 20;
            if (state.burnTicks >= 40) {
                hurt(player, touching ? 2.0F : 1.0F);
                player.sendSystemMessage(Component.literal("§c화상을 입고 있다! 열원에서 멀어지거나 물에 들어가세요."));
                state.burnTicks = 0;
            }
        } else state.burnTicks = 0;

        if (state.heat >= 50 && tickCounter % 100 == 0)
            state.thirst = Math.max(0, state.thirst - 2);
    }

    private static double heatValue(Block block) {
        if (block == Blocks.LAVA) return 12.0;
        if (block == Blocks.FIRE || block == Blocks.SOUL_FIRE) return 9.0;
        if (block == Blocks.MAGMA_BLOCK) return 7.0;
        if (block == Blocks.CAMPFIRE || block == Blocks.SOUL_CAMPFIRE) return 6.0;
        if (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER) return 3.5;
        if (block == Blocks.TORCH || block == Blocks.WALL_TORCH
                || block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH) return 1.5;
        return 0.0;
    }

    // 현재는 방어구 한 부위마다 열 노출을 12% 줄이는 기초 보호 계산이다.
    private static int getHeatArmorProtection(ServerPlayer player) {
        int protection = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR
                    && !player.getItemBySlot(slot).isEmpty()) protection++;
        }
        return Math.min(4, protection);
    }

    private static void checkStonecutter(ServerPlayer player) {
        if (!player.onGround() || tickCounter % 20 != 0) return;
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.15, player.getZ());
        BlockState block = player.level().getBlockState(below);
        if (block.is(Blocks.STONECUTTER)) {
            hurt(player, 1.0F);
            player.sendSystemMessage(Component.literal("§c석재 절단기에 올라서서 발을 다쳤다!"));
        }
    }

    private static void hurt(ServerPlayer player, float amount) {
        if (player.level() instanceof ServerLevel level)
            player.hurtServer(level, player.damageSources().generic(), amount);
    }

    private static final class SurvivalState {
        int thirst = 100;
        int fatigue = 0;
        int temperature = 50;
        int heat = 0;
        int burnTicks = 0;
    }
}