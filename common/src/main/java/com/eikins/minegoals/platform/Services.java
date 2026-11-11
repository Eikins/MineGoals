package com.eikins.minegoals.platform;

import com.eikins.minegoals.Constants;
import com.eikins.minegoals.platform.services.INetworkHelper;
import com.eikins.minegoals.platform.services.IPlatformHelper;

import java.util.ServiceLoader;

/**
 * Loads loader-specific implementations of common interfaces. Each loader lists its implementation in
 * {@code META-INF/services/<interface name>}.
 */
public final class Services {

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final INetworkHelper NETWORK = load(INetworkHelper.class);

    private Services() {
    }

    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        Constants.LOG.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
