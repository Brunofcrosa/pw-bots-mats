package com.bot;

import com.bot.logic.BotContext;
import com.bot.logic.EntityManager;
import com.bot.logic.WaypointManager;
import com.bot.model.Player;
import com.bot.model.Entity;
import com.bot.model.Vector3;
import com.bot.memory.WinMemoryReader;
import com.bot.memory.MemoryDiffScanner;
import com.bot.input.InputSimulator;
import com.bot.constants.GameConstants;
import com.bot.ui.BotInterface;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        BotInterface gui = new BotInterface();
        SwingUtilities.invokeLater(() -> gui.setVisible(true));

        WinMemoryReader memory = new WinMemoryReader();
        InputSimulator input = new InputSimulator();
        Player player = new Player();

        gui.log("Aguardando processo: " + GameConstants.WINDOW_NAME + "...");

        while (!memory.openProcess(GameConstants.WINDOW_NAME)) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        long moduleBase = memory.getModuleBaseAddress(GameConstants.PROCESS_NAME);
        if (moduleBase == 0) {
            gui.log("Erro: Não foi possível encontrar a base do módulo.");
            return;
        }

        MemoryDiffScanner diffScanner = new MemoryDiffScanner(memory, moduleBase);

        gui.getBtnSnapshot().addActionListener(e -> {
            gui.getBtnSnapshot().setEnabled(false);
            gui.log("Tirando Snapshot da memória genérica...");

            new Thread(() -> {
                int tracked = diffScanner.takeSnapshot();
                SwingUtilities.invokeLater(() -> {
                    gui.log("Snapshot pronto! " + tracked + " arrays rastreados.");
                    gui.getBtnSnapshot().setEnabled(true);
                    gui.getBtnCompareDec().setEnabled(true);
                    gui.getBtnCompareUnchanged().setEnabled(true);
                });
            }).start();
        });

        // Passa o 'player' para calcular as coordenadas no Diff
        gui.getBtnCompareDec().addActionListener(e -> processFilter(gui, diffScanner, true, player));
        gui.getBtnCompareUnchanged().addActionListener(e -> processFilter(gui, diffScanner, false, player));

        long dynamicBasePointer = moduleBase + GameConstants.BASE_OFFSET;
        EntityManager entityManager = new EntityManager(memory, dynamicBasePointer);

        List<Vector3> route = Arrays.asList(
                new Vector3(100.0f, 50.0f, 200.0f),
                new Vector3(120.0f, 50.0f, 210.0f),
                new Vector3(150.0f, 60.0f, 250.0f)
        );
        WaypointManager waypointManager = new WaypointManager(route);
        BotContext bot = new BotContext(memory, input, player, entityManager, moduleBase, waypointManager);

        gui.log("Bot acoplado com sucesso. Use a aba de Materiais para descobrir offsets.");

        new Thread(() -> {
            while (true) {
                try {
                    player.update(memory, moduleBase);
                    entityManager.update(moduleBase, player);
                    bot.update();

                    final Entity nearestMob = entityManager.getNearestMob();
                    final List<Entity> materials = entityManager.getMaterials();

                    SwingUtilities.invokeLater(() -> gui.updateUI(player, nearestMob, materials));
                    Thread.sleep(100);
                } catch (Exception e) {
                    System.err.println("Erro no loop principal: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static void processFilter(BotInterface gui, MemoryDiffScanner diffScanner, boolean decrease, Player player) {
        gui.getBtnCompareDec().setEnabled(false);
        gui.getBtnCompareUnchanged().setEnabled(false);
        gui.log("Aplicando filtro: " + (decrease ? "Diminuiu" : "Inalterado") + "...");

        new Thread(() -> {
            List<String> candidates = decrease ? diffScanner.compareDecreased() : diffScanner.compareUnchanged();

            // Auto-Descoberta Baseada em Coordenadas
            if (!candidates.isEmpty() && candidates.size() <= 200) {
                gui.log("Inspecionando " + candidates.size() + " arrays (Buscando as suas coordenadas)...");
                String found = diffScanner.findCorrectChain(candidates, player);

                if (found != null) {
                    SwingUtilities.invokeLater(() -> {
                        gui.log(">>> O ARRAY CORRETO FOI ENCONTRADO! <<<");
                        gui.log("-> Cheque o console do IntelliJ para ver os OFFSETS!");
                        gui.getBtnSnapshot().setEnabled(true);
                    });
                    return;
                } else {
                    gui.log("As coordenadas não bateram em nenhum array. Fique bem em cima da erva!");
                }
            }

            SwingUtilities.invokeLater(() -> {
                if (candidates.isEmpty()) {
                    gui.log("Nenhum array restou. O offset foi perdido. Faça um novo Snapshot.");
                } else if (candidates.size() == 1) {
                    gui.log(">>> OFFSET ENCONTRADO <<<");
                    gui.log("MATTER_LIST_CHAIN = { " + candidates.get(0) + " };");
                } else {
                    gui.log(candidates.size() + " arrays restantes. Cave outra erva e clique em DIMINUIU.");
                    gui.getBtnCompareDec().setEnabled(true);
                    gui.getBtnCompareUnchanged().setEnabled(true);
                }
                gui.getBtnSnapshot().setEnabled(true);
            });
        }).start();
    }
}