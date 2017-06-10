package se.hitta.recruitment.server;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class MainServlet extends HttpServlet {
	private static final long serialVersionUID = 7493443767127881113L;
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    	resp.setContentType("text/html; charset=UTF-8");
    	resp.setCharacterEncoding("UTF-8");
    	
    	final PrintWriter out = resp.getWriter();
    	out.println("Hello! This is just a simple front page. Please navigate to /person/ for person management.");
	}
}
