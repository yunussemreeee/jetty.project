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

package org.eclipse.jetty.server;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.SharedBlockingCallback;
import org.eclipse.jetty.util.SharedBlockingCallback.Blocker;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.util.Utf8StringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dump request handler.
 * Dumps GET and POST requests.
 * Useful for testing and debugging.
 */
public class DumpHandler extends Handler.Blocking
{
    private static final Logger LOG = LoggerFactory.getLogger(DumpHandler.class);

    private final SharedBlockingCallback _blocker = new SharedBlockingCallback(); 
    String _label = "Dump Handler";

    public DumpHandler()
    {
    }

    public DumpHandler(String label)
    {
        this._label = label;
    }

    @Override
    protected boolean accept(Request request)
    {
        return isStarted();
    }

    @Override
    protected void blocking(Request request, Response response)
    {
        try
        {
            HttpURI httpURI = request.getHttpURI();

            MultiMap<String> params = UrlEncoded.decodeQuery(httpURI.getQuery());

            if (Boolean.parseBoolean(params.getValue("flush")))
            {
                try (Blocker blocker = _blocker.acquire())
                {
                    response.write(false, blocker);
                    blocker.block();
                }
            }

            if (Boolean.parseBoolean(params.getValue("empty")))
            {
                response.setStatus(200);
                request.succeeded();
                return;
            }

            Utf8StringBuilder read = null;
            if (params.getValue("read") != null)
            {
                read = new Utf8StringBuilder();
                int len = Integer.parseInt(params.getValue("read"));
                byte[] buffer = new byte[8192];

                Content content = null;
                while (len > 0)
                {
                    if (content == null)
                    {
                        content = request.readContent();
                        if (content == null)
                        {
                            try (Blocker blocker = _blocker.acquire())
                            {
                                request.demandContent(blocker::succeeded);
                                blocker.block();
                            }
                            continue;
                        }
                    }

                    if (content instanceof Content.Error)
                    {
                        request.failed(((Content.Error)content).getCause());
                        return;
                    }

                    int l = Math.min(buffer.length, Math.min(len, content.remaining()));
                    content.fill(buffer, 0, l);
                    read.append(buffer, 0, l);
                    len -= l;

                    if (content.isEmpty())
                    {
                        content.release();
                        if (content.isLast())
                            break;
                        if (!content.isSpecial())
                            content = null;
                    }
                }
            }

            if (params.getValue("date") != null)
                response.getHeaders().put("Date", params.getValue("date"));

            if (params.getValue("ISE") != null)
                throw new IllegalStateException("Testing ISE");

            if (params.getValue("error") != null)
            {
                response.setStatus(Integer.parseInt(params.getValue("error")));
                request.succeeded();
                return;
            }

            response.setContentType(MimeTypes.Type.TEXT_HTML.asString());

            ByteArrayOutputStream buf = new ByteArrayOutputStream(2048);
            Writer writer = new OutputStreamWriter(buf, StandardCharsets.ISO_8859_1);
            writer.write("<html><h1>" + _label + "</h1>\n");
            writer.write("<pre>httpURI=" + httpURI + "</pre><br/>\n");
            writer.write("<pre>path=" + request.getPath() + "</pre><br/>\n");
            writer.write("<pre>contentType=" + request.getHeaders().get(HttpHeader.CONTENT_TYPE) + "</pre><br/>\n");
            writer.write("<pre>servername=" + Request.getServerName(request) + "</pre><br/>\n");
            writer.write("<pre>local=" + request.getConnectionMetaData().getLocal() + "</pre><br/>\n");
            writer.write("<pre>remote=" + request.getConnectionMetaData().getRemote() + "</pre><br/>\n");
            writer.write("<h3>Header:</h3><pre>");
            writer.write(String.format("%4s %s %s\n", request.getMethod(), httpURI.getPathQuery(), request.getConnectionMetaData().getProtocol()));
            Enumeration<String> headers = request.getHeaders().getFieldNames();
            while (headers.hasMoreElements())
            {
                String name = headers.nextElement();
                writer.write(name);
                writer.write(": ");
                String value = request.getHeaders().get(name);
                writer.write(value == null ? "" : value);
                writer.write("\n");
            }

            writer.write("</pre>\n<h3>Attributes:</h3>\n<pre>");
            for (String attr : request.getAttributeNames())
            {
                writer.write(attr);
                writer.write("=");
                writer.write(request.getAttribute(attr).toString());
                writer.write("\n");
            }

            writer.write("</pre>\n<h3>Content:</h3>\n<pre>");

            if (read == null)
            {
                read = new Utf8StringBuilder();
                byte[] buffer = new byte[8192];

                Content content = null;
                while (true)
                {
                    if (content == null)
                    {
                        content = request.readContent();
                        if (content == null)
                        {
                            try (Blocker blocker = _blocker.acquire())
                            {
                                request.demandContent(blocker::succeeded);
                                blocker.block();
                            }
                            continue;
                        }
                    }

                    if (content instanceof Content.Error)
                    {
                        request.failed(((Content.Error)content).getCause());
                        return;
                    }

                    int l = Math.min(buffer.length, content.remaining());
                    content.fill(buffer, 0, l);
                    read.append(buffer, 0, l);

                    if (content.isEmpty())
                    {
                        content.release();
                        if (content.isLast())
                            break;
                        if (!content.isSpecial())
                            content = null;
                    }
                }
            }
            writer.write(read.toString());

            writer.write("</pre>\n");
            writer.write("</html>\n");
            writer.flush();

            // commit now
            if (!Boolean.parseBoolean(params.getValue("no-content-length")))
                response.setContentLength(buf.size() + 1000);

            response.getHeaders().add("Before-Flush", response.isCommitted() ? "Committed???" : "Not Committed");


            try (Blocker blocker = _blocker.acquire())
            {
                response.write(false, blocker, BufferUtil.toBuffer(buf.toByteArray()));
                blocker.block();
            }
            response.addHeader("After-Flush", "These headers should not be seen in the response!!!");
            response.addHeader("After-Flush", response.isCommitted() ? "Committed" : "Not Committed?");

            // write remaining content after commit
            String padding = "ABCDEFGHIJ".repeat(99) + "ABCDEFGH\r\n";

            try (Blocker blocker = _blocker.acquire())
            {
                response.write(true, blocker, BufferUtil.toBuffer(padding.getBytes(StandardCharsets.ISO_8859_1)));
                blocker.block();
            }

            request.succeeded();
        }
        catch (Exception x)
        {
            request.failed(x);
        }
        return;
    }
}
