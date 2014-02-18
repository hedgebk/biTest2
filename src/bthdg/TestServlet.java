package bthdg;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.*;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

public class TestServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger("bthdg");
    public static final String COUNTER = "TestServlet.counter";
    public static final String MILLIS = "millis";
    public static final String HDG = "hdg";

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.warning("TestServlet.doPost()");
        doGetPost(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        log.warning("TestServlet.doGet()");
        doGetPost(request, response);
    }

    private void doGetPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String command = request.getParameter("command");
        if( command != null ) {
            processCommand(command);
        } else {
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
    }

    private long doTask(HttpServletResponse response) throws IOException {
        ServletContext servletContext = getServletContext();
        Integer counter = (Integer) servletContext.getAttribute(COUNTER);
        log.warning("COUNTER="+counter);
        if (counter == null) {
            counter = 1;
        } else {
            counter++;
        }
        servletContext.setAttribute(COUNTER, counter);

//        ServletOutputStream outputStream = response.getOutputStream();
//        outputStream.print("<html><img src='/img'></html>");

        return (counter < 20) ? System.currentTimeMillis() + 1000 : Long.MAX_VALUE;
    }

    private void processCommand(String command) {
        log.warning("processCommand '" + command +"'");
        if( command.equals("start") ) {
            startHdg();
        } if( command.equals("stop") ) {
            stopHdg();
        } if( command.equals("continue") ) {
            continueHdg();
        }
    }

    private void startHdg() {
        ServletContext servletContext = getServletContext();
        PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
        if (data == null) {
            data = new PairExchangeData(Exchange.BITSTAMP, Exchange.BTCE);
            servletContext.setAttribute(HDG, data);
            nextIteration(data);
        } else {
            log.warning("already started");
        }
    }

    private void stopHdg() {
        ServletContext servletContext = getServletContext();
        PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
        if (data != null) {
            data.stop();
        } else {
            log.warning("can not stop - not started");
        }
    }

    private void continueHdg() {
        ServletContext servletContext = getServletContext();
        PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
        if (data != null) {
            nextIteration(data);
        } else {
            log.warning("can not continue - not started");
        }
    }

    private void nextIteration(PairExchangeData data) {
        IterationContext iContext = new IterationContext();
        try {
            if( data.checkState(iContext)){
                System.out.println("GOT finish request");
            } else {
                repost(iContext);
            }
        } catch (Exception e) {
            log.severe("GOT exception during processing. setting ERROR, closing everything...");
            data.setState(ForkState.ERROR); // error - stop ALL
            iContext.delay(0);
            repost(iContext);
        }
    }

    private void repost(IterationContext iContext) {
        long delay = iContext.m_nextIterationDelay;
        if( delay > 0 ) {
            log.warning("wait " + delay + " ms." /* + " total running " + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter*/ );
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            log.warning("go to next iteration without sleep. total running "/* + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter*/);
        }

        Queue queue = QueueFactory.getQueue("oneInSec");
        log.warning("queue="+queue);
        queue.add(withUrl("/tst").param("command", "continue"));
    }

}
