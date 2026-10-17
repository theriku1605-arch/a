package com.example.rectminer;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.FMLCommonHandler;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

@Mod(
    modid = RectMinerMod.MODID,
    name = "RectMiner",
    version = "0.3.2",
    acceptableRemoteVersions = "*"
)
public class RectMinerMod {

    public static final String MODID = "rectminer";

    private static final Minecraft MC = Minecraft.getMinecraft();

    // =========================================================
    // TECLAS
    // =========================================================

    private KeyBinding setAKey;
    private KeyBinding setBKey;
    private KeyBinding toggleKey;
    private KeyBinding autoKillerKey;
    private KeyBinding autoFeedKey;
    private KeyBinding stopKey;

    // =========================================================
    // RECTMINER
    // =========================================================

    private boolean running = false;

    private Pos pointA;
    private Pos pointB;
    private Pos currentTarget;

    private final List<Pos> targets = new ArrayList<Pos>();
    private int targetIndex = 0;
    private int ticksWithoutProgress = 0;

    private static final int MAX_BLOCKS = 12000;
    private static final int BLOCK_HEIGHT = 2;
    private static final int STUCK_TIMEOUT = 20 * 45;

    private static final double MINING_REACH = 1.5D;
    private static final double WALK_DISTANCE = 1.4D;
    private static final float MAX_TURN_PER_TICK = 12.0F;
    private static final float WALK_ANGLE_TOLERANCE = 25.0F;

    // =========================================================
    // AUTOKILLER
    // =========================================================

    private boolean autoKillerRunning = false;
    private EntityLivingBase mobTarget;

    private static final double MOB_SEARCH_RANGE = 100.0D;
    private static final double MOB_ATTACK_RANGE = 3.0D;
    private static final double MOB_APPROACH_DISTANCE = 2.0D;

    private static final float MOB_MAX_TURN_PER_TICK = 15.0F;
    private static final float MOB_AIM_TOLERANCE = 12.0F;

    // =========================================================
    // AUTOFEED
    // =========================================================

    private boolean autoFeedRunning = false;
    private int autoFeedTicks = 0;

    // 10 minutos y 30 segundos a 20 ticks por segundo.
    private static final int AUTO_FEED_INTERVAL = 12600;

    // =========================================================
    // INICIALIZACIÓN
    // =========================================================

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        setAKey = new KeyBinding(
            "key.rectminer.set_a",
            Keyboard.KEY_NUMPAD1,
            "key.categories.rectminer"
        );

        setBKey = new KeyBinding(
            "key.rectminer.set_b",
            Keyboard.KEY_NUMPAD2,
            "key.categories.rectminer"
        );

        toggleKey = new KeyBinding(
            "key.rectminer.start_stop",
            Keyboard.KEY_NUMPAD3,
            "key.categories.rectminer"
        );

        autoKillerKey = new KeyBinding(
            "key.rectminer.auto_killer",
            Keyboard.KEY_NUMPAD4,
            "key.categories.rectminer"
        );

        autoFeedKey = new KeyBinding(
            "key.rectminer.auto_feed",
            Keyboard.KEY_NUMPAD5,
            "key.categories.rectminer"
        );

        stopKey = new KeyBinding(
            "key.rectminer.emergency_stop",
            Keyboard.KEY_NUMPAD0,
            "key.categories.rectminer"
        );

        ClientRegistry.registerKeyBinding(setAKey);
        ClientRegistry.registerKeyBinding(setBKey);
        ClientRegistry.registerKeyBinding(toggleKey);
        ClientRegistry.registerKeyBinding(autoKillerKey);
        ClientRegistry.registerKeyBinding(autoFeedKey);
        ClientRegistry.registerKeyBinding(stopKey);

        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    // =========================================================
    // TECLADO
    // =========================================================

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {

        if (MC.thePlayer == null || MC.theWorld == null) {
            return;
        }

        if (stopKey.isPressed()) {
            emergencyStop();
            return;
        }

        if (setAKey.isPressed()) {
            Pos pos = aimedBlock();

            if (pos != null) {
                pointA = pos;
                chat("Punto A: " + pos);
            } else {
                chat("Mira un bloque para marcar el punto A.");
            }
        }

        if (setBKey.isPressed()) {
            Pos pos = aimedBlock();

            if (pos != null) {
                pointB = pos;
                chat("Punto B: " + pos);
            } else {
                chat("Mira un bloque para marcar el punto B.");
            }
        }

        if (toggleKey.isPressed()) {
            if (autoKillerRunning) {
                chat("Desactiva AutoKiller con NUMPAD 4 antes de minar.");
            } else if (running) {
                stopMining("RectMiner pausado.");
            } else {
                startMining();
            }
        }

        if (autoKillerKey.isPressed()) {
            toggleAutoKiller();
        }

        if (autoFeedKey.isPressed()) {
            toggleAutoFeed();
        }
    }

    // =========================================================
    // TICK DEL CLIENTE
    // =========================================================

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (MC.thePlayer == null || MC.theWorld == null) {
            releaseControls();
            return;
        }

        tickAutoFeed();

        if (MC.currentScreen != null) {
            releaseControls();
            return;
        }

        if (autoKillerRunning) {
            tickAutoKiller();
        } else if (running) {
            tickRectMiner();
        }
    }

    // =========================================================
    // RECTMINER
    // =========================================================

    private void startMining() {

        if (pointA == null || pointB == null) {
            chat("Marca A con NUMPAD 1 y B con NUMPAD 2 primero.");
            return;
        }

        int minX = Math.min(pointA.x, pointB.x);
        int maxX = Math.max(pointA.x, pointB.x);
        int minZ = Math.min(pointA.z, pointB.z);
        int maxZ = Math.max(pointA.z, pointB.z);

        long width = (long) maxX - minX + 1L;
        long length = (long) maxZ - minZ + 1L;
        long volume = width * length * BLOCK_HEIGHT;

        if (volume > MAX_BLOCKS) {
            chat("Área demasiado grande: " + volume
                + " bloques. Máximo: " + MAX_BLOCKS + ".");
            return;
        }

        buildZigzagTargets();

        targetIndex = 0;
        currentTarget = null;
        ticksWithoutProgress = 0;
        running = true;

        releaseControls();

        chat("Zigzag iniciado: " + targets.size()
            + " posiciones. NUMPAD 3 pausa.");
    }

    private void buildZigzagTargets() {

        targets.clear();

        int minX = Math.min(pointA.x, pointB.x);
        int maxX = Math.max(pointA.x, pointB.x);
        int minZ = Math.min(pointA.z, pointB.z);
        int maxZ = Math.max(pointA.z, pointB.z);

        int baseY = Math.min(pointA.y, pointB.y);

        int xStart = pointA.x;
        int xDirection = pointA.x <= pointB.x ? 1 : -1;

        int zStart = pointA.z;
        int zDirection = pointA.z <= pointB.z ? 1 : -1;

        int rowCount = maxZ - minZ + 1;
        int columnCount = maxX - minX + 1;

        // Se conserva el orden de recorrido original.
        for (int row = 0; row < rowCount; row++) {

            int z = zStart + row * zDirection;
            boolean forwardRow = row % 2 == 0;

            int rowXStart = forwardRow
                ? xStart
                : xStart + (columnCount - 1) * xDirection;

            int rowXDirection = forwardRow
                ? xDirection
                : -xDirection;

            for (int col = 0; col < columnCount; col++) {

                int x = rowXStart + col * rowXDirection;

                targets.add(new Pos(x, baseY, z));
                targets.add(new Pos(x, baseY + 1, z));
            }
        }
    }

    private void tickRectMiner() {

        if (!selectCurrentTarget()) {
            stopMining("Recorrido terminado: no quedan bloques pendientes.");
            return;
        }

        double px = MC.thePlayer.posX;
        double py = MC.thePlayer.posY + MC.thePlayer.getEyeHeight();
        double pz = MC.thePlayer.posZ;

        double tx = currentTarget.x + 0.5D;
        double ty = currentTarget.y + 0.5D;
        double tz = currentTarget.z + 0.5D;

        double dx = tx - px;
        double dy = ty - py;
        double dz = tz - pz;

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double distance = Math.sqrt(
            dx * dx + dy * dy + dz * dz
        );

        float targetYaw = (float) (
            Math.atan2(dz, dx) * 180.0D / Math.PI
        ) - 90.0F;

        float targetPitch = (float) (
            -Math.atan2(dy, horizontalDistance) * 180.0D / Math.PI
        );

        float yawDifference = wrapAngle(
            targetYaw - MC.thePlayer.rotationYaw
        );

        MC.thePlayer.rotationYaw += clampTurn(
            yawDifference,
            MAX_TURN_PER_TICK
        );

        MC.thePlayer.rotationPitch = targetPitch;

        float remainingYaw = wrapAngle(
            targetYaw - MC.thePlayer.rotationYaw
        );

        boolean targetHit = isTargetUnderCrosshair();

        if (targetHit && distance <= MINING_REACH) {
            setForward(false);
            setAttack(true);
        } else {
            setAttack(false);

            if (horizontalDistance > WALK_DISTANCE
                && Math.abs(remainingYaw) <= WALK_ANGLE_TOLERANCE) {
                setForward(true);
            } else {
                setForward(false);
            }
        }

        setSprint(false);

        ticksWithoutProgress++;

        if (ticksWithoutProgress > STUCK_TIMEOUT) {
            stopMining(
                "Parada de seguridad: el objetivo no avanzó durante 45 segundos."
            );
        }
    }

    private boolean selectCurrentTarget() {

        World world = MC.theWorld;

        while (targetIndex < targets.size()) {

            Pos candidate = targets.get(targetIndex);

            if (!isMineable(world, candidate)) {
                targetIndex++;
                currentTarget = null;
                ticksWithoutProgress = 0;
                continue;
            }

            if (currentTarget == null || !currentTarget.equals(candidate)) {
                currentTarget = candidate;
                ticksWithoutProgress = 0;
                setAttack(false);
                chat("Objetivo: " + currentTarget);
            }

            return true;
        }

        currentTarget = null;
        return false;
    }

    private boolean isTargetUnderCrosshair() {

        if (currentTarget == null || MC.thePlayer == null) {
            return false;
        }

        MovingObjectPosition hit = MC.thePlayer.rayTrace(
            MINING_REACH,
            1.0F
        );

        return hit != null
            && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && hit.blockX == currentTarget.x
            && hit.blockY == currentTarget.y
            && hit.blockZ == currentTarget.z;
    }

    private boolean isMineable(World world, Pos pos) {

        Block block = world.getBlock(pos.x, pos.y, pos.z);

        return block != null
            && !block.isAir(world, pos.x, pos.y, pos.z)
            && !block.getMaterial().isLiquid();
    }

    private Pos aimedBlock() {

        MovingObjectPosition hit = MC.thePlayer.rayTrace(5.0D, 1.0F);

        if (hit != null
            && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return new Pos(hit.blockX, hit.blockY, hit.blockZ);
        }

        return null;
    }

    private void stopMining(String message) {

        running = false;
        currentTarget = null;

        targets.clear();
        targetIndex = 0;

        releaseControls();
        chat(message);
    }

    // =========================================================
    // AUTOKILLER
    // =========================================================

    private void toggleAutoKiller() {

        if (autoKillerRunning) {
            autoKillerRunning = false;
            mobTarget = null;
            releaseControls();
            chat("AutoKiller desactivado.");
            return;
        }

        if (running) {
            stopMining("RectMiner pausado para activar AutoKiller.");
        }

        autoKillerRunning = true;
        mobTarget = null;
        releaseControls();

        chat("AutoKiller activado. Buscando mobs a 100 bloques.");
    }

    private void tickAutoKiller() {

        if (!isValidMobTarget(mobTarget)) {
            mobTarget = findNearestMob();
        }

        if (mobTarget == null) {
            setAttack(false);
            setForward(false);
            setSprint(false);
            return;
        }

        double dx = mobTarget.posX - MC.thePlayer.posX;
        double dz = mobTarget.posZ - MC.thePlayer.posZ;

        double dy = mobTarget.posY + mobTarget.height * 0.5D
            - (MC.thePlayer.posY + MC.thePlayer.getEyeHeight());

        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double distance = MC.thePlayer.getDistanceToEntity(mobTarget);

        float targetYaw = (float) (
            Math.atan2(dz, dx) * 180.0D / Math.PI
        ) - 90.0F;

        float targetPitch = (float) (
            -Math.atan2(dy, horizontalDistance) * 180.0D / Math.PI
        );

        float yawDifference = wrapAngle(
            targetYaw - MC.thePlayer.rotationYaw
        );

        float pitchDifference = wrapAngle(
            targetPitch - MC.thePlayer.rotationPitch
        );

        MC.thePlayer.rotationYaw += clampTurn(
            yawDifference,
            MOB_MAX_TURN_PER_TICK
        );

        MC.thePlayer.rotationPitch += clampTurn(
            pitchDifference,
            MOB_MAX_TURN_PER_TICK
        );

        float remainingYaw = wrapAngle(
            targetYaw - MC.thePlayer.rotationYaw
        );

        float remainingPitch = wrapAngle(
            targetPitch - MC.thePlayer.rotationPitch
        );

        boolean aligned =
            Math.abs(remainingYaw) <= MOB_AIM_TOLERANCE
            && Math.abs(remainingPitch) <= MOB_AIM_TOLERANCE;

        boolean targetUnderCrosshair =
            MC.objectMouseOver != null
            && MC.objectMouseOver.typeOfHit
                == MovingObjectPosition.MovingObjectType.ENTITY
            && MC.objectMouseOver.entityHit == mobTarget;

        boolean inAttackRange = distance <= MOB_ATTACK_RANGE;

        if (inAttackRange && aligned && targetUnderCrosshair) {

            setForward(false);
            setSprint(false);
            setAttack(true);

        } else {

            setAttack(false);

            if (distance > MOB_APPROACH_DISTANCE) {
                setForward(true);
                setSprint(true);
            } else {
                setForward(false);
                setSprint(false);
            }
        }
    }

    private boolean isValidMobTarget(EntityLivingBase entity) {

        if (entity == null
            || entity.isDead
            || !entity.isEntityAlive()
            || entity instanceof EntityPlayer) {
            return false;
        }

        return MC.thePlayer.getDistanceSqToEntity(entity)
            <= MOB_SEARCH_RANGE * MOB_SEARCH_RANGE;
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

        double nearestDistanceSq =
            MOB_SEARCH_RANGE * MOB_SEARCH_RANGE;

        for (Object object : entities) {

            if (!(object instanceof EntityLivingBase)) {
                continue;
            }

            EntityLivingBase entity = (EntityLivingBase) object;

            if (entity == MC.thePlayer
                || entity instanceof EntityPlayer
                || entity.isDead
                || !entity.isEntityAlive()) {
                continue;
            }

            double distanceSq =
                MC.thePlayer.getDistanceSqToEntity(entity);

            if (distanceSq <= nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = entity;
            }
        }

        return nearest;
    }

    // =========================================================
    // AUTOFEED
    // =========================================================

    private void toggleAutoFeed() {

        autoFeedRunning = !autoFeedRunning;
        autoFeedTicks = 0;

        if (autoFeedRunning) {
            chat("AutoFeed ACTIVADO. /feed cada 10 min 30 s.");
            chat("Primer envío en 10 minutos y 30 segundos.");
        } else {
            chat("AutoFeed DESACTIVADO.");
        }
    }

    private void tickAutoFeed() {

        if (!autoFeedRunning) {
            return;
        }

        autoFeedTicks++;

        if (autoFeedTicks >= AUTO_FEED_INTERVAL) {

            autoFeedTicks = 0;

            MC.thePlayer.sendChatMessage("/feed");

            chat("AutoFeed: comando /feed enviado.");
        }
    }

    // =========================================================
    // PARADA Y CONTROLES
    // =========================================================

    private void emergencyStop() {

        running = false;
        autoKillerRunning = false;
        autoFeedRunning = false;

        autoFeedTicks = 0;

        currentTarget = null;
        mobTarget = null;

        targets.clear();
        targetIndex = 0;

        releaseControls();

        chat("Parada de emergencia: RectMiner, AutoKiller y AutoFeed detenidos.");
    }

    private void setAttack(boolean pressed) {

        if (MC.gameSettings != null) {
            MC.gameSettings.keyBindAttack.pressed = pressed;
        }
    }

    private void setForward(boolean pressed) {

        if (MC.gameSettings != null) {
            MC.gameSettings.keyBindForward.pressed = pressed;
        }
    }

    private void setSprint(boolean pressed) {

        if (MC.gameSettings != null) {
            MC.gameSettings.keyBindSprint.pressed = pressed;
        }
    }

    private void releaseControls() {

        if (MC.gameSettings == null) {
            return;
        }

        MC.gameSettings.keyBindForward.pressed = false;
        MC.gameSettings.keyBindBack.pressed = false;
        MC.gameSettings.keyBindLeft.pressed = false;
        MC.gameSettings.keyBindRight.pressed = false;
        MC.gameSettings.keyBindSprint.pressed = false;
        MC.gameSettings.keyBindJump.pressed = false;
        MC.gameSettings.keyBindAttack.pressed = false;
    }

    // =========================================================
    // UTILIDADES
    // =========================================================

    private float clampTurn(float angle, float maxTurn) {

        if (angle > maxTurn) {
            return maxTurn;
        }

        if (angle < -maxTurn) {
            return -maxTurn;
        }

        return angle;
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

    private void chat(String message) {

        if (MC.thePlayer != null) {
            MC.thePlayer.addChatMessage(
                new ChatComponentText("[RectMiner] " + message)
            );
        }
    }

    // =========================================================
    // POSICIÓN DE BLOQUE
    // =========================================================

    private static final class Pos {

        final int x;
        final int y;
        final int z;

        Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String toString() {
            return x + ", " + y + ", " + z;
        }

        @Override
        public boolean equals(Object object) {

            if (!(object instanceof Pos)) {
                return false;
            }

            Pos pos = (Pos) object;

            return x == pos.x
                && y == pos.y
                && z == pos.z;
        }

        @Override
        public int hashCode() {
            return (x * 31 + y) * 31 + z;
        }
    }
}
