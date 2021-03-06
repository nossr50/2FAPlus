package me.egg82.tfaplus;

import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.services.InternalAPI;
import me.egg82.tfaplus.sql.MySQL;
import me.egg82.tfaplus.sql.SQLite;
import me.egg82.tfaplus.utils.ConfigUtil;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class TFAAPI {
    private static final Logger logger = LoggerFactory.getLogger(TFAAPI.class);

    private static final TFAAPI api = new TFAAPI();
    private final InternalAPI internalApi = new InternalAPI();

    private TFAAPI() {}

    public static TFAAPI getInstance() { return api; }

    /**
     * Returns the current time in millis according to the SQL database server
     *
     * @return The current time, in millis, from the database server
     */
    public long getCurrentSQLTime() {
        CachedConfigValues cachedConfig;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return -1L;
        }

        try {
            if (cachedConfig.getSQLType() == SQLType.MySQL) {
                return MySQL.getCurrentTime(cachedConfig.getSQL()).get();
            } else if (cachedConfig.getSQLType() == SQLType.SQLite) {
                return SQLite.getCurrentTime(cachedConfig.getSQL()).get();
            }
        } catch (ExecutionException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            logger.error(ex.getMessage(), ex);
            Thread.currentThread().interrupt();
        }

        return -1L;
    }

    /**
     * Register a new Authy user from an existing player
     *
     * @param uuid The player UUID
     * @param email The user's e-mail address
     * @param phone The user's phone number
     * @return Whether or not the registration was successful
     */
    public boolean registerAuthy(UUID uuid, String email, String phone) { return registerAuthy(uuid, email, phone, "1"); }

    /**
     * Register a new Authy user from an existing player
     *
     * @param uuid The player UUID
     * @param email The user's e-mail address
     * @param phone The user's phone number
     * @param countryCode The user's phone numbers' country code
     * @return Whether or not the registration was successful
     */
    public boolean registerAuthy(UUID uuid, String email, String phone, String countryCode) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (email == null) {
            throw new IllegalArgumentException("email cannot be null.");
        }
        if (email.isEmpty()) {
            throw new IllegalArgumentException("email cannot be empty.");
        }
        if (phone == null) {
            throw new IllegalArgumentException("phone cannot be null.");
        }
        if (phone.isEmpty()) {
            throw new IllegalArgumentException("phone cannot be empty.");
        }
        if (countryCode == null) {
            throw new IllegalArgumentException("countryCode cannot be null.");
        }
        if (countryCode.isEmpty()) {
            throw new IllegalArgumentException("countryCode cannot be empty.");
        }

        if (!ConfigUtil.getCachedConfig().getAuthy().isPresent()) {
            logger.error("Authy is not present (missing API key in config?)");
            return false;
        }

        return internalApi.registerAuthy(uuid, email, phone, countryCode);
    }

    /**
     * Register a new TOTP user from an existing player
     *
     * @param uuid The player UUID
     * @param codeLength The length of the TOTP code (eg. 6 would generate a 6-digit code)
     * @return A base32-encoded private key, or null if error
     */
    public String registerTOTP(UUID uuid, long codeLength) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (codeLength <= 0) {
            throw new IllegalArgumentException("codeLength cannot be <= 0.");
        }

        return internalApi.registerTOTP(uuid, codeLength);
    }

    /**
     * Register a new HOTP user from an existing player
     *
     * @param uuid The player UUID
     * @param codeLength The length of the HOTP code (eg. 6 would generate a 6-digit code)
     * @return A base32-encoded private key, or null if error
     */
    public String registerHOTP(UUID uuid, long codeLength) { return registerHOTP(uuid, codeLength, 0L); }

    /**
     * Register a new HOTP user from an existing player
     *
     * @param uuid The player UUID
     * @param codeLength The length of the HOTP code (eg. 6 would generate a 6-digit code)
     * @param initialCounterValue The initial value of the HOTP counter
     * @return A base32-encoded private key, or null if error
     */
    public String registerHOTP(UUID uuid, long codeLength, long initialCounterValue) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (codeLength <= 0) {
            throw new IllegalArgumentException("codeLength cannot be <= 0.");
        }
        if (initialCounterValue < 0) {
            throw new IllegalArgumentException("initialCounterValue cannot be < 0.");
        }

        return internalApi.registerHOTP(uuid, codeLength, initialCounterValue);
    }

    /**
     * Re-synchronizes the server HOTP counter with the user's HOTP counter
     * using the tokens provided. The tokens will be a sequence provided from the client
     * that the server will then "seek" to in order to re-set the counter.
     *
     * eg. The user will generate the next X number of tokens from their client (in order) and
     * the server will then re-set its counter to match that of the client's using that token sequence.
     *
     * @param uuid The player UUID
     * @param tokens The token sequence provided by the user
     * @return Whether or not the seek/re-set was successful.
     */
    public boolean seekHOTPCounter(UUID uuid, Collection<String> tokens) {
        if (tokens == null) {
            throw new IllegalArgumentException("tokens cannot be null.");
        }

        return seekHOTPCounter(uuid, tokens.toArray(new String[0]));
    }

    /**
     * Re-synchronizes the server HOTP counter with the user's HOTP counter
     * using the tokens provided. The tokens will be a sequence provided from the client
     * that the server will then "seek" to in order to re-set the counter.
     *
     * eg. The user will generate the next X number of tokens from their client (in order) and
     * the server will then re-set its counter to match that of the client's using that token sequence.
     *
     * @param uuid The player UUID
     * @param tokens The token sequence provided by the user
     * @return Whether or not the seek/re-set was successful.
     */
    public boolean seekHOTPCounter(UUID uuid, String[] tokens) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (tokens == null) {
            throw new IllegalArgumentException("tokens cannot be null.");
        }
        if (tokens.length <= 1) {
            throw new IllegalArgumentException("tokens length cannot be <= 1");
        }

        return internalApi.seekHOTPCounter(uuid, tokens);
    }

    /**
     * Returns the current registration status of a player
     *
     * @param uuid The player UUID
     * @return Whether or not the player is currently registered
     */
    public boolean isRegistered(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        return internalApi.isRegistered(uuid);
    }

    /**
     * Deletes an existing user from a player
     *
     * @param uuid The player UUID
     * @return Whether or not the deletion was successful
     */
    public boolean delete(UUID uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        return internalApi.delete(uuid, ConfigUtil.getCachedConfig().getAuthy().isPresent() ? ConfigUtil.getCachedConfig().getAuthy().get().getUsers() : null);
    }

    /**
     * Returns the current verification status of a player
     *
     * @param uuid The player UUID
     * @param refresh Whether or not to reset the verification timer
     * @return Whether or not the player is currently verified through the verification timeout
     */
    public boolean isVerified(UUID uuid, boolean refresh) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }

        return internalApi.isVerified(uuid, refresh);
    }

    /**
     * Forces a verification check for the player
     *
     * @param uuid The player UUID
     * @param token 2FA token to verify against
     * @return An optional boolean value. Empty = error, true = success, false = failure
     */
    public Optional<Boolean> verify(UUID uuid, String token) {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid cannot be null.");
        }
        if (token == null) {
            throw new IllegalArgumentException("token cannot be null.");
        }
        if (token.isEmpty()) {
            throw new IllegalArgumentException("token cannot be empty.");
        }

            if (ConfigUtil.getAuthy().isPresent() && internalApi.hasAuthy(uuid)) {
                return internalApi.verifyAuthy(uuid, token);
            } else if (internalApi.hasTOTP(uuid)) {
                return internalApi.verifyTOTP(uuid, token);
            } else if (internalApi.hasHOTP(uuid)) {
                return internalApi.verifyHOTP(uuid, token);
            } else {
                return Optional.empty();
            }
    }

    public static Logger getLogger() {
        return logger;
    }
}
