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
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;

@Mod(
    modid = RectMinerMod.MODID,
    name = "RectMiner",
    version = "0.3.1",
    acceptableRemoteVersions = "*"
)
public class RectMinerMod {

    public static final String MODID = "rectminer";
    private static final Minecraft MC = Minecraft.getMinecraft();

    private KeyBinding setAKey;
    private KeyBinding setBKey;
    private KeyBinding toggleKey;
    private KeyBinding autoKillerKey;
    private KeyBinding stopKey;
    private KeyBinding autoFeedKey;

    private Pos pointA;
    private Pos pointB;
    private Pos currentTarget;

    private final List<Pos> targets = new ArrayList<Pos>();
    private int targetIndex;

    private boolean running;
    private boolean autoKillerRunning;
    private boolean autoFeedRunning;
    private int autoFeedTicks;
    private int ticksWithoutProgress;

    private EntityLivingBase mobTarget;

    private static final int MAX_BLOCKS = 12000;
    private static final int BLOCK_HEIGHT = 2;
    private static final int STUCK_TIMEOUT = 20 * 45;

    // AutoFeed: 10 minutos y 30 segundos a 20 ticks por segundo
    private static final int AUTO_FEED_INTERVAL = 20 * 630;

    // RectMiner
    private static final double MINING_REACH = 1.5D;
    private static final double WALK_DISTANCE = 1.4D;
    private static final float MAX_TURN_PER_TICK = 12.0F;
    private static final float WALK_ANGLE_TOLERANCE = 25.0F;

    // AutoKiller
    private static final double MOB_SEARCH_RANGE = 100.0D;
    private static final double MOB_ATTACK_RANGE = 3.0D;
    private static final double MOB_APPROACH_DISTANCE = 2.0D;
    private static final float MOB_MAX_TURN_PER_TICK = 15.0F;
    private static final float MOB_AIM_TOLERANCE = 12.0F;

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

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (MC.thePlayer == null || MC.theWorld == null) {
            releaseControls();
            return;
        }

        if (stopKey.isPressed()) {
            emergencyStop();
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

        if (autoKillerKey.isPressed()) {
            toggleAutoKiller();
        }

        if (autoFeedKey.isPressed()) {
            toggleAutoFeed();
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
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (MC.thePlayer == null || MC.theWorld == null) {
            releaseControls();
            return;
        }

        if (MC.currentScreen != null) {
            releaseControls();
            return;
        }

        if (autoFeedRunning) {
            tickAutoFeed();
        }

        if (autoKillerRunning) {
            tickAutoKiller();
            return;
        }

        if (running) {
            tickRectMiner();
        }
