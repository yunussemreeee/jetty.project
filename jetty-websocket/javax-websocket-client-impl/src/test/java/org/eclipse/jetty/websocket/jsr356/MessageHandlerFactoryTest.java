//
//  ========================================================================
//  Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.jsr356;

import java.lang.reflect.Type;
import java.util.List;
import javax.websocket.DeploymentException;

import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.eclipse.jetty.websocket.common.scopes.SimpleContainerScope;
import org.eclipse.jetty.websocket.common.scopes.WebSocketContainerScope;
import org.eclipse.jetty.websocket.jsr356.decoders.PrimitiveDecoderMetadataSet;
import org.eclipse.jetty.websocket.jsr356.handlers.ByteArrayPartialHandler;
import org.eclipse.jetty.websocket.jsr356.handlers.StringPartialHandler;
import org.eclipse.jetty.websocket.jsr356.metadata.DecoderMetadata;
import org.eclipse.jetty.websocket.jsr356.metadata.DecoderMetadataSet;
import org.eclipse.jetty.websocket.jsr356.metadata.MessageHandlerMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class MessageHandlerFactoryTest
{
    private MessageHandlerFactory factory;
    private DecoderMetadataSet metadatas;
    private DecoderFactory decoders;

    @BeforeEach
    public void init() throws DeploymentException
    {
        WebSocketContainerScope containerScope = new SimpleContainerScope(WebSocketPolicy.newClientPolicy());

        DecoderFactory primitivesFactory = new DecoderFactory(containerScope, PrimitiveDecoderMetadataSet.INSTANCE);
        metadatas = new DecoderMetadataSet();
        decoders = new DecoderFactory(containerScope, metadatas, primitivesFactory);
        factory = new MessageHandlerFactory();
    }

    @Test
    public void testByteArrayPartial() throws DeploymentException
    {
        List<MessageHandlerMetadata> metadatas = factory.getMetadata(ByteArrayPartialHandler.class);
        assertThat("Metadata.list.size", metadatas.size(), is(1));

        MessageHandlerMetadata handlerMetadata = metadatas.get(0);
        DecoderMetadata decoderMetadata = decoders.getMetadataFor(handlerMetadata.getMessageClass());
        assertThat("Message Type", decoderMetadata.getMessageType(), is(MessageType.BINARY));
        assertThat("Message Class", handlerMetadata.getMessageClass(), is((Type)byte[].class));
    }

    @Test
    public void testStringPartial() throws DeploymentException
    {
        List<MessageHandlerMetadata> metadatas = factory.getMetadata(StringPartialHandler.class);
        assertThat("Metadata.list.size", metadatas.size(), is(1));

        MessageHandlerMetadata handlerMetadata = metadatas.get(0);
        DecoderMetadata decoderMetadata = decoders.getMetadataFor(handlerMetadata.getMessageClass());
        assertThat("Message Type", decoderMetadata.getMessageType(), is(MessageType.TEXT));
        assertThat("Message Class", handlerMetadata.getMessageClass(), is((Type)String.class));
    }
}
