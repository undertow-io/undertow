package io.undertow.servlet.test.dispatcher.query;

import java.io.IOException;
import java.io.PrintWriter;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ForwardingServlet extends HttpServlet {
    public static final String PARAM_NAME = "parameter-name";
    public static final String PARAM_VALUE = "parameter-value";
    public static final String FWD_TARGET = "forward-target";

    private String paramName;
    private String paramValue;
    private String fwdTarget;

    public ForwardingServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
        paramName = config.getInitParameter(PARAM_NAME);
        paramValue = config.getInitParameter(PARAM_VALUE);
        fwdTarget = config.getInitParameter(FWD_TARGET);
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        if (fwdTarget != null) {
            req.getRequestDispatcher(fwdTarget + "?" + paramName + "=" + paramValue).forward(req, resp);
        } else {
            PrintWriter writer = resp.getWriter();
            writer.write(req.getQueryString());
            writer.close();
        }
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }
}