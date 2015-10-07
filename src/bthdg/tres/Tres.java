package bthdg.tres;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.BaseExch;
import bthdg.exch.Pair;
import bthdg.exch.TradeDataLight;
import bthdg.osc.BaseExecutor;
import bthdg.osc.TrendWatcher;
import bthdg.tres.alg.TresAlgo;
import bthdg.tres.alg.TresAlgoWatcher;
import bthdg.tres.ind.CciIndicator;
import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.ind.TresIndicator;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;
import bthdg.ws.IWs;
import bthdg.ws.WsFactory;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Tres {
    public static final Pair PAIR = Pair.BTC_CNH;
    public static final boolean PAINT_TICK_TIMES_ONLY = false;
    private static Tres s_inst;

    private Properties m_keys;
    public long m_barSizeMillis;
    int m_len1;
    int m_len2;
    int m_k;
    int m_d;
    int m_phases;
    int m_ma;
    ArrayList<TresExchData> m_exchDatas;
    private TresFrame m_frame;
    private boolean m_processLogs;
    public boolean m_silentConsole;
    public List<Long> m_tickTimes = new ArrayList<Long>();
    public boolean m_logProcessing;
    private String m_e;
    public TreScheme m_scheme;
    public boolean m_calcOsc;
    public boolean m_calcCoppock;
    public boolean m_calcCci;
    String[] m_algosArr;
    String m_runAlgoName;
    public boolean m_collectPoints = true;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }
    public int getPreheatBarsNum() { return m_len1 + m_len2 + (m_k - 1) + (m_d - 1); }
    public long getBarOffset(int index) { return m_barSizeMillis * (index % m_phases) / m_phases; }

    public Tres(String[] args) {
        if (args.length > 0) {
            String first = args[0];
            if (first.equals("logs")) {
                m_processLogs = true;
            } else {
                m_e = first;
            }
        }
    }

    public static void main(String[] args) {
        try {
            s_inst = new Tres(args);
            s_inst.start();

            new IntConsoleReader().run();
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
        if (line.equals("ui")) {
            showUI();
        }
        if (line.startsWith("copp_peak=")) {
            s_inst.updateCoppPeak(line.substring(10));
        }
        if (line.equals("h") || line.equals("help")) {
            log("copp_peak=0.1234");
        }

        return false;
    }

    private void updateCoppPeak(String coppPeakStr) {
        log("updateCoppPeak() coppPeakStr=" + coppPeakStr);
        double coppPeak = Double.parseDouble(coppPeakStr);
        CoppockIndicator.PEAK_TOLERANCE = coppPeak;
        for (TresExchData exchData : m_exchDatas) {
            for (TresAlgoWatcher playAlgo : exchData.m_playAlgos) {
                TresAlgo algo = playAlgo.m_algo;
                updateCoppPeak(coppPeak, algo);
            }
            TresAlgo algo = exchData.m_runAlgo;
            updateCoppPeak(coppPeak, algo);
        }
        String runAlgoParams = m_exchDatas.get(0).getRunAlgoParams();
        log(" updated runAlgoParams=" + runAlgoParams);
    }

    private void updateCoppPeak(double coppPeak, TresAlgo algo) {
        if (algo instanceof TresAlgo.CoppockAlgo) {
            TresAlgo.CoppockAlgo coppAlgo = (TresAlgo.CoppockAlgo) algo;
            for (TresIndicator indicator : coppAlgo.m_indicators) {
                TrendWatcher<ChartPoint> avgPeakCalculator = indicator.m_avgPeakCalculator;
                avgPeakCalculator.m_tolerance = coppPeak;
                log(" avgPeakCalculator.m_tolerance set to " + coppPeak);
                for (TresIndicator.TresPhasedIndicator phasedIndicator : indicator.m_phasedIndicators) {
                    TrendWatcher<ChartPoint> peakCalculator = phasedIndicator.m_peakCalculator;
                    peakCalculator.m_tolerance = coppPeak;
                    log("  peakCalculator.m_tolerance set to " + coppPeak);
                }
            }
        }
    }

    private void onStop() {
        log("stop()-----------------------------------------------------");
        for (TresExchData exchData : m_exchDatas) {
            exchData.stop();
        }
        stopFrame();
    }

    private void start() throws IOException {
        m_keys = BaseExch.loadKeys();
        init();

        if (m_processLogs) {
            m_silentConsole = true;
            m_logProcessing = true;
            TresLogProcessor logProcessor = new TresLogProcessor(m_keys, m_exchDatas);
            logProcessor.start();
        } else {
            for (TresExchData exchData : m_exchDatas) {
                exchData.start();
            }
        }
    }

    private void init() {
        String exchangesStr = (m_e == null) ? getProperty("tre.exchanges") : m_e;
        log("EXCHANGES=" + exchangesStr);
        String[] exchangesArr = exchangesStr.split(",");
        int exchangesLen = exchangesArr.length;
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
        PhaseData.LOCK_OSC_LEVEL = lockOscLevel;

        boolean doTrade = Boolean.parseBoolean(getProperty("tre.do_trade"));
        log("doTrade=" + doTrade);
        BaseExecutor.DO_TRADE = doTrade;

        String treScheme = getProperty("tre.scheme");
        log("treScheme=" + treScheme);
        m_scheme = TreScheme.valueOf(treScheme);

        m_calcOsc = Boolean.parseBoolean(getProperty("tre.calc_osc"));
        log("calc_osc=" + m_calcOsc);
        m_calcCoppock = Boolean.parseBoolean(getProperty("tre.calc_coppock"));
        log("calc_osc=" + m_calcCoppock);
        m_calcCci = Boolean.parseBoolean(getProperty("tre.calc_cci"));
        log("calc_osc=" + m_calcCci);

        String algosStr = getProperty("tre.play.algos");
        log("PLAY.ALGOS=" + algosStr);
        if(algosStr.length() == 0) {
            m_algosArr = null;
        } else {
            m_algosArr = algosStr.split(",");
            int indicatorsLen = m_algosArr.length;
            log(" .len=" + indicatorsLen);
        }

        m_runAlgoName = getProperty("tre.run.algo");
        log("run.algo=" + m_runAlgoName);

        String cciPeakStr = getProperty("tre.cci_peak");
        log("cci_peak=" + cciPeakStr);
        if (cciPeakStr != null) {
            double cciPeak = Double.parseDouble(cciPeakStr);
            CciIndicator.PEAK_TOLERANCE = cciPeak;
        }

        String coppPeakStr = getProperty("tre.copp_peak");
        log("copp_peak=" + coppPeakStr);
        if (coppPeakStr != null) {
            double coppPeak = Double.parseDouble(coppPeakStr);
            CoppockIndicator.PEAK_TOLERANCE = coppPeak;
        }

        String andPeakStr = getProperty("tre.and_peak");
        log("and_peak=" + andPeakStr);
        if (andPeakStr != null) {
            double andPeak = Double.parseDouble(andPeakStr);
            TresAlgo.CncAlgo.AndIndicator.PEAK_TOLERANCE = andPeak;
        }

        String cciCorrStr = getProperty("tre.cci_corr");
        log("cci_corr=" + cciCorrStr);
        if (cciCorrStr != null) {
            double cciCorr = Double.parseDouble(cciCorrStr);
            TresAlgo.CncAlgo.CCI_CORRECTION_RATIO = cciCorr;
        }

        String wmaStr = getProperty("tre.wma");
        log("wma=" + wmaStr);
        if (wmaStr != null) {
            int wma = Integer.parseInt(wmaStr);
            CoppockIndicator.PhasedCoppockIndicator.WMA_LENGTH = wma;
        }

        String lrocStr = getProperty("tre.lroc");
        log("lroc=" + lrocStr);
        if (lrocStr != null) {
            int lroc = Integer.parseInt(lrocStr);
            CoppockIndicator.PhasedCoppockIndicator.LONG_ROC_LENGTH = lroc;
        }

        String srocStr = getProperty("tre.sroc");
        log("sroc=" + srocStr);
        if (srocStr != null) {
            int sroc = Integer.parseInt(srocStr);
            CoppockIndicator.PhasedCoppockIndicator.SHORT_ROС_LENGTH = sroc;
        }

        String smaStr = getProperty("tre.sma");
        log("sma=" + smaStr);
        if (smaStr != null) {
            int sma = Integer.parseInt(smaStr);
            CciIndicator.PhasedCciIndicator.SMA_LENGTH = sma;
        }

        String minOrderStr = getProperty("tre.min_order");
        log("min_order=" + minOrderStr);
        if (minOrderStr != null) {
            double minOrder = Double.parseDouble(minOrderStr);
            TresExecutor.MIN_ORDER_SIZE = minOrder;
        }

        String maxOrderStr = getProperty("tre.max_order");
        log("max_order=" + maxOrderStr);
        if (maxOrderStr != null) {
            double maxOrder = Double.parseDouble(maxOrderStr);
            TresExecutor.MAX_ORDER_SIZE = maxOrder;
        }

        m_exchDatas = new ArrayList<TresExchData>(exchangesLen);
        for (String exch : exchangesArr) {
            IWs ws = WsFactory.get(exch, m_keys);
            m_exchDatas.add(new TresExchData(this, ws));
        }

        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
    }

    private String getProperty(String key) {
        String ret = m_keys.getProperty(key);
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

    private static class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() { System.out.print(">"); }
        @Override protected boolean processLine(String line) throws Exception { return onConsoleLine(line); }
    }

}
