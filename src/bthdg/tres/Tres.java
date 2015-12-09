package bthdg.tres;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.ehs.EmbeddedHttpServer;
import bthdg.exch.Config;
import bthdg.exch.OrderData;
import bthdg.exch.Pair;
import bthdg.exch.TradeDataLight;
import bthdg.osc.BaseExecutor;
import bthdg.tres.alg.CncAlgo;
import bthdg.tres.alg.Cno2Algo;
import bthdg.tres.alg.TreAlgo;
import bthdg.tres.ind.CciIndicator;
import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.ind.OscIndicator;
import bthdg.util.ConsoleReader;
import bthdg.util.Sync;
import bthdg.util.Utils;
import bthdg.ws.IWs;
import bthdg.ws.WsFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Tres {
    public static final Pair PAIR = Pair.BTC_CNH;
    public static final boolean PAINT_TICK_TIMES_ONLY = false;
    public static final String ENCRYPT_FILE_KEY = "tre.e_file";
    public static boolean LOG_PARAMS = true;
    private static Tres s_inst;

    public long m_barSizeMillis;
    int m_len1;
    int m_len2;
    int m_k;
    int m_d;
    public int m_phases;
    int m_ma;
    ArrayList<TresExchData> m_exchDatas;
    private TresFrame m_frame;
    private boolean m_processLogs;
    public boolean m_silentConsole;
    public List<Long> m_tickTimes = new ArrayList<Long>();
    public boolean m_logProcessing;
    private String m_e;
    String[] m_algosArr;
    public boolean m_collectPoints = true;
    private Server m_embeddedServer;
    private Config m_config;
    private boolean m_followRemote;
    private int m_serverPort;
    private String m_keystore;
    private String m_keystorePwd;
    private Boolean m_isLocal;
    private String[] m_exchangesArr;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }
    public int getPreheatBarsNum() { return m_len1 + m_len2 + (m_k - 1) + (m_d - 1); }
    public long getBarOffset(int index) { return m_barSizeMillis * (index % m_phases) / m_phases; }

    public Tres(String[] args) {
        if (args.length > 0) {
            String first = args[0];
            if (first.equals("logs")) {
                m_processLogs = true;
            } else if (first.equals("remote")) {
                m_followRemote = true;
            } else {
                m_e = first;
            }
        }
    }

    public static void main(String[] args) {
        try {
            Log.s_impl = new Log.TimestampLog();

            log("============================= started on : " + new Date());

            s_inst = new Tres(args);
            s_inst.start();

            if (s_inst.m_isLocal) {
                new IntConsoleReader().run();
            }
            Thread.sleep(120000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean onConsoleLine(String line) {
        log("onConsoleLine: " + line);
        if (line.equals("s") || line.equals("stop")) {
            s_inst.onStop();
            return true;
        }
        if (line.equals("ui") || line.equals("u")) {
            showUI();
        }
        if (line.equals("reset")) {
            s_inst.reset();
        }
        if (line.equals("start")) {
            try {
                s_inst.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (line.equals("h") || line.equals("help")) {
            log("reset");
        }

        return false;
    }

    private void reset() {
        for (TresExchData exchData : m_exchDatas) {
            exchData.reset();
        }
    }

    private void onStop() {
        log("stop()-----------------------------------------------------");

        List<AtomicBoolean> syncList = null;
        for (final TresExchData exchData : m_exchDatas) {
            final AtomicBoolean sync = new AtomicBoolean(false);
            exchData.m_executor.m_stopCallback = new Runnable() {
                @Override public void run() {
                    log(" stop() finished for " + exchData);
                    exchData.stop();
                    Sync.setAndNotify(sync);
                }
            };
            syncList = Sync.addSync(syncList, sync);
            exchData.sendStopTask();
        }
        Sync.wait(syncList);
        log(" stop() finished for all exchanges");

        stopFrame();
    }

    private void start() throws Exception {
        m_config = new Config() {
            @Override protected String getEncryptedFile() {
                return getProperty(ENCRYPT_FILE_KEY);
            }
        };

        init();
        if (!m_processLogs) {
            startServer();
            boolean needDecrypt = m_config.needDecrypt();
            if (needDecrypt) {
                if (m_isLocal) {
                    if (m_config.getProperty(ENCRYPT_FILE_KEY) != null) {
                        String pwd = ConsoleReader.readConsolePwd("pwd>");
                        if (pwd == null) {
                            throw new RuntimeException("no console - use real console, not inside IDE");
                        }
                        String error = m_config.loadEncrypted(pwd);
                        err(error, new Exception("trace"));
                    }
                } else {
                    return; // waiting for decrypt
                }
            }
        }
        init2();
    }

    private void init2() {
        createExchData();

        if (m_processLogs) {
            m_silentConsole = true;
            m_logProcessing = true;
            TresLogProcessor logProcessor = new TresLogProcessor(m_config, m_exchDatas);
            logProcessor.start();
        } else {
            for (TresExchData exchData : m_exchDatas) {
                exchData.start();
            }
        }
    }

    private void startServer() throws Exception {
        m_embeddedServer = new Server(m_serverPort);
        m_embeddedServer.startServer();

        log("embeddedServer Started on port " + m_embeddedServer.getPort());
    }

    // ======================================================
    private class Server extends EmbeddedHttpServer {
        public Server(int port) {
            super(port);
        }

        @Override protected org.eclipse.jetty.server.Server initServer(int port) {
            org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server();

            HttpConfiguration https = new HttpConfiguration();
            https.addCustomizer(new SecureRequestCustomizer());
            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setKeyStorePath(m_keystore);
            sslContextFactory.setKeyStorePassword(m_keystorePwd);

            ServerConnector sslConnector = new ServerConnector(server,
                    new SslConnectionFactory(sslContextFactory, "http/1.1"),
                    new HttpConnectionFactory(https));
            sslConnector.setPort(port);
            server.setConnectors(new Connector[]{sslConnector});

            return server;
        }

        @Override protected void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
            processRequest(request, response);
        }
    }

    private void init() {
        m_serverPort = Integer.parseInt(getProperty("tre.server_port"));
        log("server_port=" + m_serverPort);
        m_keystore = getProperty("tre.keystore");
        log("keystore=" + m_keystore);
        m_keystorePwd = getProperty("tre.keystore_pwd");
        log("keystore_pwd=" + m_keystorePwd);
        m_isLocal = Boolean.valueOf(getProperty("tre.local"));
        log("local=" + m_isLocal);

        String exchangesStr = (m_e == null) ? getProperty("tre.exchanges") : m_e;
        log("EXCHANGES=" + exchangesStr);
        m_exchangesArr = exchangesStr.split(",");
        int exchangesLen = m_exchangesArr.length;
        log(" .len=" + exchangesLen);

        String barSizeStr = getProperty("tre.bar_size");
        log("barSize=" + barSizeStr);
        m_barSizeMillis = Utils.toMillis(barSizeStr);
        log(" .millis=" + m_barSizeMillis);

        m_len1 = Integer.parseInt(getProperty("tre.len1"));
        log("len1=" + m_len1);
        m_len2 = Integer.parseInt(getProperty("tre.len2"));
        log("len2=" + m_len2);
        m_k = Integer.parseInt(getProperty("tre.k"));
        log("k=" + m_k);
        m_d = Integer.parseInt(getProperty("tre.d"));
        log("d=" + m_d);
        m_phases = Integer.parseInt(getProperty("tre.phases"));
        log("phases=" + m_phases);
        m_ma = Integer.parseInt(getProperty("tre.ma"));
        log("ma=" + m_ma);

        double lockOscLevel = Double.parseDouble(getProperty("tre.osc_lock"));
        log("osc_lock=" + lockOscLevel);
        TresOscCalculator.LOCK_OSC_LEVEL = lockOscLevel;

        boolean doTrade = Boolean.parseBoolean(getProperty("tre.do_trade"));
        log("doTrade=" + doTrade);
        BaseExecutor.DO_TRADE = doTrade;

        String algosStr = getProperty("tre.play.algos");
        if (algosStr.length() == 0) {
            m_algosArr = null;
        } else {
            log("PLAY.ALGOS=" + algosStr);
            m_algosArr = algosStr.split(",");
            int indicatorsLen = m_algosArr.length;
            log(" .len=" + indicatorsLen);
        }

        String cciPeakStr = m_config.getProperty("tre.cci_peak");
        if (cciPeakStr != null) {
            log("cci_peak=" + cciPeakStr);
            double cciPeak = Double.parseDouble(cciPeakStr);
            CciIndicator.PEAK_TOLERANCE = cciPeak;
        }

        String coppPeakStr = m_config.getProperty("tre.copp_peak");
        if (coppPeakStr != null) {
            log("copp_peak=" + coppPeakStr);
            double coppPeak = Double.parseDouble(coppPeakStr);
            CoppockIndicator.PEAK_TOLERANCE = coppPeak;
        }

        String andPeakStr = m_config.getProperty("tre.and_peak");
        if (andPeakStr != null) {
            log("and_peak=" + andPeakStr);
            double andPeak = Double.parseDouble(andPeakStr);
            CncAlgo.AndIndicator.PEAK_TOLERANCE = andPeak;
        }

        String cno2PeakStr = m_config.getProperty("tre.cno2_peak");
        if (cno2PeakStr != null) {
            log("cno2_peak=" + cno2PeakStr);
            double cno2Peak = Double.parseDouble(cno2PeakStr);
            Cno2Algo.MID_PEAK_TOLERANCE = cno2Peak;
        }

        String oscPeakStr = m_config.getProperty("tre.osc_peak");
        if (oscPeakStr != null) {
            log("osc_peak=" + oscPeakStr);
            double oscPeak = Double.parseDouble(oscPeakStr);
            OscIndicator.PEAK_TOLERANCE = oscPeak;
        }

        String cciCorrStr = m_config.getProperty("tre.cci_corr");
        if (cciCorrStr != null) {
            log("cci_corr=" + cciCorrStr);
            double cciCorr = Double.parseDouble(cciCorrStr);
            CncAlgo.CCI_CORRECTION_RATIO = cciCorr;
        }

        String treVelSizeStr = m_config.getProperty("tre.tre_vel_size");
        if (treVelSizeStr != null) {
            log("tre_vel_size=" + treVelSizeStr);
            double treVelSize = Double.parseDouble(treVelSizeStr);
            TreAlgo.TreAlgoBlended.VELOCITY_SIZE_RATE = treVelSize;
        }

        String wmaStr = m_config.getProperty("tre.wma");
        if (wmaStr != null) {
            log("wma=" + wmaStr);
            int wma = Integer.parseInt(wmaStr);
            CoppockIndicator.PhasedCoppockIndicator.WMA_LENGTH = wma;
        }

        String lrocStr = m_config.getProperty("tre.lroc");
        if (lrocStr != null) {
            log("lroc=" + lrocStr);
            int lroc = Integer.parseInt(lrocStr);
            CoppockIndicator.PhasedCoppockIndicator.LONG_ROC_LENGTH = lroc;
        }

        String srocStr = m_config.getProperty("tre.sroc");
        if (srocStr != null) {
            log("sroc=" + srocStr);
            int sroc = Integer.parseInt(srocStr);
            CoppockIndicator.PhasedCoppockIndicator.SHORT_ROС_LENGTH = sroc;
        }

        String smaStr = m_config.getProperty("tre.sma");
        if (smaStr != null) {
            log("sma=" + smaStr);
            int sma = Integer.parseInt(smaStr);
            CciIndicator.PhasedCciIndicator.SMA_LENGTH = sma;
        }

        String minOrderStr = m_config.getProperty("tre.min_order");
        if (minOrderStr != null) {
            log("min_order=" + minOrderStr);
            double minOrder = Double.parseDouble(minOrderStr);
            TresExecutor.MIN_ORDER_SIZE = minOrder;
        }

        String maxOrderStr = m_config.getProperty("tre.max_order");
        if (maxOrderStr != null) {
            log("max_order=" + maxOrderStr);
            double maxOrder = Double.parseDouble(maxOrderStr);
            TresExecutor.MAX_ORDER_SIZE = maxOrder;
        }

        String orderAlgoStr = m_config.getProperty("tre.order_algo");
        if (orderAlgoStr != null) {
            log("orderAlgoStr=" + orderAlgoStr);
            BaseExecutor.OrderPriceMode opm = BaseExecutor.OrderPriceMode.get(orderAlgoStr);
            TresExecutor.ORDER_PRICE_MODE = opm;
        }

        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
    }

    private void createExchData() {
        m_exchDatas = new ArrayList<TresExchData>(m_exchangesArr.length);
        for (String exch : m_exchangesArr) {
            IWs ws = WsFactory.get(exch, m_config.m_keys);
            m_exchDatas.add(new TresExchData(this, ws));
        }
    }

    private String getProperty(String key) {
        String ret = m_config.getProperty(key);
        if (ret == null) {
            throw new RuntimeException("no property found for key '" + key + "'");
        }
        return ret;
    }

    protected static void showUI() {
        s_inst.showFrame();
    }

    private void showFrame() {
        stopFrame();
        m_frame = new TresFrame(s_inst);
        m_frame.setVisible(true);
        m_frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                m_frame = null;
            }
        });
    }

    private void stopFrame() {
        if (m_frame != null) {
            m_frame.stop();
        }
    }

    public void onTrade(TradeDataLight tdata) {
        if(PAINT_TICK_TIMES_ONLY) { // collect tickTimes
            long timestamp = tdata.m_timestamp;
            m_tickTimes.add(timestamp);
        }
        postFrameRepaint();
    }

    protected void postFrameRepaint() {
        if (m_frame != null) {
            m_frame.fireUpdated();
        }
    }

    String getState() {
        StringBuilder sb = new StringBuilder();
        for (TresExchData exchData : m_exchDatas) {
            exchData.getState0(sb);
        }
        return sb.toString();
    }

    public void addControllers(JPanel topPanel, TresCanvas canvas) {
        for (TresExchData exchData : m_exchDatas) {
            topPanel.add(exchData.getController(canvas));
        }
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            boolean needDecrypt = m_config.needDecrypt();
            String requestURI = request.getRequestURI();
            log("processRequest " + requestURI);
            if (needDecrypt && !m_isLocal) {
                log("needDecrypt=" + needDecrypt);
                String pwd = request.getParameter("pwd");
                if ((pwd != null) && !pwd.isEmpty()) {
                    m_config.loadEncrypted(pwd);
                    init2();
                    showState(request, response);
                } else {
                    askPwd(response);
                }
            } else {
                if(requestURI.equals("/park")) { // RequestURI=/park
                    park();
                }
                showState(request, response);
            }
        } catch (IOException e) {
            err("ERROR processRequest: " + e, e);
        }
    }

    private void park() {
        TresExecutor.s_auto = false;
        TresExecutor.s_manualDirection = 0.0;
    }

    private void askPwd(HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().println("<html><body><h1>PWD</h1>" +
                "<FORM action=pwd method=post>" +
                "  <INPUT name=pwd type=password>" +
                "  <INPUT name=go value=\"Go\" type=submit>" +
                "</FORM>" +
                "</body></html>");
    }

    private void showState(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter writer = response.getWriter();

        writer.print("<html><body><h1>STATE</h1><p>");
        TresExchData exchData = m_exchDatas.get(0);
        String[] strings = TresCanvas.getState(exchData);
        for (String string : strings) {
            writer.print(string);
            writer.print("<br/>");
        }

        OrderData order = exchData.m_executor.m_order;
        if (order != null) {
//            g.setColor(order.m_side.isBuy() ? Color.BLUE : Color.RED);
            writer.print(order.toString());
            writer.print("<br/>");
        }
        writer.print("<a href=/>status</a>; <a href=park>park</a><br/>");

        writer.print("<br/>");
        writer.print(" ServletPath=" + request.getServletPath());
        writer.print("<br/>");
        writer.print(" PathInfo=" + request.getPathInfo());
        writer.print("<br/>");
        writer.print(" PathTranslated=" + request.getPathTranslated());
        writer.print("<br/>");
        writer.print(" RequestURI=" + request.getRequestURI());
        writer.print("<br/>");
        writer.print(" QueryString=" + request.getQueryString());
        writer.print("<br/>");
        writer.print("</p></body></html>");
    }

    // ============================================================================================
    private static class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() { System.out.print(">"); }
        @Override protected boolean processLine(String line) throws Exception { return onConsoleLine(line); }
    }
}
