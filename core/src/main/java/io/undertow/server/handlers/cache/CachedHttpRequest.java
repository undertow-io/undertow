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

package io.undertow.server.handlers.cache;

import java.util.Date;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.encoding.AllowedContentEncodings;
import io.undertow.util.DateUtils;
import io.undertow.util.ETag;
import io.undertow.util.ETagUtils;
import io.undertow.util.Headers;

/**
 * @author Stuart Douglas
 */
public class CachedHttpRequest {
    private final String path;
    private final ETag etag;
    private final String contentEncoding;
    private final String contentLocation;
    private final String language;
    private final String contentType;
    private final Date lastModified;
    private final int responseCode;


    public CachedHttpRequest(final HttpServerExchange exchange) {
        this.path = exchange.getRequestPath();
        this.etag = ETagUtils.getETag(exchange);
        this.contentLocation = exchange.getResponseHeaders().getFirst(Headers.CONTENT_LOCATION);
        this.language = exchange.getResponseHeaders().getFirst(Headers.CONTENT_LANGUAGE);
        this.contentType = exchange.getResponseHeaders().getFirst(Headers.CONTENT_TYPE);
        String lmString = exchange.getResponseHeaders().getFirst(Headers.LAST_MODIFIED);
        if (lmString == null) {
            this.lastModified = null;
        } else {
            this.lastModified = DateUtils.parseDate(lmString);
        }
        //the content encoding can be decided dynamically, based on the current state of the request
        //as the decision to compress generally depends on size and mime type
        final AllowedContentEncodings encoding = exchange.getAttachment(AllowedContentEncodings.ATTACHMENT_KEY);
        if(encoding != null) {
            this.contentEncoding = encoding.getCurrentContentEncoding();
        } else {
            this.contentEncoding = exchange.getResponseHeaders().getFirst(Headers.CONTENT_ENCODING);
        }
        this.responseCode = exchange.getStatusCode();
    }

    public String getPath() {
        return path;
    }

    public ETag getEtag() {
        return etag;
    }

    public String getContentEncoding() {
        return contentEncoding;
    }

    public String getLanguage() {
        return language;
    }

    public String getContentType() {
        return contentType;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public String getContentLocation() {
        return contentLocation;
    }

    public int getResponseCode() {
        return responseCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final CachedHttpRequest that = (CachedHttpRequest) o;

        if (responseCode != that.responseCode) return false;
        if (contentEncoding != null ? !contentEncoding.equals(that.contentEncoding) : that.contentEncoding != null)
            return false;
        if (contentLocation != null ? !contentLocation.equals(that.contentLocation) : that.contentLocation != null)
            return false;
        if (contentType != null ? !contentType.equals(that.contentType) : that.contentType != null) return false;
        if (etag != null ? !etag.equals(that.etag) : that.etag != null) return false;
        if (language != null ? !language.equals(that.language) : that.language != null) return false;
        if (lastModified != null ? !lastModified.equals(that.lastModified) : that.lastModified != null) return false;
        if (path != null ? !path.equals(that.path) : that.path != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = path != null ? path.hashCode() : 0;
        result = 31 * result + (etag != null ? etag.hashCode() : 0);
        result = 31 * result + (contentEncoding != null ? contentEncoding.hashCode() : 0);
        result = 31 * result + (contentLocation != null ? contentLocation.hashCode() : 0);
        result = 31 * result + (language != null ? language.hashCode() : 0);
        result = 31 * result + (contentType != null ? contentType.hashCode() : 0);
        result = 31 * result + (lastModified != null ? lastModified.hashCode() : 0);
        result = 31 * result + responseCode;
        return result;
    }
}
