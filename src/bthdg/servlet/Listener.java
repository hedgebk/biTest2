package bthdg.servlet;

import bthdg.Config;
import bthdg.Log;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.util.logging.Logger;

public class Listener implements ServletContextListener{
    private static final Logger log = Logger.getLogger("bthdg");

    // Public constructor is required by servlet spec
    public Listener() { }

    public void contextInitialized(ServletContextEvent sce) {
        log.warning("Web application is deployed. "+sce.getServletContext().getServerInfo());
        Config.s_runOnServer = true;
        Log.s_impl = new SLog();
    }

    public void contextDestroyed(ServletContextEvent sce) {
        log.warning("Servlet Context (the Web application) is undeployed or Application Server shuts down. "+sce.getServletContext().getServerInfo());
    }
}
