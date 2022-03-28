package io.undertow.servlet.test.proprietry;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class IgnoresRequestAndSetsAttributeServlet extends HttpServlet {

    public static final String ATTRIBUTE_KEY = "attributeKey";
    public static final String ATTRIBUTE_VALUE = "attributeValue";

    private String attributeKey;
    private String attributeValue;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        attributeKey = config.getInitParameter(ATTRIBUTE_KEY);
        attributeValue = config.getInitParameter(ATTRIBUTE_VALUE);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute(attributeKey, attributeValue);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
