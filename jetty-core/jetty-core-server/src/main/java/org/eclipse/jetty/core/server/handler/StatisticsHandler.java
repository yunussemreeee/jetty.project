//
// ========================================================================
// Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.core.server.handler;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import org.eclipse.jetty.core.server.Content;
import org.eclipse.jetty.core.server.Handler;
import org.eclipse.jetty.core.server.HttpStream;
import org.eclipse.jetty.core.server.Incoming;
import org.eclipse.jetty.core.server.Processor;
import org.eclipse.jetty.core.server.Request;
import org.eclipse.jetty.core.server.Response;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.statistic.CounterStatistic;
import org.eclipse.jetty.util.statistic.SampleStatistic;

public class StatisticsHandler extends Handler.Wrapper
{
    private final ConcurrentHashMap<String, Object> _connectionStats = new ConcurrentHashMap<>();
    private final CounterStatistic _requestStats = new CounterStatistic();
    private final SampleStatistic _requestTimeStats = new SampleStatistic();
    private final SampleStatistic _handleTimeStats = new SampleStatistic();

    @Override
    public void accept(Incoming request) throws Exception
    {
        super.accept(new StatisticsIncoming(request));
    }

    private class StatisticsIncoming extends Incoming.Wrapper
    {
        private StatisticsIncoming(Incoming delegate)
        {
            super(delegate);
        }

        @Override
        public void accept(Processor processor) throws Exception
        {
            getWrapped().accept((rq, rs) -> handle(processor, rq, rs));
        }

        private void handle(Processor processor, Request request, Response response) throws Exception
        {
            // TODO: is this used?
            Object connectionStats = _connectionStats.computeIfAbsent(request.getConnectionMetaData().getId(), id ->
            {
                request.getChannel().addConnectionCloseListener(x ->
                {
                    // complete connections stats
                    _connectionStats.remove(request.getConnectionMetaData().getId());
                });
                return "SomeConnectionStatsObject";
            });

            final LongAdder bytesRead = new LongAdder();
            final LongAdder bytesWritten = new LongAdder();

            _requestStats.increment();
            request.getChannel().addStreamWrapper(s -> new HttpStream.Wrapper(s)
            {
                @Override
                public void send(MetaData.Response response, boolean last, Callback callback, ByteBuffer... content)
                {
                    if (response != null)
                    {
                        // TODO status stats collected here.
                    }

                    for (ByteBuffer b : content)
                    {
                        bytesWritten.add(b.remaining());
                    }

                    super.send(response, last, callback, content);
                }

                @Override
                public Content readContent()
                {
                    Content content =  super.readContent();
                    bytesRead.add(content.remaining());
                    return content;
                }

                @Override
                public void succeeded()
                {
                    _requestStats.decrement();
                    _requestTimeStats.record(System.currentTimeMillis() - getNanoTimeStamp());
                    super.succeeded();
                }

                @Override
                public void failed(Throwable x)
                {
                    _requestStats.decrement();
                    _requestTimeStats.record(System.nanoTime() - getNanoTimeStamp());
                    super.failed(x);
                }
            });

            try
            {
                processor.process(request, response);
            }
            finally
            {
                _handleTimeStats.record(System.nanoTime() - request.getChannel().getStream().getNanoTimeStamp());
                // TODO initial dispatch duration stats collected here.
            }
        }
    }
}