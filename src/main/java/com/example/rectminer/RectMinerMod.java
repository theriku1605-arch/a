```java
package com.example.rectminer;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import cpw.mods.fml.client.registry.ClientRegistry;

@Mod(
    modid = RectMinerMod.MODID,
    name = "RectMiner",
    version = "0.3.2",
    clientSideOnly = true
)
public class RectMinerMod {

    public static final String MODID = "rectminer";

    private static final Minecraft MC = Minecraft.getMinecraft();

    // RectMiner
    private static KeyBinding pointAKey;
    private static KeyBinding pointBKey;
    private static KeyBinding minerToggleKey;

    private static boolean miningEnabled = false;
    private static boolean pointASet = false;
    private static boolean pointBSet = false;

    private static int pointAX, pointAY, pointAZ;
    private static int pointBX, pointBY, pointBZ;

    private static final List<BlockPos> targets =
        new ArrayList<BlockPos>();

    private static int targetIndex = 0;

    // AutoKiller
    private static KeyBinding autoKillerKey;
    private static boolean autoKillerEnabled = false;

    private static EntityLivingBase mobTarget = null;

    private static final double MOB_SEARCH_RANGE = 100.0D;
    private static final double MOB_ATTACK_RANGE = 3.0D;
    private static final double MOB_STOP_DISTANCE = 2.0D;

    private static final float MAX_TURN_PER_TICK = 15.0F;
    private static final float AIM_TOLERANCE = 12.0F;

    // AutoFeed
    private static KeyBinding autoFeedKey;
    private static boolean autoFeedEnabled = false;
    private static int autoFeedTicks = 0;

    // 10 minutos y 30 segundos a 20 ticks por segundo.
    private static final int AUTO_FEED_INTERVAL = 12600;

    // Parada de emergencia
    private static KeyBinding emergencyStopKey;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        pointAKey = new KeyBinding(
            "key.rectminer.pointa",
            Keyboard.KEY_NUMPAD1,
            "key.categories.rectminer"
        );

        pointBKey = new KeyBinding(
            "key.rectminer.pointb",
            Keyboard.KEY_NUMPAD2,
            "key.categories.rectminer"
        );

        minerToggleKey = new KeyBinding(
            "key.rectminer.toggle",
            Keyboard.KEY_NUMPAD3,
            "key.categories.rectminer"
        );

        autoKillerKey = new KeyBinding(
            "key.rectminer.autokiller",
            Keyboard.KEY_NUMPAD4,
            "key.categories.rectminer"
        );

        autoFeedKey = new KeyBinding(
            "key.rectminer.autofeed",
            Keyboard.KEY_NUMPAD5,
            "key.categories.rectminer"
        );

        emergencyStopKey = new KeyBinding(
            "key.rectminer.emergency",
            Keyboard.KEY_NUMPAD0,
            "key.categories.rectminer"
        );

        ClientRegistry.registerKeyBinding(pointAKey);
        ClientRegistry.registerKeyBinding(pointBKey);
        ClientRegistry.registerKeyBinding(minerToggleKey);
        ClientRegistry.registerKeyBinding(autoKillerKey);
        ClientRegistry.registerKeyBinding(autoFeedKey);
        ClientRegistry.registerKeyBinding(emergencyStopKey);

        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (MC.thePlayer == null || MC.theWorld == null) {
            releaseMovement();
            return;
        }

        handleKeys();
        tickAutoFeed();

        if (autoKillerEnabled) {
            tickAutoKiller();
        } else if (miningEnabled) {
            tickRectMiner();
        } else {
            releaseMovement();
        }
    }

    private void handleKeys() {
        if (pointAKey.isPressed()) {
            setPointA();
        }

        if (pointBKey.isPressed()) {
            setPointB();
        }

        if (minerToggleKey.isPressed()) {
            if (miningEnabled) {
                stopMining();
                message("RectMiner pausado.");
            } else if (pointASet && pointBSet) {
                startMining();
            } else {
                message("Primero selecciona A con Numpad 1 y B con Numpad 2.");
            }
        }

        if (autoKillerKey.isPressed()) {
            autoKillerEnabled = !autoKillerEnabled;

            if (autoKillerEnabled) {
                stopMining();
                mobTarget = null;
                message("AutoKiller activado. Buscando mobs hasta 100 bloques.");
            } else {
                mobTarget = null;
                releaseMovement();
                message("AutoKiller desactivado.");
            }
        }

        if (autoFeedKey.isPressed()) {
            autoFeedEnabled = !autoFeedEnabled;
            autoFeedTicks = 0;

            if (autoFeedEnabled) {
                message("AutoFeed activado. /feed se enviará en 10 min 30 s.");
            } else {
                message("AutoFeed desactivado.");
            }
        }

        if (emergencyStopKey.isPressed()) {
            stopEverything();
        }
    }

    private void setPointA() {
        BlockPos pos = aimedBlock();

        if (pos == null) {
            message("Apunta a un bloque para seleccionar A.");
            return;
        }

        pointAX = pos.x;
        pointAY = pos.y;
        pointAZ = pos.z;
        pointASet = true;

        message("Punto A: " + pointAX + ", " + pointAY + ", " + pointAZ);
    }

    private void setPointB() {
        BlockPos pos = aimedBlock();

        if (pos == null) {
            message("Apunta a un bloque para seleccionar B.");
            return;
        }

        pointBX = pos.x;
        pointBY = pos.y;
        pointBZ = pos.z;
        pointBSet = true;

        message("Punto B: " + pointBX + ", " + pointBY + ", " + pointBZ);
    }

    private BlockPos aimedBlock() {
        MovingObjectPosition hit = MC.objectMouseOver;

        if (hit == null ||
            hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return null;
        }

        return new BlockPos(
            hit.getBlockPos().getX(),
            hit.getBlockPos().getY(),
            hit.getBlockPos().getZ()
        );
    }

    private void startMining() {
        buildZigzagTargets();

        if (targets.isEmpty()) {
            message("No hay bloques seleccionados para minar.");
            return;
        }

        targetIndex = 0;
        miningEnabled = true;
        message("RectMiner iniciado. Objetivos: " + targets.size());
    }

    private void stopMining() {
        miningEnabled = false;
        releaseMovement();
    }

    /*
     * Construye una ruta en zigzag dentro del rectángulo seleccionado.
     * Cada columna incluye el bloque inferior y el superior.
     */
    private void buildZigzagTargets() {
        targets.clear();

        int minX = Math.min(pointAX, pointBX);
        int maxX = Math.max(pointAX, pointBX);
        int minZ = Math.min(pointAZ, pointBZ);
        int maxZ = Math.max(pointAZ, pointBZ);
        int baseY = Math.min(pointAY, pointBY);

        boolean reverseZ = false;

        for (int x = minX; x <= maxX; x++) {
            if (!reverseZ) {
                for (int z = minZ; z <= maxZ; z++) {
                    targets.add(new BlockPos(x, baseY, z));
                    targets.add(new BlockPos(x, baseY + 1, z));
                }
            } else {
                for (int z = maxZ; z >= minZ; z--) {
                    targets.add(new BlockPos(x, baseY, z));
                    targets.add(new BlockPos(x, baseY + 1, z));
                }
            }

            reverseZ = !reverseZ;
        }
    }

    private void tickRectMiner() {
        if (targets.isEmpty() || targetIndex >= targets.size()) {
            stopMining();
            message("RectMiner terminó la ruta.");
            return;
        }

        BlockPos target = targets.get(targetIndex);

        double dx = target.x + 0.5D - MC.thePlayer.posX;
        double dz = target.z + 0.5D - MC.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);

        float desiredYaw = (float) (
            Math.atan2(dz, dx) * 180.0D / Math.PI
        ) - 90.0F;

        MC.thePlayer.rotationYaw = approachAngle(
            MC.thePlayer.rotationYaw,
            desiredYaw,
            10.0F
        );

        if (distance > 1.4D) {
            MC.gameSettings.keyBindForward.pressed = true;
        } else {
            MC.gameSettings.keyBindForward.pressed = false;
            targetIndex++;
        }

        MC.gameSettings.keyBindSprint.pressed = false;
    }

    private void tickAutoKiller() {
        if (mobTarget == null ||
            !mobTarget.isEntityAlive() ||
            MC.thePlayer.getDistanceToEntity(mobTarget) > MOB_SEARCH_RANGE) {
            mobTarget = findNearestMob();
        }

        if (mobTarget == null) {
            releaseMovement();
            return;
        }

        faceEntity(mobTarget);

        double distance = MC.thePlayer.getDistanceToEntity(mobTarget);

        if (distance > MOB_STOP_DISTANCE) {
            MC.gameSettings.keyBindForward.pressed = true;
            MC.gameSettings.keyBindSprint.pressed = true;
        } else {
            MC.gameSettings.keyBindForward.pressed = false;
            MC.gameSettings.keyBindSprint.pressed = false;
        }

        if (distance <= MOB_ATTACK_RANGE && isAimedAt(mobTarget)) {
            MC.playerController.attackEntity(MC.thePlayer, mobTarget);
        }
    }

    private EntityLivingBase findNearestMob() {
        List<?> entities = MC.theWorld.getEntitiesWithinAABB(
            EntityLivingBase.class,
            MC.thePlayer.boundingBox.expand(
                MOB_SEARCH_RANGE,
                MOB_SEARCH_RANGE,
                MOB_SEARCH_RANGE
            )
        );

        EntityLivingBase nearest = null;
        double nearestDistance = MOB_SEARCH_RANGE * MOB_SEARCH_RANGE;

        for (Object object : entities) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }

            EntityLivingBase entity = (EntityLivingBase) object;

            if (entity == MC.thePlayer ||
                !entity.isEntityAlive() ||
                entity instanceof EntityPlayer) {
                continue;
            }

            double distanceSq = MC.thePlayer.getDistanceSqToEntity(entity);

            if (distanceSq <= nearestDistance) {
                nearestDistance = distanceSq;
                nearest = entity;
            }
        }

        return nearest;
    }

    private void faceEntity(Entity entity) {
        double dx = entity.posX - MC.thePlayer.posX;
        double dz = entity.posZ - MC.thePlayer.posZ;

        double targetY = entity.posY + entity.getEyeHeight();
        double playerY = MC.thePlayer.posY + MC.thePlayer.getEyeHeight();
        double dy = targetY - playerY;

        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float desiredYaw = (float) (
            Math.atan2(dz, dx) * 180.0D / Math.PI
        ) - 90.0F;

        float desiredPitch = (float) (
            -Math.atan2(dy, horizontal) * 180.0D / Math.PI
        );

        MC.thePlayer.rotationYaw = approachAngle(
            MC.thePlayer.rotationYaw,
            desiredYaw,
            MAX_TURN_PER_TICK
        );

        MC.thePlayer.rotationPitch = approachAngle(
            MC.thePlayer.rotationPitch,
            desiredPitch,
            MAX_TURN_PER_TICK
        );
    }

    private boolean isAimedAt(Entity entity) {
        MovingObjectPosition hit = MC.objectMouseOver;

        if (hit == null ||
            hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return false;
        }

        return hit.entityHit == entity &&
            Math.abs(MC.thePlayer.rotationPitch) <= 90.0F &&
            Math.abs(MC.thePlayer.rotationYaw) <= 360.0F &&
            Math.abs(MC.thePlayer.rotationPitch) < 90.0F;
    }

    private void tickAutoFeed() {
        if (!autoFeedEnabled) {
            return;
        }

        autoFeedTicks++;

        if (autoFeedTicks >= AUTO_FEED_INTERVAL) {
            autoFeedTicks = 0;
            MC.thePlayer.sendChatMessage("/feed");
            message("AutoFeed: comando /feed enviado.");
        }
    }

    private void stopEverything() {
        miningEnabled = false;
        autoKillerEnabled = false;
        autoFeedEnabled = false;

        autoFeedTicks = 0;
        mobTarget = null;

        releaseMovement();
        message("Parada de emergencia: todos los sistemas desactivados.");
    }

    private void releaseMovement() {
        MC.gameSettings.keyBindForward.pressed = false;
        MC.gameSettings.keyBindBack.pressed = false;
        MC.gameSettings.keyBindLeft.pressed = false;
        MC.gameSettings.keyBindRight.pressed = false;
        MC.gameSettings.keyBindSprint.pressed = false;
        MC.gameSettings.keyBindJump.pressed = false;
        MC.gameSettings.keyBindAttack.pressed = false;
    }

    private float approachAngle(float current, float target, float maxChange) {
        float difference = wrapAngle(target - current);

        if (difference > maxChange) {
            difference = maxChange;
        }

        if (difference < -maxChange) {
            difference = -maxChange;
        }

        return current + difference;
    }

    private float wrapAngle(float angle) {
        while (angle <= -180.0F) {
            angle += 360.0F;
        }

        while (angle > 180.0F) {
            angle -= 360.0F;
        }

        return angle;
    }

    private void message(String text) {
        if (MC.thePlayer != null) {
            MC.thePlayer.addChatMessage(new ChatComponentText(text));
        }
    }

    private static class BlockPos {
        final int x;
        final int y;
        final int z;

        BlockPos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
```
