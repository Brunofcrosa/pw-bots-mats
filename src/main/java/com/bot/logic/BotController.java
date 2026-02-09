package com.bot.logic;

import com.bot.input.InputSimulator;
import com.bot.memory.WinMemoryReader;
import com.bot.model.Player;
import com.bot.constants.GameConstants;

public class BotController {
    
    public enum State {
        IDLE,
        MOVING,
        COLLECTING,
        COOLDOWN
    }

    private State currentState = State.IDLE;
    private WinMemoryReader memory;
    private InputSimulator input;
    private Player player;

    public BotController(WinMemoryReader memory, InputSimulator input, Player player) {
        this.memory = memory;
        this.input = input;
        this.player = player;
    }

    public void tick() {
        switch (currentState) {
            case IDLE:
                System.out.println("IDLE: Procurando alvo...");
                // Logic to find nearest entity
                // If found -> currentState = State.MOVING;
                // For demo, let's just create a mock transition
                currentState = State.MOVING;
                break;

            case MOVING:
                System.out.println("MOVING: Indo até o alvo...");
                input.sendKey(GameConstants.WINDOW_NAME, InputSimulator.Keys.VK_W, 100);
                // Check distance... if close -> currentState = State.COLLECTING;
                currentState = State.COLLECTING; 
                break;

            case COLLECTING:
                System.out.println("COLLECTING: Coletando...");
                // Send interact key (maybe capture mouse or specific key)
                // input.sendKey(..., VK_F1, ...);
                try { Thread.sleep(2000); } catch (Exception e) {}
                currentState = State.COOLDOWN;
                break;

            case COOLDOWN:
                System.out.println("COOLDOWN: Aguardando...");
                try { Thread.sleep(1000); } catch (Exception e) {}
                currentState = State.IDLE;
                break;
        }
    }
}
