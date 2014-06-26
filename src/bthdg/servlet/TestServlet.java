package bthdg.servlet;

import bthdg.Config;
import bthdg.Deserializer;
import bthdg.Log;
import bthdg.duplet.ForkState;
import bthdg.duplet.IterationContext;
import bthdg.duplet.PairExchangeData;
import bthdg.exch.Exchange;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.withUrl;

public class TestServlet extends HttpServlet {
    public static final String COUNTER = "TestServlet.counter";
    public static final String MILLIS = "millis";
    public static final String HDG = "hdg";
    public static final boolean SIMULATE_NEW_CONTEXT = false;
    public static final double SIMULATE_NEW_CONTEXT_RATE = 0.5;
    public static final boolean SIMULATE_MEM_CACHE_FAIL = false;
    public static final double SIMULATE_MEM_CACHE_RATE = 0.25;
    
    private static void logg(String s) { Log.log(s); }
    private static void err(String s, Exception err) { Log.err(s, err); }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logg("TestServlet.doPost()");
        doGetPost(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logg("TestServlet.doGet()");
        doGetPost(request, response);
    }

    private void doGetPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String command = request.getParameter("command");
        if (command != null) {
            processCommand(command, request, response);
        } else {
            String millisStr = request.getParameter(MILLIS);
            logg(" millisStr=" + millisStr);
            long millis; // time to execute
            if (millisStr != null) {
                millis = Long.parseLong(millisStr);
            } else {
                millis = System.currentTimeMillis() + 1000;
            }

            long delay = millis - System.currentTimeMillis();
            logg("  delay=" + delay);
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                    logg("   sleep OK");
                } catch (InterruptedException e) {
                    throw new ServletException("err: " + e, e);
                }
            }

            long wakeTime = doTask(response);
            if (wakeTime != Long.MAX_VALUE) {
                Queue queue = QueueFactory.getQueue("oneInSec");
                logg("queue=" + queue);
                //queue.add();
                queue.add(withUrl("/tst").param(MILLIS, Long.toString(wakeTime)));
            } else {
                logg("COUNT finished");
            }
        }
    }

    private long doTask(HttpServletResponse response) throws IOException {
        ServletContext servletContext = getServletContext();
        Integer counter = (Integer) servletContext.getAttribute(COUNTER);
        logg("COUNTER=" + counter);
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
        logg("processCommand '" + command + "'");
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
                    logg("NOT CONFIGURED");
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
            logg("re-running already finished");
            data.maybeStartNewFork();
            nextIteration(data);
            return "ok";
        } else {
            logg("already started");
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
            logg("can not stop - not started");
            return "not started";
        }
    }

    private String continueHdg() throws IOException {
        ServletContext servletContext = getServletContext();

        PairExchangeData data = (PairExchangeData) servletContext.getAttribute(HDG);
        if(SIMULATE_NEW_CONTEXT) {
            if( new Random().nextDouble() < SIMULATE_NEW_CONTEXT_RATE ) {
                logg("SIMULATING NEW_CONTEXT");
                data = null;
            }
        }

        data = MemcacheStorage.get(data);

        if(SIMULATE_MEM_CACHE_FAIL) {
            if( new Random().nextDouble() < SIMULATE_MEM_CACHE_RATE ) {
                logg("SIMULATING MEM_CACHE_FAIL");
                data = null;
            }
        }

        data = GaeStorage.get(data);

        if (data != null) {
            nextIteration(data);
            return "ok";
        } else {
            logg("can not continue - not started");
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
        IterationContext iContext = new IterationContext(data, null);
        try {
            if (checkState(data, iContext)) {
                log("GOT finish request - no more tasks to run");
            } else {
                repost(iContext);
            }
        } catch (Exception e) {
            err("GOT exception during processing. setting ERROR, closing everything...", e);
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
        logg("updated timestamp=" + timestamp);

        ServletContext servletContext = getServletContext();
        servletContext.setAttribute(HDG, data);

        String serialized = data.serialize();
        logg("serialized(len=" + serialized.length() + ")=" + serialized);
        PairExchangeData deserialized = Deserializer.deserialize(serialized);
        deserialized.compare(data); // make sure all fine

        MemcacheStorage.save(timestamp, serialized);
        GaeStorage.save(timestamp, serialized);
    }

    private void repost(IterationContext iContext) {
        long delay = iContext.m_nextIterationDelay;
        if (delay > 0) {
            logg("wait " + delay + " ms." /* + " total running " + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter*/);
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
            logg("go to next iteration without sleep. total running "/* + Utils.millisToDHMSStr(running) + ", counter=" + iterationCounter*/);
        }

        Queue queue = QueueFactory.getQueue("oneInSec");
        logg("queue=" + queue);
        queue.add(withUrl("/tst").param("command", "continue"));
    }
}
