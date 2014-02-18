package bthdg;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.*;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

public class TestServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger("bthdg");
    public static final String COUNTER = "TestServlet.counter";
    public static final String MILLIS = "millis";

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.warning("TestServlet.doPost()");
        doGetPost(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.warning("TestServlet.doGet()");
        doGetPost(request, response);
    }

    private void doGetPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String millisStr = request.getParameter(MILLIS);
        log.warning(" millisStr="+millisStr);
        long millis; // time to execute
        if(millisStr!=null) {
            millis = Long.parseLong(millisStr);
        } else {
            millis = System.currentTimeMillis() + 1000;
        }

        long delay = millis - System.currentTimeMillis();
        log.warning("  delay="+delay);
        if(delay > 0) {
            try {
                Thread.sleep(delay);
                log.warning("   sleep OK");
            } catch (InterruptedException e) {
                throw new ServletException("err: " + e, e);
            }
        }

        long wakeTime = doTask(response);
        if( wakeTime != Long.MAX_VALUE ) {
            Queue queue = QueueFactory.getQueue("oneInSec");
            log.warning("queue="+queue);
            //queue.add();
            queue.add(withUrl("/tst").param(MILLIS, Long.toString(wakeTime)));
        } else {
            log.warning("COUNT finished");
        }
    }

    private long doTask(HttpServletResponse response) throws IOException {
        // ServletContext attributes test
        ServletContext servletContext = getServletContext();
        Integer counter = (Integer) servletContext.getAttribute(COUNTER);
        log.warning("COUNTER="+counter);
        if (counter == null) {
            counter = 1;
        } else {
            counter++;
        }
        servletContext.setAttribute(COUNTER, counter);

        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.print("<html><img src='/img'></html>");

        return (counter < 20) ? System.currentTimeMillis() + 1000 : Long.MAX_VALUE;
    }
}
