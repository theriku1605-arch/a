
package com.example.rectminer;

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
    version = "0.1.0",
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

    private boolean running;
    private int ticksWithoutProgress;
    private int targetTicks;

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
                stop("Minería pausada.");
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

        if (currentTarget == null
            || isAir(MC.theWorld, currentTarget)) {
            currentTarget = findNearestSolidInArea();
            targetTicks = 0;
            ticksWithoutProgress = 0;

            if (currentTarget == null) {
                stop("No quedan bloques sólidos detectables en la zona.");
                return;
            }
        }

        double px = MC.thePlayer.posX;
        double py = MC.thePlayer.posY
            + MC.thePlayer.getEyeHeight() * 0.65D;
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

        float yaw = (float) (
            Math.atan2(dz, dx) * 180.0D / Math.PI
        ) - 90.0F;

        float pitch = (float) (
            -Math.atan2(dy, horizontalDistance)
            * 180.0D / Math.PI
        );

        MC.thePlayer.rotationYaw = yaw;
        MC.thePlayer.rotationPitch = pitch;

        MovingObjectPosition hit =
            MC.thePlayer.rayTrace(5.0D, 1.0F);

        if (distance > 4.25D) {
            // Acercarse al objetivo sin mantener el ataque
            // como una tecla física.
            setForward(true);
            ticksWithoutProgress++;

        } else {
            setForward(false);

            if (hit != null
                && hit.typeOfHit
                    == MovingObjectPosition.MovingObjectType.BLOCK) {

                Pos hp = new Pos(
                    hit.blockX,
                    hit.blockY,
                    hit.blockZ
                );

                if (currentTarget.equals(hp)
                    && inArea(hp)
                    && !isAir(MC.theWorld, hp)) {

                    Block block = MC.theWorld.getBlock(
                        hp.x, hp.y, hp.z
                    );

                    if (block != null
                        && !block.getMaterial().isLiquid()) {

                        /*
                         * Mantener el mismo bloque seleccionado.
                         * onPlayerDamageBlock gestiona el avance
                         * de la rotura en el controlador vanilla.
                         */
                        MC.playerController.onPlayerDamageBlock(
                            hp.x, hp.y, hp.z, hit.sideHit
                        );

                        MC.thePlayer.swingItem();
                        targetTicks++;
                        ticksWithoutProgress++;
                    }
                }
            }
        }

        if (targetTicks > 20 * 30) {
            stop(
                "Objetivo atascado. Se detuvo por seguridad."
            );
            return;
        }

        if (ticksWithoutProgress > 20 * 45) {
            stop(
                "Parada de seguridad: sin progreso durante 45 segundos."
            );
        }
    }

    private void start() {
        if (pointA == null || pointB == null) {
            chat(
                "Marca A con NUMPAD 1 y B con NUMPAD 2 primero."
            );
            return;
        }

        long volume =
            ((long) Math.abs(pointA.x - pointB.x) + 1L)
            * ((long) Math.abs(pointA.y - pointB.y) + 1L)
            * ((long) Math.abs(pointA.z - pointB.z) + 1L);

        if (volume > 12000L) {
            chat(
                "Área demasiado grande (" + volume
                + " bloques); máximo 12000."
            );
            return;
        }

        running = true;
        currentTarget = null;
        targetTicks = 0;
        ticksWithoutProgress = 0;

        chat(
            "Minería iniciada. NUMPAD 3 pausa; "
            + "NUMPAD 0 parada de emergencia."
        );
    }

    private void stop(String message) {
        running = false;
        currentTarget = null;
        setForward(false);

        if (MC.thePlayer != null) {
            MC.playerController.resetBlockRemoving();
            chat(message);
        }
    }

    private Pos findNearestSolidInArea() {
        int minX = Math.min(pointA.x, pointB.x);
        int maxX = Math.max(pointA.x, pointB.x);
        int minY = Math.min(pointA.y, pointB.y);
        int maxY = Math.max(pointA.y, pointB.y);
        int minZ = Math.min(pointA.z, pointB.z);
        int maxZ = Math.max(pointA.z, pointB.z);

        Pos best = null;
        double bestDist = Double.MAX_VALUE;

        double px = MC.thePlayer.posX;
        double py = MC.thePlayer.posY + 0.5D;
        double pz = MC.thePlayer.posZ;

        World world = MC.theWorld;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Pos p = new Pos(x, y, z);

                    if (isAir(world, p)) {
                        continue;
                    }

                    Block b = world.getBlock(x, y, z);

                    if (b == null || b.getMaterial().isLiquid()) {
                        continue;
                    }

                    double dx = x + 0.5D - px;
                    double dy = y + 0.5D - py;
                    double dz = z + 0.5D - pz;

                    double d = dx * dx + dy * dy + dz * dz;

                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }

        return best;
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

    private boolean isAir(World world, Pos p) {
        Block b = world.getBlock(p.x, p.y, p.z);

        return b == null || b.isAir(
            world, p.x, p.y, p.z
        );
    }

    private boolean inArea(Pos p) {
        return p.x >= Math.min(pointA.x, pointB.x)
            && p.x <= Math.max(pointA.x, pointB.x)
            && p.y >= Math.min(pointA.y, pointB.y)
            && p.y <= Math.max(pointA.y, pointB.y)
            && p.z >= Math.min(pointA.z, pointB.z)
            && p.z <= Math.max(pointA.z, pointB.z);
    }

    private void setForward(boolean pressed) {
        if (MC.gameSettings == null) {
            return;
        }

        KeyBinding.setKeyBindState(
            MC.gameSettings.keyBindForward.getKeyCode(),
            pressed
        );

        // Evitar que el ataque quede forzado como una tecla.
        KeyBinding.setKeyBindState(
            MC.gameSettings.keyBindAttack.getKeyCode(),
            false
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
