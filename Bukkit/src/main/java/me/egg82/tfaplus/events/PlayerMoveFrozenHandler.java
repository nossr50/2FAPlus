package me.egg82.tfaplus.events;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.CollectionProvider;
import me.egg82.tfaplus.utils.LogUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class PlayerMoveFrozenHandler implements Consumer<PlayerMoveEvent> {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final LoadingCache<UUID, Boolean> recentlyMoved = Caffeine.newBuilder().expireAfterWrite(10L, TimeUnit.SECONDS).build(k -> Boolean.FALSE);

    public void accept(PlayerMoveEvent event) {
        if (event.isCancelled()) {
            return;
        }

        if (!CollectionProvider.getFrozen().containsKey(event.getPlayer().getUniqueId())) {
            return;
        }

        if (areEqualXZ(event.getFrom(), event.getTo()) && event.getTo().getY() < event.getFrom().getY()) {
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

        if (cachedConfig.getFreeze().getMove()) {
            if (!recentlyMoved.get(event.getPlayer().getUniqueId())) {
                recentlyMoved.put(event.getPlayer().getUniqueId(), Boolean.TRUE);
                event.getPlayer().sendMessage(LogUtil.getHeading() + ChatColor.DARK_RED + "You must first authenticate with your 2FA code before moving!");
            }
            event.setCancelled(true);
        }
    }

    public static boolean areEqualXZ(Location from, Location to) {
        return from.getWorld().equals(to.getWorld()) && from.getX() == to.getX() && from.getZ() == to.getZ();
    }
}
