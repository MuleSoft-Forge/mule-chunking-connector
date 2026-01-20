package com.mulesoftforge.mule.extension.chunking.internal.connection;

import org.mule.runtime.api.connection.ConnectionException;
import org.mule.runtime.api.connection.ConnectionProvider;
import org.mule.runtime.api.connection.ConnectionValidationResult;

public class ChunkingConnectionProvider implements ConnectionProvider<ChunkingConnection> {

    @Override
    public ChunkingConnection connect() throws ConnectionException {
        return new ChunkingConnection();
    }

    @Override
    public void disconnect(ChunkingConnection connection) {
        connection.invalidate();
    }

    @Override
    public ConnectionValidationResult validate(ChunkingConnection connection) {
        return ConnectionValidationResult.success();
    }
}
