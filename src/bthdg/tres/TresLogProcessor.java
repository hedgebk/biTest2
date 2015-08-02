package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.util.BufferedLineReader;
import bthdg.util.LineReader;
import bthdg.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TresLogProcessor extends Thread {
    // onTrade[OKCOIN]: TradeData{amount=0.01000, price=1766.62000, time=1437739761000, tid=0, type=BID}
    private static final Pattern TRE_TRADE_PATTERN = Pattern.compile("onTrade\\[\\w+\\]\\: TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+), time=(\\d+).+");
    // 1426040622351: State.onTrade(tData=TradeData{amount=5.00000, price=1831.00000, time=1426040623000, tid=0, type=ASK}) on NONE *********************************************
    private static final Pattern OSC_TRADE_PATTERN = Pattern.compile("\\d+: State.onTrade\\(tData=TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+), time=(\\d+).+");
    private static final Pattern FX_TRADE_PATTERN = Pattern.compile("EUR/USD,(\\d\\d\\d\\d)(\\d\\d)(\\d\\d) (\\d\\d):(\\d\\d):(\\d\\d).(\\d\\d\\d),(\\d+\\.\\d+),(\\d+.\\d+)");

    private static final Calendar GMT_CALENDAR = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ENGLISH);

    private final String m_logFilePattern;
    private TresExchData m_exchData;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public TresLogProcessor(ArrayList<TresExchData> exchDatas, String logFilePattern) {
        m_exchData = exchDatas.get(0);
        m_logFilePattern = logFilePattern;
    }

    @Override public void run() {
        try {
            int indx = m_logFilePattern.lastIndexOf("|");
            String dirPath = m_logFilePattern.substring(0, indx);
            String filePattern = m_logFilePattern.substring(indx + 1);
            Pattern pattern = Pattern.compile(filePattern);

            File dir = new File(dirPath);
            if (dir.isDirectory()) {
                List<List<TradeData>> ticks = parseFiles(pattern, dir);
                processAllTicks(ticks);
            } else {
                log("is not a directory: " + dirPath);
            }
            Tres.showUI();
        } catch (Exception e) {
            err("Error in LogProcessor: " + e, e);
        }
    }

    private void processAllTicks(List<List<TradeData>> allTicks) {
        long startTime = System.currentTimeMillis();
        Utils.DoubleDoubleAverageCalculator calc = new Utils.DoubleDoubleAverageCalculator();
        for (List<TradeData> ticks : allTicks) {
            double projected = processTicks(ticks);
            calc.addValue(projected);
        }
        double averageProjected = calc.getAverage();
        log("averageProjected: " + averageProjected);

        long endTime = System.currentTimeMillis();
        long timeTakes = endTime - startTime;
        String takesStr = Utils.millisToDHMSStr(timeTakes);
        log("takes " + takesStr);
    }

    private double processTicks(List<TradeData> ticks) {
        Tres tres = m_exchData.m_tres;
        // reset before iteration
        tres.m_startTickMillis = Long.MAX_VALUE;
        tres.m_lastTickMillis = 0;

        for (TradeData tick : ticks) {
            m_exchData.onTrade(tick);
        }

        Utils.DoubleDoubleAverageCalculator calc = new Utils.DoubleDoubleAverageCalculator();
        for (PhaseData phaseData : m_exchData.m_phaseDatas) {
            TresMaCalculator maCalculator = phaseData.m_maCalculator;
            double total = maCalculator.calcToTal();
//            log(" total=" + total);
            calc.addValue(total);
        }
        double averageTotal = calc.getAverage();
//        log("  avg=" + averageTotal);

        long runningTimeMillis = tres.m_lastTickMillis - tres.m_startTickMillis;
        double runningTimeDays = ((double) runningTimeMillis) / Utils.ONE_DAY_IN_MILLIS;
//        String runningStr = Utils.millisToDHMSStr(runningTimeMillis);
//        log("   running " + runningStr + "; runningTimeDays="+runningTimeDays);

        double aDay = Math.pow(averageTotal, 1 / runningTimeDays);
//        log("    aDay="+aDay);

        // reset after iteration
        m_exchData = m_exchData.cloneClean();

        return aDay;
    }

    private List<List<TradeData>> parseFiles(Pattern pattern, File dir) throws Exception {
        long startTime = System.currentTimeMillis();
        List<List<TradeData>> ticks = new ArrayList<List<TradeData>>();
        File[] files = dir.listFiles();
        log(files.length + " file(s) is directory " + dir);
        int parsed = 0;
        int skipped = 0;
        for (File file : files) {
            String name = file.getName();
            if (pattern.matcher(name).matches()) {
                log("next file to parse: " + name);
                List<TradeData> fileTicks = parseFile(file);
                ticks.add(fileTicks);
                parsed++;

            } else {
                skipped++;
//                        log(" skip. not matches the pattern: " + filePattern);
            }
        }
        long endTime = System.currentTimeMillis();
        long timeTakes = endTime - startTime;
        String takesStr = Utils.millisToDHMSStr(timeTakes);

        log("parsed " + parsed + " files. skipped " + skipped + " files. takes " + takesStr);
        return ticks;
    }

    private List<TradeData> parseFile(File file) throws Exception {
        LineReader reader = new LineReader(file);
        try {
            return parseLines(reader);
        } finally {
            reader.close();
        }
    }

    private List<TradeData> parseLines(LineReader reader) throws IOException {
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
            log("processed " + linesProcessed + " lines in " + timeTakes + " ms (" + (linesProcessed * 1000 / timeTakes) + " lines/s)");
            return ret;
        } finally {
            blr.close();
        }
    }

    private TradeData parseTheLine(String line) {
        if (line.startsWith("onTrade[") && line.contains("]: TradeData{")) {
            return parseTradeLine(line);
        } else if (line.contains("State.onTrade(")) {
            return parseOscTradeLine(line);
        } else if (line.startsWith("EUR/USD,")) { // fx
            return parseFxTradeLine(line);
        }
        return null;
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

            GMT_CALENDAR.set(year, month, day, hour, min, sec);
            GMT_CALENDAR.set(Calendar.MILLISECOND, millis);
            long timestamp = GMT_CALENDAR.getTimeInMillis();
            double mid = (bid + ask) / 2;

            TradeData tradeData = new TradeData(0, mid, timestamp);
            return tradeData;
        } else {
            throw new RuntimeException("not matched FX_TRADE_PATTERN line: " + line);
//            log("not matched FX_TRADE_PATTERN line: " + line);
        }
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
}
