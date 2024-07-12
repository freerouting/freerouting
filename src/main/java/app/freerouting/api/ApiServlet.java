package app.freerouting.api;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class ApiServlet extends HttpServlet
{
  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException
  {
    // Delegate to ApiController or directly implement logic here
    resp.setContentType("text/plain");
    resp.getWriter().println("Hello, World!");
  }
}