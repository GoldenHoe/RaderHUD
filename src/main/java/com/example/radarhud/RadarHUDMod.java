package com.example.radarhud;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.Camera;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;

import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

@Environment(EnvType.CLIENT)
public class RadarHUDMod implements ClientModInitializer {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static KeyBinding lockTargetKey;
    private static LivingEntity lockedTarget = null;

    @Override
    public void onInitializeClient() {
        lockTargetKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.radarhud.lock_target",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.radarhud"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (lockTargetKey.wasPressed()) {
                lockedTarget = findLockableTarget(mc.player, 100);
            }
        });

        HudRenderCallback.EVENT.register((matrices, tickDelta) -> {
            if (lockedTarget != null && lockedTarget.isAlive()) {
                renderTargetHUD(matrices, lockedTarget, tickDelta);
            }
        });
    }

    private static LivingEntity findLockableTarget(PlayerEntity player, double maxRange) {
        if (player == null) return null;
        List<LivingEntity> targets = player.getWorld().getEntitiesByClass(LivingEntity.class,
            new Box(player.getPos().subtract(maxRange, maxRange, maxRange),
                   player.getPos().add(maxRange, maxRange, maxRange)),
            e -> e != player && e.isAlive() && player.canSee(e));
        return targets.stream()
            .min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
            .orElse(null);
    }

    private static void renderTargetHUD(MatrixStack matrices, LivingEntity target, float tickDelta) {
        Vec3d pos = target.getLerpedPos(tickDelta).add(0, target.getStandingEyeHeight() * 0.5, 0);
        Vec3d projected = projectToScreen(pos);
        if (projected == null) return;

        drawBox(matrices, (int) projected.x, (int) projected.y);

        Vec3d vel = target.getVelocity();
        double speed = vel.length();
        double altDiff = target.getY() - client.player.getY();

        Vec3d predicted = pos.add(vel.multiply(1.5));
        Vec3d predictedScreen = projectToScreen(predicted);

        TextRenderer font = client.textRenderer;
        font.draw(matrices, String.format("Speed: %.1f", speed), (float) projected.x + 10, (float) projected.y + 5, 0x00FF00);
        font.draw(matrices, String.format("Alt Δ: %.1f", altDiff), (float) projected.x + 10, (float) projected.y + 15, 0x00FF00);

        if (predictedScreen != null) {
            drawCross(matrices, (int) predictedScreen.x, (int) predictedScreen.y);
        }
    }

    private static void drawBox(MatrixStack matrices, int x, int y) {
        int s = 5;
        MinecraftClient.getInstance().textRenderer.draw(matrices, "+", x - 2, y - 4, 0xFF0000);
    }

    private static void drawCross(MatrixStack matrices, int x, int y) {
        MinecraftClient.getInstance().textRenderer.draw(matrices, "*", x - 2, y - 4, 0xFFFF00);
    }

    private static Vec3d projectToScreen(Vec3d pos) {
        Camera cam = client.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();
        Vec3d rel = pos.subtract(camPos);

        if (rel.lengthSquared() < 0.01) return null;

        double x = rel.x;
        double y = rel.y;
        double z = rel.z;

        if (z > 0) {
            int w = client.getWindow().getScaledWidth();
            int h = client.getWindow().getScaledHeight();
            double scale = 70 / z;
            return new Vec3d(w / 2 + x * scale, h / 2 - y * scale, 0);
        }
        return null;
    }
}