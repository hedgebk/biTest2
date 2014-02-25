package bthdg.servlet;

import bthdg.*;
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
        log.warning("COUNTER=" + counter);
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

        if (command.equals("configure")) {
            String config = request.getParameter("cfg");
            boolean loaded = configure(config);
            if (loaded) {
                MemcacheStorage.saveConfig(config);
                response.sendRedirect("/");
            } else {
                request.setAttribute("applied", Boolean.FALSE);
                RequestDispatcher dispatcher = request.getRequestDispatcher("/config.jsp"); // forward to config page again
                dispatcher.forward(request, response);
            }
            return;
        } else if (command.equals("state")) {
            getState(response);
            return;
        } else {
            if (command.equals("start") || command.equals("stop") || command.equals("continue")) {
                if (Config.configured()) {
                    if (command.equals("start")) {
                        status = startHdg();
                    } else if (command.equals("stop")) {
                        status = stopHdg();
                    } else if (command.equals("continue")) {
                        status = continueHdg();
                    } else {
                        status = "unknown command '" + command + "'";
                    }
                } else {
                    status = "need config";
                }
            } else {
                status = "unknown command '" + command + "'";
            }
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
        return Config.load(cfg);
    }

    private String startHdg() throws IOException {
        ServletContext servletContext = getServletContext();
        PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
        if (data == null) {
            data = new PairExchangeData(Exchange.BITSTAMP, Exchange.BTCE);
            nextIteration(data);
            return "ok";
        } else if (data.m_isFinished) {
            log.warning("re-running already finished");
            data.maybeStartNewFork();
            nextIteration(data);
            return "ok";
        } else {
            log.warning("already started");
            return "already started";
        }
    }

    private String stopHdg() throws IOException {
        ServletContext servletContext = getServletContext();
        PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
        if (data != null) {
            data.stop();
            saveData(data);
            return "ok";
        } else {
            log.warning("can not stop - not started");
            return "not started";
        }
    }

    private String continueHdg() throws IOException {
        ServletContext servletContext = getServletContext();

        PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
        data = MemcacheStorage.get(data);
        data = GaeStorage.get(data);

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
        writer.append("{ \"status\": \"")
                .append(status)
                .append("\" }");
    }

    private void nextIteration(PairExchangeData data) {
        IterationContext iContext = new IterationContext();
        try {
            if (checkState(data, iContext)) {
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
        saveData(data);
        return ret;
    }

    private void saveData(PairExchangeData data) throws IOException {
        Long timestamp = data.updateTimestamp();
        log.warning("updated timestamp=" + timestamp);

        ServletContext servletContext = getServletContext();
        servletContext.setAttribute(HDG, data);

        String serialized = data.serialize();
        log.warning("serialized(len=" + serialized.length() + ")=" + serialized);
        PairExchangeData deserialized = Deserializer.deserialize(serialized);
        deserialized.compare(data); // make sure all fine

        MemcacheStorage.save(timestamp, serialized);
        GaeStorage.save(timestamp, serialized);
    }

    private void repost(IterationContext iContext) {
        long delay = iContext.m_nextIterationDelay;
        if (delay > 0) {
            log.warning("wait " + delay + " ms." /* + " total running " + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter*/);
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
        log.warning("queue=" + queue);
        queue.add(withUrl("/tst").param("command", "continue"));
    }
}
