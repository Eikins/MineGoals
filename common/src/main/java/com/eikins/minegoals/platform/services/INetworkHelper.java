package com.eikins.minegoals.platform.services;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Loader-specific networking, implemented by each loader and found through {@link java.util.ServiceLoader}. */
public interface INetworkHelper {

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
}
