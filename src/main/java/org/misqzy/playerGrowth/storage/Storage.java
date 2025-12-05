package org.misqzy.playerGrowth.storage;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Storage {
    
    boolean initialize();
    
    boolean testConnection();
    
    void close();
    
    CompletableFuture<Double> getCustomScale(UUID playerUuid);
    
    CompletableFuture<Boolean> setCustomScale(UUID playerUuid, double scale);
    
    CompletableFuture<Boolean> removeCustomScale(UUID playerUuid);
    
    CompletableFuture<Boolean> hasCustomScale(UUID playerUuid);
}

