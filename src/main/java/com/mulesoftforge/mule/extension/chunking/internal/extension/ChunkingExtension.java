package com.mulesoftforge.mule.extension.chunking.internal.extension;

import com.mulesoftforge.mule.extension.chunking.api.error.ChunkingError;
import com.mulesoftforge.mule.extension.chunking.api.streaming.ChunkingStreamingStrategy;
import com.mulesoftforge.mule.extension.chunking.api.streaming.FileStoreStrategy;
import com.mulesoftforge.mule.extension.chunking.api.streaming.InMemoryStrategy;
import com.mulesoftforge.mule.extension.chunking.api.streaming.NonRepeatableStrategy;
import com.mulesoftforge.mule.extension.chunking.api.streaming.SlidingWindowStrategy;
import com.mulesoftforge.mule.extension.chunking.internal.config.ChunkingConfiguration;
import org.mule.runtime.extension.api.annotation.Configurations;
import org.mule.runtime.extension.api.annotation.Extension;
import org.mule.runtime.extension.api.annotation.SubTypeMapping;
import org.mule.runtime.extension.api.annotation.dsl.xml.Xml;
import org.mule.runtime.extension.api.annotation.error.ErrorTypes;
import org.mule.sdk.api.annotation.JavaVersionSupport;

import static org.mule.sdk.api.meta.JavaVersion.JAVA_17;

@Xml(prefix = "chunking")
@Extension(name = "Chunking")
@Configurations({ChunkingConfiguration.class})
@ErrorTypes(ChunkingError.class)
@JavaVersionSupport({JAVA_17})
@SubTypeMapping(baseType = ChunkingStreamingStrategy.class,
                subTypes = {NonRepeatableStrategy.class,
                           InMemoryStrategy.class,
                           FileStoreStrategy.class,
                           SlidingWindowStrategy.class})
public class ChunkingExtension {
}
