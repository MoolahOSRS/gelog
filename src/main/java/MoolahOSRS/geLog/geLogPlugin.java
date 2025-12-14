// This is the main plugin file
/*
**version 0.40
 */
package MoolahOSRS.geLog;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import MoolahOSRS.geLog.ui.geLogPanel;

@Slf4j
@PluginDescriptor(
        name = "Grand Exchange Log",
        description = "Tracks & logs GE Buy and Sell offers",
        tags = {"Trading", "Notepad"})

public class geLogPlugin extends Plugin {
    /**
     * VARIABLES, ECT
     * This lets us read things from the game, like the player's name
     */
    @Inject private Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ClientThread clientThread;
    @Inject private geLogConfig config;
    @Inject private ItemManager itemManager;

    @Provides
    geLogConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(geLogConfig.class);
    }

    private String playerName;
    private Path baseFolder;
    private Path lifetimeFile;
    private Path sessionFile;
    private Path notesFile;
    private Path historyFile;

    private boolean logFolderCreated = false;
    private boolean sessionResetOnLogin = false;

    private NavigationButton navButton;
    private geLogPanel panel;

    private long lifetimeTotal = 0;
    private long sessionTotal = 0;
    private File lifetime;
    private File session;
    private File noteOSRS;
    private File history;

    private final Map<Integer, GrandExchangeOfferState> lastStates = new HashMap<>();

    private ScheduledExecutorService autosaveExecutor;
    private String lastSavedNotes = "";

    /**
     * SCRIPT START UP
     */
    @Override
    protected void startUp() throws Exception {
        log.info("Grand Exchange Log started!");
        sessionResetOnLogin = false;

        //Creates the GrandExchangeLog Folder
        baseFolder = Path.of(System.getProperty("user.home"), ".runelite", "GrandExchangeLogs");
        try {
            Files.createDirectories(baseFolder);
        }
        catch (IOException e) {
            log.error("Failed creating base folder", e);
        }

        String username = client.getLocalPlayer() != null ?
                client.getLocalPlayer().getName() : "";

        panel = new geLogPanel();
        panel.setNotesText("Notepad (TYPE HERE)");
        //Loads the coins icon on side panel
        BufferedImage icon;
        try {
                
	icon = ImageUtil.loadImageResource(getClass(), "coin_icon.png");
        } catch (Exception e) {
            e.printStackTrace();
            icon = null;
        }

        navButton = NavigationButton.builder()
                .tooltip("Grand Exchange Log")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        // Start autosave executor (will only save once files exist and config.autoSave() true)
        autosaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "geLog-autosave");
            t.setDaemon(true);
            return t;
        });

        // schedule the autosave worker every 5 seconds
        autosaveExecutor.scheduleAtFixedRate(this::autosaveNotesIfNeeded, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * SCRIPT SHUTDOWN
     */
    @Override
    protected void shutDown() throws Exception {
        log.info("Grand Exchange Log stopped!");

        // Stop autosave executor
        if(autosaveExecutor !=null&&!autosaveExecutor.isShutdown()) {
            autosaveExecutor.shutdownNow();
            autosaveExecutor = null;
        }

        if(clientToolbar !=null&&navButton !=null) {
            clientToolbar.removeNavigation(navButton);
        }
        // Reset fields
        navButton = null;
        panel = null;
        logFolderCreated = false;
        playerName = null;
        panel.setPlayerName(null);
        sessionFile = null;
        notesFile = null;
        historyFile = null;
    }

    /**
     * METHODS FOR RUNELITE
     */
    // ---------------- Game State Handling ------------
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        switch (event.getGameState()) {
            case LOGIN_SCREEN:
                // Clear displayed name when logged out
                panel.setPlayerName("");
                playerName = null;
                logFolderCreated = false;
                resetAccountState();
                break;

            case LOGGING_IN:
                resetSession();
                break;

            case HOPPING:
                sessionResetOnLogin = false;
                clientThread.invokeLater(this::updatePlayerName);
                break;

            case LOGGED_IN:
                clientThread.invokeLater(this::updatePlayerName);
                break;
        }
    }

    // ---------------- Restarting ---------------------
    private void resetSession() {
        sessionTotal = 0;
        panel.updateTotals(lifetimeTotal, sessionTotal);

        if (sessionFile != null) {
            try {
                Files.writeString(sessionFile, "0", StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.warn("Failed to write session file during reset", e);
            }
        }
    }

    private void resetAccountState() {
        lifetimeTotal = 0;
        sessionTotal = 0;

        lifetimeFile = null;
        sessionFile = null;
        notesFile = null;
        historyFile = null;

        lifetime = null;
        session = null;
        noteOSRS = null;
        history = null;

        lastStates.clear();
        lastSavedNotes = "";

        panel.updateTotals(0, 0);
        panel.setNotesText("Notepad (TYPE HERE)");


        if (config.clearhistoryOnAccountSwitch()) {
            panel.clearLogs();
        }
    }

    // ---------------- File Operations ----------------
    //Create
    private void createLogFolder() {
        // Already created?
        if (logFolderCreated) return;

        // Schedule safely on the client thread
        clientThread.invokeLater(() -> {
            if (client.getLocalPlayer() == null) return false;

            playerName = client.getLocalPlayer().getName();
            if (playerName == null || playerName.isEmpty()) return false;

            try {
                Path playerFolder = baseFolder.resolve(playerName);
                Files.createDirectories(playerFolder);

                lifetimeFile = playerFolder.resolve("lifetime.txt");
                sessionFile = playerFolder.resolve("session.txt");
                notesFile = playerFolder.resolve("noteOSRS.txt");
                historyFile = playerFolder.resolve("history.txt");

                lifetime = lifetimeFile.toFile();
                session = sessionFile.toFile();
                noteOSRS = notesFile.toFile();
                history = historyFile.toFile();

                // Lifetime file
                if (!lifetime.exists()) {
                    lifetime.createNewFile();
                } else {
                    loadLifetimeTotal();
                }

                // Session file
                if (!session.exists()) {
                    session.createNewFile();
                } else {
                    loadSessionTotal();
                }

                // Notes file
                if (!noteOSRS.exists()) {
                    noteOSRS.createNewFile();
                } else {
                    loadNotes();
                }

                // History file
                if (!Files.exists(historyFile))
                    Files.writeString(historyFile, "", StandardCharsets.UTF_8);

                history = historyFile.toFile();

                logFolderCreated = true;
                log.info("Player folder created for {}", playerName);

            } catch (IOException e) {
                log.error("Failed creating player folder", e);
            }
            return true;
        });
    }

    /*
     * file won’t load is on first launch OR if it was deleted — which is intentional behavior.
     */
    //Load
    private void loadLifetimeTotal() {
        if (lifetime.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(lifetime))) {
                String line = br.readLine();
                if (line != null) lifetimeTotal = Long.parseLong(line.trim());
            } catch (Exception ignored) {}
        }
        panel.updateTotals(lifetimeTotal, sessionTotal);
    }

    private void loadSessionTotal()
    {
        // PROTECTION: never load old stale sessions during login reset
        if (!sessionResetOnLogin)
            return;

        if (session.exists()) {
            try (BufferedReader br = new BufferedReader(new FileReader(session))) {
                String line = br.readLine();
                if (line != null)
                    sessionTotal = Long.parseLong(line.trim());
            } catch (Exception ignored) {}
        }

        panel.updateTotals(lifetimeTotal, sessionTotal);
    }

    private void loadNotes() {
        // load notes from file into panel (per-account file: noteOSRS.txt)
        if (noteOSRS != null && noteOSRS.exists()) {
            try {
                String text = Files.readString(notesFile, StandardCharsets.UTF_8);
                panel.setNotesText(text == null ? "" : text);
                lastSavedNotes = panel.getNotesText();
            } catch (IOException e) {
                log.error("Failed loading notes file", e);
            }
        } else {
            panel.setNotesText("");
            lastSavedNotes = "";
        }
    }

    private void updatePlayerName() {
       if (client.getLocalPlayer() == null) {
        clientThread.invokeLater(this::updatePlayerName);
        return;
       }

    String name = client.getLocalPlayer().getName();

    if (name == null || name.isEmpty()) {
        clientThread.invokeLater(this::updatePlayerName);
        return;
    }

    // Prevent unnecessary reloads
    if (name.equals(playerName))
            return;

    playerName = name;
    panel.setPlayerName(name);

    // Now load/create profile folder for this username
    createLogFolder();
 //   loadLifetimeTotal(); ai  recommend to remove
 //   loadSessionTotal(); ai  recommend to remove
 //   loadNotes(); ai  recommend to remove

    // UI
    panel.setPlayerName(name);
    panel.updateTotals(lifetimeTotal, sessionTotal);

    if (config.clearhistoryOnAccountSwitch())
        panel.clearLogs();
    }

    //Write & Save
    public void saveLifetimeTotal() {
        try {
            Path tmp = lifetimeFile.resolveSibling("lifetime.tmp");
            Files.writeString(tmp, Long.toString(lifetimeTotal), StandardCharsets.UTF_8);
            Files.move(tmp, lifetimeFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Failed saving lifetime total", e);
        }
    }

    public void saveSessionTotal() {
        try {
            Files.writeString(sessionFile, Long.toString(sessionTotal), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed saving session total", e);
        }
    }

    public void saveNotesToFile() {
        if (notesFile == null) return;
        try {
            Files.writeString(notesFile, panel.getNotesText(), StandardCharsets.UTF_8);
            lastSavedNotes = panel.getNotesText();
        } catch (IOException e) {
            log.error("Failed saving notes file", e);
        }
    }


    // autosave worker called periodically
    private void autosaveNotesIfNeeded() {
        try {
            if (!logFolderCreated || notesFile == null) return;
            if (!config.autoSave1()) return;

            String current = panel.getNotesText();
            if (current == null) current = "";

            if (!current.equals(lastSavedNotes)) {
                saveNotesToFile();
            }
        } catch (Exception ex) {
            log.warn("Autosave worker error", ex);
        }
    }

    private String getItemName(int itemId) {
        try {
            return itemManager.getItemComposition(itemId).getName();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event) {
        if (!logFolderCreated || client.getLocalPlayer() == null)
            return;
        if (!logFolderCreated || history == null)
            return;

        GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
        if (offers == null) {
            return;
        }

        int slot = event.getSlot();
        if (slot < 0 || slot >= offers.length) {

            return;
        }

        GrandExchangeOffer offer = offers[slot];
        if (offer == null) {
            return;
        }

        GrandExchangeOfferState state = offer.getState();

        // Prevent duplicate logs for the same state
        GrandExchangeOfferState lastState = lastStates.get(slot);
        if (lastState == state) {
            return;
        }
        lastStates.put(slot, state);

        // Only log fully completed offers
        if (state != GrandExchangeOfferState.BOUGHT && state != GrandExchangeOfferState.SOLD) {
            return;
        }

        int itemId = offer.getItemId();
        String itemName = getItemName(itemId);
        int traded = offer.getQuantitySold();
        int priceEach = offer.getPrice();
        int quantity;
        long totalValue = (long) traded * priceEach;
        String name = getItemName(itemId);


        if (state == GrandExchangeOfferState.SOLD) {
            quantity = offer.getQuantitySold();
            totalValue = (long) quantity * priceEach;

            lifetimeTotal += totalValue;
            sessionTotal += totalValue;
        } else {// BOUGHT
            quantity = offer.getTotalQuantity();
            totalValue = (long) quantity * priceEach;

            lifetimeTotal -= totalValue;
            sessionTotal -= totalValue;
        }

        if (quantity <= 0) {
            return;
        }

        String logLine = String.format(
                "%s | %s | %dx | %d gp each | %d gp",
                state == GrandExchangeOfferState.SOLD ? "Sold" : "Bought",
                itemName,
                quantity,
                priceEach,
                totalValue
        );

        // Write log line
        appendLine(lifetime, logLine);
        appendLine(session, logLine);
        appendLine(history, logLine);

        // Update UI
        panel.addLogLine(logLine);
        panel.updateTotals(lifetimeTotal, sessionTotal);

        // ALWAYS save lifetime immediately (your requirement)
        saveLifetimeTotal();
        saveSessionTotal();
    }

    private void appendLine(File file, String text) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file, true))) {
            bw.write(text);
            bw.newLine();
        } catch (IOException e) {
            log.error("Failed writing to {}", file, e);
        }
    }

}



