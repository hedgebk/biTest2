package bthdg.ehs;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class EmbeddedHttpServer implements Runnable {
    private final Server m_server;

    public int getPort() { return m_server.getConnectors()[0].getLocalPort(); }
    public boolean notStarted() { return !m_server.isStarted(); }

    public static void main(String[] args) {
        // An embedded http server
        EmbeddedHttpServer embeddedServer = new EmbeddedHttpServer();
        (new Thread(embeddedServer)).start();
        System.out.println("Starting local web server");

        // wait for embedded http server to start
        while (embeddedServer.notStarted()) {}

        System.out.println("embeddedServer Started on port "+embeddedServer.getPort());

        try {
            String browseLocation = "http://localhost:" + embeddedServer.getPort();
            System.out.println("Opening browser to go here: " + browseLocation);
            browseTo(browseLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(600000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static void browseTo(String url) throws IOException {
        Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
    }

    public EmbeddedHttpServer() {
        m_server = new Server(0);
        m_server.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request,
                               HttpServletResponse response) throws IOException, ServletException {
                System.out.println("handle request: " + target);
                response.setContentType("text/html;charset=utf-8");
                response.setStatus(HttpServletResponse.SC_OK);
                baseRequest.setHandled(true);
//                if (request.getParameter("code") != null && !request.getParameter("code").isEmpty()) {
                response.getWriter().println("<html><body><h1>TEST</h1>"
                        + "<p>this is test.</p></body></html>");
            }
        });
    }

    @Override public void run() {
        try {
            m_server.start();
            m_server.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
