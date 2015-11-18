package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeDataLight;
import bthdg.osc.BaseExecutor;
import bthdg.tres.alg.CncAlgo;
import bthdg.tres.alg.CoppockVelocityAlgo;
import bthdg.tres.alg.TresAlgoWatcher;
import bthdg.tres.ind.CciIndicator;
import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.ind.OscIndicator;
import bthdg.util.BufferedLineReader;
import bthdg.util.LineReader;
import bthdg.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TresLogProcessor extends Thread {
    // onTrade[OKCOIN]: TradeData{amount=0.01000, price=1766.62000, time=1437739761000, tid=0, type=BID}
    private static final Pattern TRE_TRADE_PATTERN = Pattern.compile("onTrade\\[\\w+\\]\\: TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+), time=(\\d+).+");
    // TresExecutor.gotTop() buy=2150.71; sell=2151.0
    private static final Pattern TRE_TOP_PATTERN = Pattern.compile("TresExecutor.gotTop\\(\\) buy=(\\d+\\.\\d+); sell=(\\d+\\.\\d+)");
    // 1426040622351: State.onTrade(tData=TradeData{amount=5.00000, price=1831.00000, time=1426040623000, tid=0, type=ASK}) on NONE *********************************************
    private static final Pattern OSC_TRADE_PATTERN = Pattern.compile("\\d+: State.onTrade\\(tData=TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+), time=(\\d+).+");
    private static final Pattern FX_TRADE_PATTERN = Pattern.compile("EUR/USD,(\\d\\d\\d\\d)(\\d\\d)(\\d\\d) (\\d\\d):(\\d\\d):(\\d\\d).(\\d\\d\\d),(\\d+\\.\\d+),(\\d+.\\d+)");

    private static final Calendar GMT_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ENGLISH);
    public static final int READ_BUFFER_SIZE = 1024 * 32;
    public static final int PARSE_THREADS_NUM = 2;
    public static final int PROCESS_THREADS_NUM = 8;

    private TresExchData m_exchData;
    private String m_logFilePattern;
    private String m_varyMa;
    private String m_varyBarSize;
    private String m_varyPhases;
    private String m_varyLen1;
    private String m_varyLen2;
    private String m_varyOscLock;
    private String m_varyOscPeak;
    private String m_varyCoppPeak;
    private String m_varyAndPeak;
    private String m_varyCciPeak;
    private String m_varyCciCorr;
    private String m_varyWma;
    private String m_varyLroc;
    private String m_varySroc;
    private String m_varySma;
    private String m_varyCovK;
    private String m_varyCovRat;
    private String m_varyCovVel;
    private AtomicInteger cloneCounter = new AtomicInteger(0);
    private long m_linesParsed;
    private long s_lastTradeMillis;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public TresLogProcessor(Properties keys, ArrayList<TresExchData> exchDatas) {
        init(keys);
        m_exchData = exchDatas.get(0);
    }

    private void init(Properties keys) {
        Tres.LOG_PARAMS = false;

        m_logFilePattern = getProperty(keys, "tre.log.file");
        if (m_logFilePattern != null) {
            log("logFilePattern=" + m_logFilePattern);
        }
        m_varyMa = keys.getProperty("tre.vary.ma");
        if (m_varyMa != null) {
            log("varyMa=" + m_varyMa);
        }
        m_varyBarSize = keys.getProperty("tre.vary.bar_size");
        if (m_varyBarSize != null) {
            log("varyBarSize=" + m_varyBarSize);
        }
        m_varyPhases = keys.getProperty("tre.vary.phases");
        if (m_varyPhases != null) {
            log("varyPhases=" + m_varyPhases);
        }
        m_varyLen1 = keys.getProperty("tre.vary.len1");
        if (m_varyLen1 != null) {
            log("varyLen1=" + m_varyLen1);
        }
        m_varyLen2 = keys.getProperty("tre.vary.len2");
        if (m_varyLen2 != null) {
            log("varyLen2=" + m_varyLen2);
        }
        m_varyOscLock = keys.getProperty("tre.vary.osc_lock");
        if (m_varyOscLock != null) {
            log("varyOscLock=" + m_varyOscLock);
        }
        m_varyOscPeak = keys.getProperty("tre.vary.osc_peak");
        if (m_varyOscPeak != null) {
            log("varyOscPeak=" + m_varyOscPeak);
        }
        m_varyCoppPeak = keys.getProperty("tre.vary.copp_peak");
        if (m_varyCoppPeak != null) {
            log("varyCoppPeak=" + m_varyCoppPeak);
        }
        m_varyAndPeak = keys.getProperty("tre.vary.and_peak");
        if (m_varyAndPeak != null) {
            log("varyAndPeak=" + m_varyAndPeak);
        }
        m_varyCciPeak = keys.getProperty("tre.vary.cci_peak");
        if (m_varyCciPeak != null) {
            log("varyCciPeak=" + m_varyCciPeak);
        }
        m_varyCciCorr = keys.getProperty("tre.vary.cci_corr");
        if (m_varyCciCorr != null) {
            log("varyCciCorr=" + m_varyCciCorr);
        }
        m_varyWma = keys.getProperty("tre.vary.wma");
        if (m_varyWma != null) {
            log("varyWma=" + m_varyWma);
        }
        m_varyLroc = keys.getProperty("tre.vary.lroc");
        if (m_varyLroc != null) {
            log("varyLroc=" + m_varyLroc);
        }
        m_varySroc = keys.getProperty("tre.vary.sroc");
        if (m_varySroc != null) {
            log("varySroc=" + m_varySroc);
        }
        m_varySma = keys.getProperty("tre.vary.sma");
        if (m_varySma != null) {
            log("varySma=" + m_varySma);
        }
        m_varyCovK = keys.getProperty("tre.vary.cov_k");
        if (m_varyCovK != null) {
            log("varyCovK=" + m_varyCovK);
        }
        m_varyCovRat = keys.getProperty("tre.vary.cov_rat");
        if (m_varyCovRat != null) {
            log("varyCovRat=" + m_varyCovRat);
        }
        m_varyCovVel = keys.getProperty("tre.vary.cov_vel");
        if (m_varyCovVel != null) {
            log("varyCovVel=" + m_varyCovVel);
        }

        BaseExecutor.DO_TRADE = false;
        log("DO_TRADE set to false");
    }

    @Override public void run() {
        try {
            log("============================= started on : " + new Date());
            String[] split = m_logFilePattern.split("\\|");
            String dirPath = split[0];
            String filePattern = split[1];
            Pattern pattern = Pattern.compile(filePattern);

            File dir = new File(dirPath);
            if (dir.isDirectory()) {
                List<TradesTopsData> datas = parseFiles(pattern, dir);
                long startTime = System.currentTimeMillis();
                processAll(datas);
                long endTime = System.currentTimeMillis();
                log("takes " + Utils.millisToDHMSStr(endTime - startTime));
            } else {
                log("is not a directory: " + dirPath);
            }
            if (m_exchData.m_tres.m_collectPoints) {
                Tres.showUI();
            }
        } catch (Exception e) {
            err("Error in LogProcessor: " + e, e);
        }
    }

    private void processAll(List<TradesTopsData> datas) throws Exception {
        Tres tres = m_exchData.m_tres;
        tres.m_collectPoints = false;
        if (m_varyMa != null) {
            varyMa(datas, tres, m_varyMa);
            return;
        }
        if (m_varyBarSize != null) {
            varyBarSize(datas, tres, m_varyBarSize);
            return;
        }
        if (m_varyPhases != null) {
            varyPhases(datas, tres, m_varyPhases);
            return;
        }
        if (m_varyLen1 != null) {
            varyLen1(datas, tres, m_varyLen1);
            return;
        }
        if (m_varyLen2 != null) {
            varyLen2(datas, tres, m_varyLen2);
            return;
        }
        if (m_varyOscLock != null) {
            varyOscLock(datas, tres, m_varyOscLock);
            return;
        }
        if (m_varyOscPeak != null) {
            varyOscPeakTolerance(datas, tres, m_varyOscPeak);
            return;
        }
        if (m_varyCoppPeak != null) {
            varyCoppockPeakTolerance(datas, tres, m_varyCoppPeak);
            return;
        }
        if (m_varyAndPeak != null) {
            varyAndPeakTolerance(datas, tres, m_varyAndPeak);
            return;
        }
        if (m_varyCciPeak != null) {
            varyCciPeakTolerance(datas, tres, m_varyCciPeak);
            return;
        }
        if (m_varyCciCorr != null) {
            varyCciCorrection(datas, tres, m_varyCciCorr);
            return;
        }

        if (m_varyWma != null) {
            varyWma(datas, tres, m_varyWma);
            return;
        }
        if (m_varyLroc != null) {
            varyLroc(datas, tres, m_varyLroc);
            return;
        }
        if (m_varySroc != null) {
            varySroc(datas, tres, m_varySroc);
            return;
        }
        if (m_varySma != null) {
            varySma(datas, tres, m_varySma);
            return;
        }
        if (m_varyCovK != null) {
            varyCovK(datas, tres, m_varyCovK);
            return;
        }
        if (m_varyCovRat != null) {
            varyCovRat(datas, tres, m_varyCovRat);
            return;
        }
        if (m_varyCovVel != null) {
            varyCovVel(datas, tres, m_varyCovVel);
            return;
        }

        tres.m_collectPoints = true;
        Map<String, Double> averageProjected = processAllTicks(datas);
        log("averageProjected: " + averageProjected);
    }

    private void varyBarSize(List<TradesTopsData> datas, Tres tres, String varyBarSize) throws Exception {
        log("varyBarSize: " + varyBarSize);

        String[] split = varyBarSize.split(";"); // 2000ms;10000ms;500ms
        long min = Utils.parseDHMSMtoMillis(split[0]);
        long max = Utils.parseDHMSMtoMillis(split[1]);
        long step = Utils.parseDHMSMtoMillis(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (long i = min; i <= max; i += step) {
            tres.m_barSizeMillis = i;
            iterate(datas, i, "%d", "barSizeMillis", maxMap);
        }
        logMax(maxMap, "barSizeMillis");
    }

    private void varyPhases(List<TradesTopsData> datas, Tres tres, String varyPhases) throws Exception {
        log("varyPhases: " + varyPhases);

        String[] split = varyPhases.split(";"); // 10;30;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (int i = min; i <= max; i += step) {
            tres.m_phases = i;
            iterate(datas, i, "%d", "phases", maxMap);
        }
        logMax(maxMap, "phases");
    }

    private void logMax(Map<String, Map.Entry<Number, Double>> maxMap, String key) {
        for (Map.Entry<String, Map.Entry<Number, Double>> entry : maxMap.entrySet()) {
            String name = entry.getKey();
            Map.Entry<Number, Double> maxEntry = entry.getValue();
            Number num = maxEntry.getKey();
            Double value = maxEntry.getValue();
            log(name + "[" + key + "=" + num + "]=" + value);
        }
    }

    private void iterate(List<TradesTopsData> datas, Number num, String format,
                         String key, Map<String, Map.Entry<Number, Double>> maxMap) throws Exception {
        long start = System.currentTimeMillis();
        Map<String, Double> averageProjected = processAllTicks(datas);
        long end = System.currentTimeMillis();
        long takes = end - start;
        log(key, num, format, averageProjected, takes);
        updateMaxMap(maxMap, averageProjected, num);
    }

    private void updateMaxMap(Map<String, Map.Entry<Number, Double>> maxMap, Map<String, Double> averageProjected,
                              Number num) {
        for (Map.Entry<String, Double> entry : averageProjected.entrySet()) {
            String name = entry.getKey();
            Double newValue = entry.getValue();
            Map.Entry<Number, Double> maxEntry = maxMap.get(name);
            if(maxEntry == null) {
                maxMap.put(name, new AbstractMap.SimpleEntry<Number, Double>(num, newValue));
            } else {
                Double value = maxEntry.getValue();
                if (newValue > value) {
                    maxMap.put(name, new AbstractMap.SimpleEntry<Number, Double>(num, newValue));
                }
            }
        }
    }

    private void log(String key, Number num, String format, Map<String, Double> averageProjected, long takes) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> entry : averageProjected.entrySet()) {
            String name = entry.getKey();
            Double value = entry.getValue();
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(name).append("=").append(String.format("%.7f", value));
        }
        String numFormatted = String.format(format, num);
        log("averageProjected[" + key + "=" + numFormatted + "]:\t" + sb + "\t in " + Utils.millisToDHMSStr(takes));
    }

    private void varyMa(List<TradesTopsData> datas, Tres tres, String varyMa) throws Exception {
        log("varyMa: " + varyMa);

        String[] split = varyMa.split(";"); // 3;10;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (int i = min; i <= max; i += step) {
            tres.m_ma = i;
            iterate(datas, i, "%d", "ma", maxMap);
        }
        logMax(maxMap, "ma");
    }

    private void varyLen1(List<TradesTopsData> datas, Tres tres, String varyLen1) throws Exception {
        log("varyLen1: " + varyLen1);

        String[] split = varyLen1.split(";"); // 10;30;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (int i = min; i <= max; i += step) {
            tres.m_len1 = i;
            iterate(datas, i, "%d", "len1", maxMap);
        }
        logMax(maxMap, "len1");
    }

    private void varyLen2(List<TradesTopsData> datas, Tres tres, String varyLen2) throws Exception {
        log("varyLen2: " + varyLen2);

        String[] split = varyLen2.split(";"); // 10;30;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (int i = min; i <= max; i += step) {
            tres.m_len2 = i;
            iterate(datas, i, "%d", "len2", maxMap);
        }
        logMax(maxMap, "len2");
    }

    private void varyOscLock(List<TradesTopsData> datas, Tres tres, String varyOscLock) throws Exception {
        log("varyOscLock: " + varyOscLock);

        String[] split = varyOscLock.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            TresOscCalculator.LOCK_OSC_LEVEL = i;
            iterate(datas, i, "%.5f", "oscLock", maxMap);
        }
        logMax(maxMap, "oscLock");
    }

    private void varyCoppockPeakTolerance(List<TradesTopsData> datas, Tres tres, String varyCoppPeak) throws Exception {
        log("varyCoppPeak: " + varyCoppPeak);
        String[] split = varyCoppPeak.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            CoppockIndicator.PEAK_TOLERANCE = i;
            iterate(datas, i, "%.6f", "CoppPeak", maxMap);
        }
        logMax(maxMap, "CoppPeak");
    }

    private void varyOscPeakTolerance(List<TradesTopsData> datas, Tres tres, String varyOscPeak) throws Exception {
        log("varyOscPeak: " + varyOscPeak);
        String[] split = varyOscPeak.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            OscIndicator.PEAK_TOLERANCE = i;
            iterate(datas, i, "%.6f", "OscPeak", maxMap);
        }
        logMax(maxMap, "OskPeak");
    }


    private void varyAndPeakTolerance(List<TradesTopsData> datas, Tres tres, String varyAndPeak) throws Exception {
        log("varyAndPeak: " + varyAndPeak);
        String[] split = varyAndPeak.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            CncAlgo.AndIndicator.PEAK_TOLERANCE = i;
            iterate(datas, i, "%.5f", "AndPeak", maxMap);
        }
        logMax(maxMap, "AndPeak");
    }

    private void varyCciPeakTolerance(List<TradesTopsData> datas, Tres tres, String varyCciPeak) throws Exception {
        log("varyCciPeak: " + varyCciPeak);
        String[] split = varyCciPeak.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            CciIndicator.PEAK_TOLERANCE = i;
            iterate(datas, i, "%.2f", "CciPeak", maxMap);
        }
        logMax(maxMap, "CciPeak");
    }

    private void varyCciCorrection(List<TradesTopsData> datas, Tres tres, String varyCciCorr) throws Exception {
        log("varyCciCorrection: " + varyCciCorr);
        String[] split = varyCciCorr.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            CncAlgo.CCI_CORRECTION_RATIO = i;
            iterate(datas, i, "%.0f", "CciCorr", maxMap);
        }
        logMax(maxMap, "CciCorr");
    }

    private void varyCovK(List<TradesTopsData> datas, Tres tres, String varyCovK) throws Exception {
        log("varyCovK: " + varyCovK);
        String[] split = varyCovK.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            CoppockVelocityAlgo.DIRECTION_CUT_LEVEL = i;
            iterate(datas, i, "%.3f", "CovK", maxMap);
        }
        logMax(maxMap, "CovK");
    }

    private void varyCovRat(List<TradesTopsData> datas, Tres tres, String varyCovRat) throws Exception {
        log("varyCovRat: " + varyCovRat);
        String[] split = varyCovRat.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            CoppockVelocityAlgo.FRAME_RATIO = i;
            iterate(datas, i, "%.3f", "CovRat", maxMap);
        }
        logMax(maxMap, "CovRat");
    }

    private void varyCovVel(List<TradesTopsData> datas, Tres tres, String varyCovVel) throws Exception {
        log("varyCovVel: " + varyCovVel);
        String[] split = varyCovVel.split(";"); // 0.00000003
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            CoppockVelocityAlgo.PEAK_TOLERANCE = i;
            iterate(datas, i, "%.9f", "CovVel", maxMap);
        }
        logMax(maxMap, "CovVel");
    }

    private void varyWma(List<TradesTopsData> datas, Tres tres, String varyWma) throws Exception {
        log("varyWma: " + varyWma);

        String[] split = varyWma.split(";"); // 10;30;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (int i = min; i <= max; i += step) {
            CoppockIndicator.PhasedCoppockIndicator.WMA_LENGTH = i;
            iterate(datas, i, "%d", "wma", maxMap);
        }
        logMax(maxMap, "wma");
    }

    private void varyLroc(List<TradesTopsData> datas, Tres tres, String varyLroc) throws Exception {
        log("varyLroc: " + varyLroc);

        String[] split = varyLroc.split(";"); // 10;30;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (int i = min; i <= max; i += step) {
            CoppockIndicator.PhasedCoppockIndicator.LONG_ROC_LENGTH = i;
            iterate(datas, i, "%d", "lroc", maxMap);
        }
        logMax(maxMap, "lroc");
    }

    private void varySroc(List<TradesTopsData> datas, Tres tres, String varySroc) throws Exception {
        log("varySroc: " + varySroc);

        String[] split = varySroc.split(";"); // 10;30;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (int i = min; i <= max; i += step) {
            CoppockIndicator.PhasedCoppockIndicator.SHORT_ROС_LENGTH = i;
            iterate(datas, i, "%d", "sroc", maxMap);
        }
        logMax(maxMap, "sroc");
    }

    private void varySma(List<TradesTopsData> datas, Tres tres, String varySma) throws Exception {
        log("varySma: " + varySma);

        String[] split = varySma.split(";"); // 10;30;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (int i = min; i <= max; i += step) {
            CciIndicator.PhasedCciIndicator.SMA_LENGTH = i;
            iterate(datas, i, "%d", "sma", maxMap);
        }
        logMax(maxMap, "sma");
    }

    private Map<String, Double> processAllTicks(List<TradesTopsData> datas) throws Exception {
        final AtomicInteger semafore = new AtomicInteger();
        ExecutorService executorService = Executors.newFixedThreadPool(PROCESS_THREADS_NUM);

        final Map<String,Utils.DoubleDoubleAverageCalculator> calcMap = new HashMap<String, Utils.DoubleDoubleAverageCalculator>();
        for (final TradesTopsData data : datas) {
            synchronized (semafore) {
                semafore.incrementAndGet();
            }
            executorService.submit(new Runnable() {
                @Override public void run() {
                    try {
                        Map<String, Double> projectedMap = processTicks(data);
                        synchronized (semafore) {
                            for (Map.Entry<String, Double> e : projectedMap.entrySet()) {
                                String name = e.getKey();
                                Double projected = e.getValue();
                                Utils.DoubleDoubleAverageCalculator calc = calcMap.get(name);
                                if (calc == null) {
                                    calc = new Utils.DoubleDoubleAverageCalculator();
                                    calcMap.put(name, calc);
                                }
                                calc.addValue(projected);
                            }
                            int value = semafore.decrementAndGet();
                            if (value == 0) {
                                semafore.notify();
                            }
                        }
                    } catch (Exception e) {
                        err("got error: " + e, e);
                    }
                }
            });
        }
        synchronized (semafore) {
            int value = semafore.get();
            if (value > 0) {
                semafore.wait();
            } else {
                log(" nothing to wait");
            }
        }
        Map<String, Double> ret = new HashMap<String, Double>();
        for (Map.Entry<String, Utils.DoubleDoubleAverageCalculator> e : calcMap.entrySet()) {
            String name = e.getKey();
            Utils.DoubleDoubleAverageCalculator calc = e.getValue();
            double averageProjected = calc.getAverage();
            ret.put(name, averageProjected);
        }
        executorService.shutdown();
        return ret;
    }

    private Map<String, Double> processTicks(TradesTopsData data) {
        Map<String, Double> ret = new HashMap<String, Double>();

        List<TradeDataLight> ticks = data.m_trades;

        // reset before iteration
        TresExchData exchData = (cloneCounter.getAndDecrement() == 0) ? m_exchData : m_exchData.cloneClean();
        for (TradeDataLight tick : ticks) {
            exchData.processTrade(tick);
        }

        long runningTimeMillis = exchData.m_lastTickMillis - exchData.m_startTickMillis;
        double runningTimeDays = ((double) runningTimeMillis) / Utils.ONE_DAY_IN_MILLIS;
        double exponent = 1 / runningTimeDays;
//        log("   running " + Utils.millisToDHMSStr(runningTimeMillis) + "; runningTimeDays="+runningTimeDays);

//        Utils.DoubleDoubleAverageCalculator calc = new Utils.DoubleDoubleAverageCalculator();
//        for (PhaseData phaseData : exchData.m_phaseDatas) {
//            TresMaCalculator maCalculator = phaseData.m_maCalculator;
//            double total = maCalculator.calcToTal();
//            calc.addValue(total);
//        }
//        double averageTotal = calc.getAverage();
//        double projected = Math.pow(averageTotal, exponent);
//        ret.put("osc", projected);

        for(TresAlgoWatcher algo : exchData.m_playAlgos) {
            String name = algo.m_algo.m_name;
            double ratio = algo.m_totalPriceRatio;
            double algoProjected = Math.pow(ratio, exponent);
            ret.put(name, algoProjected);
        }
        return ret;
    }

    private List<TradesTopsData> parseFiles(Pattern pattern, File dir) throws Exception {
        final AtomicInteger semafore = new AtomicInteger();
        ExecutorService executorService = Executors.newFixedThreadPool(PARSE_THREADS_NUM);

        long startTime = System.currentTimeMillis();
        final List<TradesTopsData> datas = new ArrayList<TradesTopsData>();
        File[] files = dir.listFiles();
        log(files.length + " file(s) is directory " + dir);
        int toParse = 0;
        int skipped = 0;
        for (final File file : files) {
            final String name = file.getName();
            if (pattern.matcher(name).matches()) {
                synchronized (semafore) {
                    semafore.incrementAndGet();
                }
                executorService.submit(new Runnable() {
                    @Override public void run() {
                        log("next file to parse: " + name);
                        TradesTopsData fileData = null;
                        try {
                            fileData = parseFile(file);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        synchronized (semafore) {
                            if (fileData != null) {
                                datas.add(fileData);
                            }
                            int value = semafore.decrementAndGet();
                            if (value == 0) {
                                semafore.notify();
                            }
                        }
                    }
                });
                toParse++;
            } else {
                skipped++;
//                        log(" skip. not matches the pattern: " + filePattern);
            }
        }
        log("toParse " + toParse + " files. skipped " + skipped + " files.");

        synchronized (semafore) {
            int value = semafore.get();
            if (value > 0) {
//                log(" waiting parse end...");
                semafore.wait();
            } else {
                log(" nothing to wait");
            }
        }

        long endTime = System.currentTimeMillis();
        long timeTakes = endTime - startTime;
        String takesStr = Utils.millisToDHMSStr(timeTakes);
        log("parsing done in " + takesStr + "; totally parsed " + m_linesParsed + " lines");

        executorService.shutdown();
        return datas;
    }

    private TradesTopsData parseFile(File file) throws Exception {
        LineReader reader = new LineReader(file, READ_BUFFER_SIZE);
        try {
            return parseLines(reader, file);
        } finally {
            reader.close();
        }
    }

    private static class TradesTopsData {
        List<TradeDataLight> m_trades = new ArrayList<TradeDataLight>();
        List<TradeDataLight> m_tops = new ArrayList<TradeDataLight>();
    }

    private TradesTopsData parseLines(LineReader reader, File file) throws IOException {
        BufferedLineReader blr = new BufferedLineReader(reader);
        try {
            TradesTopsData ret = new TradesTopsData();
            List<TradeDataLight> trades = ret.m_trades;
            List<TradeDataLight> tops = ret.m_tops;
            long startTime = System.currentTimeMillis();
            long linesProcessed = 0;
            String line;
            while ((line = blr.getLine()) != null) {
                TradeDataLight tData = parseLineForTrade(line);
                if (tData != null) {
                    trades.add(tData);
                } else {
                    TradeDataLight toData = parseLineForTop(line);
                    if (toData != null) {
                        tops.add(toData);
                    }
                }
                blr.removeLine();
                linesProcessed++;
            }
            long endTime = System.currentTimeMillis();
            long timeTakes = endTime - startTime;
            log(" parsed " + file.getName() + ". " + linesProcessed + " lines in " + timeTakes + " ms (" + (linesProcessed * 1000 / timeTakes) + " lines/s)");
            m_linesParsed += linesProcessed;
            return ret;
        } finally {
            blr.close();
        }
    }

    private TradeDataLight parseFxTradeLine(String line) {
        // EUR/USD,20150601 00:00:00.859,1.0957,1.09579
        Matcher matcher = FX_TRADE_PATTERN.matcher(line);
        if (matcher.matches()) {
            String yearStr = matcher.group(1);
            String monthStr = matcher.group(2);
            String dayStr = matcher.group(3);
            String hourStr = matcher.group(4);
            String minStr = matcher.group(5);
            String secStr = matcher.group(6);
            String millisStr = matcher.group(7);
            String bidStr = matcher.group(8);
            String askStr = matcher.group(9);
//            log("GOT TRADE: yearStr=" + yearStr + "; monthStr=" + monthStr + "; dayStr=" + dayStr + "; hourStr=" + hourStr + "; minStr=" + minStr + "; secStr=" + secStr + "; millisStr=" + millisStr + "; bidStr=" + bidStr + "; askStr=" + askStr);

            int year = Integer.parseInt(yearStr);
            int month = Integer.parseInt(monthStr);
            int day = Integer.parseInt(dayStr);
            int hour = Integer.parseInt(hourStr);
            int min = Integer.parseInt(minStr);
            int sec = Integer.parseInt(secStr);
            int millis = Integer.parseInt(millisStr);
            double bid = Double.parseDouble(bidStr);
            double ask = Double.parseDouble(askStr);
//            log(" parsed: year=" + year + "; month=" + month + "; day=" + day + "; hour=" + hour + "; min=" + min + "; sec=" + sec + "; millis=" + millis + "; bid=" + bid + "; ask=" + ask);

            long timestamp;
            synchronized (GMT_CALENDAR) {
                GMT_CALENDAR.set(year, month, day, hour, min, sec);
                GMT_CALENDAR.set(Calendar.MILLISECOND, millis);
                timestamp = GMT_CALENDAR.getTimeInMillis();
            }
            double mid = (bid + ask) / 2;

            TradeDataLight tradeData = new TradeDataLight(timestamp, mid);
            return tradeData;
        } else {
            throw new RuntimeException("not matched FX_TRADE_PATTERN line: " + line);
//            log("not matched FX_TRADE_PATTERN line: " + line);
        }
    }

    private TradeDataLight parseLineForTrade(String line) {
        if (line.startsWith("onTrade[") && line.contains("]: TradeData{")) {
            return parseTradeLine(line);
        } else if (line.contains(": State.onTrade(")) {
            return parseOscTradeLine(line);
        } else if (line.startsWith("EUR/USD,")) { // fx
            return parseFxTradeLine(line);
        }
        return null;
    }

    private TradeDataLight parseLineForTop(String line) {
        if (line.startsWith("TresExecutor.gotTop()")) {
            return parseTopLine(line);
        }
        return null;
    }

    private TradeDataLight parseOscTradeLine(String line) {
        // 1426040622351: State.onTrade(tData=TradeData{amount=5.00000, price=1831.00000, time=1426040623000, tid=0, type=ASK}) on NONE *********************************************
        Matcher matcher = OSC_TRADE_PATTERN.matcher(line);
        if (matcher.matches()) {
            String priceStr = matcher.group(1);
            String millisStr = matcher.group(2);
//                log("GOT TRADE: millisStr=" + millisStr + "; priceStr=" + priceStr);
            TradeDataLight tradeData = parseTrade(millisStr, priceStr);
            return tradeData;
        } else {
//                throw new RuntimeException("not matched OSC_TRADE_PATTERN line: " + line);
            log("not matched OSC_TRADE_PATTERN line: " + line);
            return null;
        }
    }

    private TradeDataLight parseTradeLine(String line) {
        Matcher matcher = TRE_TRADE_PATTERN.matcher(line);
        if (matcher.matches()) {
            String priceStr = matcher.group(1);
            String timeStr = matcher.group(2);
//                log("GOT TRADE: timeStr=" + timeStr + "; priceStr=" + priceStr + "; amountStr=" + amountStr);
            TradeDataLight tradeData = parseTrade(timeStr, priceStr);
            return tradeData;
        } else {
            throw new RuntimeException("not matched TRE_TRADE_PATTERN line: " + line);
        }
    }

    private TradeDataLight parseTopLine(String line) {

        // TresExecutor.gotTop() buy=2150.71; sell=2151.0
//        private static final Pattern TRE_TOP_PATTERN = Pattern.compile("TresExecutor.gotTop\\(\\) buy=(\\d+\\.\\d+); sell=(\\d+\\.\\d+)");

        Matcher matcher = TRE_TOP_PATTERN.matcher(line);
        if (matcher.matches()) {
            String buyStr = matcher.group(1);
            String sellStr = matcher.group(2);
//                log("GOT TRADE: timeStr=" + timeStr + "; priceStr=" + priceStr + "; amountStr=" + amountStr);
            TradeDataLight tradeData = parseTop(buyStr, sellStr);
            return tradeData;
        } else {
            throw new RuntimeException("not matched TRE_TOP_PATTERN line: " + line);
        }
    }

    private TradeDataLight parseTop(String buyStr, String sellStr) {
        double buy = Double.parseDouble(buyStr);
        double sell = Double.parseDouble(sellStr);
        double mid = (buy + sell) / 2;
        return new TradeDataLight(s_lastTradeMillis++, mid);
    }

    private TradeDataLight parseTrade(String millisStr, String priceStr) {
        long millis = Long.parseLong(millisStr);
        s_lastTradeMillis = millis;
        double price = Double.parseDouble(priceStr);
        return new TradeDataLight(millis, price);
    }

    private String getProperty(Properties keys, String key) {
        String ret = keys.getProperty(key);
        if (ret == null) {
            throw new RuntimeException("no property found for key '" + key + "'");
        }
        return ret;
    }
}
