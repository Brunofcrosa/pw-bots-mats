package com.bot.ui;

import com.bot.model.Player;
import com.bot.model.Entity;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class BotInterface extends JFrame {
    private JProgressBar hpBar, mpBar;
    private JLabel nameLabel, posLabel;
    private JProgressBar targetHpBar;
    private JLabel targetNameLabel, targetDistLabel, targetIdLabel;
    private DefaultListModel<String> matListModel;
    private JList<String> matList;
    private JTextArea logArea;
    private JLabel statusLabel;
    private JButton btnStart, btnStop;

    public BotInterface() {
        setTitle("PW Bot Master - Multi-Farm");
        setSize(500, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Combate", createCombatPanel());
        tabbedPane.addTab("Materiais", createMaterialsPanel());
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
        playerPanel.add(nameLabel);
        playerPanel.add(hpBar);
        playerPanel.add(mpBar);
        playerPanel.add(posLabel);
        JPanel mobPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        mobPanel.setBorder(new TitledBorder(" Informações do Mob (Alvo) "));
        targetNameLabel = new JLabel("Alvo: Nenhum");
        targetIdLabel = new JLabel("ID: ---");
        targetHpBar = createProgressBar(new Color(255, 140, 0), "Vida do Alvo");
        targetDistLabel = new JLabel("Distância: -- m");
        mobPanel.add(targetNameLabel);
        mobPanel.add(targetIdLabel);
        mobPanel.add(targetHpBar);
        mobPanel.add(targetDistLabel);
        mainPanel.add(playerPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(mobPanel);

        return mainPanel;
    }

    private JPanel createMaterialsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(" Materiais Próximos "));
        matListModel = new DefaultListModel<>();
        matList = new JList<>(matListModel);
        matList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(matList);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        logArea = new JTextArea();
        logArea.setEditable(false);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout());
        statusLabel = new JLabel(" Status: Desconectado");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnStart = new JButton("START");
        btnStop = new JButton("STOP");
        btnStop.setEnabled(false);
        btnStart.addActionListener(e -> toggleBot(true));
        btnStop.addActionListener(e -> toggleBot(false));

        buttons.add(btnStart);
        buttons.add(btnStop);
        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(buttons, BorderLayout.EAST);
        return footer;
    }

    private void toggleBot(boolean run) {
        btnStart.setEnabled(!run);
        btnStop.setEnabled(run);
        statusLabel.setText(run ? " Status: Rodando" : " Status: Parado");
        statusLabel.setForeground(run ? new Color(0, 150, 0) : Color.RED);
        log(run ? "Bot iniciado." : "Bot parado.");
    }

    private JProgressBar createProgressBar(Color color, String text) {
        JProgressBar bar = new JProgressBar();
        bar.setStringPainted(true);
        bar.setForeground(color);
        bar.setString(text);
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
                targetNameLabel.setText("Alvo: Nenhum");
                targetIdLabel.setText("ID: ---");
                targetHpBar.setValue(0);
                targetDistLabel.setText("Distância: -- m");
            }

            matListModel.clear();
            for (Entity mat : mats) {
                matListModel.addElement(String.format("ID: %d | Dist: %.1f m", mat.getId(), mat.getDistance()));
            }
        });
    }

    private void updateBar(JProgressBar bar, int cur, int max) {
        if (max <= 0) return;
        bar.setMaximum(max);
        bar.setValue(cur);
        bar.setString(cur + " / " + max);
    }

    public void log(String msg) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + time + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}