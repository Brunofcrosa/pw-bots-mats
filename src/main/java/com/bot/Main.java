package com.bot;

import com.bot.constants.GameConstants;
import com.bot.memory.WinMemoryReader;
import com.bot.model.Player;
import com.bot.logic.BotController;
import com.bot.input.InputSimulator;

public class Main {
    public static void main(String[] args) {
        WinMemoryReader memory = new WinMemoryReader();
        if (!memory.openProcess(GameConstants.WINDOW_NAME)) {
            System.err.println("Jogo não encontrado!");
            return;
        }

        long moduleBase = memory.getModuleBaseAddress(GameConstants.PROCESS_NAME);
        if (moduleBase == 0) {
            System.err.println("Não foi possível obter a base do elementclient.exe");
            return;
        }

        System.out.println("Base encontrada: " + Long.toHexString(moduleBase));

        Player player = new Player();
        InputSimulator input = new InputSimulator();
        BotController bot = new BotController(memory, input, player);

        while (true) {
            try {
                player.update(memory, moduleBase);
                System.out.println(player);
                bot.tick();
                Thread.sleep(200);
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
        memory.close();
    }
}