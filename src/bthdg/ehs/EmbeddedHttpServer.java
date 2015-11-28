package bthdg.ehs;

import bthdg.Log;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class EmbeddedHttpServer implements Runnable {
    private final Server m_server;

    private static void log(String s) { Log.log(s); }

    public int getPort() {
        Connector[] connectors = m_server.getConnectors();
        Connector connector = connectors[0];
List<String> protocols = connector.getProtocols();
log("protocols=" + protocols);
        return ((ServerConnector)connector).getLocalPort();
    }
    public boolean notStarted() { return !m_server.isStarted(); }

    public static void main(String[] args) {
        // An embedded http server
        EmbeddedHttpServer embeddedServer = new EmbeddedHttpServer(0);
        (new Thread(embeddedServer)).start();
        log("Starting local web server");

        // wait for embedded http server to start
        while (embeddedServer.notStarted()) {}

        log("embeddedServer Started on port " + embeddedServer.getPort());

        embeddedServer.openBrowser();

        try {
            Thread.sleep(600000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void openBrowser() {
        try {
            String browseLocation = "http://localhost:" + getPort();
            log("Opening browser to go here: " + browseLocation);
            browseTo(browseLocation);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void browseTo(String url) throws IOException {
        Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
    }

    public EmbeddedHttpServer(int port) {
        m_server = initServer(port);
        m_server.setHandler(new AbstractHandler() {
            @Override public void handle(String target, Request baseRequest,
                                         HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                log("handle request: " + target);
                baseRequest.setHandled(true);
                handleRequest(request, response);
            }
        });
    }

    protected Server initServer(int port) {
        return new Server(port);
    }

    protected void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
// if (request.getParameter("code") != null && !request.getParameter("code").isEmpty()) {
        response.getWriter().println("<html><body><h1>TEST</h1><p>this is test.</p></body></html>");
    }

    @Override public void run() {
        try {
            m_server.start();
            m_server.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startServer() throws InterruptedException {
        (new Thread(this)).start();
        log("Starting local web server");

        // wait for embedded http server to start
        while (notStarted()) {
            Thread.sleep(100);
        }
    }
}
