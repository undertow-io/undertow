/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.spec;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.MultiPartHandler;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.SecurityRoleRef;
import io.undertow.servlet.core.ServletUpgradeListener;
import io.undertow.servlet.handlers.ServletAttachments;
import io.undertow.servlet.handlers.ServletChain;
import io.undertow.servlet.handlers.ServletPathMatch;
import io.undertow.servlet.util.EmptyEnumeration;
import io.undertow.servlet.util.IteratorEnumeration;
import io.undertow.util.AttachmentKey;
import io.undertow.util.CanonicalPathUtils;
import io.undertow.util.DateUtils;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.LocaleUtils;
import io.undertow.util.Methods;
import io.undertow.util.QValueParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;

import org.xnio.LocalSocketAddress;

/**
 * The http servlet request implementation. This class is not thread safe
 *
 * @author Stuart Douglas
 */
public final class HttpServletRequestImpl implements HttpServletRequest {

    public static final AttachmentKey<ServletRequest> ATTACHMENT_KEY = AttachmentKey.create(ServletRequest.class);
    public static final AttachmentKey<DispatcherType> DISPATCHER_TYPE_ATTACHMENT_KEY = AttachmentKey.create(DispatcherType.class);

    private static final Charset DEFAULT_CHARSET = Charset.forName("ISO-8859-1");

    private final HttpServerExchange exchange;
    private ServletContextImpl servletContext;

    private final List<BoundAsyncListener> asyncListeners = new CopyOnWriteArrayList<BoundAsyncListener>();

    private Map<String, Object> attributes = null;

    private ServletInputStream servletInputStream;
    private BufferedReader reader;

    private Cookie[] cookies;
    private List<Part> parts = null;
    private volatile AsyncContextImpl asyncContext = null;
    private Map<String, Deque<String>> queryParameters;
    private FormData parsedFormData;
    private Charset characterEncoding;
    private boolean readStarted;

    public HttpServletRequestImpl(final HttpServerExchange exchange, final ServletContextImpl servletContext) {
        this.exchange = exchange;
        this.servletContext = servletContext;
        this.queryParameters = exchange.getQueryParameters();
    }

    public HttpServerExchange getExchange() {
        return exchange;
    }

    @Override
    public String getAuthType() {
        SecurityContext securityContext = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);

        return securityContext != null ? securityContext.getMechanismName() : null;
    }

    @Override
    public Cookie[] getCookies() {
        if (cookies == null) {
            Map<String, io.undertow.server.handlers.Cookie> cookies = CookieImpl.getRequestCookies(exchange);
            if (cookies.isEmpty()) {
                return null;
            }
            Cookie[] value = new Cookie[cookies.size()];
            int i = 0;
            for (Map.Entry<String, io.undertow.server.handlers.Cookie> entry : cookies.entrySet()) {
                io.undertow.server.handlers.Cookie cookie = entry.getValue();
                Cookie c = new Cookie(cookie.getName(), cookie.getValue());
                if (cookie.getDomain() != null) {
                    c.setDomain(cookie.getDomain());
                }
                c.setHttpOnly(cookie.isHttpOnly());
                if (cookie.getMaxAge() != null) {
                    c.setMaxAge(cookie.getMaxAge());
                }
                if (cookie.getPath() != null) {
                    c.setPath(cookie.getPath());
                }
                c.setSecure(cookie.isSecure());
                c.setVersion(cookie.getVersion());
                value[i++] = c;
            }
            this.cookies = value;
        }
        return cookies;
    }

    @Override
    public long getDateHeader(final String name) {
        String header = exchange.getRequestHeaders().getFirst(new HttpString(name));
        if (header == null) {
            return -1;
        }
        Date date = DateUtils.parseDate(header);
        if (date == null) {
            throw UndertowServletMessages.MESSAGES.headerCannotBeConvertedToDate(header);
        }
        return date.getTime();
    }

    @Override
    public String getHeader(final String name) {
        return getHeader(new HttpString(name));
    }

    public String getHeader(final HttpString name) {
        HeaderMap headers = exchange.getRequestHeaders();
        if (headers == null) {
            return null;
        }
        return headers.getFirst(name);
    }


    @Override
    public Enumeration<String> getHeaders(final String name) {
        List<String> headers = exchange.getRequestHeaders().get(new HttpString(name));
        if (headers == null) {
            return EmptyEnumeration.instance();
        }
        return new IteratorEnumeration<String>(headers.iterator());
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        final Set<String> headers = new HashSet<String>();
        for (final HttpString i : exchange.getRequestHeaders()) {
            headers.add(i.toString());
        }
        return new IteratorEnumeration<String>(headers.iterator());
    }

    @Override
    public int getIntHeader(final String name) {
        String header = getHeader(name);
        if (header == null) {
            return -1;
        }
        return Integer.parseInt(header);
    }

    @Override
    public String getMethod() {
        return exchange.getRequestMethod().toString();
    }

    @Override
    public String getPathInfo() {
        ServletPathMatch match = exchange.getAttachment(ServletAttachments.SERVLET_PATH_MATCH);
        if (match != null) {
            return match.getRemaining();
        }
        return null;
    }

    @Override
    public String getPathTranslated() {
        return null;
    }

    @Override
    public String getContextPath() {
        return servletContext.getContextPath();
    }

    @Override
    public String getQueryString() {
        return exchange.getQueryString();
    }

    @Override
    public String getRemoteUser() {
        Principal userPrincipal = getUserPrincipal();

        return userPrincipal != null ? userPrincipal.getName() : null;
    }

    @Override
    public boolean isUserInRole(final String role) {
        SecurityContext sc = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        Account account = sc.getAuthenticatedAccount();
        if (account == null) {
            return false;
        }

        final ServletChain servlet = exchange.getAttachment(ServletAttachments.CURRENT_SERVLET);
        //TODO: a more efficient imple
        for (SecurityRoleRef ref : servlet.getManagedServlet().getServletInfo().getSecurityRoleRefs()) {
            if (ref.getRole().equals(role)) {
                return account.isUserInRole(ref.getLinkedRole());
            }
        }
        return account.isUserInRole(role);
    }

    @Override
    public Principal getUserPrincipal() {
        SecurityContext securityContext = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        Principal result = null;
        Account account = null;
        if (securityContext != null && (account = securityContext.getAuthenticatedAccount()) != null) {
            result = account.getPrincipal();
        }
        return result;
    }

    @Override
    public String getRequestedSessionId() {
        return null;
    }

    @Override
    public String changeSessionId() {
        HttpSessionImpl session = servletContext.getSession(exchange, false);
        if (session == null) {
            throw UndertowServletMessages.MESSAGES.noSession();
        }
        String oldId = session.getId();
        String newId = session.getSession().changeSessionId(exchange, servletContext.getSessionCookieConfig());
        servletContext.getDeployment().getApplicationListeners().httpSessionIdChanged(session, oldId);
        return newId;
    }

    @Override
    public String getRequestURI() {
        return exchange.getRequestPath();
    }

    @Override
    public StringBuffer getRequestURL() {
        return new StringBuffer(exchange.getRequestURL());
    }

    @Override
    public String getServletPath() {
        ServletPathMatch match = exchange.getAttachment(ServletAttachments.SERVLET_PATH_MATCH);
        if (match != null) {
            return match.getMatched();
        }
        return "";
    }

    @Override
    public HttpSession getSession(final boolean create) {
        return servletContext.getSession(exchange, create);
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }


    @Override
    public boolean isRequestedSessionIdValid() {
        HttpSessionImpl session = servletContext.getSession(exchange, false);
        return session != null;
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        HttpSessionImpl session = servletContext.getSession(exchange, false);
        return session != null;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return false;
    }

    @Override
    public boolean isRequestedSessionIdFromUrl() {
        return false;
    }

    @Override
    public boolean authenticate(final HttpServletResponse response) throws IOException, ServletException {
        if (response.isCommitted()) {
            throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();
        }

        SecurityContext sc = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        sc.setAuthenticationRequired();
        // TODO: this will set the status code and headers without going through any potential
        // wrappers, is this a problem?
        if (sc.authenticate()) {
            if (sc.isAuthenticated()) {
                return true;
            } else {
                throw UndertowServletMessages.MESSAGES.authenticationFailed();
            }
        } else {
            // Not authenticated and response already sent.
            HttpServletResponseImpl responseImpl = HttpServletResponseImpl.getResponseImpl(response);
            responseImpl.closeStreamAndWriter();
            return false;
        }
    }

    @Override
    public void login(final String username, final String password) throws ServletException {
        if (username == null || password == null) {
            throw UndertowServletMessages.MESSAGES.loginFailed();
        }
        SecurityContext sc = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        if (sc.isAuthenticated()) {
            throw UndertowServletMessages.MESSAGES.userAlreadyLoggedIn();
        }
        if (!sc.login(username, password)) {
            throw UndertowServletMessages.MESSAGES.loginFailed();
        }
    }

    @Override
    public void logout() throws ServletException {
        SecurityContext sc = exchange.getAttachment(SecurityContext.ATTACHMENT_KEY);
        sc.logout();
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        if (parts == null) {
            loadParts();
        }
        return parts;
    }

    @Override
    public Part getPart(final String name) throws IOException, ServletException {
        if (parts == null) {
            loadParts();
        }
        for (Part part : parts) {
            if (part.getName().equals(name)) {
                return part;
            }
        }
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(final Class<T> handlerClass) throws IOException {
        try {
            InstanceFactory<T> factory = servletContext.getDeployment().getDeploymentInfo().getClassIntrospecter().createInstanceFactory(handlerClass);
            final InstanceHandle<T> instance = factory.createInstance();
            exchange.upgradeChannel(new ServletUpgradeListener<T>(instance));
            return instance.getInstance();
        } catch (InstantiationException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadParts() throws IOException, ServletException {
        readStarted = true;
        if (parts == null) {
            final List<Part> parts = new ArrayList<Part>();
            String mimeType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
            if (mimeType != null && mimeType.startsWith(MultiPartHandler.MULTIPART_FORM_DATA)) {
                final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
                final FormData value = parser.parseBlocking();
                for (final String namedPart : value) {
                    for (FormData.FormValue part : value.get(namedPart)) {
                        //TODO: non-file parts?
                        parts.add(new PartImpl(namedPart, part));
                    }
                }
            } else {
                throw UndertowServletMessages.MESSAGES.notAMultiPartRequest();
            }
            this.parts = parts;
        }
    }

    @Override
    public Object getAttribute(final String name) {
        if(attributes == null) {
            return null;
        }
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        if(attributes == null) {
            return Collections.emptyEnumeration();
        }
        return new IteratorEnumeration<>(attributes.keySet().iterator());
    }

    @Override
    public String getCharacterEncoding() {
        if (characterEncoding != null) {
            return characterEncoding.name();
        }
        String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (contentType == null) {
            return null;
        }
        return Headers.extractTokenFromHeader(contentType, "charset");
    }

    @Override
    public void setCharacterEncoding(final String env) throws UnsupportedEncodingException {
        if (readStarted) {
            return;
        }
        try {
            characterEncoding = Charset.forName(env);

            final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
            if (parser != null) {
                parser.setCharacterEncoding(env);
            }
        } catch (UnsupportedCharsetException e) {
            throw new UnsupportedEncodingException();
        }
    }

    @Override
    public int getContentLength() {
        final String contentLength = getHeader(Headers.CONTENT_LENGTH);
        if (contentLength == null || contentLength.isEmpty()) {
            return -1;
        }
        return Integer.parseInt(contentLength);
    }

    @Override
    public long getContentLengthLong() {
        final String contentLength = getHeader(Headers.CONTENT_LENGTH);
        if (contentLength == null || contentLength.isEmpty()) {
            return -1;
        }
        return Long.parseLong(contentLength);
    }

    @Override
    public String getContentType() {
        return getHeader(Headers.CONTENT_TYPE);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (reader != null) {
            throw UndertowServletMessages.MESSAGES.getReaderAlreadyCalled();
        }
        servletInputStream = new ServletInputStreamImpl(this);
        readStarted = true;
        return servletInputStream;
    }

    @Override
    public String getParameter(final String name) {
        Deque<String> params = queryParameters.get(name);
        if (params == null) {
            if (exchange.getRequestMethod().equals(Methods.POST)) {
                if (parsedFormData == null) {
                    if (readStarted) {
                        return null;
                    }
                    readStarted = true;
                    final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
                    if (parser == null) {
                        return null;
                    }
                    try {
                        parsedFormData = parser.parseBlocking();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                FormData.FormValue res = parsedFormData.getFirst(name);
                if (res == null) {
                    return null;
                } else {
                    return res.getValue();
                }
            }
            return null;
        }
        try {
            //TODO: we need a better way to handle decoding the request paramters
            //TODO: what charset should we be using to decode these parameters?
            return URLDecoder.decode(params.getFirst(), characterEncoding == null ? "ISO-8859-1" : characterEncoding.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Enumeration<String> getParameterNames() {
        final Set<String> parameterNames = new HashSet<String>(queryParameters.keySet());
        if (exchange.getRequestMethod().equals(Methods.POST)) {
            readStarted = true;
            final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
            if (parser != null) {
                try {
                    FormData formData = parser.parseBlocking();
                    Iterator<String> it = formData.iterator();
                    while (it.hasNext()) {
                        parameterNames.add(it.next());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return new IteratorEnumeration<String>(parameterNames.iterator());
    }

    @Override
    public String[] getParameterValues(final String name) {
        final List<String> ret = new ArrayList<String>();
        Deque<String> params = queryParameters.get(name);
        if (params != null) {
            for (String param : params) {
                try {
                    ret.add(URLDecoder.decode(param, characterEncoding == null ? "ISO-8859-1" : characterEncoding.name()));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (exchange.getRequestMethod().equals(Methods.POST)) {
            readStarted = true;
            final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
            if (parser != null) {
                try {
                    Deque<FormData.FormValue> res = parser.parseBlocking().get(name);
                    if (res == null) {
                        return null;
                    } else {
                        for (FormData.FormValue value : res) {
                            ret.add(value.getValue());
                        }
                    }

                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if (ret.isEmpty()) {
            return null;
        }
        return ret.toArray(new String[ret.size()]);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        final Map<String, String[]> ret = new HashMap<String, String[]>();
        for (Map.Entry<String, Deque<String>> entry : queryParameters.entrySet()) {
            ret.put(entry.getKey(), entry.getValue().toArray(new String[entry.getValue().size()]));
        }
        if (exchange.getRequestMethod().equals(Methods.POST)) {
            readStarted = true;
            final FormDataParser parser = exchange.getAttachment(FormDataParser.ATTACHMENT_KEY);
            if (parser != null) {
                try {
                    FormData formData = parser.parseBlocking();
                    Iterator<String> it = formData.iterator();
                    while (it.hasNext()) {
                        final String name = it.next();
                        Deque<FormData.FormValue> val = formData.get(name);
                        if (ret.containsKey(name)) {
                            String[] existing = ret.get(name);
                            String[] array = new String[val.size() + existing.length];
                            System.arraycopy(existing, 0, array, 0, existing.length);
                            int i = existing.length;
                            for (final FormData.FormValue v : val) {
                                array[i++] = v.getValue();
                            }
                            ret.put(name, array);
                        } else {
                            String[] array = new String[val.size()];
                            int i = 0;
                            for (final FormData.FormValue v : val) {
                                array[i++] = v.getValue();
                            }
                            ret.put(name, array);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return ret;
    }

    @Override
    public String getProtocol() {
        return exchange.getProtocol().toString();
    }

    @Override
    public String getScheme() {
        return exchange.getRequestScheme();
    }

    @Override
    public String getServerName() {
        return exchange.getDestinationAddress().getHostName();
    }

    @Override
    public int getServerPort() {
        return exchange.getDestinationAddress().getPort();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            if (servletInputStream != null) {
                throw UndertowServletMessages.MESSAGES.getInputStreamAlreadyCalled();
            }
            Charset charSet = DEFAULT_CHARSET;
            if (characterEncoding != null) {
                charSet = characterEncoding;
            } else {
                String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
                if (contentType != null) {
                    String c = Headers.extractTokenFromHeader(contentType, "charset");
                    if (c != null) {
                        try {
                            charSet = Charset.forName(c);
                        } catch (UnsupportedCharsetException e) {
                            throw new UnsupportedEncodingException();
                        }
                    }
                }
            }

            reader = new BufferedReader(new InputStreamReader(exchange.getInputStream(), charSet));
        }
        readStarted = true;
        return reader;
    }

    @Override
    public String getRemoteAddr() {
        return exchange.getSourceAddress().getAddress().getHostAddress();
    }

    @Override
    public String getRemoteHost() {
        return exchange.getSourceAddress().getHostName();
    }

    @Override
    public void setAttribute(final String name, final Object object) {
        if(attributes == null) {
            attributes = new HashMap<>();
        }
        Object existing = attributes.put(name, object);
        if (existing != null) {
            servletContext.getDeployment().getApplicationListeners().servletRequestAttributeReplaced(this, name, existing);
        } else {
            servletContext.getDeployment().getApplicationListeners().servletRequestAttributeAdded(this, name, object);
        }
    }

    @Override
    public void removeAttribute(final String name) {
        if(attributes == null) {
            return;
        }
        Object exiting = attributes.remove(name);
        servletContext.getDeployment().getApplicationListeners().servletRequestAttributeRemoved(this, name, exiting);
    }

    @Override
    public Locale getLocale() {
        return Locale.getDefault();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        final List<String> acceptLanguage = exchange.getRequestHeaders().get(Headers.ACCEPT_LANGUAGE);
        if (acceptLanguage == null || acceptLanguage.isEmpty()) {
            return new IteratorEnumeration<Locale>(Collections.singleton(Locale.getDefault()).iterator());
        }
        final List<Locale> ret = new ArrayList<Locale>();
        final List<List<QValueParser.QValueResult>> parsedResults = QValueParser.parse(acceptLanguage);
        for (List<QValueParser.QValueResult> qvalueResult : parsedResults) {
            for (QValueParser.QValueResult res : qvalueResult) {
                if (!res.isQValueZero()) {
                    Locale e = LocaleUtils.getLocaleFromString(res.getValue());
                    ret.add(e);
                }
            }
        }
        return new IteratorEnumeration<Locale>(ret.iterator());
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
        String realPath;
        if (path.startsWith("/")) {
            realPath = path;
        } else {
            String current = exchange.getRelativePath();
            int lastSlash = current.lastIndexOf("/");
            if (lastSlash != -1) {
                current = current.substring(0, lastSlash + 1);
            }
            realPath = CanonicalPathUtils.canonicalize(current + path);
        }
        return new RequestDispatcherImpl(realPath, servletContext);
    }

    @Override
    public String getRealPath(final String path) {
        return null;
    }

    @Override
    public int getRemotePort() {
        return exchange.getSourceAddress().getPort();
    }

    @Override
    public String getLocalName() {
        return exchange.getDestinationAddress().getHostName();
    }

    @Override
    public String getLocalAddr() {
        SocketAddress address = exchange.getConnection().getLocalAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getHostName();
        } else if (address instanceof LocalSocketAddress) {
            return ((LocalSocketAddress) address).getName();
        }
        return null;
    }

    @Override
    public int getLocalPort() {
        SocketAddress address = exchange.getConnection().getLocalAddress();
        if (address instanceof InetSocketAddress) {
            return ((InetSocketAddress) address).getPort();
        }
        return -1;
    }

    @Override
    public ServletContextImpl getServletContext() {
        return servletContext;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        if (!isAsyncSupported()) {
            throw UndertowServletMessages.MESSAGES.startAsyncNotAllowed();
        } else if (asyncContext != null) {
            throw UndertowServletMessages.MESSAGES.asyncAlreadyStarted();
        }
        onAsyncStart();
        asyncListeners.clear();
        return asyncContext = new AsyncContextImpl(exchange, exchange.getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY), exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY));
    }

    @Override
    public AsyncContext startAsync(final ServletRequest servletRequest, final ServletResponse servletResponse) throws IllegalStateException {
        if (!isAsyncSupported()) {
            throw UndertowServletMessages.MESSAGES.startAsyncNotAllowed();
        } else if (asyncContext != null) {
            throw UndertowServletMessages.MESSAGES.asyncAlreadyStarted();
        }
        onAsyncStart();
        asyncListeners.clear();
        return asyncContext = new AsyncContextImpl(exchange, servletRequest, servletResponse);
    }

    @Override
    public boolean isAsyncStarted() {
        return asyncContext != null;
    }

    @Override
    public boolean isAsyncSupported() {
        Boolean supported = exchange.getAttachment(AsyncContextImpl.ASYNC_SUPPORTED);
        return supported == null || supported;
    }

    @Override
    public AsyncContextImpl getAsyncContext() {
        if (asyncContext == null) {
            throw UndertowServletMessages.MESSAGES.asyncNotStarted();
        }
        return asyncContext;
    }

    @Override
    public DispatcherType getDispatcherType() {
        return exchange.getAttachment(DISPATCHER_TYPE_ATTACHMENT_KEY);
    }


    public Map<String, Deque<String>> getQueryParameters() {
        return queryParameters;
    }

    public void setQueryParameters(final Map<String, Deque<String>> queryParameters) {
        this.queryParameters = queryParameters;
    }

    public void setServletContext(final ServletContextImpl servletContext) {
        this.servletContext = servletContext;
    }

    void asyncRequestDispatched() {
        asyncContext = null;
    }

    public static HttpServletRequestImpl getRequestImpl(final ServletRequest request) {
        final HttpServletRequestImpl requestImpl;
        if (request instanceof HttpServletRequestImpl) {
            requestImpl = (HttpServletRequestImpl) request;
        } else if (request instanceof ServletRequestWrapper) {
            requestImpl = getRequestImpl(((ServletRequestWrapper) request).getRequest());
        } else {
            throw UndertowServletMessages.MESSAGES.requestWasNotOriginalOrWrapper(request);
        }
        return requestImpl;
    }


    public void addAsyncListener(final AsyncListener listener) {
        asyncListeners.add(new BoundAsyncListener(listener, this, exchange.getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY)));
    }

    public void addAsyncListener(final AsyncListener listener, final ServletRequest servletRequest, final ServletResponse servletResponse) {
        asyncListeners.add(new BoundAsyncListener(listener, servletRequest, servletResponse));
    }

    public void onAsyncComplete() {
        for (final BoundAsyncListener listener : asyncListeners) {
            AsyncEvent event = new AsyncEvent(asyncContext, listener.servletRequest, listener.servletResponse);
            try {
                listener.asyncListener.onComplete(event);
            } catch (IOException e) {
                UndertowServletLogger.REQUEST_LOGGER.ioExceptionDispatchingAsyncEvent(e);
            }
        }
    }

    public void onAsyncTimeout() {
        for (final BoundAsyncListener listener : asyncListeners) {
            AsyncEvent event = new AsyncEvent(asyncContext, listener.servletRequest, listener.servletResponse);
            try {
                listener.asyncListener.onTimeout(event);
            } catch (IOException e) {
                UndertowServletLogger.REQUEST_LOGGER.ioExceptionDispatchingAsyncEvent(e);
            }
        }
    }

    public void onAsyncStart() {
        for (final BoundAsyncListener listener : asyncListeners) {
            AsyncEvent event = new AsyncEvent(asyncContext, listener.servletRequest, listener.servletResponse);
            try {
                listener.asyncListener.onStartAsync(event);
            } catch (IOException e) {
                UndertowServletLogger.REQUEST_LOGGER.ioExceptionDispatchingAsyncEvent(e);
            }
        }
    }

    public void onAsyncError(Throwable t) {
        for (final BoundAsyncListener listener : asyncListeners) {
            AsyncEvent event = new AsyncEvent(asyncContext, listener.servletRequest, listener.servletResponse, t);
            try {
                listener.asyncListener.onStartAsync(event);
            } catch (IOException e) {
                UndertowServletLogger.REQUEST_LOGGER.ioExceptionDispatchingAsyncEvent(e);
            }
        }
    }

    private final class BoundAsyncListener {
        final AsyncListener asyncListener;
        final ServletRequest servletRequest;
        final ServletResponse servletResponse;

        private BoundAsyncListener(final AsyncListener asyncListener, final ServletRequest servletRequest, final ServletResponse servletResponse) {
            this.asyncListener = asyncListener;
            this.servletRequest = servletRequest;
            this.servletResponse = servletResponse;
        }
    }
}
