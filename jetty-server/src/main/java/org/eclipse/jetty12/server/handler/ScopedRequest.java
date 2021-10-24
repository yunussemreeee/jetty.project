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

package org.eclipse.jetty12.server.handler;

import org.eclipse.jetty12.server.Request;
import org.eclipse.jetty12.server.handler.ContextHandler.Context;

public class ScopedRequest extends Request.Wrapper
{
    private final String _pathInContext;
    private final Context _context;

    protected ScopedRequest(Context context, Request wrapped, String pathInContext)
    {
        super(wrapped);
        _pathInContext = pathInContext;
        this._context = context;
    }

    public Context getContext()
    {
        return _context;
    }

    public String getPathInContext()
    {
        return _pathInContext;
    }
}