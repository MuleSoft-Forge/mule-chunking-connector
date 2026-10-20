package com.mulesoftforge.mule.extension.chunking.internal.config;

import com.mulesoftforge.mule.extension.chunking.internal.operation.ChunkOperations;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.Operations;

@Configuration(name = "config")
@Operations({ChunkOperations.class})
public class ChunkingConfiguration {
    // No configuration parameters needed - operation has its own defaults
}
