package me.egg82.tfaplus.events;

import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.LogUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.bukkit.ChatColor;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

public class InventoryDragFrozenHandler implements Consumer<InventoryDragEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void accept(InventoryDragEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!CollectionProvider.getFrozen().containsKey(event.getWhoClicked().getUniqueId())) {
            return;
        }

        CachedConfigValues cachedConfig;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            event.setCancelled(true); // Assume event cancellation
            return;
        }

        if (cachedConfig.getFreeze().getInventory()) {
            event.getWhoClicked().sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "You must first authenticate with your 2FA code before doing that!");
            event.setCancelled(true);
        }
    }
}
