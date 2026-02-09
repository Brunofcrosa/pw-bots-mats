package com.bot;
import com.bot.logic.BotContext;
import com.bot.logic.EntityManager;
import com.bot.model.Player;
import com.bot.model.Entity;
import com.bot.memory.WinMemoryReader;
import com.bot.input.InputSimulator;
import com.bot.constants.GameConstants;
import com.bot.ui.BotInterface;
import javax.swing.SwingUtilities;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        BotInterface gui = new BotInterface();
        SwingUtilities.invokeLater(() -> gui.setVisible(true));
        WinMemoryReader memory = new WinMemoryReader();
        InputSimulator input = new InputSimulator();
        Player player = new Player();
        EntityManager entityManager = new EntityManager(memory);
        gui.log("Aguardando processo: " + GameConstants.WINDOW_NAME + "...");
        while (!memory.openProcess(GameConstants.WINDOW_NAME)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        long moduleBase = memory.getModuleBaseAddress(GameConstants.PROCESS_NAME);
        if (moduleBase == 0) {
            gui.log("Erro: Não foi possível encontrar a base do módulo.");
            return;
        }
        BotContext bot = new BotContext(memory, input, player, entityManager, moduleBase);
        gui.log("Bot acoplado com sucesso. Pronto para iniciar.");
        new Thread(() -> {
            while (true) {
                try {
                    player.update(memory, moduleBase);
                    entityManager.update(moduleBase, player);
                    bot.update();
                    Entity nearestMob = entityManager.getNearestMob();
                    List<Entity> materials = entityManager.getMaterials();
                    SwingUtilities.invokeLater(() -> gui.updateUI(player, nearestMob, materials));
                    Thread.sleep(100);

                } catch (Exception e) {
                    System.err.println("Erro no loop principal: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }
}