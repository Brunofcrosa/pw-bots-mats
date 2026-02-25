package com.bot.logic;

import com.bot.logic.states.BotState;
import com.bot.logic.states.IdleState;
import com.bot.memory.WinMemoryReader;
import com.bot.memory.PacketSender;
import com.bot.model.Player;
import com.bot.input.InputSimulator;

public class BotContext {
    private BotState currentState;
    private final WinMemoryReader memory;
    private final InputSimulator input;
    private final EntityManager entityManager;
    private final Player player;
    private final long moduleBase;
    private final WaypointManager waypointManager;
    private final PacketSender packetSender;

    public BotContext(WinMemoryReader memory, InputSimulator input, Player player, EntityManager entityManager, long moduleBase, WaypointManager waypointManager, PacketSender packetSender) {
        this.memory = memory; this.input = input; this.player = player;
        this.entityManager = entityManager; this.moduleBase = moduleBase;
        this.waypointManager = waypointManager; this.packetSender = packetSender;
        this.currentState = new IdleState();
    }

    public void update() { currentState.execute(this); }
    public void setState(BotState newState) { this.currentState = newState; }
    public WinMemoryReader getMemory() { return memory; }
    public InputSimulator getInput() { return input; }
    public Player getPlayer() { return player; }
    public EntityManager getEntityManager() { return entityManager; }
    public long getModuleBase() { return moduleBase; }
    public WaypointManager getWaypointManager() { return waypointManager; }
    public PacketSender getPacketSender() { return packetSender; }
}