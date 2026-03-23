package com.bot.ui;

import com.bot.constants.BotSettings;
import com.bot.logic.RouteManager;
import com.bot.model.Player;
import com.bot.model.Entity;
import com.bot.model.ResourceDatabase;
import com.bot.model.ResourceSpawn;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;

public class BotInterface extends JFrame {
    private JProgressBar hpBar, mpBar, targetHpBar;
    private JLabel nameLabel, posLabel, targetNameLabel, targetDistLabel, targetIdLabel, statusLabel;
    private DefaultListModel<String> matListModel;
    private JList<String> matList;

    private RouteManager routeManager;
    private final Map<String, JCheckBox> routeCheckboxes = new LinkedHashMap<>();
    private JLabel routeStatusLabel;
    private JLabel routeSpawnCountLabel;
    private DefaultListModel<String> cooldownListModel;
    private JTabbedPane tabbedPane;
    private JTextArea logArea;
    private JButton btnStart, btnStop, btnSnapshot, btnCompareDec, btnCompareUnchanged;
    private JCheckBox chkDiagnosticLogs;
    private java.util.function.Consumer<Boolean> onBotToggle;

    public BotInterface() {
        setTitle("PW Bot Master - Multi-Farm");
        setSize(620, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Combate", createCombatPanel());
        tabbedPane.addTab("Materiais", createMaterialsPanel());
        tabbedPane.addTab("Farm de Rotas", createRoutePanel());
        tabbedPane.addTab("Logs", createLogPanel());
        add(tabbedPane, BorderLayout.CENTER);
        add(createFooterPanel(), BorderLayout.SOUTH);
    }

    private JPanel createCombatPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel playerPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        playerPanel.setBorder(new TitledBorder(" Informações do Personagem "));
        nameLabel = new JLabel("Nome: --- (Lv: --)");
        hpBar = createProgressBar(Color.RED, "HP");
        mpBar = createProgressBar(Color.BLUE, "MP");
        posLabel = new JLabel("Posição: X: 0 | Y: 0 | Z: 0");
        playerPanel.add(nameLabel); playerPanel.add(hpBar); playerPanel.add(mpBar); playerPanel.add(posLabel);

        JPanel mobPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        mobPanel.setBorder(new TitledBorder(" Informações do Mob (Alvo) "));
        targetNameLabel = new JLabel("Alvo: Nenhum");
        targetIdLabel = new JLabel("ID: ---");
        targetHpBar = createProgressBar(new Color(255, 140, 0), "Vida do Alvo");
        targetDistLabel = new JLabel("Distância: -- m");
        mobPanel.add(targetNameLabel); mobPanel.add(targetIdLabel); mobPanel.add(targetHpBar); mobPanel.add(targetDistLabel);

        mainPanel.add(playerPanel); mainPanel.add(Box.createVerticalStrut(15)); mainPanel.add(mobPanel);
        return mainPanel;
    }

    private JPanel createMaterialsPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel matPanel = new JPanel(new BorderLayout());
        matPanel.setBorder(BorderFactory.createTitledBorder(" Lista de Materiais (Detectados) "));
        matListModel = new DefaultListModel<>();
        matList = new JList<>(matListModel);
        matList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        matPanel.add(new JScrollPane(matList), BorderLayout.CENTER);
        panel.add(matPanel, BorderLayout.CENTER);

        JPanel scannerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnSnapshot = new JButton("1. Snapshot");
        btnCompareDec = new JButton("2. Diminuiu (Cavar)");
        btnCompareUnchanged = new JButton("3. Inalterado (Mover)");
        chkDiagnosticLogs = new JCheckBox("Logs diagnóstico");

        chkDiagnosticLogs.setSelected(BotSettings.isDiagnosticLogsEnabled());
        chkDiagnosticLogs.addActionListener(e -> {
            boolean enabled = chkDiagnosticLogs.isSelected();
            BotSettings.setDiagnosticLogsEnabled(enabled);
            log(enabled ? "Logs de diagnóstico: ON" : "Logs de diagnóstico: OFF");
        });

        btnCompareDec.setEnabled(false);
        btnCompareUnchanged.setEnabled(false);

        scannerPanel.add(btnSnapshot);
        scannerPanel.add(btnCompareDec);
        scannerPanel.add(btnCompareUnchanged);
        scannerPanel.add(chkDiagnosticLogs);

        btnSnapshot.setVisible(false);
        btnCompareDec.setVisible(false);
        btnCompareUnchanged.setVisible(false);
        scannerPanel.add(new JLabel("Detecção automática ativa (sem snapshot manual)."));
        panel.add(scannerPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        logArea = new JTextArea(); logArea.setEditable(false);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout());
        statusLabel = new JLabel(" Status: Desconectado");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnStart = new JButton("START"); btnStop = new JButton("STOP"); btnStop.setEnabled(false);
        btnStart.addActionListener(e -> toggleBot(true)); btnStop.addActionListener(e -> toggleBot(false));
        buttons.add(btnStart); buttons.add(btnStop);
        footer.add(statusLabel, BorderLayout.WEST); footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void toggleBot(boolean run) {
        btnStart.setEnabled(!run); btnStop.setEnabled(run);
        statusLabel.setText(run ? " Status: Rodando" : " Status: Parado");
        statusLabel.setForeground(run ? new Color(0, 150, 0) : Color.RED);
        log(run ? "Bot iniciado." : "Bot parado.");
        if (onBotToggle != null) onBotToggle.accept(run);
    }

    public void setOnBotToggle(java.util.function.Consumer<Boolean> callback) {
        this.onBotToggle = callback;
    }

    private JProgressBar createProgressBar(Color color, String text) {
        JProgressBar bar = new JProgressBar();
        bar.setStringPainted(true); bar.setForeground(color); bar.setString(text);
        return bar;
    }

    public void updateUI(Player player, Entity target, List<Entity> mats) {
        SwingUtilities.invokeLater(() -> {
            if (player != null) {
                nameLabel.setText(String.format("Nome: %s (Lv: %d)", player.getName(), player.getLevel()));
                updateBar(hpBar, player.getHp(), player.getMaxHp());
                updateBar(mpBar, player.getMp(), player.getMaxMp());
                posLabel.setText(String.format("Posição: X: %.1f | Y: %.1f | Z: %.1f", player.getX(), player.getY(), player.getZ()));
            }
            if (target != null) {
                targetNameLabel.setText("Alvo: Mob");
                targetIdLabel.setText("ID: " + target.getId());
                targetDistLabel.setText(String.format("Distância: %.1f m", target.getDistance()));
                updateBar(targetHpBar, target.getHp(), target.getMaxHp());
            } else {
                targetNameLabel.setText("Alvo: Nenhum"); targetIdLabel.setText("ID: ---");
                targetHpBar.setValue(0); targetDistLabel.setText("Distância: -- m");
            }
            matListModel.clear();
            for (Entity mat : mats) {
                String displayName = mat.getName() != null ? mat.getName() : "Material";
                matListModel.addElement(String.format("%s (tid:%d) | Dist: %.1f m",
                        displayName, mat.getTemplateId(), mat.getDistance()));
            }
        });
    }

    private void updateBar(JProgressBar bar, int cur, int max) {
        if (max <= 0) return;
        bar.setMaximum(max); bar.setValue(cur); bar.setString(cur + " / " + max);
    }

    public void updateBotStatus(String stateName, int materialsCount) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(String.format(" Status: %s | Materiais: %d", stateName, materialsCount));
        });
    }

    public void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + time + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public JButton getBtnSnapshot() { return btnSnapshot; }
    public JButton getBtnCompareDec() { return btnCompareDec; }
    public JButton getBtnCompareUnchanged() { return btnCompareUnchanged; }

    public void setRouteManager(RouteManager rm) {
        this.routeManager = rm;
        buildRouteCheckboxes(rm.getResourceDb());
    }

    private JPanel createRoutePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel filterPanel = new JPanel(new BorderLayout());
        filterPanel.setBorder(BorderFactory.createTitledBorder(" Filtros de Recurso "));

        JPanel checkboxContainer = new JPanel();
        checkboxContainer.setLayout(new BoxLayout(checkboxContainer, BoxLayout.Y_AXIS));
        JScrollPane filterScroll = new JScrollPane(checkboxContainer);
        filterScroll.setPreferredSize(new Dimension(300, 260));
        filterScroll.getVerticalScrollBar().setUnitIncrement(12);
        filterPanel.add(filterScroll, BorderLayout.CENTER);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton btnAll = new JButton("Selecionar Todos");
        JButton btnClear = new JButton("Limpar");
        btnRow.add(btnAll);
        btnRow.add(btnClear);
        filterPanel.add(btnRow, BorderLayout.SOUTH);

        btnAll.addActionListener(e -> {
            routeCheckboxes.values().forEach(cb -> cb.setSelected(true));
            syncRouteSelection();
        });
        btnClear.addActionListener(e -> {
            routeCheckboxes.values().forEach(cb -> cb.setSelected(false));
            syncRouteSelection();
        });

        JPanel statusPanel = new JPanel(new BorderLayout(4, 4));
        statusPanel.setBorder(BorderFactory.createTitledBorder(" Status da Rota "));

        routeStatusLabel = new JLabel("Alvo: ---");
        routeSpawnCountLabel = new JLabel("Spawns selecionados: 0");

        JPanel infoBox = new JPanel(new GridLayout(2, 1, 2, 2));
        infoBox.add(routeStatusLabel);
        infoBox.add(routeSpawnCountLabel);
        statusPanel.add(infoBox, BorderLayout.NORTH);

        cooldownListModel = new DefaultListModel<>();
        JList<String> cooldownList = new JList<>(cooldownListModel);
        cooldownList.setFont(new Font("Monospaced", Font.PLAIN, 11));
        JScrollPane cdScroll = new JScrollPane(cooldownList);
        cdScroll.setBorder(BorderFactory.createTitledBorder(" Cooldowns Ativos "));
        statusPanel.add(cdScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, filterPanel, statusPanel);
        split.setDividerLocation(280);
        split.setResizeWeight(0.45);
        panel.add(split, BorderLayout.CENTER);

        JLabel hint = new JLabel(
            "  Selecione os recursos desejados. BOT vai percorrer os spawns e coletar automaticamente.");
        hint.setFont(new Font("SansSerif", Font.ITALIC, 11));
        hint.setForeground(Color.DARK_GRAY);
        panel.add(hint, BorderLayout.SOUTH);

        return panel;
    }

    private void buildRouteCheckboxes(ResourceDatabase db) {
        routeCheckboxes.clear();
        Map<String, List<String>> byCategory = ResourceDatabase.getNamesByCategory();

        Component[] tabs = tabbedPane.getComponents();
        JPanel routeTab = null;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if ("Farm de Rotas".equals(tabbedPane.getTitleAt(i))) {
                routeTab = (JPanel) tabbedPane.getComponentAt(i);
                break;
            }
        }
        if (routeTab == null) return;

        JSplitPane split = (JSplitPane) routeTab.getComponent(0);
        JPanel filterPanel = (JPanel) split.getLeftComponent();
        JScrollPane scroll = (JScrollPane) filterPanel.getComponent(0);
        JPanel container = (JPanel) scroll.getViewport().getView();
        container.removeAll();

        for (Map.Entry<String, List<String>> entry : byCategory.entrySet()) {
            String cat = entry.getKey();
            List<String> names = entry.getValue();

            JLabel catLabel = new JLabel(cat.toUpperCase());
            catLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
            catLabel.setForeground(new Color(60, 60, 140));
            catLabel.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 0));
            catLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            container.add(catLabel);

            for (String name : names) {
                JCheckBox cb = new JCheckBox(name);
                cb.setFont(new Font("SansSerif", Font.PLAIN, 12));
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                cb.addActionListener(e -> syncRouteSelection());
                routeCheckboxes.put(name, cb);
                container.add(cb);
            }
        }

        container.revalidate();
        container.repaint();
    }

    private void syncRouteSelection() {
        if (routeManager == null) return;
        Set<String> selected = new LinkedHashSet<>();
        for (Map.Entry<String, JCheckBox> e : routeCheckboxes.entrySet()) {
            if (e.getValue().isSelected()) selected.add(e.getKey());
        }
        routeManager.setSelectedNames(selected);
        updateSpawnCount();
    }

    private void updateSpawnCount() {
        if (routeManager == null || routeSpawnCountLabel == null) return;
        int total = routeManager.getTotalSpawnCount();
        int cd = routeManager.getActiveCooldownCount();
        routeSpawnCountLabel.setText(String.format(
            "Spawns: %d total | %d em cooldown", total, cd));
    }

    public void updateRoutePanel(RouteManager rm, String currentTargetName) {
        if (rm == null || routeStatusLabel == null) return;
        SwingUtilities.invokeLater(() -> {
            if (currentTargetName != null) {
                routeStatusLabel.setText("Alvo: " + currentTargetName);
            } else {
                routeStatusLabel.setText(rm.isRouteActive() ? "Alvo: buscando..." : "Alvo: ---");
            }
            updateSpawnCount();

            cooldownListModel.clear();
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Long> e : rm.getCooldowns().entrySet()) {
                long remMs = e.getValue() - now;
                if (remMs <= 0) continue;
                long remMin = remMs / 60000;
                long remSec = (remMs % 60000) / 1000;
                cooldownListModel.addElement(String.format("%s — %dm%02ds",
                        e.getKey(), remMin, remSec));
            }
        });
    }
}
