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
    version = "0.4.0",
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
    private KeyBinding killAuraKey;

    private Pos pointA;
    private Pos pointB;
    private Pos currentTarget;

    private final List<Pos> targets = new ArrayList<Pos>();
    private int targetIndex;

    private boolean running;
    private boolean autoKillerRunning;
    private boolean autoFeedRunning;
    private boolean killAuraRunning;
    private int autoFeedTicks;
    private int ticksWithoutProgress;

    private EntityLivingBase mobTarget;

    private static final int MAX_BLOCKS = 12000;
    private static final int BLOCK_HEIGHT = 2;
    private static final int STUCK_TIMEOUT = 20 * 45;

    // AutoFeed: 10 minutos y 30 segundos a 20 ticks por segundo
    private static final int AUTO_FEED_INTERVAL = 20 * 660;

    // RectMiner
    private static final double MINING_REACH = 1.5D;
    private static final double WALK_DISTANCE = 1.4D;
    private static final float MAX_TURN_PER_TICK = 12.0F;
    private static final float WALK_ANGLE_TOLERANCE = 25.0F;

    // AutoKiller
    private static final double MOB_SEARCH_RANGE = 100.0D;
    // KillAura: radio de seleccion local. El servidor puede imponer un alcance menor.
    private static final double KILL_AURA_RANGE = 5.0D;
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
