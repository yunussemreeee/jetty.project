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

package org.eclipse.jetty12.server.servlet;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.SessionTrackingMode;
import jakarta.servlet.descriptor.JspConfigDescriptor;
import org.eclipse.jetty12.server.handler.ContextHandler;

public class ServletContextContext implements ServletContext
{
    final ContextHandler.Context _context;
    final ServletHandler _servletHandler;

    ServletContextContext(ContextHandler.Context context, ServletHandler servletHandler)
    {
        _context = context;
        _servletHandler = servletHandler;
    }

    ContextHandler.Context getContext()
    {
        return _context;
    }

    ServletHandler getServletHandler()
    {
        return _servletHandler;
    }

    @Override
    public String getContextPath()
    {
        return _context.getContextPath();
    }

    @Override
    public ServletContext getContext(String uripath)
    {
        return null;
    }

    @Override
    public int getMajorVersion()
    {
        return 6;
    }

    @Override
    public int getMinorVersion()
    {
        return 0;
    }

    @Override
    public int getEffectiveMajorVersion()
    {
        return 6;
    }

    @Override
    public int getEffectiveMinorVersion()
    {
        return 0;
    }

    @Override
    public String getMimeType(String file)
    {
        return null;
    }

    @Override
    public Set<String> getResourcePaths(String path)
    {
        return null;
    }

    @Override
    public URL getResource(String path) throws MalformedURLException
    {
        // TODO more to do here
        return _context.getResourceBase().resolve(path).toUri().toURL();
    }

    @Override
    public InputStream getResourceAsStream(String path)
    {
        return null;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String pathInContext)
    {
        ServletHandler.MappedServlet mapping = _servletHandler.findMapping(pathInContext);
        return new ServletDispatcher(_context, _servletHandler, mapping);
    }

    @Override
    public RequestDispatcher getNamedDispatcher(String name)
    {
        return null;
    }

    @Override
    public void log(String msg)
    {

    }

    @Override
    public void log(String message, Throwable throwable)
    {

    }

    @Override
    public String getRealPath(String path)
    {
        return null;
    }

    @Override
    public String getServerInfo()
    {
        return null;
    }

    @Override
    public String getInitParameter(String name)
    {
        return null;
    }

    @Override
    public Enumeration<String> getInitParameterNames()
    {
        return null;
    }

    @Override
    public boolean setInitParameter(String name, String value)
    {
        return false;
    }

    @Override
    public Object getAttribute(String name)
    {
        return null;
    }

    @Override
    public Enumeration<String> getAttributeNames()
    {
        return null;
    }

    @Override
    public void setAttribute(String name, Object object)
    {

    }

    @Override
    public void removeAttribute(String name)
    {

    }

    @Override
    public String getServletContextName()
    {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, String className)
    {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Servlet servlet)
    {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass)
    {
        return null;
    }

    @Override
    public ServletRegistration.Dynamic addJspFile(String servletName, String jspFile)
    {
        return null;
    }

    @Override
    public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException
    {
        return null;
    }

    @Override
    public ServletRegistration getServletRegistration(String servletName)
    {
        return null;
    }

    @Override
    public Map<String, ? extends ServletRegistration> getServletRegistrations()
    {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, String className)
    {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Filter filter)
    {
        return null;
    }

    @Override
    public FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass)
    {
        return null;
    }

    @Override
    public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException
    {
        return null;
    }

    @Override
    public FilterRegistration getFilterRegistration(String filterName)
    {
        return null;
    }

    @Override
    public Map<String, ? extends FilterRegistration> getFilterRegistrations()
    {
        return null;
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig()
    {
        return null;
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes)
    {

    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes()
    {
        return null;
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes()
    {
        return null;
    }

    @Override
    public void addListener(String className)
    {

    }

    @Override
    public <T extends EventListener> void addListener(T t)
    {

    }

    @Override
    public void addListener(Class<? extends EventListener> listenerClass)
    {

    }

    @Override
    public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException
    {
        return null;
    }

    @Override
    public JspConfigDescriptor getJspConfigDescriptor()
    {
        return null;
    }

    @Override
    public ClassLoader getClassLoader()
    {
        return null;
    }

    @Override
    public void declareRoles(String... roleNames)
    {

    }

    @Override
    public String getVirtualServerName()
    {
        return null;
    }

    @Override
    public int getSessionTimeout()
    {
        return 0;
    }

    @Override
    public void setSessionTimeout(int sessionTimeout)
    {

    }

    @Override
    public String getRequestCharacterEncoding()
    {
        return null;
    }

    @Override
    public void setRequestCharacterEncoding(String encoding)
    {

    }

    @Override
    public String getResponseCharacterEncoding()
    {
        return null;
    }

    @Override
    public void setResponseCharacterEncoding(String encoding)
    {

    }
}