package me.egg82.tfaplus.sql;

import me.egg82.tfaplus.core.*;
import me.egg82.tfaplus.utils.ValidationUtil;
import ninja.egg82.core.SQLQueryResult;
import ninja.egg82.sql.SQL;
import ninja.leaping.configurate.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SQLite {
    private static final Logger logger = LoggerFactory.getLogger(SQLite.class);

    private SQLite() {}

    public static CompletableFuture<Void> createTables(SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + tablePrefix + "login';");
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "login` ("
                        + "`ip` TEXT(45) NOT NULL,"
                        + "`uuid` TEXT(36) NOT NULL DEFAULT 0,"
                        + "`created` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "UNIQUE(`ip`, `uuid`)"
                        + ");");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }).thenRunAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + tablePrefix + "authy';");
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "authy` ("
                        + "`uuid` TEXT(36) NOT NULL,"
                        + "`id` INTEGER NOT NULL DEFAULT 0,"
                        + "UNIQUE(`uuid`)"
                        + ");");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }).thenRunAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + tablePrefix + "totp';");
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "totp` ("
                        + "`uuid` TEXT(36) NOT NULL,"
                        + "`length` INTEGER NOT NULL DEFAULT 0,"
                        + "`key` BLOB NOT NULL,"
                        + "UNIQUE(`uuid`)"
                        + ");");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        }).thenRunAsync(() -> {
            try {
                SQLQueryResult query = sql.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + tablePrefix + "hotp';");
                if (query.getData().length > 0 && query.getData()[0].length > 0 && ((Number) query.getData()[0][0]).intValue() != 0) {
                    return;
                }

                sql.execute("CREATE TABLE `" + tablePrefix + "hotp` ("
                        + "`uuid` TEXT(36) NOT NULL,"
                        + "`length` INTEGER NOT NULL DEFAULT 0,"
                        + "`counter` INTEGER NOT NULL DEFAULT 0,"
                        + "`key` BLOB NOT NULL,"
                        + "UNIQUE(`uuid`)"
                        + ");");
            } catch (SQLException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<SQLFetchResult> loadInfo(SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            List<LoginData> loginData = new ArrayList<>();
            List<AuthyData> authyData = new ArrayList<>();
            List<TOTPData> totpData = new ArrayList<>();
            List<HOTPData> hotpData = new ArrayList<>();
            List<String> removedKeys = new ArrayList<>();

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `ip`, `created` FROM `" + tablePrefix + "login`;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("2faplus:hotp:" + o[0]);
                        removedKeys.add("2faplus:totp:" + o[0]);
                        removedKeys.add("2faplus:authy:" + o[0]);
                        removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                        sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", o[0]);
                        continue;
                    }
                    if (!ValidationUtil.isValidIp((String) o[1])) {
                        removedKeys.add("2faplus:login:" + o[0] + "|" + o[1]);
                        sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `ip`=?;", o[1]);
                        continue;
                    }

                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    String ip = (String) o[1];
                    long created = getTime(o[2]).getTime();

                    loginData.add(new LoginData(uuid, ip, created));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `id` FROM `" + tablePrefix + "authy`;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("2faplus:authy:" + o[0]);
                        removedKeys.add("2faplus:totp:" + o[0]);
                        removedKeys.add("2faplus:hotp:" + o[0]);
                        sql.execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", o[0]);
                        continue;
                    }

                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    long id = ((Number) o[1]).longValue();

                    authyData.add(new AuthyData(uuid, id));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `length`, `key` FROM `" + tablePrefix + "totp`;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("2faplus:uuid:" + o[0]);
                        removedKeys.add("2faplus:authy:" + o[0]);
                        removedKeys.add("2faplus:totp:" + o[0]);
                        removedKeys.add("2faplus:hotp:" + o[0]);
                        sql.execute("DELETE FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", o[0]);
                        continue;
                    }

                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    long length = ((Number) o[1]).longValue();
                    byte[] key = (byte[]) o[2];

                    totpData.add(new TOTPData(uuid, length, key));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            try {
                SQLQueryResult query = sql.query("SELECT `uuid`, `length`, `counter`, `key` FROM `" + tablePrefix + "hotp`;");

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Validate UUID/IP and remove bad data
                    if (!ValidationUtil.isValidUuid((String) o[0])) {
                        removedKeys.add("2faplus:uuid:" + o[0]);
                        removedKeys.add("2faplus:authy:" + o[0]);
                        removedKeys.add("2faplus:totp:" + o[0]);
                        removedKeys.add("2faplus:hotp:" + o[0]);
                        sql.execute("DELETE FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", o[0]);
                        continue;
                    }

                    // Grab all data and convert to more useful object types
                    UUID uuid = UUID.fromString((String) o[0]);
                    long length = ((Number) o[1]).longValue();
                    long counter = ((Number) o[2]).longValue();
                    byte[] key = (byte[]) o[3];

                    hotpData.add(new HOTPData(uuid, length, counter, key));
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return new SQLFetchResult(loginData.toArray(new LoginData[0]), authyData.toArray(new AuthyData[0]), totpData.toArray(new TOTPData[0]), hotpData.toArray(new HOTPData[0]), removedKeys.toArray(new String[0]));
        });
    }

    public static CompletableFuture<LoginData> getLoginData(UUID uuid, String ip, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";
        return CompletableFuture.supplyAsync(() -> {
            LoginData data = null;

            try {
                SQLQueryResult query = sql.query("SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Grab all data and convert to more useful object types
                    long created = getTime(o[0]).getTime();
                    data = new LoginData(uuid, ip, created);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return data;
        });
    }

    public static CompletableFuture<AuthyData> getAuthyData(UUID uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            AuthyData data = null;

            try {
                SQLQueryResult query = sql.query("SELECT `id` FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid.toString());

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Grab all data and convert to more useful object types
                    long id = ((Number) o[0]).longValue();
                    data = new AuthyData(uuid, id);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return data;
        });
    }

    public static CompletableFuture<TOTPData> getTOTPData(UUID uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            TOTPData data = null;

            try {
                SQLQueryResult query = sql.query("SELECT `length`, `key` FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid.toString());

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Grab all data and convert to more useful object types
                    long length = ((Number) o[0]).longValue();
                    byte[] key = (byte[]) o[1];
                    data = new TOTPData(uuid, length, key);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return data;
        });
    }

    public static CompletableFuture<HOTPData> getHOTPData(UUID uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            HOTPData data = null;

            try {
                SQLQueryResult query = sql.query("SELECT `length`, `counter`, `key` FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid.toString());

                // Iterate rows
                for (Object[] o : query.getData()) {
                    // Grab all data and convert to more useful object types
                    long length = ((Number) o[0]).longValue();
                    long counter = ((Number) o[1]).longValue();
                    byte[] key = (byte[]) o[2];
                    data = new HOTPData(uuid, length, counter, key);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return data;
        });
    }

    public static CompletableFuture<LoginData> updateLogin(SQL sql, ConfigurationNode storageConfigNode, UUID uuid, String ip) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            LoginData result = null;

            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "login` (`uuid`, `ip`, `created`) VALUES (?, ?, (SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?));", uuid.toString(), ip, uuid.toString(), ip);
                SQLQueryResult query = sql.query("SELECT `created` FROM `" + tablePrefix + "login` WHERE `uuid`=? AND `ip`=?;", uuid.toString(), ip);

                for (Object[] o : query.getData()) {
                    long created = getTime(o[0]).getTime();
                    result = new LoginData(uuid, ip, created);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return result;
        });
    }

    public static CompletableFuture<AuthyData> updateAuthy(SQL sql, ConfigurationNode storageConfigNode, UUID uuid, long id) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "authy` (`uuid`, `id`) VALUES(?, ?);", uuid.toString(), id);
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return new AuthyData(uuid, id);
        });
    }

    public static CompletableFuture<TOTPData> updateTOTP(SQL sql, ConfigurationNode storageConfigNode, UUID uuid, long length, SecretKey key) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "totp` (`uuid`, `length`, `key`) VALUES(?, ?, ?);", uuid.toString(), length, key.getEncoded());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return new TOTPData(uuid, length, key);
        });
    }

    public static CompletableFuture<HOTPData> updateHOTP(SQL sql, ConfigurationNode storageConfigNode, UUID uuid, long length, long counter, SecretKey key) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.supplyAsync(() -> {
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "hotp` (`uuid`, `length`, `counter`, `key`) VALUES(?, ?, ?, ?);", uuid.toString(), length, counter, key.getEncoded());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return new HOTPData(uuid, length, counter, key);
        });
    }

    public static CompletableFuture<Void> addLogin(LoginData data, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "login` (`uuid`, `ip`, `created`) VALUES (?, ?, ?);", data.getUUID().toString(), data.getIP(), data.getCreated());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Void> addAuthy(AuthyData data, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "authy` (`uuid`, `id`) VALUES (?, ?);", data.getUUID().toString(), data.getID());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Void> addTOTP(TOTPData data, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "totp` (`uuid`, `length`, `key`) VALUES (?, ?, ?);", data.getUUID().toString(), data.getLength(), data.getKey().getEncoded());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Void> addHOTP(HOTPData data, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("INSERT OR REPLACE INTO `" + tablePrefix + "hotp` (`uuid`, `length`, `counter`, `key`) VALUES (?, ?, ?, ?);", data.getUUID().toString(), data.getLength(), data.getCounter(), data.getKey().getEncoded());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Void> delete(UUID uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", uuid.toString());
                sql.execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid.toString());
                sql.execute("DELETE FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid.toString());
                sql.execute("DELETE FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid.toString());
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Void> delete(String uuid, SQL sql, ConfigurationNode storageConfigNode) {
        String tablePrefix = !storageConfigNode.getNode("data", "prefix").getString("").isEmpty() ? storageConfigNode.getNode("data", "prefix").getString() : "2faplus_";

        return CompletableFuture.runAsync(() -> {
            try {
                sql.execute("DELETE FROM `" + tablePrefix + "login` WHERE `uuid`=?;", uuid);
                sql.execute("DELETE FROM `" + tablePrefix + "authy` WHERE `uuid`=?;", uuid);
                sql.execute("DELETE FROM `" + tablePrefix + "totp` WHERE `uuid`=?;", uuid);
                sql.execute("DELETE FROM `" + tablePrefix + "hotp` WHERE `uuid`=?;", uuid);
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }
        });
    }

    public static CompletableFuture<Long> getCurrentTime(SQL sql) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long start = System.currentTimeMillis();
                SQLQueryResult query = sql.query("SELECT CURRENT_TIMESTAMP;");

                for (Object[] o : query.getData()) {
                    return getTime(o[0]).getTime() + (System.currentTimeMillis() - start);
                }
            } catch (SQLException | ClassCastException ex) {
                logger.error(ex.getMessage(), ex);
            }

            return -1L;
        });
    }

    private static Timestamp getTime(Object o) {
        if (o instanceof String) {
            return Timestamp.valueOf((String) o);
        } else if (o instanceof Number) {
            return new Timestamp(((Number) o).longValue());
        }
        return null;
    }
}
