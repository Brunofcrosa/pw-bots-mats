package com.bot;

import com.bot.logic.BotContext;
import com.bot.logic.EntityManager;
import com.bot.logic.WaypointManager;
import com.bot.model.Player;
import com.bot.model.Entity;
import com.bot.model.Vector3;
import com.bot.model.ResourceDatabase;
import com.bot.memory.WinMemoryReader;
import com.bot.memory.PacketSender;
import com.bot.input.InputSimulator;
import com.bot.constants.BotSettings;
import com.bot.constants.GameConstants;
import com.bot.ui.BotInterface;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (!isRunningAsAdmin()) {
            System.out.println("[INFO] Sem privilegios de administrador. Relancando com elevacao...");
            relaunchAsAdmin();
            System.exit(0);
            return;
        }
        System.out.println("[OK] Executando como administrador.");

        BotInterface gui = new BotInterface();
        SwingUtilities.invokeLater(() -> gui.setVisible(true));
        BotSettings.setUiLogSink(gui::log);
        gui.log("[OK] Executando como administrador.");

        WinMemoryReader memory = new WinMemoryReader();
        InputSimulator input = new InputSimulator();
        Player player = new Player();

        gui.log("[INFO] Aguardando processo: " + GameConstants.WINDOW_NAME + "...");

        while (!memory.openProcess(GameConstants.WINDOW_NAME)) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        long moduleBase = memory.getModuleBaseAddress(GameConstants.PROCESS_NAME);
        if (moduleBase == 0) {
            gui.log("[WARN] Nao foi possivel encontrar a base do modulo.");
            return;
        }

        gui.getBtnSnapshot().setEnabled(false);
        gui.getBtnCompareDec().setEnabled(false);
        gui.getBtnCompareUnchanged().setEnabled(false);

        long dynamicBasePointer = moduleBase + GameConstants.BASE_OFFSET;

        
        ResourceDatabase resourceDb = new ResourceDatabase();
        String coordsPath = System.getProperty("user.dir") + "\\coordenadas\\coords world.txt";
        java.io.File coordsFile = new java.io.File(coordsPath);
        if (coordsFile.exists()) {
            resourceDb.loadFromFile(coordsPath);
        } else {
            gui.log("[WARN] Arquivo de coordenadas nao encontrado: " + coordsPath);
        }

        EntityManager entityManager = new EntityManager(memory, dynamicBasePointer, resourceDb);

        
        PacketSender packetSender = new PacketSender(memory);
        boolean pktReady = packetSender.initialize(moduleBase);
        if (pktReady) {
            gui.log("[OK] PacketSender inicializado com sucesso.");
        } else {
            gui.log("[WARN] PacketSender nao inicializado. Interacao por pacotes desativada.");
        }

        
        List<Vector3> route = Arrays.asList();
        WaypointManager waypointManager = new WaypointManager(route);
        BotContext bot = new BotContext(memory, input, player, entityManager, moduleBase, waypointManager, packetSender);

        
        gui.setOnBotToggle(running -> bot.setRunning(running));

        gui.log("[INFO] Bot acoplado com sucesso.");
        gui.log("[INFO] Deteccao automatica de materiais ativa.");
        gui.log("[INFO] Clique START para iniciar coleta automatica.");

        new Thread(() -> {
            while (true) {
                try {
                    player.update(memory, moduleBase);
                    entityManager.update(moduleBase, player);
                    bot.update();

                    final Entity nearestMob = entityManager.getNearestMob();
                    final List<Entity> materials = entityManager.getMaterials();
                    final String stateName = bot.getStateName();

                    SwingUtilities.invokeLater(() -> {
                        gui.updateUI(player, nearestMob, materials);
                        if (bot.isRunning()) {
                            gui.updateBotStatus(stateName, materials.size());
                        }
                    });
                    Thread.sleep(100);
                } catch (Exception e) {
                    System.err.println("Erro no loop principal: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static boolean isRunningAsAdmin() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"net", "session"});
            byte[] buf = new byte[1024];
            while (p.getInputStream().read(buf) != -1) {}
            while (p.getErrorStream().read(buf) != -1) {}
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void relaunchAsAdmin() {
        try {
            String java = System.getProperty("java.home") + "\\bin\\java.exe";
            String cp = System.getProperty("java.class.path");
            String cmd = "Start-Process -FilePath '" + java.replace("'", "''") +
                "' -ArgumentList '-cp','" + cp.replace("'", "''") +
                "','com.bot.Main' -Verb RunAs";
            Runtime.getRuntime().exec(new String[]{"powershell", "-Command", cmd});
        } catch (Exception e) {
            System.err.println("Falha ao elevar privilegios: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
