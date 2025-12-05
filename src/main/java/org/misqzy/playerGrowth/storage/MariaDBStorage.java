package org.misqzy.playerGrowth.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MariaDBStorage implements Storage {
    
    private final Plugin plugin;
    private HikariDataSource dataSource;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int maxPoolSize;
    private final int minIdle;
    private final long connectionTimeout;
    private final long idleTimeout;
    private final long maxLifetime;
    
    public MariaDBStorage(Plugin plugin, String host, int port, String database, 
                         String username, String password, int maxPoolSize, int minIdle,
                         long connectionTimeout, long idleTimeout, long maxLifetime) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.maxPoolSize = maxPoolSize;
        this.minIdle = minIdle;
        this.connectionTimeout = connectionTimeout;
        this.idleTimeout = idleTimeout;
        this.maxLifetime = maxLifetime;
    }
    
    @Override
    public boolean initialize() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC");
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.mariadb.jdbc.Driver");
            
            config.setMaximumPoolSize(maxPoolSize);
            config.setMinimumIdle(minIdle);
            config.setConnectionTimeout(connectionTimeout);
            config.setIdleTimeout(idleTimeout);
            config.setMaxLifetime(maxLifetime);
            config.setLeakDetectionThreshold(60000);
            
            dataSource = new HikariDataSource(config);
            createTable();
            
            return testConnection();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize MariaDB database: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean testConnection() {
        try {
            if (dataSource == null || dataSource.isClosed()) {
                return false;
            }
            try (Connection conn = dataSource.getConnection()) {
                return conn.isValid(5);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("MariaDB connection test failed: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
    
    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS player_scales (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "scale DOUBLE NOT NULL, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }
    
    @Override
    public CompletableFuture<Double> getCustomScale(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!testConnection()) {
                    return null;
                }
                
                String sql = "SELECT scale FROM player_scales WHERE uuid = ?";
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        return rs.getDouble("scale");
                    }
                }
                return null;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get custom scale: " + e.getMessage());
                return null;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> setCustomScale(UUID playerUuid, double scale) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!testConnection()) {
                    return false;
                }
                
                String sql = "INSERT INTO player_scales (uuid, scale, updated_at) VALUES (?, ?, NOW()) " +
                        "ON DUPLICATE KEY UPDATE scale = ?, updated_at = NOW()";
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    stmt.setDouble(2, scale);
                    stmt.setDouble(3, scale);
                    stmt.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set custom scale: " + e.getMessage());
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> removeCustomScale(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!testConnection()) {
                    return false;
                }
                
                String sql = "DELETE FROM player_scales WHERE uuid = ?";
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    int rows = stmt.executeUpdate();
                    return rows > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove custom scale: " + e.getMessage());
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> hasCustomScale(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!testConnection()) {
                    return false;
                }
                
                String sql = "SELECT COUNT(*) FROM player_scales WHERE uuid = ?";
                try (Connection conn = dataSource.getConnection();
                     PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
                return false;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to check custom scale: " + e.getMessage());
                return false;
            }
        });
    }
}

