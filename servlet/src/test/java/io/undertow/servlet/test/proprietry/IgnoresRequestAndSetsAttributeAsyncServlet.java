package io.undertow.servlet.test.proprietry;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public class IgnoresRequestAndSetsAttributeAsyncServlet extends HttpServlet {

    public static final String ATTRIBUTE_KEY = IgnoresRequestAndSetsAttributeServlet.ATTRIBUTE_KEY;
    public static final String ATTRIBUTE_VALUE = IgnoresRequestAndSetsAttributeServlet.ATTRIBUTE_VALUE;

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
        final AsyncContext ctx = req.startAsync();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    ctx.complete();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        t.start();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
