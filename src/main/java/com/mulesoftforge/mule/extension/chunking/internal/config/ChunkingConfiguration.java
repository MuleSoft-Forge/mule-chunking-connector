package com.mulesoftforge.mule.extension.chunking.internal.config;

import com.mulesoftforge.mule.extension.chunking.internal.operation.ChunkOperations;
import org.mule.runtime.extension.api.annotation.Configuration;
import org.mule.runtime.extension.api.annotation.Operations;

/**
 * The connector's global configuration element. It has no settings, and read-chunked does not use one, so an
 * application needs none.
 *
 * @since 0.1.0
 */
@Configuration(name = "config")
@Operations({ChunkOperations.class})
public class ChunkingConfiguration {
}
