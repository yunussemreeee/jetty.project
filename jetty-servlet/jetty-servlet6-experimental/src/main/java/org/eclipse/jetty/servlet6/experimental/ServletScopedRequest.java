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

package org.eclipse.jetty.servlet6.experimental;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestAttributeListener;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.server.ConnectionMetaData;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextRequest;
import org.eclipse.jetty.servlet6.experimental.writer.EncodingHttpWriter;
import org.eclipse.jetty.servlet6.experimental.writer.Iso88591HttpWriter;
import org.eclipse.jetty.servlet6.experimental.writer.ResponseWriter;
import org.eclipse.jetty.servlet6.experimental.writer.Utf8HttpWriter;
import org.eclipse.jetty.util.SharedBlockingCallback;
import org.eclipse.jetty.util.SharedBlockingCallback.Blocker;
import org.eclipse.jetty.util.StringUtil;

public class ServletScopedRequest extends ContextRequest implements Runnable
{
    public static final String __MULTIPART_CONFIG_ELEMENT = "org.eclipse.jetty.multipartConfig";

    final ServletChannel _servletRequestState;
    final MutableHttpServletRequest _httpServletRequest;
    final MutableHttpServletResponse _httpServletResponse;
    final ServletHandler.MappedServlet _mappedServlet;
    final ServletScopedResponse _response;
    final HttpOutput _httpOutput;
    final HttpInput _httpInput;
    boolean _newContext;
    private UserIdentity.Scope _scope;

    final List<ServletRequestAttributeListener> _requestAttributeListeners = new ArrayList<>();

    protected ServletScopedRequest(
        ServletChannel servletChannel,
        Request request,
        Response response,
        String pathInContext,
        ServletHandler.MappedServlet mappedServlet)
    {
        super(servletChannel.getContextHandler(), request, pathInContext);
        _servletRequestState = servletChannel;
        _httpServletRequest = new MutableHttpServletRequest();
        _httpServletResponse = new MutableHttpServletResponse(response);
        _mappedServlet = mappedServlet;
        _response = new ServletScopedResponse(response);
        _httpOutput = new HttpOutput(response);
        _httpInput = new HttpInput(this);
    }

    @Override
    public ServletScopedResponse getResponse()
    {
        return _response;
    }

    public HttpInput getHttpInput()
    {
        return _httpInput;
    }

    public HttpOutput getHttpOutput()
    {
        return _httpOutput;
    }

    public void errorClose()
    {
        // Make the response immutable and soft close the output.
    }

    @Override
    public Object getAttribute(String name)
    {
        // return hidden attributes for request logging
        switch (name)
        {
            case "o.e.j.s.s.ServletScopedRequest.request":
                return _httpServletRequest;
            case "o.e.j.s.s.ServletScopedRequest.response":
                return _httpServletResponse;
            case "o.e.j.s.s.ServletScopedRequest.servlet":
                return _mappedServlet.getServletPathMapping(getPath()).getServletName();
            case "o.e.j.s.s.ServletScopedRequest.url-pattern":
                return _mappedServlet.getServletPathMapping(getPath()).getPattern();
            default:
                return super.getAttribute(name);
        }
    }

    /**
     * @return The current {@link ContextHandler.Context context} used for this error handling for this request.  If the request is asynchronous,
     * then it is the context that called async. Otherwise it is the last non-null context passed to #setContext
     */
    public ContextHandler.Context getErrorContext()
    {
        return _servletRequestState.getContext();
    }

    public boolean takeNewContext()
    {
        boolean nc = _newContext;
        _newContext = false;
        return nc;
    }

    ServletChannel getServletRequestState()
    {
        return _servletRequestState;
    }

    public HttpServletRequest getHttpServletRequest()
    {
        return _httpServletRequest;
    }

    public MutableHttpServletRequest getMutableHttpServletRequest()
    {
        return _httpServletRequest;
    }

    public HttpServletResponse getHttpServletResponse()
    {
        return _httpServletResponse;
    }

    public ServletHandler.MappedServlet getMappedServlet()
    {
        return _mappedServlet;
    }

    public static ServletScopedRequest getRequest(HttpServletRequest httpServletRequest)
    {
        while (httpServletRequest != null)
        {
            if (httpServletRequest instanceof ServletChannel)
                return ((ServletChannel)httpServletRequest).getServletScopedRequest();
            if (httpServletRequest instanceof HttpServletRequestWrapper)
                httpServletRequest = (HttpServletRequest)((HttpServletRequestWrapper)httpServletRequest).getRequest();
            else
                break;
        }
        return null;
    }

    @Override
    public void run()
    {
        _servletRequestState.handle();
    }

    public String getServletName()
    {
        if (_scope != null)
            return _scope.getName();
        return null;
    }

    Runnable onContentAvailable()
    {
        // TODO not sure onReadReady is right method or at least could be renamed.
        return _servletRequestState.onReadReady() ? this : null;
    }

    public void addEventListener(final EventListener listener)
    {
        if (listener instanceof ServletRequestAttributeListener)
            _requestAttributeListeners.add((ServletRequestAttributeListener)listener);
        if (listener instanceof AsyncListener)
            throw new IllegalArgumentException(listener.getClass().toString());
    }

    public void removeEventListener(final EventListener listener)
    {
        _requestAttributeListeners.remove(listener);
    }

    public class MutableHttpServletRequest implements HttpServletRequest
    {
        private AsyncContextState _async;
        
        public Request getRequest()
        {
            return ServletScopedRequest.this;
        }

        public HttpServletResponse getHttpServletResponse()
        {
            return _httpServletResponse;
        }

        public MutableHttpServletResponse getMutableHttpServletResponse()
        {
            return _httpServletResponse;
        }
        
        @Override
        public String getRequestId()
        {
            return ServletScopedRequest.this.getId();
        }

        @Override
        public String getProtocolRequestId()
        {
            return ServletScopedRequest.this.getHttpChannel().getHttpStream().getId();
        }

        @Override
        public ServletConnection getServletConnection()
        {
            // TODO cache the results
            final ConnectionMetaData connectionMetaData = ServletScopedRequest.this.getConnectionMetaData();
            return new ServletConnection()
            {
                @Override
                public String getConnectionId()
                {
                    return connectionMetaData.getId();
                }

                @Override
                public String getProtocol()
                {
                    return connectionMetaData.getProtocol();
                }

                @Override
                public String getProtocolConnectionId()
                {
                    return connectionMetaData.getConnection().toString(); // TODO getId
                }

                @Override
                public boolean isSecure()
                {
                    return connectionMetaData.isSecure();
                }
            };
        }

        @Override
        public String getAuthType()
        {
            return null;
        }

        @Override
        public Cookie[] getCookies()
        {
            return new Cookie[0];
        }

        @Override
        public long getDateHeader(String name)
        {
            return 0;
        }

        @Override
        public String getHeader(String name)
        {
            return ServletScopedRequest.this.getHeaders().get(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name)
        {
            return ServletScopedRequest.this.getHeaders().getValues(name);
        }

        @Override
        public Enumeration<String> getHeaderNames()
        {
            return ServletScopedRequest.this.getHeaders().getFieldNames();
        }

        @Override
        public int getIntHeader(String name)
        {
            return (int)ServletScopedRequest.this.getHeaders().getLongField(name);
        }

        @Override
        public String getMethod()
        {
            return getRequest().getMethod();
        }

        @Override
        public String getPathInfo()
        {
            return ServletScopedRequest.this._mappedServlet.getServletPathMapping(getRequest().getPath()).getPathInfo();
        }

        @Override
        public String getPathTranslated()
        {
            return null;
        }

        @Override
        public String getContextPath()
        {
            return ServletScopedRequest.this.getContext().getContextPath();
        }

        @Override
        public String getQueryString()
        {
            return ServletScopedRequest.this.getHttpURI().getQuery();
        }

        @Override
        public String getRemoteUser()
        {
            return null;
        }

        @Override
        public boolean isUserInRole(String role)
        {
            return false;
        }

        @Override
        public Principal getUserPrincipal()
        {
            return null;
        }

        @Override
        public String getRequestedSessionId()
        {
            return null;
        }

        @Override
        public String getRequestURI()
        {
            return ServletScopedRequest.this.getHttpURI().toString();
        }

        @Override
        public StringBuffer getRequestURL()
        {
            return null;
        }

        @Override
        public String getServletPath()
        {
            return ServletScopedRequest.this._mappedServlet.getServletPathMapping(getRequest().getPath()).getServletPath();
        }

        @Override
        public HttpSession getSession(boolean create)
        {
            return null;
        }

        @Override
        public HttpSession getSession()
        {
            return null;
        }

        @Override
        public String changeSessionId()
        {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid()
        {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie()
        {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL()
        {
            return false;
        }

        @Override
        public boolean authenticate(HttpServletResponse response) throws IOException, ServletException
        {
            return false;
        }

        @Override
        public void login(String username, String password) throws ServletException
        {

        }

        @Override
        public void logout() throws ServletException
        {

        }

        @Override
        public Collection<Part> getParts() throws IOException, ServletException
        {
            return null;
        }

        @Override
        public Part getPart(String name) throws IOException, ServletException
        {
            return null;
        }

        @Override
        public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException
        {
            return null;
        }

        @Override
        public Object getAttribute(String name)
        {
            return ServletScopedRequest.this.getAttribute(name);
        }

        @Override
        public Enumeration<String> getAttributeNames()
        {
            return Collections.enumeration(ServletScopedRequest.this.getAttributeNames());
        }

        @Override
        public String getCharacterEncoding()
        {
            return null;
        }

        @Override
        public void setCharacterEncoding(String env) throws UnsupportedEncodingException
        {
        }

        @Override
        public int getContentLength()
        {
            return 0;
        }

        @Override
        public long getContentLengthLong()
        {
            return 0;
        }

        @Override
        public String getContentType()
        {
            return null;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException
        {
            return _httpInput;
        }

        @Override
        public String getParameter(String name)
        {
            return null;
        }

        @Override
        public Enumeration<String> getParameterNames()
        {
            return null;
        }

        @Override
        public String[] getParameterValues(String name)
        {
            return new String[0];
        }

        @Override
        public Map<String, String[]> getParameterMap()
        {
            return null;
        }

        @Override
        public String getProtocol()
        {
            return null;
        }

        @Override
        public String getScheme()
        {
            return null;
        }

        @Override
        public String getServerName()
        {
            return null;
        }

        @Override
        public int getServerPort()
        {
            return 0;
        }

        @Override
        public BufferedReader getReader() throws IOException
        {
            return null;
        }

        @Override
        public String getRemoteAddr()
        {
            return null;
        }

        @Override
        public String getRemoteHost()
        {
            return null;
        }

        @Override
        public void setAttribute(String name, Object o)
        {

        }

        @Override
        public void removeAttribute(String name)
        {

        }

        @Override
        public Locale getLocale()
        {
            return null;
        }

        @Override
        public Enumeration<Locale> getLocales()
        {
            return null;
        }

        @Override
        public boolean isSecure()
        {
            return false;
        }

        @Override
        public RequestDispatcher getRequestDispatcher(String path)
        {
            return null;
        }

        @Override
        public int getRemotePort()
        {
            return 0;
        }

        @Override
        public String getLocalName()
        {
            return null;
        }

        @Override
        public String getLocalAddr()
        {
            return null;
        }

        @Override
        public int getLocalPort()
        {
            return 0;
        }

        @Override
        public ServletContext getServletContext()
        {
            return _servletRequestState.getServletContext();
        }

        @Override
        public AsyncContext startAsync() throws IllegalStateException
        {
            ServletChannel state = _servletRequestState;
            if (_async == null)
                _async = new AsyncContextState(state);
            // TODO adapt to new context and base Request
            AsyncContextEvent event = new AsyncContextEvent(null, _async, state, null, this, _httpServletResponse);
            state.startAsync(event);
            return _async;
        }

        @Override
        public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException
        {
            ServletChannel state = _servletRequestState;
            if (_async == null)
                _async = new AsyncContextState(state);
            // TODO adapt to new context and base Request
            AsyncContextEvent event = new AsyncContextEvent(null, _async, state, null, servletRequest, servletResponse);
            state.startAsync(event);
            return _async;
        }

        @Override
        public boolean isAsyncStarted()
        {
            return _servletRequestState.isAsyncStarted();
        }

        @Override
        public boolean isAsyncSupported()
        {
            return false;
        }

        @Override
        public AsyncContext getAsyncContext()
        {
            return null;
        }

        @Override
        public DispatcherType getDispatcherType()
        {
            return DispatcherType.REQUEST;
        }
    }

    class MutableHttpServletResponse implements HttpServletResponse
    {
        private final SharedBlockingCallback _blocker = new SharedBlockingCallback();
        private final Response _response;

        MutableHttpServletResponse(Response response)
        {
            _response = response;
        }

        @Override
        public void addCookie(Cookie cookie)
        {
            // TODO
        }

        @Override
        public boolean containsHeader(String name)
        {
            return _response.getHeaders().contains(name);
        }

        @Override
        public String encodeURL(String url)
        {
            return null;
        }

        @Override
        public String encodeRedirectURL(String url)
        {
            return null;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException
        {
            switch (sc)
            {
                case -1:
                    _servletRequestState.getServletScopedRequest().getResponse().getCallback().failed(new IOException(msg));
                    break;

                case HttpStatus.PROCESSING_102:
                    try (Blocker blocker = _blocker.acquire())
                    {
                        // TODO static MetaData
                        _servletRequestState.getServletScopedRequest().getHttpChannel().getHttpStream()
                            .send(new MetaData.Response(null, 102, null), false, blocker);
                    }
                    break;

                default:
                    // This is just a state change
                    _servletRequestState.sendError(sc, msg);
                    break;
            }
        }

        @Override
        public void sendError(int sc) throws IOException
        {
            sendError(sc, null);
        }

        @Override
        public void sendRedirect(String location) throws IOException
        {
            // TODO
        }

        @Override
        public void setDateHeader(String name, long date)
        {
            _response.getHeaders().putDateField(name, date);
        }

        @Override
        public void addDateHeader(String name, long date)
        {

        }

        @Override
        public void setHeader(String name, String value)
        {
            _response.getHeaders().put(name, value);
        }

        @Override
        public void addHeader(String name, String value)
        {
            _response.getHeaders().add(name, value);
        }

        @Override
        public void setIntHeader(String name, int value)
        {
            // TODO do we need int versions?
            _response.getHeaders().putLongField(name, value);
        }

        @Override
        public void addIntHeader(String name, int value)
        {
            // TODO do we need a native version?
            _response.getHeaders().add(name, Integer.toString(value));
        }

        @Override
        public void setStatus(int sc)
        {
            _response.setStatus(sc);
        }

        @Override
        public int getStatus()
        {
            return _response.getStatus();
        }

        @Override
        public String getHeader(String name)
        {
            return _response.getHeaders().get(name);
        }

        @Override
        public Collection<String> getHeaders(String name)
        {
            return null;
        }

        @Override
        public Collection<String> getHeaderNames()
        {
            return null;
        }

        @Override
        public String getCharacterEncoding()
        {
            // TODO
            return StringUtil.__ISO_8859_1;
        }

        @Override
        public String getContentType()
        {
            return null;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException
        {
            return _httpOutput;
        }

        @Override
        public PrintWriter getWriter() throws IOException
        {
            HttpOutput httpOutput = new HttpOutput(_response);
            String encoding = getCharacterEncoding();
            Locale locale = getLocale();
            if (StringUtil.__ISO_8859_1.equalsIgnoreCase(encoding))
                return new ResponseWriter(new Iso88591HttpWriter(httpOutput), locale, encoding);
            else if (StringUtil.__UTF8.equalsIgnoreCase(encoding))
                return new ResponseWriter(new Utf8HttpWriter(httpOutput), locale, encoding);
            else
                return new ResponseWriter(new EncodingHttpWriter(httpOutput, encoding), locale, encoding);
        }

        @Override
        public void setCharacterEncoding(String charset)
        {

        }

        @Override
        public void setContentLength(int len)
        {

        }

        @Override
        public void setContentLengthLong(long len)
        {

        }

        @Override
        public void setContentType(String type)
        {

        }

        @Override
        public void setBufferSize(int size)
        {

        }

        @Override
        public int getBufferSize()
        {
            return 0;
        }

        @Override
        public void flushBuffer() throws IOException
        {
            try (Blocker blocker = _blocker.acquire())
            {
                _response.write(false, blocker);
            }
        }

        @Override
        public void resetBuffer()
        {
            // TODO I don't think this is right... maybe just a HttpWriter reset
            if (!_response.isCommitted())
                _response.reset();
        }

        @Override
        public boolean isCommitted()
        {
            return _response.isCommitted();
        }

        @Override
        public void reset()
        {
            if (!_response.isCommitted())
                _response.reset();
        }

        @Override
        public void setLocale(Locale loc)
        {
        }

        @Override
        public Locale getLocale()
        {
            return null;
        }
    }
}
