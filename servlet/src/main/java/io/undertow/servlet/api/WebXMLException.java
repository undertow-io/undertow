package io.undertow.servlet.api;

/**
 * Wrapper class for any exceptions thrown while parsing web.xml file.
 */
public class WebXMLException extends Exception {
	public WebXMLException(Exception parent) {
		super(parent);
	}
	public WebXMLException(String message) {
		super(message);
	}
}
