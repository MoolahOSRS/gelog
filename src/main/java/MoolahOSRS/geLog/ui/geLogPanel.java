package MoolahOSRS.geLog.ui;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;

public class geLogPanel extends PluginPanel {
    private final JLabel playerNameLabel;
    private final JLabel lifetimeLabel;
    private final JLabel sessionLabel;
    private final JTextArea logArea;
    private final JTextArea notesArea;

    public geLogPanel() {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel header = new JPanel();
        header.setLayout(new GridLayout(3, 1));
        header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        header.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        playerNameLabel = new JLabel("");
        lifetimeLabel = new JLabel("Lifetime: 0 gp");
        sessionLabel = new JLabel("Session: 0 gp");

        playerNameLabel.setForeground(Color.YELLOW);
        lifetimeLabel.setForeground(Color.WHITE);
        sessionLabel.setForeground(Color.WHITE);

        playerNameLabel.setFont(new Font("Arial", Font.BOLD, 20));
        lifetimeLabel.setFont(new Font("Arial", Font.BOLD, 16));
        sessionLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        header.add(playerNameLabel);
        header.add(lifetimeLabel);
        header.add(sessionLabel);

        add(header, BorderLayout.NORTH);

        // Central area
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BorderLayout());
        centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        final int SCROLL_UNIT_INCREMENT = 16;

        // Log area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        logArea.setForeground(Color.WHITE);

        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setPreferredSize(new Dimension(0, 230));
        logScrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        centerPanel.add(logScrollPane, BorderLayout.NORTH);

        // Notes
        notesArea = new JTextArea();
        notesArea.setEditable(true);
        notesArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        notesArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        notesArea.setForeground(Color.WHITE);

        JScrollPane notesScrollPane = new JScrollPane(notesArea);
        notesScrollPane.setPreferredSize(new Dimension(0, 310));
        notesScrollPane.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);
        centerPanel.add(notesScrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);
    }

    // ------------- PLAYER NAME UPDATE --------------
    public void setPlayerName(String name) {
        if (name == null || name.isEmpty()) {
            playerNameLabel.setText("");
        } else {
            playerNameLabel.setText(name);
        }
    }

    // -------- Update Totals + Apply Color -------------
    public void updateTotals(long lifetime, long session) {
        lifetimeLabel.setText("Lifetime: " + format(lifetime) + " gp");
        sessionLabel.setText("Session: " + format(session) + " gp");

        updateLabelColor(lifetimeLabel, lifetime);
        updateLabelColor(sessionLabel, session);
    }

    private void updateLabelColor(JLabel label, long value) {
        if (value > 0)
            label.setForeground(Color.GREEN);
        else if (value < 0)
            label.setForeground(Color.RED);
        else
            label.setForeground(Color.WHITE);
    }

    public void addLogLine(String text) {
        logArea.append(text + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
    public void clearLogs() { logArea.setText(""); }

    public void setNotesText(String text) {
        notesArea.setText(text);
    }

    public String getNotesText() {
        return notesArea.getText();
    }

    private String format(long gp) {
        return String.format("%,d", gp);
    }
}

