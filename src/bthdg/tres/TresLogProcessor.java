package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.util.BufferedLineReader;
import bthdg.util.LineReader;
import bthdg.util.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class TresLogProcessor extends Thread {
    // onTrade[OKCOIN]: TradeData{amount=0.01000, price=1766.62000, time=1437739761000, tid=0, type=BID}
    private static final Pattern TRE_TRADE_PATTERN = Pattern.compile("onTrade\\[\\w+\\]\\: TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+), time=(\\d+).+");
    // 1426040622351: State.onTrade(tData=TradeData{amount=5.00000, price=1831.00000, time=1426040623000, tid=0, type=ASK}) on NONE *********************************************
    private static final Pattern OSC_TRADE_PATTERN = Pattern.compile("\\d+: State.onTrade\\(tData=TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+), time=(\\d+).+");

    private final String m_logFile;
    private final TresExchData m_exchData;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Throwable t) { Log.err(s, t); }

    public TresLogProcessor(ArrayList<TresExchData> exchDatas, String logFile) {
        m_exchData = exchDatas.get(0);
        m_logFile = logFile;
    }

    @Override public void run() {
        try {
            LineReader reader = new LineReader(m_logFile);
            try {
                processLines(reader);
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            err("Error in LogProcessor: " + e, e);
        }
    }

    private void processLines(LineReader reader) {
        BufferedLineReader blr = new BufferedLineReader(reader);
        try {
            long startTime = System.currentTimeMillis();
            long linesProcessed = 0;
            String line;
            while ((line = blr.getLine()) != null) {
                processTheLine(line);
                blr.removeLine();
                linesProcessed++;
            }
            long endTime = System.currentTimeMillis();
            long timeTakes = endTime - startTime;
            log("processed " + linesProcessed + " lines in " + timeTakes + " ms (" + (linesProcessed * 1000 / timeTakes) + " lines/s)");

            Utils.DoubleAverageCalculator calc = new Utils.DoubleDoubleAverageCalculator();
            for (PhaseData phaseData : m_exchData.m_phaseDatas) {
                TresMaCalculator maCalculator = phaseData.m_maCalculator;
                double total = maCalculator.calcToTal();
                log(" total=" + total);
                calc.addValue(total);
            }
            log("  avg=" + calc.getAverage());

            Tres.showUI();
        } catch (Throwable t) {
            err("Error processing line: " + t, t);
        }
    }

    private void processTheLine(String line) {
        // onTrade[OKCOIN]: TradeData{amount=0.01000, price=1766.62000, time=1437739761000, tid=0, type=BID}
        if (line.startsWith("onTrade[")) {
            processTradeLine(line);
        } else if (line.contains("State.onTrade(")) {
            processOscTradeLine(line);
        }
    }

    private void processOscTradeLine(String line) {
        // 1426040622351: State.onTrade(tData=TradeData{amount=5.00000, price=1831.00000, time=1426040623000, tid=0, type=ASK}) on NONE *********************************************
        Matcher matcher = OSC_TRADE_PATTERN.matcher(line);
        if (matcher.matches()) {
            String priceStr = matcher.group(1);
            String millisStr = matcher.group(2);
//                log("GOT TRADE: millisStr=" + millisStr + "; priceStr=" + priceStr);
            processTrade(millisStr, priceStr);
        } else {
//                throw new RuntimeException("not matched OSC_TRADE_PATTERN line: " + line);
            log("not matched OSC_TRADE_PATTERN line: " + line);
        }
    }

    private void processTrade(String millisStr, String priceStr) {
        long millis = Long.parseLong(millisStr);
        double price = Double.parseDouble(priceStr);
        TradeData tradeData = new TradeData(0, price, millis);
        m_exchData.onTrade(tradeData);
    }

    private void processTradeLine(String line) {
        Matcher matcher = TRE_TRADE_PATTERN.matcher(line);
        if (matcher.matches()) {
            String priceStr = matcher.group(1);
            String timeStr = matcher.group(2);
//                log("GOT TRADE: timeStr=" + timeStr + "; priceStr=" + priceStr + "; amountStr=" + amountStr);
            processTrade(timeStr, priceStr);
        } else {
            throw new RuntimeException("not matched TRE_TRADE_PATTERN line: " + line);
        }
    }
}
