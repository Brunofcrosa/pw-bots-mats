package com.bot.logic;

import com.bot.logic.states.BotState;
import com.bot.logic.states.IdleState;
import com.bot.memory.WinMemoryReader;
import com.bot.memory.PacketSender;
import com.bot.model.Entity;
import com.bot.model.Player;
import com.bot.input.InputSimulator;
import com.bot.constants.BotSettings;

import java.util.HashMap;
import java.util.Map;

public class BotContext {
    private BotState currentState;
    private final WinMemoryReader memory;
    private final InputSimulator input;
    private final EntityManager entityManager;
    private final Player player;
    private final long moduleBase;
    private final WaypointManager waypointManager;
    private final PacketSender packetSender;

    private Entity targetEntity;
    private boolean running = false;

    
    private final Map<Long, Long> blacklist = new HashMap<>();
    private static final long BLACKLIST_DURATION_MS = 30000; 

    public BotContext(WinMemoryReader memory, InputSimulator input, Player player, EntityManager entityManager, long moduleBase, WaypointManager waypointManager, PacketSender packetSender) {
        this.memory = memory; this.input = input; this.player = player;
        this.entityManager = entityManager; this.moduleBase = moduleBase;
        this.waypointManager = waypointManager; this.packetSender = packetSender;
        this.currentState = new IdleState();
    }

    public void update() {
        if (!running) return;
        currentState.execute(this);
    }

    public void setState(BotState newState) {
        String oldName = currentState.getClass().getSimpleName();
        String newName = newState.getClass().getSimpleName();
        String msg = "[STATE] " + oldName + " -> " + newName;
        System.out.println(msg);
        BotSettings.logToUi(msg);
        this.currentState = newState;
    }

    
    public void blacklist(long baseAddress) {
        blacklist.put(baseAddress, System.currentTimeMillis() + BLACKLIST_DURATION_MS);
    }

    public boolean isBlacklisted(long baseAddress) {
        Long expiry = blacklist.get(baseAddress);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            blacklist.remove(baseAddress);
            return false;
        }
        return true;
    }

    
    public boolean isRunning() { return running; }
    public void setRunning(boolean running) {
        this.running = running;
        if (running) {
            setState(new IdleState());
        }
    }

    
    public Entity getTargetEntity() { return targetEntity; }
    public void setTargetEntity(Entity target) { this.targetEntity = target; }

    
    public WinMemoryReader getMemory() { return memory; }
    public InputSimulator getInput() { return input; }
    public Player getPlayer() { return player; }
    public EntityManager getEntityManager() { return entityManager; }
    public long getModuleBase() { return moduleBase; }
    public WaypointManager getWaypointManager() { return waypointManager; }
    public PacketSender getPacketSender() { return packetSender; }
    public String getStateName() { return currentState.getClass().getSimpleName(); }
}
