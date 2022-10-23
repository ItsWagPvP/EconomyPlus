package me.itswagpvp.economyplus.listener;

import me.itswagpvp.economyplus.EconomyPlus;
import me.itswagpvp.economyplus.database.CacheManager;
import me.itswagpvp.economyplus.database.misc.Selector;
import me.itswagpvp.economyplus.misc.Updater;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerHandler implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {

        FileConfiguration config = EconomyPlus.plugin.getConfig();
        if (config.get("Updater-Notifications") == null || config.getBoolean("Updater-Notifications", true)) {
            if (event.getPlayer().hasPermission("economyplus.update") || event.getPlayer().isOp()) {
                Updater.checkForPlayerUpdate(event.getPlayer());
            }
        }

        String playerName = Selector.playerToString(event.getPlayer());

        if (!EconomyPlus.getDBType().getList().contains(playerName)) {
            double startingBalance = EconomyPlus.plugin.getConfig().getDouble("Starting-Balance", 0.00D);
            double startingBank = EconomyPlus.plugin.getConfig().getDouble("Starting-Bank-Balance", 0.00D);
            CacheManager.getCache(1).put(playerName, startingBalance);
            CacheManager.getCache(2).put(playerName, startingBank);
            //
            EconomyPlus.getDBType().setTokens(playerName, startingBalance);
            EconomyPlus.getDBType().setBank(playerName, startingBank);
        }
    }
}
