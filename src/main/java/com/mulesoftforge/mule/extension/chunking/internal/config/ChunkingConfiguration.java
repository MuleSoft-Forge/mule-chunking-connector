package com.mulesoftforge.mule.extension.chunking.internal.config;

import com.mulesoftforge.mule.extension.chunking.internal.connection.ChunkingConnectionProvider;
import com.mulesoftforge.mule.extension.chunking.internal.operation.ChunkOperations;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.Operations;
import org.mule.runtime.extension.api.annotation.connectivity.ConnectionProviders;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.Parameter;
import org.mule.runtime.extension.api.annotation.param.display.DisplayName;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

@Configuration(name = "config")
@Operations({ChunkOperations.class})
@ConnectionProviders({ChunkingConnectionProvider.class})
public class ChunkingConfiguration {

    @Parameter
    @Optional(defaultValue = "65536")
    @DisplayName("Default Chunk Size")
    @Summary("Default chunk size in bytes (default: 65536 = 64KB)")
    private int defaultChunkSize;

    public int getDefaultChunkSize() {
        return defaultChunkSize;
    }
}
