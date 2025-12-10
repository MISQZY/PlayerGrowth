package org.misqzy.playerGrowth.storage;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class H2Storage implements Storage {
    
    private final Plugin plugin;
    private Connection connection;
    private String connectionString;
    
    public H2Storage(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean initialize() {
        try {
            Class.forName("org.h2.Driver");
            
            File dataFolder = new File(plugin.getDataFolder(), "data");
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            
            File dbFile = new File(dataFolder, "database.db");
            connectionString = "jdbc:h2:" + dbFile.getAbsolutePath().replace(".db", "") + ";MODE=MySQL;AUTO_SERVER=TRUE";
            
            connection = DriverManager.getConnection(connectionString);
            createTable();
            
            return testConnection();
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("H2 driver not found! Make sure H2 dependency is included in the JAR.");
            plugin.getLogger().severe("Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize H2 database: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Unexpected error during H2 initialization: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public boolean testConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(connectionString);
            }
            return connection.isValid(5);
        } catch (SQLException e) {
            plugin.getLogger().warning("H2 connection test failed: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to close H2 connection: " + e.getMessage());
        }
    }
    
    private void createTable() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS player_scales (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "scale DOUBLE NOT NULL, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
        }
    }
    
    @Override
    public CompletableFuture<Double> getCustomScale(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    if (!testConnection()) {
                        return null;
                    }
                }
                
                String sql = "SELECT scale FROM player_scales WHERE uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        return rs.getDouble("scale");
                    }
                }
                return null;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get custom scale: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> setCustomScale(UUID playerUuid, double scale) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    if (!testConnection()) {
                        return false;
                    }
                }
                
                String deleteSql = "DELETE FROM player_scales WHERE uuid = ?";
                try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
                    deleteStmt.setString(1, playerUuid.toString());
                    deleteStmt.executeUpdate();
                }
                
                String insertSql = "INSERT INTO player_scales (uuid, scale, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)";
                try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                    insertStmt.setString(1, playerUuid.toString());
                    insertStmt.setDouble(2, scale);
                    insertStmt.executeUpdate();
                    return true;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set custom scale: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> removeCustomScale(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    if (!testConnection()) {
                        return false;
                    }
                }
                
                String sql = "DELETE FROM player_scales WHERE uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    int rows = stmt.executeUpdate();
                    return rows > 0;
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove custom scale: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Boolean> hasCustomScale(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (connection == null || connection.isClosed()) {
                    if (!testConnection()) {
                        return false;
                    }
                }
                
                String sql = "SELECT COUNT(*) FROM player_scales WHERE uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, playerUuid.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        return rs.getInt(1) > 0;
                    }
                }
                return false;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to check custom scale: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
}

