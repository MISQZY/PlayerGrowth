package org.misqzy.playergrowth.common.platform;

import java.io.File;
import java.util.logging.Logger;

/** Handle to whatever platform (Bukkit-API server, proxy, ...) hosts this module. */
public interface Platform {

    String name();

    /** Unique identifier for this server within a network - used to tag/skip sync messages. */
    String serverId();

    File dataFolder();

    Logger logger();

    Scheduler scheduler();
}
