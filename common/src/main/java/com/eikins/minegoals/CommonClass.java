package com.eikins.minegoals;

import com.eikins.minegoals.platform.Services;

/** Shared initialisation, called by each loader's entry point. */
public final class CommonClass {

    private CommonClass() {
    }

    public static void init() {
        Constants.LOG.info("MineGoals initialising on {}", Services.PLATFORM.getPlatformName());
    }
}
