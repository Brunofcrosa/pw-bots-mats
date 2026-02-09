package com.bot.logic;

import com.bot.logic.states.BotState;
import com.bot.logic.states.IdleState;
import com.bot.memory.WinMemoryReader;
import com.bot.model.Player;
import com.bot.input.InputSimulator;

public class BotContext {
    private BotState currentState;
    private final WinMemoryReader memory;
    private final InputSimulator input;
    private final EntityManager entityManager;
    private final Player player;
    private final long moduleBase;

    public BotContext(WinMemoryReader memory, InputSimulator input, Player player, EntityManager entityManager, long moduleBase) {
        this.memory = memory;
        this.input = input;
        this.player = player;
        this.entityManager = entityManager;
        this.moduleBase = moduleBase;
        this.currentState = new IdleState();
    }

    public void update() {
        currentState.execute(this);
    }

    public void setState(BotState newState) {
        this.currentState = newState;
    }

    public WinMemoryReader getMemory() { return memory; }
    public InputSimulator getInput() { return input; }
    public Player getPlayer() { return player; }
    public EntityManager getEntityManager() { return entityManager; }
    public long getModuleBase() { return moduleBase; }
}