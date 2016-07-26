package io.undertow.servlet.test.xml;

import io.undertow.servlet.api.AuthMethodConfig;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ErrorPage;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.FilterMappingInfo;
import io.undertow.servlet.api.LoginConfig;
import io.undertow.servlet.api.MimeMapping;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.api.WebXMLException;
import io.undertow.servlet.api.WebXMLParser;
import java.util.Map;
import javax.servlet.MultipartConfigElement;
import javax.servlet.descriptor.JspConfigDescriptor;
import javax.servlet.descriptor.JspPropertyGroupDescriptor;
import javax.servlet.descriptor.TaglibDescriptor;
import org.junit.Assert;
import org.junit.Test;

public class WebXMLParserTestCase {
	@Test
	public void testWebXMLParsing() throws WebXMLException, ClassNotFoundException {
		DeploymentInfo info = new WebXMLParser().parse(this.getClass().getClassLoader().getResourceAsStream("web.xml"));
		Assert.assertEquals("test", info.getDisplayName());

		//test version and display-name
		Assert.assertEquals(3, info.getMajorVersion());
		Assert.assertEquals(1, info.getMinorVersion());
		Assert.assertEquals("test", info.getDisplayName());

		//test servlet and servlet-mapping
		Assert.assertEquals(5, info.getServlets().size()); //4 defined, plus JSP servlet
		Map map = info.getServlets();
		ServletInfo servletInfo = (ServletInfo)map.get("TestServlet1");
		Assert.assertEquals("io.undertow.servlet.test.xml.TestServlet", servletInfo.getServletClass().getName());
		Assert.assertFalse(servletInfo.isEnabled());
		MultipartConfigElement multipartConfig = servletInfo.getMultipartConfig();
		Assert.assertEquals(System.getProperty("java.io.tmpdir"), multipartConfig.getLocation());
		Assert.assertEquals(-1L, multipartConfig.getMaxFileSize());
		Assert.assertEquals(-1L, multipartConfig.getMaxRequestSize());
		Assert.assertEquals(0, multipartConfig.getFileSizeThreshold());
		servletInfo = (ServletInfo)map.get("TestServlet2");
		Assert.assertEquals("io.undertow.servlet.test.xml.TestServlet", servletInfo.getServletClass().getName());
		Assert.assertTrue(servletInfo.isAsyncSupported());
		Assert.assertTrue(servletInfo.isEnabled());
		multipartConfig = servletInfo.getMultipartConfig();
		Assert.assertNotNull(multipartConfig.getLocation());
		Assert.assertEquals("/tmp/foo", multipartConfig.getLocation());
		Assert.assertEquals(100, multipartConfig.getMaxFileSize());
		Assert.assertEquals(200, multipartConfig.getMaxRequestSize());
		Assert.assertEquals(10, multipartConfig.getFileSizeThreshold());
		servletInfo = (ServletInfo)map.get("TestServlet3");
		Assert.assertEquals("io.undertow.servlet.test.xml.TestServlet", servletInfo.getServletClass().getName());
		Assert.assertFalse(servletInfo.isAsyncSupported());
		Assert.assertTrue(servletInfo.isEnabled());
		multipartConfig = servletInfo.getMultipartConfig();
		Assert.assertNotNull(multipartConfig.getLocation());
		Assert.assertEquals(System.getProperty("java.io.tmpdir"), multipartConfig.getLocation());
		Assert.assertEquals(100, multipartConfig.getMaxFileSize());
		Assert.assertEquals(-1L, multipartConfig.getMaxRequestSize());
		Assert.assertEquals(0, multipartConfig.getFileSizeThreshold());
		servletInfo = (ServletInfo)map.get("TestServlet4");
		Assert.assertEquals("org.apache.jasper.servlet.JspServlet", servletInfo.getServletClass().getName());
		Assert.assertFalse(servletInfo.isAsyncSupported());
		Assert.assertTrue(servletInfo.isEnabled());
		multipartConfig = servletInfo.getMultipartConfig();
		Assert.assertNotNull(multipartConfig.getLocation());
		Assert.assertEquals(System.getProperty("java.io.tmpdir"), multipartConfig.getLocation());
		Assert.assertEquals(-1L, multipartConfig.getMaxFileSize());
		Assert.assertEquals(-1L, multipartConfig.getMaxRequestSize());
		Assert.assertEquals(0, multipartConfig.getFileSizeThreshold());

		//test filter and filter-mapping
		Assert.assertEquals(3, info.getFilters().size());
		Assert.assertEquals(3, info.getFilterMappings().size());
		map = info.getFilters();
		FilterInfo filterInfo = (FilterInfo)map.get("TestFilter1");
		Assert.assertEquals("io.undertow.servlet.test.xml.TestFilter", filterInfo.getFilterClass().getName());
		Assert.assertTrue(filterInfo.getInitParams().containsKey("foo"));
		Assert.assertEquals("bar", filterInfo.getInitParams().get("foo"));
		filterInfo = (FilterInfo)map.get("TestFilter2");
		Assert.assertEquals("io.undertow.servlet.test.xml.TestFilter", filterInfo.getFilterClass().getName());
		Assert.assertTrue(filterInfo.isAsyncSupported());
		filterInfo = (FilterInfo)map.get("TestFilter3");
		Assert.assertEquals("io.undertow.servlet.test.xml.TestFilter", filterInfo.getFilterClass().getName());
		Assert.assertEquals(3, info.getFilterMappings().size());
		for(FilterMappingInfo filterMappingInfo : info.getFilterMappings()) {
			switch(filterMappingInfo.getFilterName()) {
				case "TestFilter1":
					Assert.assertEquals(FilterMappingInfo.MappingType.URL, filterMappingInfo.getMappingType());
					Assert.assertEquals("/*", filterMappingInfo.getMapping());
					break;
				case "TestFilter2":
					Assert.assertEquals(FilterMappingInfo.MappingType.SERVLET, filterMappingInfo.getMappingType());
					Assert.assertEquals("TestServlet2", filterMappingInfo.getMapping());
					break;
				case "TestFilter3":
					Assert.assertEquals(FilterMappingInfo.MappingType.URL, filterMappingInfo.getMappingType());
					Assert.assertEquals("/test3", filterMappingInfo.getMapping());
					break;
				default:
					throw new IllegalStateException("Filter mapping should not exist for "+filterMappingInfo.getFilterName());
			}
		}

		//test security-constraint
		SecurityConstraint securityConstraint = info.getSecurityConstraints().get(0);
		Assert.assertEquals(1, securityConstraint.getWebResourceCollections().iterator().next().getUrlPatterns().size());
		Assert.assertTrue(securityConstraint.getWebResourceCollections().iterator().next().getUrlPatterns().contains("/*"));
		Assert.assertEquals(2, securityConstraint.getWebResourceCollections().iterator().next().getHttpMethods().size());
		Assert.assertTrue(securityConstraint.getWebResourceCollections().iterator().next().getHttpMethods().contains("GET"));
		Assert.assertTrue(securityConstraint.getWebResourceCollections().iterator().next().getHttpMethods().contains("POST"));
		Assert.assertEquals(TransportGuaranteeType.CONFIDENTIAL, securityConstraint.getTransportGuaranteeType());

		//test error-page
		Assert.assertEquals(3, info.getErrorPages().size());
		for(ErrorPage errorPage : info.getErrorPages()) {
			switch(errorPage.getLocation()) {
				case "/404.html":
					Assert.assertEquals(new Integer(404), errorPage.getErrorCode());
					Assert.assertNull(errorPage.getExceptionType());
					break;
				case "/npe.html":
					Assert.assertNull(errorPage.getErrorCode());
					Assert.assertEquals(NullPointerException.class, errorPage.getExceptionType());
					break;
				case "/error.html":
					Assert.assertNull(errorPage.getErrorCode());
					Assert.assertNull(errorPage.getExceptionType());
					break;
				default:
					throw new IllegalStateException("Error page should not exist for "+errorPage.getLocation());
			}
		}

		//test jsp-config
		JspConfigDescriptor jspConfigDescriptor = info.getJspConfigDescriptor();
		for(JspPropertyGroupDescriptor descriptor : jspConfigDescriptor.getJspPropertyGroups()) {
			Assert.assertEquals(1, descriptor.getUrlPatterns().size());
			switch(descriptor.getUrlPatterns().iterator().next()) {
				case "*.jsp":
					Assert.assertEquals("UTF-8", descriptor.getPageEncoding());
					break;
				case "test1.jsp":
					Assert.assertEquals(2, descriptor.getIncludePreludes().size());
					Assert.assertTrue(descriptor.getIncludePreludes().contains("foo"));
					Assert.assertTrue(descriptor.getIncludePreludes().contains("bar"));
					Assert.assertEquals(1, descriptor.getIncludeCodas().size());
					Assert.assertTrue(descriptor.getIncludeCodas().contains("baz"));
					Assert.assertEquals("text/plain", descriptor.getDefaultContentType());
					Assert.assertEquals("buf", descriptor.getBuffer());
					break;
				case "test2.jsp":
					Assert.assertEquals("true", descriptor.getElIgnored());
					Assert.assertEquals("true", descriptor.getScriptingInvalid());
					Assert.assertEquals("true", descriptor.getIsXml());
					Assert.assertEquals("true", descriptor.getDeferredSyntaxAllowedAsLiteral());
					Assert.assertEquals("true", descriptor.getTrimDirectiveWhitespaces());
					Assert.assertEquals("true", descriptor.getErrorOnUndeclaredNamespace());
					break;
				default:
					throw new IllegalStateException("JSP property group should not exist for "+descriptor.getUrlPatterns().toString());
			}
		}
		for(TaglibDescriptor descriptor : jspConfigDescriptor.getTaglibs()) {
			switch(descriptor.getTaglibURI()) {
				case "foo":
					Assert.assertEquals("WEB-INF/foo.tld", descriptor.getTaglibLocation());
					break;
				case "bar":
					Assert.assertEquals("WEB-INF/bar.tld", descriptor.getTaglibLocation());
					break;
				default:
					throw new IllegalStateException("JSP taglib should not exist for "+descriptor.getTaglibURI().toString());
			}
		}

		//test listener
		Assert.assertEquals(1, info.getListeners().size());
		Assert.assertEquals(TestListener.class, info.getListeners().get(0).getListenerClass());

		//test login-config
		LoginConfig loginConfig = info.getLoginConfig();
		Assert.assertEquals("login_form", loginConfig.getRealmName());
		Assert.assertEquals(2, loginConfig.getAuthMethods().size());
		Assert.assertEquals("FORM", loginConfig.getAuthMethods().get(0).getName());
		Assert.assertEquals("BASIC", loginConfig.getAuthMethods().get(1).getName());
		Assert.assertEquals("login.html", loginConfig.getLoginPage());
		Assert.assertEquals("fail_login.html", loginConfig.getErrorPage());

		//test mime-mapping
		for(MimeMapping mimeMapping : info.getMimeMappings()) {
			switch(mimeMapping.getExtension()) {
				case "txt":
					Assert.assertEquals("text/plain", mimeMapping.getMimeType());
					break;
				case "css":
					Assert.assertEquals("text/css", mimeMapping.getMimeType());
					break;
				case "exe":
					Assert.assertEquals("application/x-dosexec", mimeMapping.getMimeType());
					break;
				default:
					throw new IllegalStateException("Mime mapping should not exist for "+mimeMapping.getExtension());
			}
		}

		//test security-role
		Assert.assertEquals(2, info.getSecurityRoles().size());
		Assert.assertTrue(info.getSecurityRoles().contains("test_role"));
		Assert.assertTrue(info.getSecurityRoles().contains("default"));

		//test session-config
		Assert.assertEquals(100, info.getDefaultSessionTimeout());

		//test welcome-file-list
		Assert.assertEquals(2, info.getWelcomePages().size());
		Assert.assertTrue(info.getWelcomePages().contains("index.jsp"));
		Assert.assertTrue(info.getWelcomePages().contains("index.html"));
	}
}
