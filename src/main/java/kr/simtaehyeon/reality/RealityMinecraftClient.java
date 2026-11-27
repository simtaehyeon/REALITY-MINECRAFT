package kr.simtaehyeon.reality;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class RealityMinecraftClient implements ClientModInitializer {
    private static volatile int thirst = 100;
    private static volatile int fatigue = 0;
    private static volatile int bodyTemperatureTenths = 365;
    private static volatile int heat = 0;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SurvivalHudPayload.TYPE, (payload, context) -> {
            thirst = payload.thirst();
            fatigue = payload.fatigue();
            bodyTemperatureTenths = payload.bodyTemperatureTenths();
            heat = payload.heat();
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath(RealityMinecraft.MOD_ID, "survival_status_hud"),
                RealityMinecraftClient::render
        );
    }

    private static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gui.hud.isHidden()) return;

        int left = 6;
        int top = 6;
        int width = 148;
        int height = 68;

        graphics.fill(left, top, left + width, top + height, 0xAA101820);
        graphics.fill(left, top, left + width, top + 2, 0xFF45B6C8);

        drawStat(graphics, minecraft, "갈증", thirst, 100, left + 7, top + 8, 0xFF55D9F2);
        drawStat(graphics, minecraft, "피로", fatigue, 100, left + 7, top + 22, 0xFFFFD166);

        double bodyTemperature = bodyTemperatureTenths / 10.0;
        int temperatureColor = bodyTemperature >= 39.0 || bodyTemperature <= 35.0
                ? 0xFFFF5555 : 0xFFFFF1D0;
        graphics.text(minecraft.font,
                String.format(java.util.Locale.ROOT, "체온  %.1f°C", bodyTemperature),
                left + 7, top + 36, temperatureColor, true);

        graphics.text(minecraft.font, "열기  " + heat + "/100",
                left + 7, top + 50, 0xFFFFA45B, true);

        // 열과 피로가 위험해지면 왼쪽 HUD의 표시 색으로 즉시 경고한다.
        if (fatigue >= 70) {
            graphics.fill(left + width - 5, top + 22, left + width - 2, top + 31, 0xFFFFD166);
        }
        if (bodyTemperature >= 39.0 || bodyTemperature <= 35.0) {
            graphics.fill(left + width - 5, top + 36, left + width - 2, top + 45, 0xFFFF5555);
        }
    }

    private static void drawStat(GuiGraphicsExtractor graphics, Minecraft minecraft,
                                 String label, int value, int max, int x, int y, int color) {
        int clamped = Math.max(0, Math.min(max, value));
        graphics.text(minecraft.font, label + "  " + clamped + "/" + max, x, y, color, true);
        int barLeft = x + 73;
        int barTop = y + 3;
        int barWidth = 60;
        graphics.fill(barLeft, barTop, barLeft + barWidth, barTop + 5, 0xFF303840);
        int filled = barWidth * clamped / max;
        if (filled > 0) {
            graphics.fill(barLeft, barTop, barLeft + filled, barTop + 5, color);
        }
    }
}
