
package com.example.autokiller;

import java.util.List;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;

import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

@Mod(
    modid = AutoKillerMod.MODID,
    name = "AutoKiller",
    version = "1.0.0",
    acceptableRemoteVersions = "*"
)
public class AutoKillerMod {

    public static final String MODID = "autokiller";

    private static final Minecraft MC = Minecraft.getMinecraft();

    private KeyBinding toggleKey;
    private KeyBinding emergencyStopKey;

    private boolean running = false;

    private EntityLivingBase currentTarget;

    // Distancia máxima para buscar objetivos.
    private static final double TARGET_RANGE = 3.0D;

    // Distancia a la que deja de avanzar para atacar.
    private static final double APPROACH_DISTANCE = 2.0D;

    // Alcance máximo para intentar atacar.
    private static final double ATTACK_RANGE = 3.0D;

    // Giro progresivo de la cámara.
    private static final float MAX_TURN_PER_TICK = 15.0F;

    // Tolerancia angular para atacar.
    private static final float AIM_TOLERANCE = 12.0F;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        toggleKey = new KeyBinding(
            "key.autokiller.toggle",
            Keyboard.KEY_NUMPAD3,
            "key.categories.autokiller"
        );

        emergencyStopKey = new KeyBinding(
            "key.autokiller.emergency_stop",
            Keyboard.KEY_NUMPAD0,
            "key.categories.autokiller"
        );

        ClientRegistry.registerKeyBinding(toggleKey);
        ClientRegistry.registerKeyBinding(emergencyStopKey);

        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {

        if (MC.thePlayer == null || MC.theWorld == null) {
            releaseControls();
            return;
        }

        if (emergencyStopKey.isPressed()) {
            stopAutoKiller("Parada de emergencia.");
            return;
        }

        if (toggleKey.isPressed()) {
            if (running) {
                stopAutoKiller("AutoKiller desactivado.");
            } else {
                startAutoKiller();
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

            releaseControls();
            currentTarget = null;
            return;
        }

        // Buscar un objetivo válido dentro del rango.
        currentTarget = findTarget();

        if (currentTarget == null) {
            setAttack(false);
            setForward(false);
            return;
        }

        double px = MC.thePlayer.posX;
        double py = MC.thePlayer.posY
            + MC.thePlayer.getEyeHeight();
        double pz = MC.thePlayer.posZ;

        // Apuntar aproximadamente al centro del cuerpo.
        double tx = currentTarget.posX;
        double ty = currentTarget.posY
            + currentTarget.height * 0.5D;
        double tz = currentTarget.posZ;

        double dx = tx - px;
        double dy = ty - py;
        double dz = tz - pz;

        double horizontalDistance = Math.sqrt(
            dx * dx + dz * dz
        );

        double distance = Math.sqrt(
            dx * dx + dy * dy + dz * dz
        );

        // Calcular la orientación hacia la entidad.
        float targetYaw = (float) (
            Math.atan2(dz, dx) * 180.0D / Math.PI
        ) - 90.0F;

        float targetPitch = (float) (
            -Math.atan2(dy, horizontalDistance)
            * 180.0D / Math.PI
        );

        // Girar progresivamente hacia el objetivo.
        float yawDifference = wrapAngle(
            targetYaw - MC.thePlayer.rotationYaw
        );

        float pitchDifference = wrapAngle(
            targetPitch - MC.thePlayer.rotationPitch
        );

        float yawTurn = clampTurn(yawDifference);
        float pitchTurn = clampTurn(pitchDifference);

        MC.thePlayer.rotationYaw += yawTurn;
        MC.thePlayer.rotationPitch += pitchTurn;

        // Comprobar la alineación después del giro.
        float remainingYaw = wrapAngle(
            targetYaw - MC.thePlayer.rotationYaw
        );

        float remainingPitch = wrapAngle(
            targetPitch - MC.thePlayer.rotationPitch
        );

        boolean aligned =
            Math.abs(remainingYaw) <= AIM_TOLERANCE
            && Math.abs(remainingPitch) <= AIM_TOLERANCE;

        // Comprobar si la mira apunta a la entidad seleccionada.
        boolean targetUnderCrosshair =
            MC.objectMouseOver != null
            && MC.objectMouseOver.typeOfHit
                == MovingObjectPosition.MovingObjectType.ENTITY
            && MC.objectMouseOver.entityHit == currentTarget;

        boolean inAttackRange = distance <= ATTACK_RANGE;

        if (inAttackRange && aligned && targetUnderCrosshair) {
            // Detenerse para atacar con el clic izquierdo.
            setForward(false);
            setAttack(true);
        } else {
            setAttack(false);

            // Acercarse únicamente caminando hacia delante.
            if (distance > APPROACH_DISTANCE) {
                setForward(true);
            } else {
                setForward(false);
            }
        }
    }

    private EntityLivingBase findTarget() {

        List<EntityLivingBase> entities =
            MC.theWorld.getEntitiesWithinAABB(
                EntityLivingBase.class,
                MC.thePlayer.boundingBox.expand(
                    TARGET_RANGE,
                    TARGET_RANGE,
                    TARGET_RANGE
                )
            );

        EntityLivingBase nearest = null;
        double nearestDistanceSq =
            TARGET_RANGE * TARGET_RANGE;

        for (EntityLivingBase entity : entities) {

            if (entity == null
                || entity == MC.thePlayer
                || entity instanceof EntityPlayer
                || !entity.isEntityAlive()
                || entity.isDead) {
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

    private float clampTurn(float angle) {

        if (angle > MAX_TURN_PER_TICK) {
            return MAX_TURN_PER_TICK;
        }

        if (angle < -MAX_TURN_PER_TICK) {
            return -MAX_TURN_PER_TICK;
        }

        return angle;
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

    private void startAutoKiller() {

        if (MC.thePlayer == null || MC.theWorld == null) {
            return;
        }

        running = true;
        currentTarget = null;

        releaseControls();

        chat(
            "AutoKiller activado. Rango: "
            + TARGET_RANGE + " bloques."
        );
    }

    private void stopAutoKiller(String message) {

        running = false;
        currentTarget = null;

        releaseControls();

        chat(message);
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

    private void chat(String message) {

        if (MC.thePlayer != null) {
            MC.thePlayer.addChatMessage(
                new ChatComponentText(
                    "[AutoKiller] " + message
                )
            );
        }
    }
}
