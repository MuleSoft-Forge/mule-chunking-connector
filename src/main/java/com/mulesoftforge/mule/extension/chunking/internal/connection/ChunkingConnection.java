package com.mulesoftforge.mule.extension.chunking.internal.connection;

/**
 * Stateless connection for binary streaming operations.
 * Required by PagingProvider interface but not used for state.
 */
public class ChunkingConnection {

    public void invalidate() {
        // No resources to clean up
    }
}
