package com.mulesoftforge.mule.extension.chunking.internal.extension;

import com.mulesoftforge.mule.extension.chunking.api.error.ChunkingError;
import com.mulesoftforge.mule.extension.chunking.internal.config.ChunkingConfiguration;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.runtime.extension.api.annotation.error.ErrorTypes;
import org.mule.sdk.api.annotation.JavaVersionSupport;

import static org.mule.sdk.api.meta.JavaVersion.JAVA_17;

@Xml(prefix = "chunking")
@Extension(name = "Chunking")
@Configurations({ChunkingConfiguration.class})
@ErrorTypes(ChunkingError.class)
@JavaVersionSupport({JAVA_17})
public class ChunkingExtension {
}
