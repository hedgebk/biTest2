package bthdg.servlet;

import bthdg.*;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.*;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestServlet extends HttpServlet {
    private static final Logger log = Logger.getLogger("bthdg");
    public static final String COUNTER = "TestServlet.counter";
    public static final String MILLIS = "millis";
    public static final String HDG = "hdg";

    private static MemcacheService s_memCache = MemcacheServiceFactory.getMemcacheService();
    public static final String MEM_CACHE_KEY = "data";
    public static final String MEM_CACHE_KEY_TIME = "time";

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
        if (command != null) {
            processCommand(command, request, response);
        } else {
            String millisStr = request.getParameter(MILLIS);
            log.warning(" millisStr=" + millisStr);
            long millis; // time to execute
            if (millisStr != null) {
                millis = Long.parseLong(millisStr);
            } else {
                millis = System.currentTimeMillis() + 1000;
            }

            long delay = millis - System.currentTimeMillis();
            log.warning("  delay=" + delay);
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                    log.warning("   sleep OK");
                } catch (InterruptedException e) {
                    throw new ServletException("err: " + e, e);
                }
            }

            long wakeTime = doTask(response);
            if (wakeTime != Long.MAX_VALUE) {
                Queue queue = QueueFactory.getQueue("oneInSec");
                log.warning("queue=" + queue);
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

    private void processCommand(String command, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        log.warning("processCommand '" + command + "'");
        String status;
        if (command.equals("start")) {
            status = startHdg();
        } else if (command.equals("stop")) {
            status = stopHdg();
        } else if (command.equals("continue")) {
            status = continueHdg();
        } else if (command.equals("configure")) {
            boolean loaded = configure(request.getParameter("cfg"));
            if (!loaded) {
                request.setAttribute("applied", Boolean.FALSE);
                RequestDispatcher dispatcher = request.getRequestDispatcher("/config.jsp"); // forward to config page again
                dispatcher.forward(request, response);
            } else {
                response.sendRedirect("/");
            }
            return;
        } else if (command.equals("state")) {
            getState(response);
            return;
        } else {
            status = "unknown command '" + command + "'";
        }
        sendStatus(response, status);
    }

    private void getState(HttpServletResponse response) throws IOException {
        ServletContext servletContext = getServletContext();
        PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
        String st = (data == null) ? "not started" : data.getState();
        String forks = (data == null) ? "[]" : data.getForksState();

        response.setContentType("application/json");
        PrintWriter writer = response.getWriter();
        writer.append("{ \"time\": \"")
                .append(Long.toString(System.currentTimeMillis()))
                .append("\", \"status\": \"")
                .append(st)
                .append("\", \"forks\": ")
                .append(forks)
                .append(" }");
    }

    private boolean configure(String cfg) throws IOException {
        StringReader reader = new StringReader(cfg);
        Properties properties = new Properties();
        properties.load(reader);
        return Config.load(properties);
    }

    private String startHdg() throws IOException {
        if(Config.s_configured) {
            ServletContext servletContext = getServletContext();
            PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
            if (data == null) {
                data = new PairExchangeData(Exchange.BITSTAMP, Exchange.BTCE);
                servletContext.setAttribute(HDG, data);
                nextIteration(data);
                return "ok";
            } else if( data.m_isFinished ) {
                log.warning("re-running already finished");
                data.maybeStartNewFork();
                nextIteration(data);
                return "ok";
            } else {
                log.warning("already started");
                return "already started";
            }
        } else {
            return "need config";
        }
    }

    private String stopHdg() {
        ServletContext servletContext = getServletContext();
        PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
        if (data != null) {
            data.stop();
            return "ok";
        } else {
            log.warning("can not stop - not started");
            return"not started";
        }
    }

    private String continueHdg() throws IOException {
        ServletContext servletContext = getServletContext();
        PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
        Long memTimestamp = (Long) s_memCache.get(MEM_CACHE_KEY_TIME);
        log.warning("memcache timestamp: " + memTimestamp);
        if (data == null) { // try to get from memCache
            log.warning("no data in servletContext");
            if (memTimestamp != null) {
                String serialized = (String) s_memCache.get(MEM_CACHE_KEY);
                if (serialized != null) {
                    log.warning("got data in memcache");
                    PairExchangeData deserialized = Deserializer.deserialize(serialized);
                    data = deserialized;
                } else {
                    log.warning("no serialized data in memcache");
                }
            } else {
                log.warning("no timestamp in memcache");
            }
        } else { // we have some data in servlet context - check if it outdated
            long timestamp = data.m_timestamp;
            log.warning("servletContext timestamp: "+timestamp);
            if (memTimestamp != null) {
                if (memTimestamp > timestamp) {
                    log.warning("memcache timestamp is bigger");
                    String serialized = (String) s_memCache.get(MEM_CACHE_KEY);
                    if (serialized != null) {
                        log.warning("using newer data in memcache");
                        PairExchangeData deserialized = Deserializer.deserialize(serialized);
                        data = deserialized;
                    } else {
                        log.warning("no serialized data in memcache");
                    }
                } else {
                    log.warning("memcache timestamp is less or the same");
                }
            } else {
                log.warning("no timestamp in memcache");
            }
        }

        if (data != null) {
            nextIteration(data);
            return "ok";
        } else {
            log.warning("can not continue - not started");
            return "not started";
        }
    }

    private void sendStatus(HttpServletResponse response, String status) throws IOException {
        response.setContentType("application/json");
        PrintWriter writer = response.getWriter();
        writer.append("{ \"status\": \"" + status + "\" }");
    }

    private void nextIteration(PairExchangeData data) {
        IterationContext iContext = new IterationContext();
        try {
            if(checkState(data, iContext)){
                System.out.println("GOT finish request - no more tasks to run");
            } else {
                repost(iContext);
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "GOT exception during processing. setting ERROR, closing everything...", e);
            data.setState(ForkState.ERROR); // error - stop ALL
            iContext.delay(0);
            repost(iContext); // need to finish
        }
    }

    private boolean checkState(PairExchangeData data, IterationContext iContext) throws Exception {
        boolean ret = data.checkState(iContext);
        Long timestamp = data.updateTimestamp();
        log.warning("updated timestamp="+timestamp);

        ServletContext servletContext = getServletContext();
        servletContext.setAttribute(HDG, data);

        String serialized = data.serialize();
        log.warning("serialized(len=" + serialized.length() + ")=" + serialized);
        PairExchangeData deserialized = Deserializer.deserialize(serialized);
        deserialized.compare(data); // make sure all fine

        s_memCache.put(MEM_CACHE_KEY_TIME, timestamp);
        s_memCache.put(MEM_CACHE_KEY, serialized);

        return ret;
    }

    private void repost(IterationContext iContext) {
        long delay = iContext.m_nextIterationDelay;
        if( delay > 0 ) {
            log.warning("wait " + delay + " ms." /* + " total running " + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter*/ );
            try {
                synchronized (this) {
                    this.wait(delay);
                }
//                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            // todo: protect from called many times with delay==0 - add delay manually
            log.warning("go to next iteration without sleep. total running "/* + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter*/);
        }

        Queue queue = QueueFactory.getQueue("oneInSec");
        log.warning("queue="+queue);
        queue.add(withUrl("/tst").param("command", "continue"));
    }

}
