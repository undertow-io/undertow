package io.undertow.servlet.test.metrics;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Tomaz Cerar
 */
public class MetricTestServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        PrintWriter out = resp.getWriter();
        out.print("metric");
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            //we dont care
        }
    }
}
