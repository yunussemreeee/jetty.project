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

package org.eclipse.jetty.embedded;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;

/**
 * Example of serving content from a JAR file.
 * The JAR file in this example does not belong to any Classpath.
 */
public class JarServer
{
    public static Server createServer(int port) throws Exception
    {
        Server server = new Server(port);

        Path jarFile = Paths.get("src/main/other/content.jar");
        if (!Files.exists(jarFile))
            throw new FileNotFoundException(jarFile.toString());

        ServletContextHandler context = new ServletContextHandler();
        Resource.setDefaultUseCaches(true);
        Resource base = Resource.newResource("jar:" + jarFile.toAbsolutePath().toUri().toASCIIString() + "!/");
        context.setBaseResource(base);
        context.addServlet(new ServletHolder(new DefaultServlet()), "/");

        HandlerList handlers = new HandlerList();
        handlers.addHandler(context);
        handlers.addHandler(new DefaultHandler());
        server.setHandler(handlers);
        return server;
    }

    public static void main(String[] args) throws Exception
    {
        int port = ExampleUtil.getPort(args, "jetty.http.port", 8080);
        Server server = createServer(port);
        server.start();
        server.join();
    }
}
