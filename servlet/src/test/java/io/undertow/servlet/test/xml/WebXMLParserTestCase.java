package io.undertow.servlet.test.xml;

import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.FilterMappingInfo;
import io.undertow.servlet.api.SecurityConstraint;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.api.TransportGuaranteeType;
import io.undertow.servlet.api.WebXMLException;
import io.undertow.servlet.api.WebXMLParser;
import java.util.Map;
import javax.servlet.MultipartConfigElement;
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
		filterInfo = (FilterInfo)map.get("TestFilter2");
		Assert.assertEquals("io.undertow.servlet.test.xml.TestFilter", filterInfo.getFilterClass().getName());
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

		//test jsp-config

		//test listener

		//test login-config

		//test mime-mapping

		//test security-role

		//test session-config

		//test welcome-file-list
	}
}
