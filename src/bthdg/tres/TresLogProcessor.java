package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.tres.alg.TresAlgoWatcher;
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
    private String m_varyLen1;
    private String m_varyLen2;
    private String m_varyOscLock;
    private int cloneCounter = 0;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public TresLogProcessor(Properties keys, ArrayList<TresExchData> exchDatas) {
        init(keys);
        m_exchData = exchDatas.get(0);
    }

    private void init(Properties keys) {
        m_logFilePattern = getProperty(keys, "tre.log.file");
        log("logFilePattern=" + m_logFilePattern);
        m_varyMa = keys.getProperty("tre.vary.ma");
        log("varyMa=" + m_varyMa);
        m_varyBarSize = keys.getProperty("tre.vary.bar_size");
        log("varyBarSize=" + m_varyBarSize);
        m_varyLen1 = keys.getProperty("tre.vary.len1");
        log("varyLen1=" + m_varyLen1);
        m_varyLen2 = keys.getProperty("tre.vary.len2");
        log("varyLen2=" + m_varyLen2);
        m_varyOscLock = keys.getProperty("tre.vary.osc_lock");
        log("varyOscLock=" + m_varyOscLock);
    }

    @Override public void run() {
        try {
            String[] split = m_logFilePattern.split("\\|");
            String dirPath = split[0];
            String filePattern = split[1];
            Pattern pattern = Pattern.compile(filePattern);

            File dir = new File(dirPath);
            if (dir.isDirectory()) {
                List<List<TradeData>> ticks = parseFiles(pattern, dir);
                processAll(ticks);
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

    private void processAll(List<List<TradeData>> allTicks) throws Exception {
        long startTime = System.currentTimeMillis();

        Tres tres = m_exchData.m_tres;
        tres.m_collectPoints = false;
        String varyMa = m_varyMa;
        if (varyMa != null) {
            varyMa(allTicks, tres, varyMa);
        } else {
            String varyBarSize = m_varyBarSize;
            if (varyBarSize != null) {
                varyBarSize(allTicks, tres, varyBarSize);
            } else {
                String varyLen1 = m_varyLen1;
                if (varyLen1 != null) {
                    varyLen1(allTicks, tres, varyLen1);
                } else {
                    String varyLen2 = m_varyLen2;
                    if (varyLen2 != null) {
                        varyLen2(allTicks, tres, varyLen2);
                    } else {
                        String varyOscLock = m_varyOscLock;
                        if (varyOscLock != null) {
                            varyOscLock(allTicks, tres, varyOscLock);
                        } else {
                            tres.m_collectPoints = true;
                            Map<String, Double> averageProjected = processAllTicks(allTicks);
                            log("averageProjected: " + averageProjected);
                        }
                    }
                }
            }
        }

        long endTime = System.currentTimeMillis();
        long timeTakes = endTime - startTime;
        String takesStr = Utils.millisToDHMSStr(timeTakes);
        log("takes " + takesStr);
    }

    private void varyBarSize(List<List<TradeData>> allTicks, Tres tres, String varyBarSize) throws Exception {
        log("varyBarSize: " + varyBarSize);

        String[] split = varyBarSize.split(";"); // 2000ms;10000ms;500ms
        long min = Utils.parseDHMSMtoMillis(split[0]);
        long max = Utils.parseDHMSMtoMillis(split[1]);
        long step = Utils.parseDHMSMtoMillis(split[2]);
        for (long i = min; i <= max; i += step) {
            tres.m_barSizeMillis = i;
            long start = System.currentTimeMillis();
            Map<String, Double> averageProjected = processAllTicks(allTicks);
            long end = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Double> entry : averageProjected.entrySet()) {
                String name = entry.getKey();
                Double value = entry.getValue();
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(name).append("=").append(String.format("%.5f", value));
            }
            log("averageProjected[barSizeMillis=" + i + "]:\t" + sb + "\t in " + Utils.millisToDHMSStr(end - start));
        }
    }

    private void varyMa(List<List<TradeData>> allTicks, Tres tres, String varyMa) throws Exception {
        log("varyMa: " + varyMa);

        String[] split = varyMa.split(";"); // 3;10;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        for (int i = min; i <= max; i += step) {
            tres.m_ma = i;
            Map<String, Double> averageProjected = processAllTicks(allTicks);
            log("averageProjected[ma=" + i + "]: " + averageProjected);
        }
    }

    private void varyLen1(List<List<TradeData>> allTicks, Tres tres, String varyLen1) throws Exception {
        log("varyLen1: " + varyLen1);

        String[] split = varyLen1.split(";"); // 10;30;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        for (int i = min; i <= max; i += step) {
            tres.m_len1 = i;
            Map<String, Double> averageProjected = processAllTicks(allTicks);
            log("averageProjected[varyLen1=" + i + "]: " + averageProjected);
        }
    }

    private void varyLen2(List<List<TradeData>> allTicks, Tres tres, String varyLen2) throws Exception {
        log("varyLen2: " + varyLen2);

        String[] split = varyLen2.split(";"); // 10;30;1
        int min = Integer.parseInt(split[0]);
        int max = Integer.parseInt(split[1]);
        int step = Integer.parseInt(split[2]);
        for (int i = min; i <= max; i += step) {
            tres.m_len2 = i;
            Map<String, Double> averageProjected = processAllTicks(allTicks);
            log("averageProjected[varyLen2=" + i + "]: " + averageProjected);
        }
    }

    private void varyOscLock(List<List<TradeData>> allTicks, Tres tres, String varyOscLock) throws Exception {
        log("varyOscLock: " + varyOscLock);

        String[] split = varyOscLock.split(";"); // 0.09;0.11;0.001
        double min = Double.parseDouble(split[0]);
        double max = Double.parseDouble(split[1]);
        double step = Double.parseDouble(split[2]);
        for (double i = min; i <= max; i += step) {
            PhaseData.LOCK_OSC_LEVEL = i;
            Map<String, Double> averageProjected = processAllTicks(allTicks);
            log("averageProjected[oscLock=" + i + "]: " + averageProjected);
        }
    }

    private Map<String, Double> processAllTicks(List<List<TradeData>> allTicks) throws Exception {
        final AtomicInteger semafore = new AtomicInteger();
        ExecutorService executorService = Executors.newFixedThreadPool(PROCESS_THREADS_NUM);

        final Map<String,Utils.DoubleDoubleAverageCalculator> calcMap = new HashMap<String, Utils.DoubleDoubleAverageCalculator>();
        for (final List<TradeData> ticks : allTicks) {
            synchronized (semafore) {
                semafore.incrementAndGet();
            }
            executorService.submit(new Runnable() {
                @Override public void run() {
                    try {
                        Map<String, Double> projectedMap = processTicks(ticks);
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
        return ret;
    }

    private Map<String, Double> processTicks(List<TradeData> ticks) {
        Map<String, Double> ret = new HashMap<String, Double>();

        // reset before iteration
        TresExchData exchData = (cloneCounter++ == 0) ? m_exchData : m_exchData.cloneClean();
        for (TradeData tick : ticks) {
            exchData.processTrade(tick);
        }

        long runningTimeMillis = exchData.m_lastTickMillis - exchData.m_startTickMillis;
        double runningTimeDays = ((double) runningTimeMillis) / Utils.ONE_DAY_IN_MILLIS;
//        log("   running " + Utils.millisToDHMSStr(runningTimeMillis) + "; runningTimeDays="+runningTimeDays);

        Utils.DoubleDoubleAverageCalculator calc = new Utils.DoubleDoubleAverageCalculator();
        for (PhaseData phaseData : exchData.m_phaseDatas) {
            TresMaCalculator maCalculator = phaseData.m_maCalculator;
            double total = maCalculator.calcToTal();
            calc.addValue(total);
        }
        double averageTotal = calc.getAverage();
        double exponent = 1 / runningTimeDays;
        double projected = Math.pow(averageTotal, exponent);
        ret.put("osc", projected);

        for(TresAlgoWatcher algo : exchData.m_algos) {
            String name = algo.m_algo.m_name;
            double ratio = algo.m_totalPriceRatio;
            double algoProjected = Math.pow(ratio, exponent);
            ret.put(name, algoProjected);
        }
        return ret;
    }

    private List<List<TradeData>> parseFiles(Pattern pattern, File dir) throws Exception {
        final AtomicInteger semafore = new AtomicInteger();
        ExecutorService executorService = Executors.newFixedThreadPool(PARSE_THREADS_NUM);

        long startTime = System.currentTimeMillis();
        final List<List<TradeData>> ticks = new ArrayList<List<TradeData>>();
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
                        List<TradeData> fileTicks = null;
                        try {
                            fileTicks = parseFile(file);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        synchronized (semafore) {
                            if (fileTicks != null) {
                                ticks.add(fileTicks);
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
        log("parsing done in " + takesStr);

        return ticks;
    }

    private List<TradeData> parseFile(File file) throws Exception {
        LineReader reader = new LineReader(file, READ_BUFFER_SIZE);
        try {
            return parseLines(reader, file);
        } finally {
            reader.close();
        }
    }

    private List<TradeData> parseLines(LineReader reader, File file) throws IOException {
        BufferedLineReader blr = new BufferedLineReader(reader);
        try {
            List<TradeData> ret = new ArrayList<TradeData>();
            long startTime = System.currentTimeMillis();
            long linesProcessed = 0;
            String line;
            while ((line = blr.getLine()) != null) {
                TradeData tData = parseTheLine(line);
                if(tData != null) {
                    ret.add(tData);
                }
                blr.removeLine();
                linesProcessed++;
            }
            long endTime = System.currentTimeMillis();
            long timeTakes = endTime - startTime;
            log(" parsed " + file.getName() + ". " + linesProcessed + " lines in " + timeTakes + " ms (" + (linesProcessed * 1000 / timeTakes) + " lines/s)");
            return ret;
        } finally {
            blr.close();
        }
    }

    private TradeData parseFxTradeLine(String line) {
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

            TradeData tradeData = new TradeData(0, mid, timestamp);
            return tradeData;
        } else {
            throw new RuntimeException("not matched FX_TRADE_PATTERN line: " + line);
//            log("not matched FX_TRADE_PATTERN line: " + line);
        }
    }

    private TradeData parseTheLine(String line) {
        if (line.startsWith("onTrade[") && line.contains("]: TradeData{")) {
            return parseTradeLine(line);
        } else if (line.contains(": State.onTrade(")) {
            return parseOscTradeLine(line);
        } else if (line.startsWith("EUR/USD,")) { // fx
            return parseFxTradeLine(line);
        }
        return null;
    }

    private TradeData parseOscTradeLine(String line) {
        // 1426040622351: State.onTrade(tData=TradeData{amount=5.00000, price=1831.00000, time=1426040623000, tid=0, type=ASK}) on NONE *********************************************
        Matcher matcher = OSC_TRADE_PATTERN.matcher(line);
        if (matcher.matches()) {
            String priceStr = matcher.group(1);
            String millisStr = matcher.group(2);
//                log("GOT TRADE: millisStr=" + millisStr + "; priceStr=" + priceStr);
            TradeData tradeData = parseTrade(millisStr, priceStr);
            return tradeData;
        } else {
//                throw new RuntimeException("not matched OSC_TRADE_PATTERN line: " + line);
            log("not matched OSC_TRADE_PATTERN line: " + line);
            return null;
        }
    }

    private TradeData parseTradeLine(String line) {
        Matcher matcher = TRE_TRADE_PATTERN.matcher(line);
        if (matcher.matches()) {
            String priceStr = matcher.group(1);
            String timeStr = matcher.group(2);
//                log("GOT TRADE: timeStr=" + timeStr + "; priceStr=" + priceStr + "; amountStr=" + amountStr);
            TradeData tradeData = parseTrade(timeStr, priceStr);
            return tradeData;
        } else {
            throw new RuntimeException("not matched TRE_TRADE_PATTERN line: " + line);
        }
    }

    private TradeData parseTrade(String millisStr, String priceStr) {
        long millis = Long.parseLong(millisStr);
        double price = Double.parseDouble(priceStr);
        return new TradeData(0, price, millis);
    }

    private String getProperty(Properties keys, String key) {
        String ret = keys.getProperty(key);
        if (ret == null) {
            throw new RuntimeException("no property found for key '" + key + "'");
        }
        return ret;
    }

}
