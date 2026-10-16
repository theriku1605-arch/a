
package com.example.rectminer;

import java.util.ArrayList;
import java.util.List;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

@Mod(
    modid = RectMinerMod.MODID,
    name = "RectMiner",
    version = "0.2.1",
    acceptableRemoteVersions = "*"
)
public class RectMinerMod {

    public static final String MODID = "rectminer";
    private static final Minecraft MC = Minecraft.getMinecraft();

    private KeyBinding setAKey;
    private KeyBinding setBKey;
    private KeyBinding toggleKey;
    private KeyBinding stopKey;

    private Pos pointA;
    private Pos pointB;
    private Pos currentTarget;

    private final List<Pos> targets = new ArrayList<Pos>();
    private int targetIndex;

    private boolean running;
    private int ticksWithoutProgress;

    private static final int MAX_BLOCKS = 12000;
    private static final int BLOCK_HEIGHT = 2;
    private static final int STUCK_TIMEOUT = 20 * 45;

    private static final double MINING_REACH = 4.5D;

    // Distancia horizontal a partir de la cual puede avanzar.
    private static final double WALK_DISTANCE = 0.4D;

    // Giro máximo por tick.
    private static final float MAX_TURN_PER_TICK = 12.0F;

    // No caminar si todavía está demasiado desalineado.
    private static final float WALK_ANGLE_TOLERANCE = 25.0F;

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

        stopKey = new KeyBinding(
            "key.rectminer.emergency_stop",
            Keyboard.KEY_NUMPAD0,
            "key.categories.rectminer"
        );

        ClientRegistry.registerKeyBinding(setAKey);
        ClientRegistry.registerKeyBinding(setBKey);
        ClientRegistry.registerKeyBinding(toggleKey);
        ClientRegistry.registerKeyBinding(stopKey);

        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (MC.thePlayer == null || MC.theWorld == null) {
            releaseControls();
            return;
        }

        if (stopKey.isPressed()) {
            stop("Parada de emergencia.");
            return;
        }

        if (setAKey.isPressed()) {
            Pos p = aimedBlock();

            if (p != null) {
                pointA = p;
                chat("Punto A: " + p);
            } else {
                chat("Mira un bloque para marcar el punto A.");
            }
        }

        if (setBKey.isPressed()) {
            Pos p = aimedBlock();

            if (p != null) {
                pointB = p;
                chat("Punto B: " + p);
            } else {
                chat("Mira un bloque para marcar el punto B.");
            }
        }

        if (toggleKey.isPressed()) {
            if (running) {
                stop("Recorrido pausado.");
            } else {
                start();
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !running) {
            return;
        }

        if (MC.thePlayer == null
            || MC.theWorld == null
            || MC.currentScreen != null) {
            stop("Detenido: jugador no disponible o menú abierto.");
            return;
        }

        if (pointA == null || pointB == null) {
            stop("Faltan los puntos A y B.");
            return;
        }

        if (!selectCurrentTarget()) {
            stop("Recorrido terminado: no quedan bloques pendientes.");
            return;
        }

        double px = MC.thePlayer.posX;
        double py = MC.thePlayer.posY
            + MC.thePlayer.getEyeHeight();
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
            -Math.atan2(dy, horizontalDistance)
            * 180.0D / Math.PI
        );

        // Calcular el giro más corto hacia el objetivo.
        float yawDifference = wrapAngle(
            targetYaw - MC.thePlayer.rotationYaw
        );

        // Girar gradualmente.
        float turn = yawDifference;

        if (turn > MAX_TURN_PER_TICK) {
            turn = MAX_TURN_PER_TICK;
        } else if (turn < -MAX_TURN_PER_TICK) {
            turn = -MAX_TURN_PER_TICK;
        }

        MC.thePlayer.rotationYaw += turn;
        MC.thePlayer.rotationPitch = targetPitch;

        // Calcular cuánto falta para quedar alineado DESPUÉS del giro.
        float remainingYaw = wrapAngle(
            targetYaw - MC.thePlayer.rotationYaw
        );

        boolean targetHit = isTargetUnderCrosshair();

        if (targetHit && distance <= MINING_REACH) {
            // El bloque está apuntado y al alcance: minar sin avanzar.
            setForward(false);
            setAttack(true);
        } else {
            setAttack(false);

            boolean facingTarget =
                Math.abs(remainingYaw) <= WALK_ANGLE_TOLERANCE;

            boolean needsToMove =
                horizontalDistance > WALK_DISTANCE;

            if (needsToMove && facingTarget) {
                // Avanzar únicamente hacia delante.
                setForward(true);
            } else {
                // Girar o apuntar sin caminar en una dirección incorrecta.
                setForward(false);
            }
        }

        ticksWithoutProgress++;

        if (ticksWithoutProgress > STUCK_TIMEOUT) {
            stop(
                "Parada de seguridad: el objetivo no avanzó "
                + "durante 45 segundos."
            );
        }
    }

    private float wrapAngle(float angle) {
        while (angle < -180.0F) {
            angle += 360.0F;
        }

        while (angle >= 180.0F) {
            angle -= 360.0F;
        }

        return angle;
    }

    private void start() {
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
            chat(
                "Área demasiado grande (" + volume
                + " bloques). Máximo: " + MAX_BLOCKS + "."
            );
            return;
        }

        buildZigzagTargets();

        targetIndex = 0;
        currentTarget = null;
        ticksWithoutProgress = 0;

        running = true;
        releaseControls();

        chat(
            "Zigzag iniciado: " + targets.size()
            + " posiciones. NUMPAD 3 pausa; NUMPAD 0 detiene."
        );
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

                // Orden original: bloque inferior y después superior.
                targets.add(new Pos(x, baseY, z));
                targets.add(new Pos(x, baseY + 1, z));
            }
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

            if (currentTarget == null
                || !currentTarget.equals(candidate)) {
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

        MovingObjectPosition hit =
            MC.thePlayer.rayTrace(MINING_REACH, 1.0F);

        return hit != null
            && hit.typeOfHit
                == MovingObjectPosition.MovingObjectType.BLOCK
            && hit.blockX == currentTarget.x
            && hit.blockY == currentTarget.y
            && hit.blockZ == currentTarget.z;
    }

    private boolean isMineable(World world, Pos p) {
        Block block = world.getBlock(p.x, p.y, p.z);

        return block != null
            && !block.isAir(world, p.x, p.y, p.z)
            && !block.getMaterial().isLiquid();
    }

    private Pos aimedBlock() {
        MovingObjectPosition hit =
            MC.thePlayer.rayTrace(5.0D, 1.0F);

        if (hit != null
            && hit.typeOfHit
                == MovingObjectPosition.MovingObjectType.BLOCK) {
            return new Pos(
                hit.blockX,
                hit.blockY,
                hit.blockZ
            );
        }

        return null;
    }

    private void setAttack(boolean pressed) {
        if (MC.gameSettings == null) {
            return;
        }

        KeyBinding.setKeyBindState(
            MC.gameSettings.keyBindAttack.getKeyCode(),
            pressed
        );
    }

    private void setForward(boolean pressed) {
        if (MC.gameSettings == null) {
            return;
        }

        KeyBinding.setKeyBindState(
            MC.gameSettings.keyBindForward.getKeyCode(),
            pressed
        );

        if (!pressed) {
            KeyBinding.setKeyBindState(
                MC.gameSettings.keyBindBack.getKeyCode(),
                false
            );

            KeyBinding.setKeyBindState(
                MC.gameSettings.keyBindLeft.getKeyCode(),
                false
            );

            KeyBinding.setKeyBindState(
                MC.gameSettings.keyBindRight.getKeyCode(),
                false
            );

            KeyBinding.setKeyBindState(
                MC.gameSettings.keyBindJump.getKeyCode(),
                false
            );
        }
    }

    private void releaseControls() {
        setAttack(false);
        setForward(false);
    }

    private void stop(String message) {
        running = false;
        currentTarget = null;
        targets.clear();
        targetIndex = 0;

        releaseControls();

        if (MC.thePlayer != null) {
            chat(message);
        }
    }

    private void chat(String message) {
        if (MC.thePlayer != null) {
            MC.thePlayer.addChatMessage(
                new ChatComponentText("[RectMiner] " + message)
            );
        }
    }

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
        public boolean equals(Object o) {
            if (!(o instanceof Pos)) {
                return false;
            }

            Pos p = (Pos) o;

            return x == p.x && y == p.y && z == p.z;
        }

        @Override
        public int hashCode() {
            return (x * 31 + y) * 31 + z;
        }
    }
}
