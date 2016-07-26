package io.undertow.servlet.api;

import io.undertow.jsp.JspFileHandler;
import io.undertow.jsp.JspServletBuilder;
import io.undertow.servlet.core.ManagedServlet;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.EventListener;
import java.util.HashMap;
import java.util.LinkedList;
import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.descriptor.JspPropertyGroupDescriptor;
import javax.servlet.descriptor.TaglibDescriptor;
import javax.servlet.http.HttpServlet;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.jasper.runtime.InstanceManagerFactory;
import org.apache.jasper.servlet.JspServlet;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class WebXMLParser {
	protected interface WebXMLParserFactory {
		void parse(DeploymentInfo deploymentInfo, Element element) throws ClassNotFoundException, WebXMLException;
	}

	protected class UnimplementedFieldParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) {}
	}

	protected class DisplayNameParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) {
			deploymentInfo.setDisplayName(element.getTextContent());
		}
	}

	protected class ErrorPageParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) throws ClassNotFoundException {
			String location = element.getElementsByTagName("location").item(0).getTextContent();
			if(element.getElementsByTagName("error-code").getLength() > 0) {
				deploymentInfo.addErrorPage(new ErrorPage(location, Integer.parseInt(element.getElementsByTagName("error-code").item(0).getTextContent())));
			}
			else if(element.getElementsByTagName("exception-type").getLength() > 0) {
				deploymentInfo.addErrorPage(new ErrorPage(location, Class.forName(element.getElementsByTagName("exception-type").item(0).getTextContent()).asSubclass(Throwable.class)));
			}
			else {
				deploymentInfo.addErrorPage(new ErrorPage(location));
			}
		}
	}

	protected class FilterParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) throws ClassNotFoundException, WebXMLException {
			String name = element.getElementsByTagName("filter-name").item(0).getTextContent();
			FilterInfo filterInfo = new FilterInfo(name, Class.forName(element.getElementsByTagName("filter-class").item(0).getTextContent()).asSubclass(Filter.class));
			NodeList nodeList = element.getElementsByTagName("async-supported");
			if(nodeList.getLength() > 0) {
				filterInfo.setAsyncSupported(Boolean.parseBoolean(nodeList.item(0).getTextContent()));
			}
			nodeList = element.getElementsByTagName("init-param");
			for(int i = 0; i < nodeList.getLength(); i++) {
				Element param = (Element)nodeList.item(i);
				NodeList temp = param.getElementsByTagName("param-name");
				if(temp.getLength() == 0)
					throw new WebXMLException("No param-name specified on init-param for filter "+name);
				String paramName = temp.item(0).getTextContent();
				temp = param.getElementsByTagName("param-value");
				if(temp.getLength() == 0)
					throw new WebXMLException("No param-value specified on init-param "+paramName+" for filter "+name);
				String paramValue = temp.item(0).getTextContent();
				filterInfo.addInitParam(paramName, paramValue);
			}
			deploymentInfo.addFilter(filterInfo);
		}
	}

	protected class FilterMappingParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) throws WebXMLException {
			String name = element.getElementsByTagName("filter-name").item(0).getTextContent();
			DispatcherType dispatcherType = null;
			NodeList nodeList = element.getElementsByTagName("dispatcher");
			if(nodeList.getLength() > 0) {
				dispatcherType = DispatcherType.valueOf(nodeList.item(0).getTextContent());
			}
			else {
				dispatcherType = DispatcherType.REQUEST;
			}
			boolean verifyMapping = false;
			NodeList mappings = element.getElementsByTagName("servlet-name");
			for(int i = 0; i < mappings.getLength(); i++) {
				deploymentInfo.addFilterServletNameMapping(name, mappings.item(i).getTextContent(), dispatcherType);
				verifyMapping = true;
			}
			mappings = element.getElementsByTagName("url-pattern");
			for(int i = 0; i < mappings.getLength(); i++) {
				deploymentInfo.addFilterUrlMapping(name, mappings.item(i).getTextContent(), dispatcherType);
				verifyMapping = true;
			}
			if(!verifyMapping)
				throw new WebXMLException("Neither of servlet or url-pattern defined for filter-mapping for "+name);
		}
	}
	protected class JSPConfigParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) {
			JspConfigDescriptorImpl jspConfig = new JspConfigDescriptorImpl();
			NodeList nodeList = element.getElementsByTagName("taglib");
			for(int i = 0; i < nodeList.getLength(); i++) {
				Element temp = (Element)nodeList.item(i);
				TaglibDescriptorImpl descriptor = new TaglibDescriptorImpl();
				NodeList node = temp.getElementsByTagName("taglib-location");
				if(node.getLength() > 0) {
					descriptor.taglibLocation = node.item(0).getTextContent();
				}
				node = temp.getElementsByTagName("taglib-uri");
				if(node.getLength() > 0) {
					descriptor.taglibURI = node.item(0).getTextContent();
				}
				if(descriptor.taglibLocation != null && descriptor.taglibURI != null) {
					jspConfig.tagLibs.add(descriptor);
				}
			}
			nodeList = element.getElementsByTagName("jsp-property-group");
			for(int i = 0; i < nodeList.getLength(); i++) {
				Element temp = (Element)nodeList.item(i);
				NodeList node = temp.getElementsByTagName("url-pattern"); //required
				if(node.getLength() == 0)
					throw new IllegalStateException("Empty required property url-pattern for jsp-property-group");
				JspPropertyGroupDescriptorImpl descriptor = new JspPropertyGroupDescriptorImpl();
				for(int j = 0; j < node.getLength(); j++) {
					descriptor.urlPatterns.add(node.item(j).getTextContent());
				}
				node = temp.getElementsByTagName("include-prelude");
				for(int j = 0; j < node.getLength(); j++) {
					descriptor.includePreludes.add(node.item(j).getTextContent());
				}
				node = temp.getElementsByTagName("include-coda");
				for(int j = 0; j < node.getLength(); j++) {
					descriptor.includeCodas.add(node.item(j).getTextContent());
				}
				node = temp.getElementsByTagName("el-ignored");
				if(node.getLength() > 0) {
					descriptor.elIgnored = node.item(0).getTextContent();
				}
				node = temp.getElementsByTagName("page-encoding");
				if(node.getLength() > 0) {
					descriptor.pageEncoding = node.item(0).getTextContent();
				}
				node = temp.getElementsByTagName("scripting-invalid");
				if(node.getLength() > 0) {
					descriptor.scriptingInvalid = node.item(0).getTextContent();
				}
				node = temp.getElementsByTagName("is-xml");
				if(node.getLength() > 0) {
					descriptor.isXml = node.item(0).getTextContent();
				}
				node = temp.getElementsByTagName("deferred-syntax-allowed-as-literal");
				if(node.getLength() > 0) {
					descriptor.deferredSyntaxAllowedAsLiteral = node.item(0).getTextContent();
				}
				node = temp.getElementsByTagName("trim-directive-whitespaces");
				if(node.getLength() > 0) {
					descriptor.trimDirectiveWhitespaces = node.item(0).getTextContent();
				}
				node = temp.getElementsByTagName("default-content-type");
				if(node.getLength() > 0) {
					descriptor.defaultContentType = node.item(0).getTextContent();
				}
				node = temp.getElementsByTagName("buffer");
				if(node.getLength() > 0) {
					descriptor.buffer = node.item(0).getTextContent();
				}
				node = temp.getElementsByTagName("error-on-undeclared-namespace");
				if(node.getLength() > 0) {
					descriptor.errorOnUndeclaredNamespace = node.item(0).getTextContent();
				}
				jspConfig.jspPropertyGroups.add(descriptor);
			}
			deploymentInfo.setJspConfigDescriptor(jspConfig);
		}

		protected class JspConfigDescriptorImpl implements JspConfigDescriptor {
			protected Collection<TaglibDescriptor> tagLibs = new LinkedList<>();
			protected Collection<JspPropertyGroupDescriptor> jspPropertyGroups = new LinkedList<>();

			@Override
			public Collection<TaglibDescriptor> getTaglibs() {
				return tagLibs;
			}

			@Override
			public Collection<JspPropertyGroupDescriptor> getJspPropertyGroups() {
				return jspPropertyGroups;
			}
		}

		protected class TaglibDescriptorImpl implements TaglibDescriptor {
			protected String taglibURI = null,
					taglibLocation = null;

			@Override
			public String getTaglibURI() {
				return taglibURI;
			}

			@Override
			public String getTaglibLocation() {
				return taglibLocation;
			}
		}

		protected class JspPropertyGroupDescriptorImpl implements JspPropertyGroupDescriptor {
			protected Collection<String> urlPatterns = new LinkedList<>(),
					includePreludes = new LinkedList<>(),
					includeCodas = new LinkedList<>();
			protected String elIgnored = null,
					pageEncoding = null,
					scriptingInvalid = null,
					isXml = null,
					deferredSyntaxAllowedAsLiteral = null,
					trimDirectiveWhitespaces = null,
					defaultContentType = null,
					buffer = null,
					errorOnUndeclaredNamespace = null;

			@Override
			public Collection<String> getUrlPatterns() {
				return urlPatterns;
			}

			@Override
			public Collection<String> getIncludePreludes() {
				return includePreludes;
			}

			@Override
			public Collection<String> getIncludeCodas() {
				return includeCodas;
			}

			@Override
			public String getElIgnored() {
				return elIgnored;
			}

			@Override
			public String getPageEncoding() {
				return pageEncoding;
			}

			@Override
			public String getScriptingInvalid() {
				return scriptingInvalid;
			}

			@Override
			public String getIsXml() {
				return isXml;
			}

			@Override
			public String getDeferredSyntaxAllowedAsLiteral() {
				return deferredSyntaxAllowedAsLiteral;
			}

			@Override
			public String getTrimDirectiveWhitespaces() {
				return trimDirectiveWhitespaces;
			}

			@Override
			public String getDefaultContentType() {
				return defaultContentType;
			}

			@Override
			public String getBuffer() {
				return buffer;
			}

			@Override
			public String getErrorOnUndeclaredNamespace() {
				return errorOnUndeclaredNamespace;
			}
		}
	}

	protected class ListenerParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) throws ClassNotFoundException {
			NodeList listeners = element.getElementsByTagName("listener-class");
			for(int i = 0; i < listeners.getLength(); i++) {
				deploymentInfo.addListener(new ListenerInfo(Class.forName(listeners.item(i).getTextContent()).asSubclass(EventListener.class)));
			}
		}
	}

	protected class LoginConfigParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) {
			NodeList nodes = element.getElementsByTagName("realm-name");
			if(nodes.getLength() > 0) {
				String name = element.getElementsByTagName("realm-name").item(0).getTextContent();
				nodes = element.getElementsByTagName("form-login-config");
				LoginConfig loginConfig = null;
				if(nodes.getLength() > 0) {
					Element temp = (Element)nodes.item(0);
					loginConfig = new LoginConfig(name,
							temp.getElementsByTagName("form-login-page").item(0).getTextContent(),
							temp.getElementsByTagName("form-error-page").item(0).getTextContent());
				}
				else {
					loginConfig = new LoginConfig(name);
				}
				nodes = element.getElementsByTagName("auth-method");
				if(nodes.getLength() > 0) {
					String[] authMethods = nodes.item(0).getTextContent().split(",");
					for(String authMethod : authMethods) {
						loginConfig.addLastAuthMethod(authMethod);
					}
				}
				deploymentInfo.setLoginConfig(loginConfig);
			}
		}
	}

	protected class MimeMappingParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) {
			String extension = element.getElementsByTagName("extension").item(0).getTextContent(),
					mimeType = element.getElementsByTagName("mime-type").item(0).getTextContent();
			deploymentInfo.addMimeMapping(new MimeMapping(extension, mimeType));
		}
	}

	protected class SecurityConstraintParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) {
			NodeList nodeList = element.getElementsByTagName("web-resource-collection"); //required
			if(nodeList.getLength() == 0)
				throw new IllegalStateException("Empty required property web-resource-collection for security-constraint");
			SecurityConstraint constraint = new SecurityConstraint();
			for(int i = 0; i < nodeList.getLength(); i++) {
				Element temp = (Element)nodeList.item(i);
				WebResourceCollection webResourceCollection = new WebResourceCollection();
				NodeList nodes = temp.getElementsByTagName("url-pattern");
				for(int j = 0; j < nodes.getLength(); j++)
					webResourceCollection.addUrlPattern(nodes.item(j).getTextContent());
				nodes = temp.getElementsByTagName("http-method");
				for(int j = 0; j < nodes.getLength(); j++)
					webResourceCollection.addHttpMethod(nodes.item(j).getTextContent());
				// web-resource-name (required by servlet spec) doesn't appear to be part of WebResourceCollection.java
				constraint.addWebResourceCollection(webResourceCollection);
			}
			nodeList = element.getElementsByTagName("auth-constraint");
			if(nodeList.getLength() > 0) {
				Element temp = (Element)nodeList.item(0);
				NodeList roleNames = temp.getElementsByTagName("role-name");
				for(int i = 0; i < roleNames.getLength(); i++)
					constraint.addRoleAllowed(roleNames.item(i).getTextContent());
			}
			nodeList = element.getElementsByTagName("user-data-constraint");
			if(nodeList.getLength() > 0) {
				Element temp = (Element)nodeList.item(0);
				NodeList transportGuarantee = temp.getElementsByTagName("transport-guarantee");
				if(transportGuarantee.getLength() > 0)
					constraint.setTransportGuaranteeType(TransportGuaranteeType.valueOf(transportGuarantee.item(0).getTextContent()));
			}
			deploymentInfo.addSecurityConstraint(constraint);
		}
	}

	protected class SecurityRoleParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) {
			deploymentInfo.addSecurityRole(element.getElementsByTagName("role-name").item(0).getTextContent());
		}
	}

	private final HashMap<String,ServletInfo> detachedServletMappings = new HashMap<>();

	protected class ServletParser implements WebXMLParserFactory {

		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) throws ClassNotFoundException {
			String name = element.getElementsByTagName("servlet-name").item(0).getTextContent();
			Class servletClass = null;
			if(element.getElementsByTagName("servlet-class").getLength() > 0)
				servletClass = Class.forName(element.getElementsByTagName("servlet-class").item(0).getTextContent()).asSubclass(Servlet.class);
			else if(jspEnabled && element.getElementsByTagName("jsp-file").getLength() > 0)
				servletClass = JspServlet.class;
			else
				servletClass = HttpServlet.class;
			ServletInfo servletInfo = detachedServletMappings.get(name);
			if(servletInfo == null) {
				servletInfo = new ServletInfo(name, servletClass);
			}
			else { //servlet-mapping came before servlet
				detachedServletMappings.remove(name); //remove this to track whether all mappings had corresponding servlets later
				//create new ServletInfo instance since filler class was used, then copy over current settings
				ServletInfo newServletInfo = new ServletInfo(servletInfo.getName(), servletClass);
				newServletInfo.setExecutor(servletInfo.getExecutor());
				newServletInfo.setInstanceFactory(servletInfo.getInstanceFactory());
				newServletInfo.setRequireWelcomeFileMapping(servletInfo.isRequireWelcomeFileMapping());
				newServletInfo.setServletSecurityInfo(servletInfo.getServletSecurityInfo());
				for(String mapping : servletInfo.getMappings())
					newServletInfo.addMapping(mapping);
				servletInfo = newServletInfo;
			}
			NodeList nodeList = element.getElementsByTagName("async-supported");
			if(nodeList.getLength() > 0) {
				servletInfo.setAsyncSupported(Boolean.parseBoolean(nodeList.item(0).getTextContent()));
			}
			nodeList = element.getElementsByTagName("enabled");
			if(nodeList.getLength() > 0) {
				servletInfo.setEnabled(Boolean.parseBoolean(nodeList.item(0).getTextContent()));
			}
			else {
				servletInfo.setEnabled(true);
			}
			nodeList = element.getElementsByTagName("jsp-file");
			if(nodeList.getLength() > 0) {
				String jspFile = nodeList.item(0).getTextContent();
				servletInfo.setJspFile(jspFile);
				if(jspEnabled) {
					servletInfo.addHandlerChainWrapper(JspFileHandler.jspFileHandlerWrapper(jspFile)); //from SimpleJspTestCase.java in Jastow
				}
			}
			nodeList = element.getElementsByTagName("load-on-startup");
			if(nodeList.getLength() > 0) {
				servletInfo.setLoadOnStartup(Integer.parseInt(nodeList.item(0).getTextContent()));
			}
			nodeList = element.getElementsByTagName("multipart-config");
			if(nodeList.getLength() > 0) {
				Element temp = (Element)nodeList.item(0);
				NodeList node = temp.getElementsByTagName("location");
				MultipartConfigElement config = null;
				String location = System.getProperty("java.io.tmpdir");
				if(node.getLength() > 0) {
					location = node.item(0).getTextContent();
				}
				long maxFileSize = -1L;
				node = temp.getElementsByTagName("max-file-size");
				if(node.getLength() > 0) {
					maxFileSize = Long.parseLong(node.item(0).getTextContent());
				}
				long maxRequestSize = -1L;
				node = temp.getElementsByTagName("max-request-size");
				if(node.getLength() > 0) {
					maxRequestSize = Long.parseLong(node.item(0).getTextContent());
				}
				int fileSizeThreshold = 0;
				node = temp.getElementsByTagName("file-size-threshold");
				if(node.getLength() > 0) {
					fileSizeThreshold = Integer.parseInt(node.item(0).getTextContent());
				}
				config = new MultipartConfigElement(location, maxFileSize, maxRequestSize, fileSizeThreshold);
				servletInfo.setMultipartConfig(config);
			}
			else {
				servletInfo.setMultipartConfig(new MultipartConfigElement(System.getProperty("java.io.tmpdir"), -1L, -1L, 0));
			}
			nodeList = element.getElementsByTagName("run-as");
			if(nodeList.getLength() > 0) {
				servletInfo.setRunAs(nodeList.item(0).getTextContent());
			}
			nodeList = element.getElementsByTagName("security-role-ref");
			for(int i = 0; i < nodeList.getLength(); i++) {
				Element securityRoleRef = (Element)nodeList.item(i);
				String roleName = securityRoleRef.getElementsByTagName("role-name").item(0).getTextContent();
				String roleLink = securityRoleRef.getElementsByTagName("role-link").item(0).getTextContent();
				servletInfo.addSecurityRoleRef(roleName, roleLink);
			}
			deploymentInfo.addServlet(servletInfo);
		}
	}
	protected class ServletMappingParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) {
			String name = element.getElementsByTagName("servlet-name").item(0).getTextContent();
			ServletInfo servletInfo = deploymentInfo.getServlets().get(name);
			if(servletInfo == null) { //servlet-mapping came before servlet so create a blank one to fill in later
				servletInfo = new ServletInfo(name, HttpServlet.class);
				detachedServletMappings.put(name, servletInfo);
			}
			NodeList mappings = element.getElementsByTagName("url-pattern");
			for(int i = 0; i < mappings.getLength(); i++)
				servletInfo.addMapping(mappings.item(0).getTextContent());
		}
	}

	protected class SessionConfigParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) {
			NodeList timeout = element.getElementsByTagName("session-timeout");
			if(timeout.getLength() > 0) {
				deploymentInfo.setDefaultSessionTimeout(Integer.parseInt(timeout.item(0).getTextContent()));
			}
		}
	}

	protected class WelcomePageParser implements WebXMLParserFactory {
		@Override
		public void parse(DeploymentInfo deploymentInfo, Element element) {
			NodeList pages = element.getElementsByTagName("welcome-file");
			for(int i = 0; i < pages.getLength(); i++) {
				deploymentInfo.addWelcomePage(pages.item(i).getTextContent());
			}
		}
	}

	protected HashMap<String,WebXMLParserFactory> elementTypes = new HashMap<>();
	protected boolean jspEnabled;

	public WebXMLParser() {
		elementTypes.put("context-param", new UnimplementedFieldParser()); //todo Undertow-specific configurations?
		elementTypes.put("description", new UnimplementedFieldParser());
		elementTypes.put("display-name", new DisplayNameParser());
		elementTypes.put("distributable", new UnimplementedFieldParser());
		elementTypes.put("ejb-local-ref", new UnimplementedFieldParser());
		elementTypes.put("ejb-ref", new UnimplementedFieldParser());
		elementTypes.put("env-entry", new UnimplementedFieldParser());
		elementTypes.put("error-page", new ErrorPageParser());
		elementTypes.put("filter", new FilterParser());
		elementTypes.put("filter-mapping", new FilterMappingParser());
		elementTypes.put("icon", new UnimplementedFieldParser());
		elementTypes.put("jsp-config", new JSPConfigParser());
		elementTypes.put("listener", new ListenerParser());
		elementTypes.put("login-config", new LoginConfigParser());
		elementTypes.put("message-destination-ref", new UnimplementedFieldParser());
		elementTypes.put("mime-mapping", new MimeMappingParser());
		elementTypes.put("resource-env-ref", new UnimplementedFieldParser());
		elementTypes.put("resource-ref", new UnimplementedFieldParser());
		elementTypes.put("security-constraint", new SecurityConstraintParser());
		elementTypes.put("security-role", new SecurityRoleParser());
		elementTypes.put("servlet", new ServletParser());
		elementTypes.put("servlet-mapping", new ServletMappingParser());
		elementTypes.put("session-config", new SessionConfigParser());
		elementTypes.put("welcome-file-list", new WelcomePageParser());
		try {
			Class.forName("io.undertow.jsp.JspServletBuilder"); //check if Jastow present
			jspEnabled = true;
		} catch (ClassNotFoundException e) {
			jspEnabled = false;
		}
	}

	/**
	 * Parses the supplied web.xml file into a DeploymentInfo instance.
	 * @param webXMLInputStream InputStream for web.xml file
	 * @return DeploymentInfo instance corresponding to the supplied web.xml
	 * @throws ClassNotFoundException a class referenced in web.xml did not exist
	 * @throws WebXMLException format of web.xml was incorrect
	 */
	public DeploymentInfo parse(InputStream webXMLInputStream) throws ClassNotFoundException, WebXMLException {
		try {
			DeploymentInfo deploymentInfo = new DeploymentInfo();
			if(jspEnabled) {
				ServletInfo servletInfo = JspServletBuilder.createServlet("jastow-default-jsp-servlet", "*.jsp");
				deploymentInfo.addServlet(servletInfo);
			}
			//using DOM parser since:
			// a) web.xml shouldn't be large enough to not be loadable into memory
			// b) web.xml should only need to be parsed once at startup, so the memory used by DOM parser will be freed
			// c) the structure of web.xml will be much easier to handle with DOM as opposed to SAX
			// d) using JAXB, while even simpler than DOM, would add an additional dependency to the project
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document document = builder.parse(webXMLInputStream);
			Element webapp = document.getDocumentElement();
			parseVersion(deploymentInfo, webapp);
			NodeList nodeList = webapp.getChildNodes();
			for(int i = 0; i < nodeList.getLength(); i++) {
				if(nodeList.item(i) instanceof Element) {
					Element current = (Element)nodeList.item(i);
					WebXMLParserFactory parser = elementTypes.get(current.getNodeName());
					if(parser != null) {
						parser.parse(deploymentInfo, current);
					}
				}
			}
			return deploymentInfo;
		} catch (SAXException | ParserConfigurationException | IOException e) {
			throw new WebXMLException(e);
		}
	}

	protected void parseVersion(DeploymentInfo deploymentInfo, Element element) throws WebXMLException {
		String[] version = element.getAttribute("version").split("\\.");
		if(version.length != 2)
			throw new WebXMLException("Improper or missing servlet API version.");
		deploymentInfo.setMajorVersion(Integer.parseInt(version[0]));
		deploymentInfo.setMinorVersion(Integer.parseInt(version[1]));
	}

	/**
	 * Indicates whether or not JSP is enabled for this deployment (i.e. whether or not Jastow is in the classpath.
	 * @return true if JSP is enabled (Jastow is present), false otherwise
	 */
	public boolean isJspEnabled() {
		return jspEnabled;
	}

	/**
	 * Finalizes Jastow setup for deployment.
	 * @param deploymentInfo fully parsed DeploymentInfo instance from web.xml
	 * @param deploymentManager initialized DeploymentManager instance
	 * @throws ClassNotFoundException a class referenced in web.xml did not exist
	 */
	public void setupJsp(DeploymentInfo deploymentInfo, DeploymentManager deploymentManager) throws ClassNotFoundException {
		if(!jspEnabled)
			throw new UnsupportedOperationException("Attempted to set up JSP context without having Jastow present in classpath.");
		ManagedServlet managagedServlet = deploymentManager.getDeployment().getServlets().getManagedServlet("jastow-default-jsp-servlet");
		JspServletBuilder.setupDeployment(deploymentInfo, new HashMap<>(), new HashMap<>(),
				InstanceManagerFactory.getInstanceManager(managagedServlet.getServletConfig()));
		//todo use HackInstanceManager instead of creating one with InstanceManagerFactory?
	}
}
