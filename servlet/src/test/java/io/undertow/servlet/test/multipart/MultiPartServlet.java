package io.undertow.servlet.test.multipart;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import io.undertow.util.FileUtils;

/**
 * @author Stuart Douglas
 */
public class MultiPartServlet extends HttpServlet {

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        Collection<Part> parts = req.getParts();
        PrintWriter writer = resp.getWriter();
        writer.println("PARAMS:");
        for(Part part : parts) {
            writer.println("name: " + part.getName());
            writer.println("filename: " + part.getSubmittedFileName());
            writer.println("content-type: " + part.getContentType());
            Collection<String> headerNames = new TreeSet<String>(part.getHeaderNames());
            for(String header: headerNames) {
                writer.println(header + ": " + part.getHeader(header));
            }
            writer.println("size: " + part.getSize());
            writer.println("content: " + FileUtils.readFile(part.getInputStream()));
        }
    }
}
