package com.parallels.pa.reqlimit;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.RequestLimit;

public class CookieRequestLimitingHandler implements HttpHandler {
	private int maximumConcurrentRequests = 5;
	private int queueSize = 10;
	private String cookieName = "JSESSIONID";
	private HttpHandler nextHandler;

	public CookieRequestLimitingHandler(HttpHandler nextHandler) {
		super();
		this.nextHandler = nextHandler;
	}

	private LoadingCache<String, RequestLimit> limiters = CacheBuilder.newBuilder()
			.maximumSize(1000)
			.expireAfterAccess(1, TimeUnit.MINUTES)
			.build(new CacheLoader<String, RequestLimit>() {
				@Override
				public RequestLimit load(String key) throws Exception {
					return new RequestLimit(maximumConcurrentRequests, queueSize);
				}
			});

	@Override
	public void handleRequest(HttpServerExchange exchange) throws Exception {
		Map<String, Cookie> cookies = Optional.ofNullable(exchange.getRequestCookies()).orElse(Collections.emptyMap());
		if (cookies.containsKey(cookieName)) {
			limiters.get(cookies.get(cookieName).getValue()).handleRequest(exchange, nextHandler);
		} else {
			nextHandler.handleRequest(exchange);
		}
	}

	public int getMaximumConcurrentRequests() {
		return maximumConcurrentRequests;
	}

	public void setMaximumConcurrentRequests(int maximumConcurrentRequests) {
		this.maximumConcurrentRequests = maximumConcurrentRequests;
	}

	public int getQueueSize() {
		return queueSize;
	}

	public void setQueueSize(int queueSize) {
		this.queueSize = queueSize;
	}

	public String getCookieName() {
		return cookieName;
	}

	public void setCookieName(String cookieName) {
		this.cookieName = cookieName;
	}
}
