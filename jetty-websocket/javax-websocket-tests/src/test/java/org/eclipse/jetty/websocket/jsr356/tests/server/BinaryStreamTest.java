//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.websocket.jsr356.tests.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.core.CloseStatus;
import org.eclipse.jetty.websocket.core.frames.BinaryFrame;
import org.eclipse.jetty.websocket.core.frames.CloseFrame;
import org.eclipse.jetty.websocket.core.frames.OpCode;
import org.eclipse.jetty.websocket.core.frames.WebSocketFrame;
import org.eclipse.jetty.websocket.jsr356.tests.DataUtils;
import org.eclipse.jetty.websocket.jsr356.tests.LocalFuzzer;
import org.eclipse.jetty.websocket.jsr356.tests.LocalServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class BinaryStreamTest
{
    private static final String PATH = "/echo";
    
    private static LocalServer server;

    @BeforeClass
    public static void startServer() throws Exception
    {
        server = new LocalServer();
        ServerEndpointConfig config = ServerEndpointConfig.Builder.create(ServerBinaryStreamer.class, PATH).build();
        server.getServerContainer().addEndpoint(config);
        server.start();
    }
    
    @AfterClass
    public static void stopServer() throws Exception
    {
        server.stop();
    }
    
    @Test
    public void testEchoWithMediumMessage() throws Exception
    {
        testEcho(1024);
    }
    
    @Test
    public void testLargestMessage() throws Exception
    {
        testEcho(server.getServerContainer().getDefaultMaxBinaryMessageBufferSize());
    }
    
    private byte[] newData(int size)
    {
        byte[] pattern = "01234567890abcdefghijlklmopqrstuvwxyz".getBytes(StandardCharsets.UTF_8);
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++)
        {
            data[i] = pattern[i % pattern.length];
        }
        return data;
    }
    
    private void testEcho(int size) throws Exception
    {
        byte[] data = newData(size);
        
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new BinaryFrame().setPayload(data));
        send.add(new CloseFrame().setPayload(CloseStatus.NORMAL));
        
        ByteBuffer expectedMessage = DataUtils.copyOf(data);
        
        try (LocalFuzzer session = server.newLocalFuzzer("/echo"))
        {
            session.sendBulk(send);
            BlockingQueue<WebSocketFrame> receivedFrames = session.getOutputFrames();
            session.expectMessage(receivedFrames, OpCode.BINARY, expectedMessage);
        }
    }
    
    @Test
    public void testMoreThanLargestMessageOneByteAtATime() throws Exception
    {
        int size = server.getServerContainer().getDefaultMaxBinaryMessageBufferSize() + 16;
        byte[] data = newData(size);
        
        List<WebSocketFrame> send = new ArrayList<>();
        send.add(new BinaryFrame().setPayload(data));
        send.add(new CloseFrame().setPayload(CloseStatus.NORMAL));
        
        ByteBuffer expectedMessage = DataUtils.copyOf(data);
        
        try (LocalFuzzer session = server.newLocalFuzzer("/echo"))
        {
            session.sendSegmented(send, 1);
            BlockingQueue<WebSocketFrame> receivedFrames = session.getOutputFrames();
            session.expectMessage(receivedFrames, OpCode.BINARY, expectedMessage);
        }
    }
    
    @ServerEndpoint(PATH)
    public static class ServerBinaryStreamer
    {
        private static final Logger LOG = Log.getLogger(ServerBinaryStreamer.class);
        
        @OnMessage
        public void echo(Session session, InputStream input) throws IOException
        {
            byte[] buffer = new byte[128];
            try (OutputStream output = session.getBasicRemote().getSendStream())
            {
                int readCount = 0;
                int read;
                while ((read = input.read(buffer)) >= 0)
                {
                    output.write(buffer, 0, read);
                    readCount += read;
                }
                LOG.debug("Read {} bytes", readCount);
            }
        }
    }
}