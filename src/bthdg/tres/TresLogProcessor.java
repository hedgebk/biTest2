package bthdg.tres;

import bthdg.Log;
import bthdg.exch.Config;
import bthdg.exch.TradeDataLight;
import bthdg.osc.BaseExecutor;
import bthdg.tres.alg.*;
import bthdg.tres.ind.CciIndicator;
import bthdg.tres.ind.CoppockIndicator;
import bthdg.tres.opt.OptimizeField;
import bthdg.tres.opt.OptimizeFieldConfig;
import bthdg.util.BufferedLineReader;
import bthdg.util.LineReader;
import bthdg.util.Utils;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.MultiDirectionalSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.apache.commons.math3.util.FastMath;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TresLogProcessor extends Thread {
    // onTrade[OKCOIN]: TradeData{amount=0.01000, price=1766.62000, time=1437739761000, tid=0, type=BID}
    private static final Pattern TRE_TRADE_PATTERN = Pattern.compile("onTrade\\[\\w+\\]\\: TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+), time=(\\d+).+");
    private static final Pattern TRE_TRADE_PATTERN2 = Pattern.compile("onTrade\\[\\w+\\]\\: TrData\\{sz=\\d+\\.\\d+, pr=(\\d+\\.\\d+), tm=(\\d+).+");
    // TresExecutor.gotTop() buy=2150.71; sell=2151.0
    private static final Pattern TRE_TOP_PATTERN = Pattern.compile("TresExecutor.gotTop\\(\\) buy=(\\d+\\.\\d+); sell=(\\d+\\.\\d+)");
    // 1426040622351: State.onTrade(tData=TradeData{amount=5.00000, price=1831.00000, time=1426040623000, tid=0, type=ASK}) on NONE *********************************************
    private static final Pattern OSC_TRADE_PATTERN = Pattern.compile("\\d+: State.onTrade\\(tData=TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+), time=(\\d+).+");
    private static final Pattern FX_TRADE_PATTERN = Pattern.compile("EUR/USD,(\\d\\d\\d\\d)(\\d\\d)(\\d\\d) (\\d\\d):(\\d\\d):(\\d\\d).(\\d\\d\\d),(\\d+\\.\\d+),(\\d+.\\d+)");

    private static final Calendar GMT_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ENGLISH);
    public static final int READ_BUFFER_SIZE = 1024 * 128;
    public static final int PARSE_THREADS_NUM = 2;
    public static final int PROCESS_THREADS_NUM = 8;
    private final Config m_config;
    private Map<OptimizeField, String> m_varyConfigs = new HashMap<OptimizeField, String>();

    private TresExchData m_exchData;
    private String m_logFilePattern;
    private String m_varyMa;
    private String m_varyBarSizeMul;
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
    private String m_varyTreVelSize;
    private String m_varyCno2Peak;
    private String m_varyCno2Frame;
    private AtomicInteger cloneCounter = new AtomicInteger(0);
    private long m_linesParsed;
    private long s_lastTradeMillis;
    private boolean m_iterated;
    private HashMap<OptimizeField, String> m_optCfg;
    private HashMap<OptimizeField, String> m_gridCfg;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public TresLogProcessor(Config config, ArrayList<TresExchData> exchDatas) {
        m_config = config;
        init(config);
        m_exchData = exchDatas.get(0);
    }

    private void init(Config config) {
        Tres.LOG_PARAMS = false;

        m_logFilePattern = getProperty(config, "tre.log.file");
        if (m_logFilePattern != null) {
            log("logFilePattern=" + m_logFilePattern);
        }
        m_varyMa = config.getProperty("tre.vary.ma");
        if (m_varyMa != null) {
            log("varyMa=" + m_varyMa);
        }
        m_varyBarSizeMul = config.getProperty("tre.vary.bar_size_mul");
        if (m_varyBarSizeMul != null) {
            log("varyBarSizeMul=" + m_varyBarSizeMul);
        }
        m_varyPhases = config.getProperty("tre.vary.phases");
        if (m_varyPhases != null) {
            log("varyPhases=" + m_varyPhases);
        }
        m_varyLen1 = config.getProperty("tre.vary.len1");
        if (m_varyLen1 != null) {
            log("varyLen1=" + m_varyLen1);
        }
        m_varyLen2 = config.getProperty("tre.vary.len2");
        if (m_varyLen2 != null) {
            log("varyLen2=" + m_varyLen2);
        }
        m_varyOscLock = config.getProperty("tre.vary.osc_lock");
        if (m_varyOscLock != null) {
            log("varyOscLock=" + m_varyOscLock);
        }
        m_varyOscPeak = config.getProperty("tre.vary.osc_peak");
        if (m_varyOscPeak != null) {
            log("varyOscPeak=" + m_varyOscPeak);
        }
        m_varyCoppPeak = config.getProperty("tre.vary.copp_peak");
        if (m_varyCoppPeak != null) {
            log("varyCoppPeak=" + m_varyCoppPeak);
        }
        m_varyAndPeak = config.getProperty("tre.vary.and_peak");
        if (m_varyAndPeak != null) {
            log("varyAndPeak=" + m_varyAndPeak);
        }
        m_varyCciPeak = config.getProperty("tre.vary.cci_peak");
        if (m_varyCciPeak != null) {
            log("varyCciPeak=" + m_varyCciPeak);
        }
        m_varyCciCorr = config.getProperty("tre.vary.cci_corr");
        if (m_varyCciCorr != null) {
            log("varyCciCorr=" + m_varyCciCorr);
        }
        m_varyWma = config.getProperty("tre.vary.wma");
        if (m_varyWma != null) {
            log("varyWma=" + m_varyWma);
        }
        m_varyLroc = config.getProperty("tre.vary.lroc");
        if (m_varyLroc != null) {
            log("varyLroc=" + m_varyLroc);
        }
        m_varySroc = config.getProperty("tre.vary.sroc");
        if (m_varySroc != null) {
            log("varySroc=" + m_varySroc);
        }
        m_varySma = config.getProperty("tre.vary.sma");
        if (m_varySma != null) {
            log("varySma=" + m_varySma);
        }
        m_varyCovK = config.getProperty("tre.vary.cov_k");
        if (m_varyCovK != null) {
            log("varyCovK=" + m_varyCovK);
        }
        m_varyCovRat = config.getProperty("tre.vary.cov_rat");
        if (m_varyCovRat != null) {
            log("varyCovRat=" + m_varyCovRat);
        }
        m_varyCovVel = config.getProperty("tre.vary.cov_vel");
        if (m_varyCovVel != null) {
            log("varyCovVel=" + m_varyCovVel);
        }
        m_varyTreVelSize = config.getProperty("tre.vary.tre_vel_size");
        if (m_varyTreVelSize != null) {
            log("varyTreVelSize=" + m_varyTreVelSize);
        }

        m_varyCno2Peak = config.getProperty("tre.vary.cno2_peak");
        if (m_varyCno2Peak != null) {
            log("varyCno2Peak=" + m_varyCno2Peak);
        }

        m_varyCno2Frame = config.getProperty("tre.vary.cno2_frame");
        if (m_varyCno2Frame != null) {
            log("varyCno2Frame=" + m_varyCno2Frame);
        }


        String rateStr = config.getProperty("tre.bar_size_to_phases_rate");
        if (rateStr != null) {
            double rate = Double.parseDouble(rateStr);
            OptimizeField.BAR_SIZE_TO_PHASES_RATE = rate;
            log("bar_size_to_phases_rate=" + rateStr);
        }

        getOptimizeFieldConfigs();

        String avgHalfBidAskDiff = config.getProperty("tre.avg_half_bid_ask_diff");
        if (avgHalfBidAskDiff != null) {
            log("avgHalfBidAskDiff=" + avgHalfBidAskDiff);
            BaseAlgoWatcher.AVG_HALF_BID_ASK_DIF = Double.parseDouble(avgHalfBidAskDiff);
        }

        getOptimizeConfig(config);
        getGridConfig(config);

        BaseExecutor.DO_TRADE = false;
        log("DO_TRADE set to false");
    }

    private void getOptimizeFieldConfigs() {
        for (OptimizeField optimizeField : OptimizeField.values()) {
            getConfig(optimizeField);
        }
    }

    private void getConfig(OptimizeField optimizeField) {
        String name = optimizeField.m_key;
        String config = m_config.getProperty("tre.vary." + name);
        if (config != null) {
            log("vary " + name + "=" + config);
            m_varyConfigs.put(optimizeField, config);
        }
    }

    private void getGridConfig(Config config) {
        for (OptimizeField field : OptimizeField.values()) {
            String key = field.m_key;
            String cfgStr = config.getProperty("tre.grid." + key);
            if (cfgStr != null) {
                log("opt." + key + "=" + cfgStr);
                if (m_gridCfg == null) {
                    m_gridCfg = new HashMap<OptimizeField, String>();
                }
                m_gridCfg.put(field, cfgStr);
            }
        }
        if (m_gridCfg != null) {
            log("GridConfig=" + m_gridCfg);
        }
    }

    private void getOptimizeConfig(Config config) {
        for (OptimizeField field : OptimizeField.values()) {
            String key = field.m_key;
            String cfgStr = config.getProperty("tre.opt." + key);
            if (cfgStr != null) {
                log("opt." + key + "=" + cfgStr);
                if (m_optCfg == null) {
                    m_optCfg = new HashMap<OptimizeField, String>();
                }
                m_optCfg.put(field, cfgStr);
            }
        }
        if (m_optCfg != null) {
            log("OptimizeConfig=" + m_optCfg);
        }
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
        }
        if (m_varyBarSizeMul != null) {
            varyBarSizeMul(datas, tres, m_varyBarSizeMul);
        }
        if (m_varyPhases != null) {
            varyPhases(datas, tres, m_varyPhases);
        }
        if (m_varyLen1 != null) {
            varyLen1(datas, tres, m_varyLen1);
        }
        if (m_varyLen2 != null) {
            varyLen2(datas, tres, m_varyLen2);
        }
        if (m_varyOscLock != null) {
            varyOscLock(datas, m_varyOscLock);
        }
        if (m_varyOscPeak != null) {
            varyOscPeakTolerance(datas, tres, m_varyOscPeak);
        }
        if (m_varyCoppPeak != null) {
            varyCoppockPeakTolerance(datas, m_varyCoppPeak);
        }
        if (m_varyAndPeak != null) {
            varyAndPeakTolerance(datas, m_varyAndPeak);
        }
        if (m_varyCciPeak != null) {
            varyCciPeakTolerance(datas, m_varyCciPeak);
        }
        if (m_varyCciCorr != null) {
            varyCciCorrection(datas, m_varyCciCorr);
        }

        if (m_varyWma != null) {
            varyWma(datas, m_varyWma);
        }
        if (m_varyLroc != null) {
            varyLroc(datas, m_varyLroc);
        }
        if (m_varySroc != null) {
            varySroc(datas, m_varySroc);
        }
        if (m_varySma != null) {
            varySma(datas, m_varySma);
        }
        if (m_varyCovK != null) {
            varyCovK(datas, m_varyCovK);
        }
        if (m_varyCovRat != null) {
            varyCovRat(datas, m_varyCovRat);
        }
        if (m_varyCovVel != null) {
            varyCovVel(datas, m_varyCovVel);
        }
        if (m_varyTreVelSize != null) {
            varyTreVelSize(datas, m_varyTreVelSize);
        }

        if (m_varyCno2Peak != null) {
            varyCno2PeakTolerance(datas, m_varyCno2Peak);
        }
        if (m_varyCno2Frame != null) {
            varyCno2FrameRate(datas, m_varyCno2Frame);
        }

        varyOptimizeFieldsDouble(datas, tres);

        checkOptimize(tres, datas);
        checkGrid(tres, datas);

        if (!m_iterated) {
            tres.m_collectPoints = true;
            Map<String, Double> averageProjected = processAllTicks(datas);
            log("averageProjected: " + averageProjected);
        }
    }

    private void varyOptimizeFieldsDouble(List<TradesTopsData> datas, Tres tres) throws Exception {
        Map<OptimizeField, Map.Entry<Number, Double>> optimized = new HashMap<OptimizeField, Map.Entry<Number, Double>>();
        double maxValue = 0;
        OptimizeField maxField = null;
        StringBuilder sb = new StringBuilder();
        for (OptimizeField optimizeField : OptimizeField.values()) {
            Map.Entry<Number, Double> maxEntry = varyDouble(datas, tres, optimizeField);
            if (maxEntry != null) {
                optimized.put(optimizeField, maxEntry);
                log("   maxEntry: key=" + maxEntry.getKey() + "; value=" + maxEntry.getValue());

                Number key = maxEntry.getKey(); // best
                double value = maxEntry.getValue();
                if (value > maxValue) {
                    log("   old maxValue=" + maxValue + "; new value=" + value + "; key=" + key + "; maxField=" + maxField);
                    maxValue = value;
                    maxField = optimizeField;
                }
            }
        }
        for (OptimizeField optimizeField : OptimizeField.values()) {
            Map.Entry<Number, Double> maxEntry = optimized.get(optimizeField);
            if (maxEntry != null) {
                Number num = maxEntry.getKey(); // best
                double value = num.doubleValue();
                boolean isMaxField = (optimizeField == maxField);
                double prevValue = optimizeField.get(tres);
                double newValue = isMaxField ? value : (value + prevValue) / 2;
                String format = optimizeField.getFormat();
                String newConfig = String.format(format, newValue);
                if (newConfig != null) {
                    sb.append("tre.");
                    sb.append(optimizeField.m_key);
                    sb.append("=");
                    sb.append(newConfig);
                    sb.append("\t\t# ");
                    sb.append(isMaxField ? "max" : "   ");
                    sb.append(" value=");
                    sb.append(Utils.format5(maxEntry.getValue()));
                    sb.append("\tprev:");
                    sb.append(String.format(format, prevValue));
                    sb.append("\n");
                }
            }
        }
        log("new config ::::::::::::::::::::::::::::::::::\n" + sb.toString());
    }

    private Map.Entry<Number, Double> varyDouble(List<TradesTopsData> datas, Tres tres, OptimizeField optimizeField) throws Exception {
        String config = m_varyConfigs.get(optimizeField);
        if (config != null) {
            String name = optimizeField.m_key;
            log("vary " + name + ": " + config + "; field: " + optimizeField);
            return varyDouble(datas, tres, optimizeField, config);
        }
        return null;
    }

    private void checkGrid(Tres tres, List<TradesTopsData> datas) {
        if (m_gridCfg != null) {
            List<OptimizeFieldConfig> fieldConfigs = new ArrayList<OptimizeFieldConfig>(m_gridCfg.size());
            for (Map.Entry<OptimizeField, String> entry : m_gridCfg.entrySet()) {
                OptimizeField field = entry.getKey();
                String configStr = entry.getValue();
                String[] split = configStr.split(";"); // 123;456.6;222.2
                double min = Double.parseDouble(split[0]);
                double max = Double.parseDouble(split[1]);
                double step = Double.parseDouble(split[2]);
                OptimizeFieldConfig fieldConfig = new OptimizeFieldConfig(field, min, max, step);
                fieldConfigs.add(fieldConfig);
            }
            doGrid(tres, datas, fieldConfigs);
        }
    }

    private void checkOptimize(Tres tres, List<TradesTopsData> datas) {
        if (m_optCfg != null) {
            List<OptimizeFieldConfig> fieldConfigs = new ArrayList<OptimizeFieldConfig>(m_optCfg.size());
            for (Map.Entry<OptimizeField, String> entry : m_optCfg.entrySet()) {
                OptimizeField field = entry.getKey();
                String configStr = entry.getValue();
                String[] split = configStr.split(";"); // 123;456.6;222.2
                double min = Double.parseDouble(split[0]);
                double max = Double.parseDouble(split[1]);
                double start = Double.parseDouble(split[2]);
                OptimizeFieldConfig fieldConfig = new OptimizeFieldConfig(field, min, max, start);
                fieldConfigs.add(fieldConfig);
            }
            doOptimize(tres, datas, fieldConfigs);
        }
    }

    private void doGrid(Tres tres, List<TradesTopsData> datas, List<OptimizeFieldConfig> fieldConfigs) {
        final String algoName = tres.m_exchDatas.get(0).m_playAlgos.get(0).m_algo.m_name;
        log("doGrid(algoName=" + algoName + ")...");
    }


    private void doOptimize(final Tres tres, final List<TradesTopsData> datas, final List<OptimizeFieldConfig> fieldConfigs) {
        final String algoName = tres.m_exchDatas.get(0).m_playAlgos.get(0).m_algo.m_name;
        log("doOptimize(algoName=" + algoName + ")...");
        double[] startPoint = buildStartPoint(fieldConfigs);
        SimpleBounds bounds = buildBounds(fieldConfigs);

        MultivariateFunction function = new MultivariateFunction() {
            @Override public double value(double[] point) {
                StringBuilder sb = new StringBuilder();
                sb.append("function(");
                int length = point.length;
                for (int i = 0; i < length; i++) {
                    double value = point[i];
                    OptimizeFieldConfig fieldConfig = fieldConfigs.get(i);
                    OptimizeField field = fieldConfig.m_field;
                    double val = value * fieldConfig.m_multiplier;
                    field.set(tres, val);
                    sb.append(field.m_key).append("=").append(Utils.format5(val)).append("(").append(Utils.format5(value)).append("); ");
                }
                sb.append(")=");

                double value;
                try {
                    Map<String, Double> averageProjected = processAllTicks(datas);
                    log("averageProjected: " + averageProjected);
                    value = averageProjected.get(algoName);
                } catch (Exception e) {
                    err("error in optimize.function: " + e, e);
                    value = 0;
                }
                sb.append(Utils.format5(value));
                log(sb.toString());
                return value;
            }
        };

        MultivariateOptimizer optimize;
        PointValuePair pair1 = null;
        PointValuePair pair2 = null;
        PointValuePair pair3 = null;

        // -------------------------------------------------------
        log("-----------------------------------------------------");
        optimize = new PowellOptimizer(1e-13, FastMath.ulp(1d));
        try {
            pair1 = optimize.optimize(
                    new ObjectiveFunction(function),
                    new MaxEval(250),
                    GoalType.MAXIMIZE,
                    new InitialGuess(startPoint)
            );

            log("point=" + Arrays.toString(pair1.getPoint()) + "; value=" + pair1.getValue());
            log("optimize: Evaluations=" + optimize.getEvaluations()
                    + "; Iterations=" + optimize.getIterations());

            setOptimalConfig(tres, fieldConfigs, pair1.getPoint());
        } catch (Exception e) {
            err("error: " + e, e);
        }

        // -------------------------------------------------------
        log("-----------------------------------------------------");
//        // Choices that exceed 2n+1 are not recommended.
        int numberOfInterpolationPoints = 5; //1 * startPoint.length + 1;
        optimize = new BOBYQAOptimizer(numberOfInterpolationPoints);
        try {
            pair2 = optimize.optimize(
                    new ObjectiveFunction(function),
                    new MaxEval(250),
                    GoalType.MAXIMIZE,
                    new InitialGuess(startPoint),
                    bounds
            );

            log("point=" + Arrays.toString(pair2.getPoint()) + "; value=" + pair2.getValue());
            log("optimize: Evaluations=" + optimize.getEvaluations()
                    + "; Iterations=" + optimize.getIterations());

            setOptimalConfig(tres, fieldConfigs, pair2.getPoint());
        } catch (Exception e) {
            err("error: " + e, e);
        }

        // -------------------------------------------------------
        log("-----------------------------------------------------");
        optimize = new SimplexOptimizer(1e-3, 1e-6);
        try {
            pair3 = optimize.optimize(
                    new ObjectiveFunction(function),
                    new MaxEval(150),
                    GoalType.MAXIMIZE,
                    new InitialGuess(startPoint),
                    new MultiDirectionalSimplex(startPoint.length));

            log("point=" + Arrays.toString(pair3.getPoint()) + "; value=" + pair3.getValue());
            log("optimize: Evaluations=" + optimize.getEvaluations()
                    + "; Iterations=" + optimize.getIterations());

            setOptimalConfig(tres, fieldConfigs, pair3.getPoint());
        } catch (Exception e) {
            err("error: " + e, e);
        }

        log("-----------------------------------------------------");
        log("-----------------------------------------------------");
        if (pair1 != null) {
            log("point=" + Arrays.toString(pair1.getPoint()) + "; value=" + pair1.getValue());
        }
        if (pair2 != null) {
            log("point=" + Arrays.toString(pair2.getPoint()) + "; value=" + pair2.getValue());
        }
        if (pair3 != null) {
            log("point=" + Arrays.toString(pair3.getPoint()) + "; value=" + pair3.getValue());
        }
    }

    private void setOptimalConfig(Tres tres, List<OptimizeFieldConfig> fieldConfigs, double[] point) {
        log("OptimalConfig:");
        int dimension = fieldConfigs.size();
        for (int i = 0; i < dimension; i++) {
            OptimizeFieldConfig fieldConfig = fieldConfigs.get(i);
            OptimizeField field = fieldConfig.m_field;
            double value = point[i];
            double valueMultiplied = value * fieldConfig.m_multiplier;
            log(" field[" + field.m_key + "]=" + valueMultiplied + "(value)");
            field.set(tres, valueMultiplied);
        }
    }

    private SimpleBounds buildBounds(List<OptimizeFieldConfig> fieldConfigs) {
        int dimension = fieldConfigs.size();
        double[] mins = new double[dimension];
        double[] maxs = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            OptimizeFieldConfig fieldConfig = fieldConfigs.get(i);
            OptimizeField field = fieldConfig.m_field;
            double min = fieldConfig.m_min;
            double max = fieldConfig.m_max;
            double minim = min / fieldConfig.m_multiplier;
            double maxim = max / fieldConfig.m_multiplier;
            mins[i] = minim;
            maxs[i] = maxim;
            log("field[" + field.m_key + "] min=" + minim + "(" + min + "); max=" + maxim + "(" + max + ")");
        }
        SimpleBounds bounds = new SimpleBounds(mins, maxs);
        return bounds;
    }

    private double[] buildStartPoint(List<OptimizeFieldConfig> fieldConfigs) {
        int dimension = fieldConfigs.size();
        double[] startPoint = new double[dimension];
        StringBuilder sb = new StringBuilder();
        sb.append("startPoint: ");
        for (int i = 0; i < dimension; i++) {
            OptimizeFieldConfig fieldConfig = fieldConfigs.get(i);
            OptimizeField field = fieldConfig.m_field;
            double start = fieldConfig.m_start;
            double strt = start / fieldConfig.m_multiplier;
            startPoint[i] = strt;
            sb.append(field.m_key).append("=").append(Utils.format5(start)).append("(").append(Utils.format5(strt)).append("); ");
        }
        log(sb.toString());
        return startPoint;
    }

    private void varyBarSizeMul(List<TradesTopsData> datas, Tres tres, String varyBarSizeMul) throws Exception {
        log("varyBarSizeMul: " + varyBarSizeMul);
        long old = tres.m_barSizeMillis;

        String[] split = varyBarSizeMul.split(";"); // 2000ms;10000ms;1.1
        long min = Utils.parseDHMSMtoMillis(split[0]);
        long max = Utils.parseDHMSMtoMillis(split[1]);
        double mul = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (long i = min; i <= max; i = (long) (i * mul)) {
            tres.m_barSizeMillis = i;
            iterate(datas, i, "%d", "varyBarSizeMul", maxMap);
        }
        logMax(maxMap, "varyBarSizeMul");
        tres.m_barSizeMillis = old;
    }

    private void varyPhases(List<TradesTopsData> datas, Tres tres, String varyPhases) throws Exception {
        log("varyPhases: " + varyPhases);
        int old = tres.m_phases;

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
        tres.m_phases = old;
    }

    private void logMax(Map<String, Map.Entry<Number, Double>> maxMap, String key) {
        logMax(maxMap, key, "%.6f");
    }

    private void logMax(Map<String, Map.Entry<Number, Double>> maxMap, String varyKey, String format) {
        for (Map.Entry<String, Map.Entry<Number, Double>> entry : maxMap.entrySet()) {
            String algoName = entry.getKey();
            Map.Entry<Number, Double> maxEntry = entry.getValue();
            Number num = maxEntry.getKey();
            Double value = maxEntry.getValue();
            log(algoName + "[" + varyKey + "=" + String.format(format, num.doubleValue()) + "]=" + value);
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
        m_iterated = true;
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
        int old = tres.m_ma;

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
        tres.m_ma = old;
    }

    private void varyLen1(List<TradesTopsData> datas, Tres tres, String varyLen1) throws Exception {
        log("varyLen1: " + varyLen1);
        varyInteger(datas, tres, OptimizeField.OSC_LEN1, varyLen1);
    }

    private void varyLen2(List<TradesTopsData> datas, Tres tres, String varyLen2) throws Exception {
        log("varyLen2: " + varyLen2);
        varyInteger(datas, tres, OptimizeField.OSC_LEN2, varyLen2);
    }

    private void varyInteger(List<TradesTopsData> datas, Tres tres, OptimizeField optimizeField, String config) throws Exception {
        int old = (int) optimizeField.get(tres);

        String[] split = config.split(";"); // 10;30;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (int i = min; i <= max; i += step) {
            optimizeField.set(tres, i);
            iterate(datas, i, "%d", optimizeField.m_key, maxMap);
        }
        logMax(maxMap, optimizeField.m_key);
        optimizeField.set(tres, old);
    }

    private void varyOscLock(List<TradesTopsData> datas, String varyOscLock) throws Exception {
        log("varyOscLock: " + varyOscLock);
        double old = TresOscCalculator.LOCK_OSC_LEVEL;

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
        TresOscCalculator.LOCK_OSC_LEVEL = old;
    }

    private void varyCoppockPeakTolerance(List<TradesTopsData> datas, String varyCoppPeak) throws Exception {
        log("varyCoppPeak: " + varyCoppPeak);
        double old = CoppockIndicator.PEAK_TOLERANCE;
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
        CoppockIndicator.PEAK_TOLERANCE = old;
    }

    private void varyOscPeakTolerance(List<TradesTopsData> datas, Tres tres, String varyOscPeak) throws Exception {
        log("varyOscPeak: " + varyOscPeak);
        varyDouble(datas, tres, OptimizeField.OSC_PEAK, varyOscPeak);
    }

    private Map.Entry<Number, Double> varyDouble(List<TradesTopsData> datas, Tres tres, OptimizeField optimizeField, String config) throws Exception {
        double old = optimizeField.get(tres);

        String[] split = config.split(";"); // 10.1;30.2;1.3
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step;
        String stepStr = split[2];
        if (stepStr.startsWith("s")) { // steps count passes
            int stepsCount = Integer.parseInt(stepStr.substring(1));
            step = (max - min) / stepsCount;
            log(" step=" + Utils.format8(step));
        } else { // absolute step passed
            step = Double.parseDouble(stepStr);
        }
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        String format = optimizeField.getFormat();
// before
//iterate(datas, old, format, optimizeField.m_key, maxMap);
        for (double i = min; i <= max; i += step) {
            optimizeField.set(tres, i);
            iterate(datas, i, format, optimizeField.m_key, maxMap);
        }
        logMax(maxMap, optimizeField.m_key, format);
        optimizeField.set(tres, old);
// after
//iterate(datas, old, format, optimizeField.m_key, maxMap);

        String algoName = tres.m_exchDatas.get(0).m_runAlgoWatcher.m_algo.m_name;
        Map.Entry<Number, Double> maxEntry = maxMap.get(algoName);
        return maxEntry;
    }

    private void varyAndPeakTolerance(List<TradesTopsData> datas, String varyAndPeak) throws Exception {
        log("varyAndPeak: " + varyAndPeak);
        double old = CncAlgo.AndIndicator.PEAK_TOLERANCE;
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
        CncAlgo.AndIndicator.PEAK_TOLERANCE = old;
    }

    private void varyCciPeakTolerance(List<TradesTopsData> datas, String varyCciPeak) throws Exception {
        log("varyCciPeak: " + varyCciPeak);
        double old = CciIndicator.PEAK_TOLERANCE;
        String[] split = varyCciPeak.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            CciIndicator.PEAK_TOLERANCE = i;
            iterate(datas, i, "%.3f", "CciPeak", maxMap);
        }
        logMax(maxMap, "CciPeak");
        CciIndicator.PEAK_TOLERANCE = old;

    }

    private void varyCno2PeakTolerance(List<TradesTopsData> datas, String varyCno2Peak) throws Exception {
        log("varyCno2Peak: " + varyCno2Peak);
        double old = Cno2Algo.MID_PEAK_TOLERANCE;
        String[] split = varyCno2Peak.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            Cno2Algo.MID_PEAK_TOLERANCE = i;
            iterate(datas, i, "%.3f", "Cno2Peak", maxMap);
        }
        logMax(maxMap, "Cno2Peak");
        Cno2Algo.MID_PEAK_TOLERANCE = old;
    }

    private void varyCciCorrection(List<TradesTopsData> datas, String varyCciCorr) throws Exception {
        log("varyCciCorrection: " + varyCciCorr);
        double old = CncAlgo.CCI_CORRECTION_RATIO;
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
        CncAlgo.CCI_CORRECTION_RATIO = old;
    }

    private void varyCovK(List<TradesTopsData> datas, String varyCovK) throws Exception {
        log("varyCovK: " + varyCovK);
        double old = CoppockVelocityAlgo.DIRECTION_CUT_LEVEL;
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
        CoppockVelocityAlgo.DIRECTION_CUT_LEVEL = old;
    }

    private void varyCovRat(List<TradesTopsData> datas, String varyCovRat) throws Exception {
        log("varyCovRat: " + varyCovRat);
        double old = CoppockVelocityAlgo.FRAME_RATIO;
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
        CoppockVelocityAlgo.FRAME_RATIO = old;
    }

    private void varyCovVel(List<TradesTopsData> datas, String varyCovVel) throws Exception {
        log("varyCovVel: " + varyCovVel);
        double old = CoppockVelocityAlgo.PEAK_TOLERANCE;
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
        CoppockVelocityAlgo.PEAK_TOLERANCE = old;
    }

    private void varyTreVelSize(List<TradesTopsData> datas, String varyTreVelSize) throws Exception {
        log("varyTreVelSize: " + varyTreVelSize);
        double old = TreAlgo.TreAlgoBlended.VELOCITY_SIZE_RATE;
        String[] split = varyTreVelSize.split(";"); // 0.00000003
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            TreAlgo.TreAlgoBlended.VELOCITY_SIZE_RATE = i;
            iterate(datas, i, "%.7f", "TreVelSize", maxMap);
        }
        logMax(maxMap, "TreVelSize");
        TreAlgo.TreAlgoBlended.VELOCITY_SIZE_RATE = old;
    }

    private void varyCno2FrameRate(List<TradesTopsData> datas, String varyCno2Frame) throws Exception {
        log("varyCno2Frame: " + varyCno2Frame);
        double old = Cno2Algo.FRAME_RATIO;

        String[] split = varyCno2Frame.split(";"); // 10;30;1
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        Map<String, Map.Entry<Number, Double>> maxMap = new HashMap<String, Map.Entry<Number, Double>>();
        for (double i = min; i <= max; i += step) {
            Cno2Algo.FRAME_RATIO = i;
            iterate(datas, i, "%d", "Cno2Frame", maxMap);
        }
        logMax(maxMap, "Cno2Frame");
        Cno2Algo.FRAME_RATIO = old;
    }

    private void varyWma(List<TradesTopsData> datas, String varyWma) throws Exception {
        log("varyWma: " + varyWma);
        int old = CoppockIndicator.PhasedCoppockIndicator.WMA_LENGTH;

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
        CoppockIndicator.PhasedCoppockIndicator.WMA_LENGTH = old;
    }

    private void varyLroc(List<TradesTopsData> datas, String varyLroc) throws Exception {
        log("varyLroc: " + varyLroc);
        int old = CoppockIndicator.PhasedCoppockIndicator.LONG_ROC_LENGTH;

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
        CoppockIndicator.PhasedCoppockIndicator.LONG_ROC_LENGTH = old;
    }

    private void varySroc(List<TradesTopsData> datas, String varySroc) throws Exception {
        log("varySroc: " + varySroc);
        int old = CoppockIndicator.PhasedCoppockIndicator.SHORT_ROС_LENGTH;

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
        CoppockIndicator.PhasedCoppockIndicator.SHORT_ROС_LENGTH = old;
    }

    private void varySma(List<TradesTopsData> datas, String varySma) throws Exception {
        log("varySma: " + varySma);
        int old = CciIndicator.PhasedCciIndicator.SMA_LENGTH;

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
        CciIndicator.PhasedCciIndicator.SMA_LENGTH = old;
    }

    private Map<String, Double> processAllTicks(List<TradesTopsData> datas) throws Exception {
        final AtomicInteger semafore = new AtomicInteger();
        ExecutorService executorService = Executors.newFixedThreadPool(PROCESS_THREADS_NUM);
        final long[] totalRunningTimeMillis = new long[]{0};

        final Map<String,Double> totalRatioMap = new HashMap<String, Double>();
        for (final TradesTopsData data : datas) {
            synchronized (semafore) {
                semafore.incrementAndGet();
            }
            executorService.submit(new Runnable() {
                @Override public void run() {
                    try {
                        ProcessTicksResult processTicksResult = processTicks(data);
                        Map<String, Double> ratioMap = processTicksResult.m_ratioMap;
                        long runningTimeMillis = processTicksResult.m_runningTimeMillis;

                        //log(" finished " + data.m_fName + ": runningTimeMillis=" + runningTimeMillis + "(" + Utils.format5(runningTimeDays) + "days); ratioMap=" + ratioMap);

                        synchronized (semafore) {
                            for (Map.Entry<String, Double> e : ratioMap.entrySet()) {
                                String algoMame = e.getKey();
                                Double ratio = e.getValue();
                                Double totalRatio = totalRatioMap.get(algoMame);
                                if (totalRatio == null) {
                                    //log("  algo[" + algoMame + "]: ratio=" + ratio);
                                    totalRatioMap.put(algoMame, ratio);
                                } else {
                                    double newTotalRatio = totalRatio * ratio;
                                    //log("  algo[" + algoMame + "]: totalRatio=" + totalRatio + "; ratio=" + ratio + "; newTotalRatio=" + newTotalRatio);
                                    totalRatioMap.put(algoMame, newTotalRatio);
                                }
                            }
                            totalRunningTimeMillis[0] += runningTimeMillis;
                            //log(" totalRunningTimeMillis=" + totalRunningTimeMillis[0]);
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

        // all calculations are done
        long totalRunningTime = totalRunningTimeMillis[0];
        double runningTimeDays = ((double) totalRunningTime) / Utils.ONE_DAY_IN_MILLIS;
        double exponent = 1 / runningTimeDays;
        //log("all calculations are done: totalRunningTime=" + totalRunningTime + "(" + Utils.format5(runningTimeDays) + "days); exponent=" + exponent);

        Map<String, Double> ret = new HashMap<String, Double>();
        for (Map.Entry<String, Double> e : totalRatioMap.entrySet()) {
            String algoName = e.getKey();
            Double totalRatio = e.getValue();
            double projectedRatio = Math.pow(totalRatio, exponent);
            ret.put(algoName, projectedRatio);
            //log(" algo[" + algoName + "] totalRatio=" + totalRatio + "; projectedRatio=" + projectedRatio);
        }
        executorService.shutdown();
        return ret;
    }

    private ProcessTicksResult processTicks(TradesTopsData data) {
        Map<String, Double> ratioMap = new HashMap<String, Double>();

        List<TradeDataLight> ticks = data.m_trades;

        // reset before iteration
        TresExchData exchData = (cloneCounter.getAndDecrement() == 0) ? m_exchData : m_exchData.cloneClean();
        for (TradeDataLight tick : ticks) {
            exchData.processTrade(tick);
        }

        long runningTimeMillis = exchData.m_lastTickMillis - exchData.m_startTickMillis;

        for(BaseAlgoWatcher algo : exchData.m_playAlgos) {
            String name = algo.m_algo.m_name;
            double ratio = algo.totalPriceRatio();
            ratioMap.put(name, ratio);
        }
        return new ProcessTicksResult(runningTimeMillis, ratioMap);
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
        log("parsing done in " + takesStr + "; totally parsed " + String.format("%,d", m_linesParsed ) + " lines");

        executorService.shutdown();
        return datas;
    }

    private TradesTopsData parseFile(File file) throws Exception {
        File dir = file.getParentFile();
        File ticksDir = new File(dir, "c");
        if(!ticksDir.exists()) {
            ticksDir.mkdirs();
        }
        String name = file.getName();
        File ticksFile = new File(ticksDir, name + ".ticks");
        if (ticksFile.exists()) {
            try {
                FileInputStream fileIn = new FileInputStream(ticksFile);
                ObjectInputStream in = new ObjectInputStream(fileIn);
                try {
                    long startTime = System.currentTimeMillis();
                    TradesTopsData res = (TradesTopsData) in.readObject();
                    long endTime = System.currentTimeMillis();
                    long timeTakes = endTime - startTime;
log("ticks loaded " + res.m_trades.size() + " in " + timeTakes + "ms. from " + ticksFile.getCanonicalPath());
                    return res;
                } finally {
                    in.close();
                    fileIn.close();
                }
            } catch (Exception e) {
                err("error reading ticks file: " + e, e);
            }
        }
        LineReader reader = new LineReader(file, READ_BUFFER_SIZE);
        try {
            TradesTopsData res = parseLines(reader, file);
            try {
                FileOutputStream fileOut = new FileOutputStream(ticksFile);
                ObjectOutputStream out = new ObjectOutputStream(fileOut);
                try {
                    long startTime = System.currentTimeMillis();
                    out.writeObject(res);
                    long endTime = System.currentTimeMillis();
                    long timeTakes = endTime - startTime;
log("ticks saved " + res.m_trades.size() + " in " + timeTakes + "ms. to " + ticksFile.getCanonicalPath());
                } finally {
                    out.close();
                    fileOut.close();
                }
            } catch (Exception e) {
                err("error writing ticks file: " + e, e);
            }
            return res;
        } finally {
            reader.close();
        }
    }

    private TradesTopsData parseLines(LineReader reader, File file) throws IOException {
        BufferedLineReader blr = new BufferedLineReader(reader);
        try {
            TradesTopsData ret = new TradesTopsData(file.getName());
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
            long timeTakes = endTime - startTime + 1;
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
            float bid = Float.parseFloat(bidStr);
            float ask = Float.parseFloat(askStr);
//            log(" parsed: year=" + year + "; month=" + month + "; day=" + day + "; hour=" + hour + "; min=" + min + "; sec=" + sec + "; millis=" + millis + "; bid=" + bid + "; ask=" + ask);

            long timestamp;
            synchronized (GMT_CALENDAR) {
                GMT_CALENDAR.set(year, month, day, hour, min, sec);
                GMT_CALENDAR.set(Calendar.MILLISECOND, millis);
                timestamp = GMT_CALENDAR.getTimeInMillis();
            }
            float mid = (bid + ask) / 2;

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
        if (!matcher.matches()) {
            matcher = TRE_TRADE_PATTERN2.matcher(line);
        }
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
        float buy = Float.parseFloat(buyStr);
        float sell = Float.parseFloat(sellStr);
        float mid = (buy + sell) / 2;
        return new TradeDataLight(s_lastTradeMillis++, mid);
    }

    private TradeDataLight parseTrade(String millisStr, String priceStr) {
        long millis = Long.parseLong(millisStr);
        s_lastTradeMillis = millis;
        float price = Float.parseFloat(priceStr);
        return new TradeDataLight(millis, price);
    }

    private String getProperty(Config config, String key) {
        String ret = config.getProperty(key);
        if (ret == null) {
            throw new RuntimeException("no property found for key '" + key + "'");
        }
        return ret;
    }


    // --------------------------------------------------------------------------------------
    private static class ProcessTicksResult{
        private final long m_runningTimeMillis;
        private final Map<String, Double> m_ratioMap;

        public ProcessTicksResult(long runningTimeMillis, Map<String, Double> ratioMap) {
            m_runningTimeMillis = runningTimeMillis;
            m_ratioMap = ratioMap;
        }
    }


    // ----------------------------------------------------------------------------
    private static class TradesTopsData implements Serializable {
        private final String m_fName;
        final List<TradeDataLight> m_trades = new ArrayList<TradeDataLight>();
        final List<TradeDataLight> m_tops = new ArrayList<TradeDataLight>();

        public TradesTopsData(String name) {
            m_fName = name;
        }
    }
}
