package me.itswagpvp.economyplus;

import me.itswagpvp.economyplus.bank.commands.Bank;
import me.itswagpvp.economyplus.bank.other.InterestsManager;
import me.itswagpvp.economyplus.commands.*;
import me.itswagpvp.economyplus.database.CacheManager;
import me.itswagpvp.economyplus.database.misc.DatabaseType;
import me.itswagpvp.economyplus.database.misc.StorageMode;
import me.itswagpvp.economyplus.database.mysql.MySQL;
import me.itswagpvp.economyplus.database.sqlite.SQLite;
import me.itswagpvp.economyplus.hooks.Loader;
import me.itswagpvp.economyplus.hooks.PlaceholderAPI;
import me.itswagpvp.economyplus.hooks.holograms.HolographicDisplays;
import me.itswagpvp.economyplus.listener.PlayerHandler;
import me.itswagpvp.economyplus.messages.Messages;
import me.itswagpvp.economyplus.metrics.bStats;
import me.itswagpvp.economyplus.misc.*;
import me.itswagpvp.economyplus.vault.VEconomy;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;

import static me.itswagpvp.economyplus.messages.Messages.getMessageConfig;
import static org.bukkit.Bukkit.getServer;

public class EconomyPlus extends JavaPlugin {

    // Plugin instance
    public static EconomyPlus plugin;

    // Messages
    public static String lang = "EN";
    // BalTop
    public static BalTopManager balTopManager;

    // Debug mode
    public static boolean debugMode;
    // Database
    private static DatabaseType dbType = DatabaseType.UNDEFINED;
    private static StorageMode storageMode = StorageMode.UNDEFINED;
    //Updater updater;
    // YAML Database (data.yml)
    private File ymlFile;
    public static FileConfiguration ymlConfig;

    // Returns the DatabaseType (MYSQL/H2/YAML/Undefined)
    public static DatabaseType getDBType() {
        return dbType;
    }

    // Returns the StorageMode (NICKNAME/UUID)
    public static StorageMode getStorageMode() {
        return storageMode;
    }

    // Made for /ep convert
    public void setStorageMode(String newStorageMode) {
        storageMode = StorageMode.valueOf(newStorageMode);
        getConfig().set("Database.Mode", newStorageMode);
        saveConfig();
    }

    long before;

    public void onLoad() {

        // Plugin startup logic

        before = System.currentTimeMillis();

        plugin = this;

        saveDefaultConfig();

        if (getConfig().getBoolean("Debug-Mode", false)) {
            debugMode = true;
            getLogger().setLevel(Level.FINEST);
        }

        getConfig().options().copyDefaults(true);

        new StorageManager().createStorageConfig();

        if (!setupEconomy()) {
            vaultError("Can't find Vault!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadDatabase();

        loadEconomy();

        if (dbType == DatabaseType.UNDEFINED) {
            Bukkit.getConsoleSender().sendMessage("§c[EconomyPlus] Unable to start the plugin without a valid database option!");
            getServer().getPluginManager().disablePlugin(this);
        }

    }

    String vault = "";

    @Override
    public void onEnable() {

        new Loader().loadPlaceholderAPI();

        double cver = Double.parseDouble(getConfig().getString("Version"));
        double pver = Double.parseDouble(getDescription().getVersion());

        Bukkit.getConsoleSender().sendMessage("§8+------------------------------------+");
        Bukkit.getConsoleSender().sendMessage("             §dEconomy§5Plus");
        Bukkit.getConsoleSender().sendMessage("            §aEnabled §dv" + pver);
        Bukkit.getConsoleSender().sendMessage("§8");

        Bukkit.getConsoleSender().sendMessage("§f-> §cLoading core:");

        Bukkit.getConsoleSender().sendMessage("   - §fStorage-Mode: §a" + storageMode.toString());

        Bukkit.getConsoleSender().sendMessage("   - §fDatabase: §bLoaded (" + dbType.toString().replace("H2", "SQLite") + ")");

        enableDatabase();

        Bukkit.getConsoleSender().sendMessage("   - §fVault: " + vault);

        loadCommands();

        loadMessages();

        loadMetrics();

        sanityCheck();

        loadEvents();

        Bukkit.getConsoleSender().sendMessage("§8");

        if(plugin.getConfig().getBoolean("Hooks.PlaceholderAPI", true) || plugin.getConfig().getBoolean("Hooks.HolographicDisplays", true)) {
            Bukkit.getConsoleSender().sendMessage("§f-> §cLoading hooks:");
            loadHolograms();
            Bukkit.getConsoleSender().sendMessage(new Loader().placeholder);
            Bukkit.getConsoleSender().sendMessage("§f");
        }

        if (cver != pver) {

            String aorb; //ahead or behind
            int outdated; //versions outdated value

            if (cver > pver) { //ahead versions (could auto fix maybe but prob not?)
                outdated = Integer.parseInt(String.valueOf(Math.round((cver - pver)/0.1)).replace(".0", ""));
                aorb = "ahead";
            } else { //behind versions (outdated)
                outdated = Integer.parseInt(String.valueOf(Math.round((pver - cver)/0.1)).replace(".0", ""));
                aorb = "behind";
            }

            Bukkit.getConsoleSender().sendMessage("§f-> §eYour config.yml is outdated!");
            Bukkit.getConsoleSender().sendMessage("   - §fConfig: " + "§c" + cver + " (" + outdated + " versions " + aorb + ")");
            Bukkit.getConsoleSender().sendMessage("   - §fPlugin: " + "§d" + getDescription().getVersion());
            Bukkit.getConsoleSender().sendMessage("");
        }

        Updater.check();

        Bukkit.getConsoleSender().sendMessage("§8+---------------[§a " + (System.currentTimeMillis() - before) + "ms §8]-------------+");

        if (pver >= Updater.getLatestVersion()) {
            Bukkit.getConsoleSender().sendMessage("[EconomyPlus] You are up to date! §d(v" + pver + ")");
        }

        if (plugin.getConfig().getBoolean("Bank.Enabled", true) && plugin.getConfig().getBoolean("Bank.Interests.Enabled", true)) {
            new InterestsManager().startBankInterests();
        }

        //
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic

        Bukkit.getConsoleSender().sendMessage("§8+------------------------------------+");
        Bukkit.getConsoleSender().sendMessage("             §dEconomy§5Plus");
        Bukkit.getConsoleSender().sendMessage("              §cDisabling");
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§f-> §cStopping threads...");
        ThreadsUtils.stopAllThreads();

        Bukkit.getConsoleSender().sendMessage("§f-> §cClosing database connection");

        try {
            dbType.close();
            Bukkit.getScheduler().cancelTasks(this);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

        Bukkit.getConsoleSender().sendMessage("§8+------------------------------------+");

    }

    // Hook into VaultEconomy
    private void loadEconomy() {

        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
            getServer().getServicesManager().register(net.milkbowl.vault.economy.Economy.class, new VEconomy(this), this, ServicePriority.Normal);
        } catch (ClassNotFoundException e) {
            vault = "§CError\n" + e.getMessage();
            return;
        }

        vault = "§6Hooked";

    }

    private void loadDatabase() {

        // Select how the plugin needs to storage the player datas
        if (getConfig().getString("Database.Mode", "NICKNAME").equalsIgnoreCase("UUID")) {
            storageMode = StorageMode.UUID;
        } else if (getConfig().getString("Database.Mode", "NICKNAME").equalsIgnoreCase("NICKNAME")) {
            storageMode = StorageMode.NICKNAME;
        } else {
            storageMode = StorageMode.UUID;
        }

        // Detect and set the type of database
        if (getConfig().getString("Database.Type").equalsIgnoreCase("MySQL")) {
            try {
                new MySQL().connect();
                new MySQL().createTable();
                new MySQL().updateTable();
            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage("§fDatabase: §cError (MySQL)");
                Bukkit.getConsoleSender().sendMessage(e.getMessage());
                return;
            }
            dbType = DatabaseType.MySQL;
        } else if (getConfig().getString("Database.Type").equalsIgnoreCase("H2")) {
            try {
                new SQLite().load();
            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage("§fDatabase: §cError (SQLite)");
                e.printStackTrace();
                return;
            }

            dbType = DatabaseType.H2;
        } else if (getConfig().getString("Database.Type").equalsIgnoreCase("YAML")) {
            try {
                createYMLStorage();
            } catch (Exception e) {
                Bukkit.getConsoleSender().sendMessage("§fDatabase: §cError (YAML)");
                Bukkit.getConsoleSender().sendMessage(e.getMessage());
                return;
            }

            dbType = DatabaseType.YAML;
        } else {
            dbType = DatabaseType.UNDEFINED;
        }

    }

    private void enableDatabase() {
        // Load the cache for the database - Vault API
        if (dbType == DatabaseType.MySQL) {
            new CacheManager().cacheOnlineDatabase();
            long period = getConfig().getLong("Database.Cache.MySQL", 10) * 20;
            Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> new CacheManager().cacheOnlineDatabase(), 120, period);
        } else {
            new CacheManager().cacheLocalDatabase();
            new CacheManager().startAutoSave();
        }

        Bukkit.getConsoleSender().sendMessage("     - §fCaching accounts...");
    }

    private void loadEvents() {
        try {
            Bukkit.getPluginManager().registerEvents(new PlayerHandler(), this);
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("   - §cError loading listeners");
            Bukkit.getConsoleSender().sendMessage(e.getMessage());
        }
    }

    private void loadCommands() {

        try {

            getCommand("baltop").setExecutor(new BalTop());
            getCommand("baltop").setTabCompleter(new TabCompleterLoader());

            getCommand("economyplus").setExecutor(new Main());
            getCommand("economyplus").setTabCompleter(new TabCompleterLoader());

            getCommand("bal").setExecutor(new Bal());
            getCommand("bal").setTabCompleter(new TabCompleterLoader());

            getCommand("pay").setExecutor(new Pay());
            getCommand("pay").setTabCompleter(new TabCompleterLoader());

            getCommand("eco").setExecutor(new Eco());
            getCommand("eco").setTabCompleter(new TabCompleterLoader());

            if (getConfig().getBoolean("Bank.Enabled")) {
                getCommand("bank").setExecutor(new Bank());
                getCommand("bank").setTabCompleter(new TabCompleterLoader());
            }

            getCommand("paytoggle").setExecutor(new PayToggle());
            getCommand("paytoggle").setTabCompleter(new TabCompleterLoader());
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("   - §fCommands: §cError");
            Bukkit.getConsoleSender().sendMessage(e.getMessage());
            return;
        }
        Bukkit.getConsoleSender().sendMessage("   - §fCommands: §aLoaded");
    }

    // Loads the bStats metrics
    private void loadMetrics() {

        if (!getConfig().getBoolean("Metrics")) return;

        try {
            new bStats(this, 11565);
        } catch (Exception e) {
            Bukkit.getConsoleSender().sendMessage("   - §cError loading bStats");
            Bukkit.getConsoleSender().sendMessage(e.getMessage());
        }
    }

    private void loadHolograms() {

        if (!getConfig().getBoolean("Hooks.HolographicDisplays")) return;

        if (getServer().getPluginManager().getPlugin("HolographicDisplays") == null) {
            Bukkit.getConsoleSender().sendMessage("   - §fHolographicDisplays: §cCan't find the jar!");
            return;
        }

        try {

            if (new StorageManager().getStorageConfig().getString("Hologram.BalTop.World") != null) {

                Bukkit.getConsoleSender().sendMessage("   - §fHolographicDisplays: §aHooked!");

                Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        new HolographicDisplays().createHologram();
                    }
                }, 1);

                return;
            }

        } catch (Exception e) {

            Bukkit.getConsoleSender().sendMessage("   - §fHolographicDisplays: §cError!");
            Bukkit.getConsoleSender().sendMessage(e.getMessage());
            return;

        }

        Bukkit.getConsoleSender().sendMessage("   - §fHolographicDisplays: §aHooked!");

    }

    private void loadMessages() {

        Messages.load();

        String messages = getConfig().getString("Language");
        if(!(Messages.getMessageConfig(messages.toUpperCase()) == null)) {
            lang = messages.toUpperCase();
        } else {
            Bukkit.getConsoleSender().sendMessage("   - §fMessages: §cInvalid file! (" + messages + "), using EN");
        }

    }

    public BalTopManager getBalTopManager() {

        if (balTopManager == null) {
            balTopManager = new BalTopManager();
            new BalTopManager();
        }
        return balTopManager;
    }

    // Controls if there's Vault installed
    private boolean setupEconomy() {
        return getServer().getPluginManager().getPlugin("Vault") != null;
    }

    private void sanityCheck() {
        if (getServer().getPluginManager().isPluginEnabled("Vault")) return;

        vaultError("Vault is not enabled.");
        getServer().getPluginManager().disablePlugin(this);
    }

    private void vaultError(String specific) {
        Bukkit.getConsoleSender().sendMessage("§8+------------------------------------+");
        Bukkit.getConsoleSender().sendMessage("             §dEconomy§5Plus");
        Bukkit.getConsoleSender().sendMessage("              §cDisabling");
        Bukkit.getConsoleSender().sendMessage("§8");
        Bukkit.getConsoleSender().sendMessage("§f-> §c" + specific);
        Bukkit.getConsoleSender().sendMessage("§8+------------------------------------+");
    }

    // Get the string from /messages/file.yml and format it with color codes (hex for 1.16+)
    public String getMessage(String path) {

        if (getMessageConfig(lang).get(path) == null) {
            return path;
        }

        String rawMessage = getMessageConfig(lang).getString(path);

        assert rawMessage != null;

        //TODO Keep this only for implementing #isMessageEnabled
        if (rawMessage.equalsIgnoreCase("none")) {
            return "";
        }

        if (Utils.supportRGBColors()) {
            String hexMessage = Utils.hexColor(rawMessage);
            return ChatColor.translateAlternateColorCodes('&', hexMessage);
        }

        return ChatColor.translateAlternateColorCodes('&', rawMessage);
    }

    public boolean isMessageEnabled(String path) {
        if (!getMessageConfig(lang).isString(path)) {
            return false;
        }

        return !getMessageConfig(lang).getString(path).equalsIgnoreCase("none");
    }

    // Returns data.yml if DatabaseType is YAML
    public FileConfiguration getYMLData() {
        return ymlConfig;
    }

    // Safe-save data.yml
    public void saveYMLConfig() {
        try {
            ymlConfig.save(ymlFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Create data.yml if DatabaseType is YAML
    public void createYMLStorage() {
        ymlFile = new File(getDataFolder(), "data.yml");
        if (!ymlFile.exists()) {
            ymlFile.getParentFile().mkdirs();
            saveResource("data.yml", false);
        }

        loadYML();
    }

    // Load the updated data.yml
    private void loadYML() {
        ymlConfig = new YamlConfiguration();
        try {
            ymlConfig.load(ymlFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    //log something
    public static void log(String value) {
        Bukkit.getConsoleSender().sendMessage("[EconomyPlus] " + ChatColor.translateAlternateColorCodes('&', value));
    }

}