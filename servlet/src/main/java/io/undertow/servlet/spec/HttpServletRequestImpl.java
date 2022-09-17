/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.undertow.servlet.spec;

import io.undertow.security.api.SecurityContext;
import io.undertow.security.idm.Account;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RequestTooBigException;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.MultiPartParserDefinition;
import io.undertow.server.protocol.http.HttpAttachments;
import io.undertow.server.session.Session;
import io.undertow.server.session.SessionConfig;
import io.undertow.servlet.UndertowServletLogger;
import io.undertow.servlet.UndertowServletMessages;
import io.undertow.servlet.api.AuthorizationManager;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.core.ManagedServlet;
import io.undertow.servlet.core.ServletUpgradeListener;
import io.undertow.servlet.handlers.ServletChain;
import io.undertow.servlet.handlers.ServletPathMatch;
import io.undertow.servlet.handlers.ServletRequestContext;
import io.undertow.servlet.util.EmptyEnumeration;
import io.undertow.servlet.util.IteratorEnumeration;
import io.undertow.util.AttachmentKey;
import io.undertow.util.DateUtils;
import io.undertow.util.HeaderMap;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.LocaleUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.security.AccessController;
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

import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.ServletResponseWrapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletMapping;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpUpgradeHandler;
import jakarta.servlet.http.Part;
import jakarta.servlet.http.PushBuilder;

/**
 * The http servlet request implementation. This class is not thread safe
 *
 * @author Stuart Douglas
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public final class HttpServletRequestImpl implements HttpServletRequest {

    @Deprecated
    public static final AttachmentKey<Boolean> SECURE_REQUEST = HttpServerExchange.SECURE_REQUEST;

    static final AttachmentKey<Boolean> REQUESTED_SESSION_ID_SET = AttachmentKey.create(Boolean.class);
    static final AttachmentKey<String> REQUESTED_SESSION_ID = AttachmentKey.create(String.class);

    private final HttpServerExchange exchange;
    private final ServletContextImpl originalServletContext;
    private ServletContextImpl servletContext;

    private Map<String, Object> attributes = null;

    private ServletInputStream servletInputStream;
    private BufferedReader reader;

    private Cookie[] cookies;
    private List<Part> parts = null;
    private volatile boolean asyncStarted = false;
    private volatile AsyncContextImpl asyncContext = null;
    private Map<String, Deque<String>> queryParameters;
    private FormData parsedFormData;
    private RuntimeException formParsingException;
    private Charset characterEncoding;
    private boolean readStarted;
    private SessionConfig.SessionCookieSource sessionCookieSource;

    public HttpServletRequestImpl(final HttpServerExchange exchange, final ServletContextImpl servletContext) {
        this.exchange = exchange;
        this.servletContext = servletContext;
        this.originalServletContext = servletContext;
    }

    public HttpServerExchange getExchange() {
        return exchange;
    }

    @Override
    public String getAuthType() {
        SecurityContext securityContext = exchange.getSecurityContext();

        return securityContext != null ? securityContext.getMechanismName() : null;
    }

    @Override
    public Cookie[] getCookies() {
        if (cookies == null) {
            Iterable<io.undertow.server.handlers.Cookie> cookies = exchange.requestCookies();
            int count = 0;
            for (io.undertow.server.handlers.Cookie cookie : cookies) {
                count++;
            }
            if (count == 0) {
                return null;
            }
            Cookie[] value = new Cookie[count];
            int i = 0;
            for (io.undertow.server.handlers.Cookie cookie : cookies) {
                try {
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
                } catch (IllegalArgumentException e) {
                    // Ignore bad cookie
                }
            }
            if( i < count ) {
                Cookie[] shrunkCookies = new Cookie[i];
                System.arraycopy(value, 0, shrunkCookies, 0, i);
                value = shrunkCookies;
            }
            this.cookies = value;
        }
        return cookies;
    }

    @Override
    public long getDateHeader(final String name) {
        String header = exchange.getRequestHeaders().getFirst(name);
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
        HeaderMap headers = exchange.getRequestHeaders();
        return headers.getFirst(name);
    }

    public String getHeader(final HttpString name) {
        HeaderMap headers = exchange.getRequestHeaders();
        return headers.getFirst(name);
    }


    @Override
    public Enumeration<String> getHeaders(final String name) {
        List<String> headers = exchange.getRequestHeaders().get(name);
        if (headers == null) {
            return EmptyEnumeration.instance();
        }
        return new IteratorEnumeration<>(headers.iterator());
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        final Set<String> headers = new HashSet<>();
        for (final HttpString i : exchange.getRequestHeaders().getHeaderNames()) {
            headers.add(i.toString());
        }
        return new IteratorEnumeration<>(headers.iterator());
    }

    @Override
    public HttpServletMapping getHttpServletMapping() {
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        ServletPathMatch match = src.getOriginalServletPathMatch();
        final DispatcherType dispatcherType = getDispatcherType();
        //UNDERTOW-1899 - ERROR is essentially forward operation
        if(dispatcherType == DispatcherType.FORWARD || dispatcherType == DispatcherType.ERROR || dispatcherType == DispatcherType.ASYNC || dispatcherType == DispatcherType.REQUEST) {
            match = src.getServletPathMatch();
        }
        String matchValue;
        switch (match.getMappingMatch()) {
            case EXACT:
                matchValue = match.getMatched();
                if(matchValue.startsWith("/")) {
                    matchValue = matchValue.substring(1);
                }
                break;
            case DEFAULT:
            case CONTEXT_ROOT:
                matchValue = "";
                break;
            case PATH:
                matchValue = match.getRemaining();
                if (matchValue == null) {
                    matchValue = "";
                } else if (matchValue.startsWith("/")) {
                    matchValue = matchValue.substring(1);
                }
                break;
            case EXTENSION:
                String matched = match.getMatched();
                String matchString = match.getMatchString();
                int startIndex = matched.startsWith("/") ? 1 : 0;
                int endIndex = matched.length() - matchString.length() + 1;
                matchValue = matched.substring(startIndex, endIndex);
                break;
            default:
                matchValue = match.getRemaining();
        }
        return new MappingImpl(matchValue, match.getMatchString(), match.getMappingMatch(), match.getServletChain().getManagedServlet().getServletInfo().getName());
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
        ServletPathMatch match = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getServletPathMatch();
        if (match != null) {
            return match.getRemaining();
        }
        return null;
    }

    @Override
    public String getPathTranslated() {
        return servletContext.getRealPath(getPathInfo());
    }

    @Override
    public String getContextPath() {
        return servletContext.getContextPath();
    }

    @Override
    public String getQueryString() {
        return exchange.getQueryString().isEmpty() ? null : exchange.getQueryString();
    }

    @Override
    public String getRemoteUser() {
        Principal userPrincipal = getUserPrincipal();

        return userPrincipal != null ? userPrincipal.getName() : null;
    }

    @Override
    public boolean isUserInRole(final String role) {
        if (role == null) {
            return false;
        }
        //according to the servlet spec this aways returns false
        if (role.equals("*")) {
            return false;
        }
        SecurityContext sc = exchange.getSecurityContext();
        Account account = sc != null ? sc.getAuthenticatedAccount() : null;
        if (account == null) {
            return false;
        }
        ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);

        if (role.equals("**")) {
            Set<String> roles = servletRequestContext.getDeployment().getDeploymentInfo().getSecurityRoles();
            if (!roles.contains("**")) {
                return true;
            }
        }

        final ServletChain servlet = servletRequestContext.getCurrentServlet();
        final Deployment deployment = servletContext.getDeployment();
        final AuthorizationManager authorizationManager = deployment.getDeploymentInfo().getAuthorizationManager();
        return authorizationManager.isUserInRole(role, account, servlet.getManagedServlet().getServletInfo(), this, deployment);
    }

    @Override
    public Principal getUserPrincipal() {
        SecurityContext securityContext = exchange.getSecurityContext();
        Principal result = null;
        Account account = null;
        if (securityContext != null && (account = securityContext.getAuthenticatedAccount()) != null) {
            result = account.getPrincipal();
        }
        return result;
    }

    @Override
    public String getRequestedSessionId() {
        Boolean isRequestedSessionIdSaved = exchange.getAttachment(REQUESTED_SESSION_ID_SET);
        if (isRequestedSessionIdSaved != null && isRequestedSessionIdSaved) {
            return exchange.getAttachment(REQUESTED_SESSION_ID);
        }
        SessionConfig config = originalServletContext.getSessionConfig();
        if(config instanceof ServletContextImpl.ServletContextSessionConfig) {
            return ((ServletContextImpl.ServletContextSessionConfig)config).getDelegate().findSessionId(exchange);
        }
        return config.findSessionId(exchange);
    }

    @Override
    public String changeSessionId() {
        HttpSessionImpl session = servletContext.getSession(originalServletContext, exchange, false);
        if (session == null) {
            throw UndertowServletMessages.MESSAGES.noSession();
        }
        if (this.exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getServletResponse().isCommitted()) {
            if (!this.servletContext.getDeployment().getDeploymentInfo().isOrphanSessionAllowed()) {
                throw UndertowServletMessages.MESSAGES.sessionIdChangeAfterResponseCommittedNotAllowed();
            }
            UndertowServletLogger.REQUEST_LOGGER.debug("Servlet container configured to permit session identifier changes after response was committed. This can result in a memory leak if session has no timeout.");
        }
        String oldId = session.getId();
        Session underlyingSession;
        if(System.getSecurityManager() == null) {
            underlyingSession = session.getSession();
        } else {
            underlyingSession = AccessController.doPrivileged(new HttpSessionImpl.UnwrapSessionAction(session));
        }
        String newId = underlyingSession.changeSessionId(exchange, originalServletContext.getSessionConfig());
        servletContext.getDeployment().getApplicationListeners().httpSessionIdChanged(session, oldId);
        return newId;
    }

    @Override
    public String getRequestId() {
        return exchange.getRequestId();
    }

    @Override
    public String getProtocolRequestId() {
        return exchange.getConnection().getProtocolRequestId();
    }

    @Override
    public ServletConnection getServletConnection() {
        String connectionId = Long.toString(exchange.getConnection().getId());
        return new ServletConnectionImpl(connectionId, exchange.getProtocol().toString(), isSecure());
    }

    @Override
    public String getRequestURI() {
        //we need the non-decoded string, which means we need to use exchange.getRequestURI()
        if(exchange.isHostIncludedInRequestURI()) {
            //we need to strip out the host part
            String uri = exchange.getRequestURI();
            int slashes =0;
            for(int i = 0; i < uri.length(); ++i) {
                if(uri.charAt(i) == '/') {
                    if(++slashes == 3) {
                        return uri.substring(i);
                    }
                }
            }
            return "/";
        } else {
            return exchange.getRequestURI();
        }
    }

    @Override
    public StringBuffer getRequestURL() {
        return new StringBuffer(exchange.getRequestURL());
    }

    @Override
    public String getServletPath() {
        ServletPathMatch match = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getServletPathMatch();
        if (match != null) {
            return match.getMatched();
        }
        return "";
    }

    @Override
    public HttpSession getSession(final boolean create) {
        return servletContext.getSession(originalServletContext, exchange, create);
    }

    @Override
    public HttpSession getSession() {
        return getSession(true);
    }


    @Override
    public boolean isRequestedSessionIdValid() {
        HttpSessionImpl session = servletContext.getSession(originalServletContext, exchange, false);
        if(session == null) {
            return false;
        }
        if(session.isInvalid()) {
            return false;
        }
        return session.getId().equals(getRequestedSessionId());
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return sessionCookieSource() == SessionConfig.SessionCookieSource.COOKIE;
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return sessionCookieSource() == SessionConfig.SessionCookieSource.URL;
    }

    @Override
    public boolean authenticate(final HttpServletResponse response) throws IOException, ServletException {
        if (response.isCommitted()) {
            throw UndertowServletMessages.MESSAGES.responseAlreadyCommited();
        }

        SecurityContext sc = exchange.getSecurityContext();
        if (sc == null) {
            throw UndertowServletMessages.MESSAGES.noSecurityContextAvailable();
        }

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
            if(!exchange.isResponseStarted() && exchange.getStatusCode() == 200) {
                throw UndertowServletMessages.MESSAGES.authenticationFailed();
            } else {
                return false;
            }
        }
    }

    @Override
    public void login(final String username, final String password) throws ServletException {
        if (username == null || password == null) {
            throw UndertowServletMessages.MESSAGES.loginFailed();
        }
        SecurityContext sc = exchange.getSecurityContext();
        if (sc == null) {
            throw UndertowServletMessages.MESSAGES.noSecurityContextAvailable();
        } else if (sc.isAuthenticated()) {
            throw UndertowServletMessages.MESSAGES.userAlreadyLoggedIn();
        }
        boolean login = false;
        try {
            login = sc.login(username, password);
        }
        catch (SecurityException se) {
            if (se.getCause() instanceof ServletException)
                throw (ServletException) se.getCause();
            throw new ServletException(se);
        }
        if (!login) {
            throw UndertowServletMessages.MESSAGES.loginFailed();
        }
    }

    @Override
    public void logout() throws ServletException {
        SecurityContext sc = exchange.getSecurityContext();
        if (sc == null) {
            throw UndertowServletMessages.MESSAGES.noSecurityContextAvailable();
        }
        sc.logout();
        if(servletContext.getDeployment().getDeploymentInfo().isInvalidateSessionOnLogout()) {
            HttpSession session = getSession(false);
            if(session != null) {
                session.invalidate();
            }
        }
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        verifyMultipartServlet();
        if (parts == null) {
            loadParts();
        }
        return parts;
    }

    private void verifyMultipartServlet() {
        ServletRequestContext src = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        MultipartConfigElement multipart = src.getServletPathMatch().getServletChain().getManagedServlet().getMultipartConfig();
        if(multipart == null) {
            throw UndertowServletMessages.MESSAGES.multipartConfigNotPresent();
        }
    }

    @Override
    public Part getPart(final String name) throws IOException, ServletException {
        verifyMultipartServlet();
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
            exchange.upgradeChannel(new ServletUpgradeListener<>(instance, servletContext.getDeployment(), exchange));
            return instance.getInstance();
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private void loadParts() throws IOException, ServletException {
        final ServletRequestContext requestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);

        if (parts == null) {
            final List<Part> parts = new ArrayList<>();
            String mimeType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
            if (mimeType != null && mimeType.startsWith(MultiPartParserDefinition.MULTIPART_FORM_DATA)) {

                FormData formData = parseFormData();
                if(formData != null) {
                    for (final String namedPart : formData) {
                        for (FormData.FormValue part : formData.get(namedPart)) {
                            parts.add(new PartImpl(namedPart,
                                    part,
                                    requestContext.getOriginalServletPathMatch().getServletChain().getManagedServlet().getMultipartConfig(),
                                    servletContext, this));
                        }
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
        if (attributes == null) {
            return null;
        }
        return attributes.get(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        if (attributes == null) {
            return EmptyEnumeration.instance();
        }
        return new IteratorEnumeration<>(attributes.keySet().iterator());
    }

    @Override
    public String getCharacterEncoding() {
        if (characterEncoding != null) {
            return characterEncoding.name();
        }

        String characterEncodingFromHeader = getCharacterEncodingFromHeader();
        if (characterEncodingFromHeader != null) {
            return characterEncodingFromHeader;
        }
        // first check, web-app context level default request encoding
        if (servletContext.getDeployment().getDeploymentInfo().getDefaultRequestEncoding() != null) {
            return servletContext.getDeployment().getDeploymentInfo().getDefaultRequestEncoding();
        }
        // now check the container level default encoding
        if (servletContext.getDeployment().getDeploymentInfo().getDefaultEncoding() != null) {
            return servletContext.getDeployment().getDeploymentInfo().getDefaultEncoding();
        }
        return null;
    }

    private String getCharacterEncodingFromHeader() {
        String contentType = exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE);
        if (contentType == null) {
            return null;
        }

        return Headers.extractQuotedValueFromHeader(contentType, "charset");
    }

    @Override
    public void setCharacterEncoding(final String env) throws UnsupportedEncodingException {
        if (readStarted) {
            return;
        }
        try {
            characterEncoding = Charset.forName(env);

            final ManagedServlet originalServlet = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getOriginalServletPathMatch().getServletChain().getManagedServlet();
            final FormDataParser parser = originalServlet.getFormParserFactory().createParser(exchange);
            if (parser != null) {
                parser.setCharacterEncoding(env);
            }
        } catch (UnsupportedCharsetException e) {
            throw new UnsupportedEncodingException();
        }
    }

    @Override
    public int getContentLength() {
        long length = getContentLengthLong();
        if(length > Integer.MAX_VALUE) {
            return -1;
        }
        return (int)length;
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
        if(servletInputStream == null) {
            servletInputStream = new ServletInputStreamImpl(this);
        }
        readStarted = true;
        return servletInputStream;
    }

    public void closeAndDrainRequest() throws IOException {
        if (reader != null) {
            reader.close();
        }
        if (servletInputStream == null) {
            servletInputStream = new ServletInputStreamImpl(this);
        }
        servletInputStream.close();
    }

    /**
     * Frees any resources (namely buffers) that may be associated with this request.
     *
     */
    public void freeResources() throws IOException {
        try {
            if(reader != null) {
                reader.close();
            }
            if(servletInputStream != null) {
                servletInputStream.close();
            }
        } finally {
            clearAttributes();
        }
    }

    @Override
    public String getParameter(final String name) {
        if(queryParameters == null) {
            queryParameters = exchange.getQueryParameters();
        }
        Deque<String> params = queryParameters.get(name);
        if (params == null) {
            final FormData parsedFormData = parseFormData();
            if (parsedFormData != null) {
                FormData.FormValue res = parsedFormData.getFirst(name);
                if (res == null || res.isFileItem()) {
                    return null;
                } else {
                    return res.getValue();
                }
            }
            return null;
        }
        return params.getFirst();
    }

    @Override
    public Enumeration<String> getParameterNames() {
        if (queryParameters == null) {
            queryParameters = exchange.getQueryParameters();
        }
        final Set<String> parameterNames = new HashSet<>(queryParameters.keySet());
        final FormData parsedFormData = parseFormData();
        if (parsedFormData != null) {
            Iterator<String> it = parsedFormData.iterator();
            while (it.hasNext()) {
                String name = it.next();
                for(FormData.FormValue param : parsedFormData.get(name)) {
                    if(!param.isFileItem()) {
                        parameterNames.add(name);
                        break;
                    }
                }
            }
        }
        return new IteratorEnumeration<>(parameterNames.iterator());
    }

    @Override
    public String[] getParameterValues(final String name) {
        if (queryParameters == null) {
            queryParameters = exchange.getQueryParameters();
        }
        final List<String> ret = new ArrayList<>();
        Deque<String> params = queryParameters.get(name);
        if (params != null) {
            for (String param : params) {
                ret.add(param);
            }
        }
        final FormData parsedFormData = parseFormData();
        if (parsedFormData != null) {
            Deque<FormData.FormValue> res = parsedFormData.get(name);
            if (res != null) {
                for (FormData.FormValue value : res) {
                    if(!value.isFileItem()) {
                        ret.add(value.getValue());
                    }
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
        if (queryParameters == null) {
            queryParameters = exchange.getQueryParameters();
        }
        final Map<String, ArrayList<String>> arrayMap = new HashMap<>();
        for (Map.Entry<String, Deque<String>> entry : queryParameters.entrySet()) {
            arrayMap.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        final FormData parsedFormData = parseFormData();
        if (parsedFormData != null) {
            Iterator<String> it = parsedFormData.iterator();
            while (it.hasNext()) {
                final String name = it.next();
                Deque<FormData.FormValue> val = parsedFormData.get(name);
                if (arrayMap.containsKey(name)) {
                    ArrayList<String> existing = arrayMap.get(name);
                    for (final FormData.FormValue v : val) {
                        if(!v.isFileItem()) {
                            existing.add(v.getValue());
                        }
                    }
                } else {
                    final ArrayList<String> values = new ArrayList<>();
                    for (final FormData.FormValue v : val) {
                        if(!v.isFileItem()) {
                            values.add(v.getValue());
                        }
                    }
                    if (!values.isEmpty()) {
                        arrayMap.put(name, values);
                    }
                }
            }
        }
        final Map<String, String[]> ret = new HashMap<>();
        for(Map.Entry<String, ArrayList<String>> entry : arrayMap.entrySet()) {
            ret.put(entry.getKey(), entry.getValue().toArray(new String[entry.getValue().size()]));
        }
        return ret;
    }

    private FormData parseFormData() {
        if(formParsingException != null) {
            throw formParsingException;
        }
        if (parsedFormData == null) {
            if (readStarted) {
                return null;
            }
            final ManagedServlet originalServlet = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getCurrentServlet().getManagedServlet();
            final FormDataParser parser = originalServlet.getFormParserFactory().createParser(exchange);
            if (parser == null) {
                return null;
            }
            readStarted = true;
            try {
                return parsedFormData = parser.parseBlocking();
            } catch (RequestTooBigException | MultiPartParserDefinition.FileTooLargeException e) {
                throw formParsingException = new IllegalStateException(e);
            } catch (RuntimeException e) {
                throw formParsingException = e;
            } catch (IOException e) {
                throw formParsingException = new RuntimeException(e);
            }
        }
        return parsedFormData;
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
        return exchange.getHostName();
    }

    @Override
    public int getServerPort() {
        return exchange.getHostPort();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            if (servletInputStream != null) {
                throw UndertowServletMessages.MESSAGES.getInputStreamAlreadyCalled();
            }
            Charset charSet = null;
            if (this.characterEncoding != null) {
                charSet = this.characterEncoding;
            } else {
                final String c = getCharacterEncoding();
                if (c != null) {
                    try {
                        charSet = Charset.forName(c);
                    } catch (UnsupportedCharsetException e) {
                        throw new UnsupportedEncodingException(e.getMessage());
                    }
                }
            }

            reader = new BufferedReader(charSet == null ? new InputStreamReader(exchange.getInputStream(), StandardCharsets.ISO_8859_1)
                    : new InputStreamReader(exchange.getInputStream(), charSet));
        }
        readStarted = true;
        return reader;
    }

    @Override
    public String getRemoteAddr() {
        InetSocketAddress sourceAddress = exchange.getSourceAddress();
        if(sourceAddress == null) {
            return "";
        }
        InetAddress address = sourceAddress.getAddress();
        if(address == null) {
            //this is unresolved, so we just return the host name
            //not exactly spec, but if the name should be resolved then a PeerNameResolvingHandler should be used
            //and this is probably better than just returning null
            return sourceAddress.getHostString();
        }
        return address.getHostAddress();
    }

    @Override
    public String getRemoteHost() {
        InetSocketAddress sourceAddress = exchange.getSourceAddress();
        if(sourceAddress == null) {
            return "";
        }
        return sourceAddress.getHostString();
    }

    @Override
    public void setAttribute(final String name, final Object object) {
        if(object == null) {
            removeAttribute(name);
            return;
        }
        if (attributes == null) {
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
        if (attributes == null) {
            return;
        }
        Object exiting = attributes.remove(name);
        servletContext.getDeployment().getApplicationListeners().servletRequestAttributeRemoved(this, name, exiting);
    }

    @Override
    public Locale getLocale() {
        return getLocales().nextElement();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        final List<String> acceptLanguage = exchange.getRequestHeaders().get(Headers.ACCEPT_LANGUAGE);
        List<Locale> ret = LocaleUtils.getLocalesFromHeader(acceptLanguage);
        if(ret.isEmpty()) {
            return new IteratorEnumeration<>(Collections.singletonList(Locale.getDefault()).iterator());
        }
        return new IteratorEnumeration<>(ret.iterator());
    }

    @Override
    public boolean isSecure() {
        return exchange.isSecure();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
        if (path == null) {
            return null;
        }
        String realPath;
        if (path.startsWith("/")) {
            realPath = path;
        } else {
            String current = exchange.getRelativePath();
            int lastSlash = current.lastIndexOf("/");
            if (lastSlash != -1) {
                current = current.substring(0, lastSlash + 1);
            }
            realPath = current + path;
        }
        return servletContext.getRequestDispatcher(realPath);
    }

    @Override
    public int getRemotePort() {
        return exchange.getSourceAddress().getPort();
    }

    /**
     * String java.net.InetAddress.getHostName()
     * Gets the host name for this IP address.
     * If this InetAddress was created with a host name, this host name will be remembered and returned; otherwise, a reverse name lookup will be performed and the result will be returned based on the system configured name lookup service. If a lookup of the name service is required, call getCanonicalHostName.
     * If there is a security manager, its checkConnect method is first called with the hostname and -1 as its arguments to see if the operation is allowed. If the operation is not allowed, it will return the textual representation of the IP address.
     * @see InetAddres#getHostName
     */
    @Override
    public String getLocalName() {
        return exchange.getDestinationAddress().getHostName();
    }

    @Override
    public String getLocalAddr() {
        InetSocketAddress destinationAddress = exchange.getDestinationAddress();
        if (destinationAddress == null) {
            return "";
        }
        InetAddress address = destinationAddress.getAddress();
        if (address == null) {
            //this is unresolved, so we just return the host name
            return destinationAddress.getHostString();
        }
        return address.getHostAddress();
    }

    @Override
    public int getLocalPort() {
        return exchange.getDestinationAddress().getPort();
    }

    @Override
    public ServletContextImpl getServletContext() {
        return servletContext;
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        if (!isAsyncSupported()) {
            throw UndertowServletMessages.MESSAGES.startAsyncNotAllowed();
        } else if (asyncStarted) {
            throw UndertowServletMessages.MESSAGES.asyncAlreadyStarted();
        }
        asyncStarted = true;
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        return asyncContext = new AsyncContextImpl(exchange, servletRequestContext.getServletRequest(), servletRequestContext.getServletResponse(), servletRequestContext, false, asyncContext);
    }

    @Override
    public AsyncContext startAsync(final ServletRequest servletRequest, final ServletResponse servletResponse) throws IllegalStateException {
        final ServletRequestContext servletRequestContext = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
        if (!servletContext.getDeployment().getDeploymentInfo().isAllowNonStandardWrappers()) {
            if (servletRequestContext.getOriginalRequest() != servletRequest) {
                if (!(servletRequest instanceof ServletRequestWrapper)) {
                    throw UndertowServletMessages.MESSAGES.requestWasNotOriginalOrWrapper(servletRequest);
                }
            }
            if (servletRequestContext.getOriginalResponse() != servletResponse) {
                if (!(servletResponse instanceof ServletResponseWrapper)) {
                    throw UndertowServletMessages.MESSAGES.responseWasNotOriginalOrWrapper(servletResponse);
                }
            }
        }
        if (!isAsyncSupported()) {
            throw UndertowServletMessages.MESSAGES.startAsyncNotAllowed();
        } else if (asyncStarted) {
            throw UndertowServletMessages.MESSAGES.asyncAlreadyStarted();
        }
        asyncStarted = true;
        servletRequestContext.setServletRequest(servletRequest);
        servletRequestContext.setServletResponse(servletResponse);
        return asyncContext = new AsyncContextImpl(exchange, servletRequest, servletResponse, servletRequestContext, true, asyncContext);
    }

    @Override
    public boolean isAsyncStarted() {
        return asyncStarted;
    }

    @Override
    public boolean isAsyncSupported() {
        return exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).isAsyncSupported();
    }

    @Override
    public AsyncContextImpl getAsyncContext() {
        if (!isAsyncStarted()) {
            throw UndertowServletMessages.MESSAGES.asyncNotStarted();
        }
        return asyncContext;
    }

    public AsyncContextImpl getAsyncContextInternal() {
        return asyncContext;
    }

    @Override
    public DispatcherType getDispatcherType() {
        return exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY).getDispatcherType();
    }


    public Map<String, Deque<String>> getQueryParameters() {
        if (queryParameters == null) {
            queryParameters = exchange.getQueryParameters();
        }
        return queryParameters;
    }

    public void setQueryParameters(final Map<String, Deque<String>> queryParameters) {
        this.queryParameters = queryParameters;
    }

    public void setServletContext(final ServletContextImpl servletContext) {
        this.servletContext = servletContext;
    }

    void asyncRequestDispatched() {
        asyncStarted = false;
    }

    public String getOriginalRequestURI() {
        String uri = (String) getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        if(uri != null) {
            return uri;
        }
        uri = (String) getAttribute(AsyncContext.ASYNC_REQUEST_URI);
        if(uri != null) {
            return uri;
        }
        return getRequestURI();
    }


    public String getOriginalServletPath() {
        String uri = (String) getAttribute(RequestDispatcher.FORWARD_SERVLET_PATH);
        if(uri != null) {
            return uri;
        }
        uri = (String) getAttribute(AsyncContext.ASYNC_SERVLET_PATH);
        if(uri != null) {
            return uri;
        }
        return getServletPath();
    }

    public String getOriginalPathInfo() {
        String uri = (String) getAttribute(RequestDispatcher.FORWARD_PATH_INFO);
        if(uri != null) {
            return uri;
        }
        uri = (String) getAttribute(AsyncContext.ASYNC_PATH_INFO);
        if(uri != null) {
            return uri;
        }
        return getPathInfo();
    }

    public String getOriginalContextPath() {
        String uri = (String) getAttribute(RequestDispatcher.FORWARD_CONTEXT_PATH);
        if(uri != null) {
            return uri;
        }
        uri = (String) getAttribute(AsyncContext.ASYNC_CONTEXT_PATH);
        if(uri != null) {
            return uri;
        }
        return getContextPath();
    }

    public String getOriginalQueryString() {
        String uri = (String) getAttribute(RequestDispatcher.FORWARD_QUERY_STRING);
        if(uri != null) {
            return uri;
        }
        uri = (String) getAttribute(AsyncContext.ASYNC_QUERY_STRING);
        if(uri != null) {
            return uri;
        }
        return getQueryString();
    }

    private SessionConfig.SessionCookieSource sessionCookieSource() {
        HttpSession session = getSession(false);
        if(session == null) {
            return SessionConfig.SessionCookieSource.NONE;
        }
        if(sessionCookieSource == null) {
            sessionCookieSource = originalServletContext.getSessionConfig().sessionCookieSource(exchange);
        }
        return sessionCookieSource;
    }

    @Override
    public String toString() {
        return "HttpServletRequestImpl [ " + getMethod() + ' ' + getRequestURI() + " ]";
    }

    public void clearAttributes() {
        if(attributes != null) {
            this.attributes.clear();
        }
    }

    @Override
    public PushBuilder newPushBuilder() {
        if(exchange.getConnection().isPushSupported()) {
            return new PushBuilderImpl(this);
        }
        return null;
    }

    @Override
    public Map<String, String> getTrailerFields() {
        HeaderMap trailers = exchange.getAttachment(HttpAttachments.REQUEST_TRAILERS);
        if(trailers == null) {
            return Collections.emptyMap();
        }
        Map<String, String> ret = new HashMap<>();
        for(HeaderValues entry : trailers) {
            ret.put(entry.getHeaderName().toString().toLowerCase(Locale.ENGLISH), entry.getFirst());
        }
        return ret;
    }

    @Override
    public boolean isTrailerFieldsReady() {
        if(exchange.isRequestComplete()) {
            return true;
        }
        return !exchange.getConnection().isRequestTrailerFieldsSupported();
    }
}
