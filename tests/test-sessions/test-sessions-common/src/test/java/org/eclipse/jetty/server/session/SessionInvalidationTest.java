//
//  ========================================================================
//  Copyright (c) 1995-2021 Mort Bay Consulting Pty Ltd and others.
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

package org.eclipse.jetty.server.session;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SessionInvalidationTest
 *
 * Test that various methods on sessions can't be accessed after invalidation
 */
public class SessionInvalidationTest
{
    @Test
    public void testInvalidation() throws Exception
    {
        String contextPath = "";
        String servletMapping = "/server";
        int scavengePeriod = -1;

        DefaultSessionCacheFactory cacheFactory = new DefaultSessionCacheFactory();
        cacheFactory.setEvictionPolicy(SessionCache.NEVER_EVICT);
        SessionDataStoreFactory storeFactory = new TestSessionDataStoreFactory();
        ((AbstractSessionDataStoreFactory)storeFactory).setGracePeriodSec(scavengePeriod);

        TestServer server = new TestServer(0, 0, scavengePeriod,
            cacheFactory, storeFactory);
        ServletContextHandler context = server.addContext(contextPath);
        TestServlet servlet = new TestServlet();
        ServletHolder holder = new ServletHolder(servlet);
        context.addServlet(holder, servletMapping);

        try
        {
            server.start();
            int port1 = server.getPort();

            HttpClient client = new HttpClient();
            client.start();
            try
            {
                String url = "http://localhost:" + port1 + contextPath + servletMapping;
                // Create the session
                ContentResponse response1 = client.GET(url + "?action=init");
                assertEquals(HttpServletResponse.SC_OK, response1.getStatus());
                String sessionCookie = response1.getHeaders().get("Set-Cookie");
                assertTrue(sessionCookie != null);

                // Make a request which will invalidate the existing session
                Request request2 = client.newRequest(url + "?action=test");
                ContentResponse response2 = request2.send();
                assertEquals(HttpServletResponse.SC_OK, response2.getStatus());
            }
            finally
            {
                client.stop();
            }
        }
        finally
        {
            server.stop();
        }
    }

    public static class TestServlet extends HttpServlet
    {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse httpServletResponse) throws ServletException, IOException
        {
            String action = request.getParameter("action");

            if ("init".equals(action))
            {
                HttpSession session = request.getSession(true);
                assertNotNull(session);
            }
            else if ("test".equals(action))
            {
                HttpSession session = request.getSession(false);
                assertNotNull(session);

                //invalidate existing session
                session.invalidate();

                assertThrows(IllegalStateException.class, () -> session.invalidate());
                assertThrows(IllegalStateException.class, () -> session.getLastAccessedTime());
                assertThrows(IllegalStateException.class, () -> session.getCreationTime());
                assertThrows(IllegalStateException.class, () -> session.getAttribute("foo"));
                assertThrows(IllegalStateException.class, () -> session.getAttributeNames());
                assertThrows(IllegalStateException.class, () -> session.getValue("foo"));
                assertThrows(IllegalStateException.class, () -> session.getValueNames());
                assertThrows(IllegalStateException.class, () -> session.putValue("a", "b"));
                assertThrows(IllegalStateException.class, () -> session.removeAttribute("foo"));
                assertThrows(IllegalStateException.class, () -> session.removeValue("foo"));
                assertThrows(IllegalStateException.class, () -> session.setAttribute("a", "b"));
                assertDoesNotThrow(() -> session.getId());
            }
        }
    }
}
