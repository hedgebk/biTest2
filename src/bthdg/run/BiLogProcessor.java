package bthdg.run;

import bthdg.BaseChartPaint;
import bthdg.PaintChart;
import bthdg.exch.Exchange;
import bthdg.exch.Pair;
import bthdg.util.Utils;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BiLogProcessor {
    private static final String LOG_FILE = "logs\\runner.log";

//    public static final String EXCH1 = "BTCN";      // 5
//    public static final String EXCH2 = "OKCOIN";

    public static final String EXCH1 = "OKCOIN";    // 9.7
    public static final String EXCH2 = "HUOBI";

//    public static final String EXCH1 = "BTCN";        // 5.2
//    public static final String EXCH2 = "HUOBI";

    public static final String PAIR = EXCH1 + "_" + EXCH2;
    public static final Exchange ROUND_EXCH = Exchange.BTCN;
    public static final boolean BALANCED_ACCT = true;
    private static final boolean LOG_TOP = false;
    private static final boolean LOG_PAIR = false;

    private static double s_level = 0.0003;
    private static long s_time;
    private static Map<String, PairData> s_pairs;
    private static Map<String, ExchData> s_tops;
    private static List<BiLogData> s_timePoints = new ArrayList<BiLogData>();
    private static Map<String, List<StartEndData>> s_startEndsMap = new HashMap<String, List<StartEndData>>(); // pair->StartEndData[]
    private static Map<String, BiLogData> s_starts = new HashMap<String, BiLogData>(); // exch->start_BiLogData
    private static BiLogData s_data;
    private static String s_lastPair;
    private static boolean s_isNotThreeLogged;

    private static final int XFACTOR = 1;
    private static final int WIDTH = 1620 * XFACTOR * 16;
    public static final int HEIGHT = 900 * XFACTOR;
    public static final Color LIGHT_RED = new Color(255, 0, 0, 70);
    public static final Color LIGHT_BLUE = new Color(0, 0, 255, 70);
    public static final Color LIGHT_ORANGE = new Color(200, 100, 0, 90);
    private static final boolean PAINT_BALANCE_CALCULATION = false;

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = Utils.logStartTimeMemory();
        try {
            importFromFile();
            paintChart();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void importFromFile() throws Exception {
        BufferedReader reader = new BufferedReader(new FileReader(LOG_FILE));
        try {
            State state = State.START;
            String line;
            while ((line = reader.readLine()) != null) {
                State newState = state.process(line);
                if (newState != null) {
                    state = newState;
                }
            }
        } finally {
            reader.close();
        }
    }

    private enum State {
        START {
            @Override public State process(String line) {
                return tryStart(line);
            }
        },
        ITERATION {
            @Override public State process(String line) {
                return tryIteration(line);
            }
        },
        TOP {
            @Override public State process(String line) {
                State state = tryTop(line);
                if (state == null) {
                    if (line.contains("diffDiff=")) {
                        state = tryPair(line);
                    } else {
                        state = tryIterationTook(line);
//                        if(!line.contains("SocketTimeoutException")
//                                && !line.contains("SocketException")
//                                && !line.contains("UnknownHostException")
//                                && !line.contains(".parseDeep()")
//                                && !line.contains("\tat ")
//                                && !line.contains("Unexpected token END OF FILE")
//                                && line.length() > 0) {
//                            System.out.println("waiting top but got: " + line);
//                        }
                    }
                }
                return state;
            }
        },
        PAIR {
            @Override public State process(String line) {
                State state = tryPair(line);
                if(state == null) {
//                    if( s_lastPair != null ) { // we have pair line matched
//                        BiLogData lastPairStart = s_starts.get(s_lastPair);
//                        state = (lastPairStart == null) ? tryOpen(line) : tryClose(line); // try OPEN/CLOSE
//                    }
//                    if(state == null) {
                        state = tryIterationTook(line);
//                        if(state == null) {
//                            if(!line.contains(", midMid=")
//                                    && !line.contains("iteration took")
//                                    && !line.contains("%%%%%%")) {
//                                System.out.println("nothing in context of PAIR for line: " + line);
//                            }
//                        } else {
//                            s_lastPair = null; // pair line and no OPEN/CLOSE processed
//                        }
//                    }
                }
                return state;
            }
        },
        ;

        private static State tryIterationTook(String line) {
            if(line.contains("iteration took")) {
                record();
                return ITERATION;
            }
            return null;
        }

        private static State tryStart(String line) {
            //  date=Tue Jul 15 18:45:23 EEST 2014; START_LEVEL=0.00032
            if (line.contains("START_LEVEL=")) {
                Matcher matcher = START_LEVEL.matcher(line);
                if(matcher.matches()) {
                    String level = matcher.group(1); // 0.00032
                    s_level = Double.parseDouble(level);
                    System.out.println("Got START_LEVEL=" + s_level);
                    return START;
                }
            }
            return tryIteration(line);
        }
        private static State tryIteration(String line) {
            if (line.contains("iteration ")) {
                // iteration 66 ---------------------- elapsed: 3min 18sec 38ms
                Matcher matcher = ELAPSED.matcher(line);
                if(matcher.matches()) {
                    String elapsed = matcher.group(1); // 3min 18sec 38ms
                    long millis = Utils.parseDHMSMtoMillis(elapsed);
                    System.out.println("elapsed=" + elapsed + ", millis=" + millis);
                    s_time = millis;
                    s_lastPair = null;
                    s_tops = new HashMap<String, ExchData>();  // prepare for next iteration
                    s_pairs = new HashMap<String, PairData>(); // prepare for next iteration
                    s_data = new BiLogData(millis, s_tops, s_pairs);
                    return TOP;
                }
            }
            return null; // do not change state
        }

        private static State tryTop(String line) {
            // BTCN  : Top{bid=3,778.6300, ask=3,782.2100, last=3,778.6000}
            Matcher matcher = TOP_.matcher(line);
            if(matcher.matches()) {
                String exch = matcher.group(1);
                String bidStr = matcher.group(2);
                String askStr = matcher.group(3);
                String lastStr = matcher.group(4);
                if (LOG_TOP) {
                    System.out.println("exch=" + exch + ", bidStr=" + bidStr + ", askStr=" + askStr + ", lastStr=" + lastStr);
                }

                s_tops.put(exch, new ExchData(exch, bidStr, askStr, lastStr));
                return TOP; // wait for next top
            }
            return null;
        }

        private static State tryPair(String line) {
            Matcher matcher = PAIR_.matcher(line);
            if (matcher.matches()) {
                String pair = matcher.group(1);
                String diffStr = matcher.group(2);
                String avgDiffStr = matcher.group(3);
                String diffDiffStr = matcher.group(4);
                String bidAskDiffStr = matcher.group(5);
                if (LOG_PAIR) {
                    System.out.println("pair=" + pair + ", diffStr=" + diffStr + ", avgDiffStr=" + avgDiffStr + ", diffDiffStr=" + diffDiffStr + ", bidAskDiffStr=" + bidAskDiffStr);
                }

                s_pairs.put(pair, new PairData(pair, diffStr, avgDiffStr, diffDiffStr, bidAskDiffStr));
//                s_lastPair = pair;

                return PAIR;
            }
//            if(line.contains("bidAskDiff=")) {
//                System.out.println("non matched PAIR line: " + line);
//            }
            return null;
        }

        private static State tryOpen(String line) {
            if( line.contains("GOT START") ) {
                System.out.println("GOT START: " + line);
                String[] split = line.split(" ");
                String exch = split[0];
                BiLogData old = s_starts.put(exch, s_data);
                if (old != null) {
                    System.out.println("GOT second START without matching END");
                }
                s_lastPair = null;
                return PAIR; // start of one pair found - look for another pair
            }
            return null;
        }

        private static State tryClose(String line) {
            if( line.contains("GOT END") ) {
                System.out.println("GOT END: " + line);
                String pair = s_lastPair;
                BiLogData start = s_starts.remove(s_lastPair);
                if (start == null) {
                    System.out.println("no start data found for matched end: " + line);
                }
                s_lastPair = null;

                StartEndData sed = new StartEndData(start, s_data);
                List<StartEndData> startEnds = s_startEndsMap.get(pair);
                if(startEnds == null) {
                    startEnds = new ArrayList<StartEndData>();
                    s_startEndsMap.put(pair, startEnds);
                }
                startEnds.add(sed);
                return PAIR; // end of one pair found - look for another pair
            }
            return null;
        }

        private static void record() {
            if (s_tops.size() != 3) {
                if (!s_isNotThreeLogged) {
                    System.out.println("not 3 tops");
                    s_isNotThreeLogged = true;
                }
            }
            if (s_pairs.size() != 3) {
                if (!s_isNotThreeLogged) {
                    System.out.println("not 3 pairs");
                    s_isNotThreeLogged = true;
                }
            }
            s_timePoints.add(s_data);
            s_data = null;
            s_tops = null;
            s_pairs = null;
        }

        public static final Pattern START_LEVEL = Pattern.compile(".*START_LEVEL=(.*); CANCEL_DEEP_PRICE_INDEX.*"); // date=Tue Jul 15 18:45:23 EEST 2014; START_LEVEL=0.00032
        public static final Pattern ELAPSED = Pattern.compile("iteration .* elapsed\\: (.*)");
        public static final Pattern TOP_ = Pattern.compile("(\\w+).*Top\\{bid\\=([\\d,]+\\.\\d+), ask\\=([\\d,]+\\.\\d+), last\\=([\\d,]+\\.\\d+)\\}");
        public static final Pattern PAIR_ = Pattern.compile("(\\w+) diff=(-?\\d+.*), avgDiff=(-?\\d+.*), diffDiff=(-?\\d+.*), bidAskDiff=(-?\\d+.*)");

        public State process(String line) { throw new RuntimeException("not implemented on " + this); }
    }

    private static class PairData {
        final String m_pair;
        final String m_diffStr;
        final String m_avgDiffStr;
        final String m_diffDiffStr;
        final String m_bidAskDiffStr;
        private Double m_diff;
        private Double m_avgDiff;
        private Double m_bidAskDiff;

        public PairData(String pair, String diffStr, String avgDiffStr, String diffDiffStr, String bidAskDiffStr) {
            m_pair = pair;
            m_diffStr = diffStr;
            m_avgDiffStr = avgDiffStr;
            m_diffDiffStr = diffDiffStr;
            m_bidAskDiffStr = bidAskDiffStr;
        }

        Double getDiff() {
            if(m_diff == null) {
                m_diff = Double.parseDouble(m_diffStr);
            }
            return m_diff;
        }

        Double getAvgDiff() {
            if(m_avgDiff == null) {
                m_avgDiff = Double.parseDouble(m_avgDiffStr);
            }
            return m_avgDiff;
        }

        Double getBidAskDiff() {
            if(m_bidAskDiff == null) {
                m_bidAskDiff = Double.parseDouble(m_bidAskDiffStr);
            }
            return m_bidAskDiff;
        }

        public Double[] getPriceDiffs() {
            return new Double[] {getDiff(), getAvgDiff()};
        }
    }

    private static class ExchData {
        final String m_exch;
        final String m_bidStr;
        final String m_askStr;
        final String m_lastStr;
        private Double m_bid;
        private Double m_ask;

        public ExchData(String exch, String bidStr, String askStr, String lastStr) {
            m_exch = exch;
            m_bidStr = bidStr;
            m_askStr = askStr;
            m_lastStr = lastStr;
        }

        Double getBid() {
            if(m_bid == null) {
                m_bid = parse(m_bidStr);
            }
            return m_bid;
        }

        Double getAsk() {
            if(m_ask == null) {
                m_ask = parse(m_askStr);
            }
            return m_ask;
        }

        private double parse(String str) {
            return Double.parseDouble(str.replace(",",""));
        }

        public double getMid() {
            return (getBid() + getAsk())/2;
        }
    }

    private static class BiLogData {
        final long m_time;
        final Map<String, ExchData> m_tops;
        final Map<String, PairData> m_pairs;

        public BiLogData(long time, Map<String, ExchData> tops, Map<String, PairData> pairs) {
            m_time = time;
            m_tops = tops;
            m_pairs = pairs;
        }
    }

    private static class StartEndData {
        final BiLogData m_start;
        final BiLogData m_end;

        public StartEndData(BiLogData start, BiLogData end) {
            m_start = start;
            m_end = end;
        }
    }

    private static void paintChart() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        BaseChartPaint.setupGraphics(g);

        Utils.LongMinMaxCalculator<BiLogData> timeCalc = new Utils.LongMinMaxCalculator<BiLogData>(s_timePoints) {
            @Override public Long getValue(BiLogData trace) { return trace.m_time; }
        };
        long minTimestamp = timeCalc.m_minValue;
        long maxTimestamp = timeCalc.m_maxValue;

        Utils.DoubleMinMaxCalculator<BiLogData> priceDiffCalc = new Utils.DoubleMinMaxCalculator<BiLogData>() {
            @Override public Double getValue(BiLogData obj) {
                return null; // obj.getPriceDiffs();
            }

            @Override public Double[] getValues(BiLogData obj) {
                Map<String, PairData> pairs = obj.m_pairs;
                if (pairs == null) {
                    System.out.println("pairs = " + pairs);
                }
                if (pairs == null) {
                    return null;
                } else {
                    PairData pairData = pairs.get(PAIR);
                    if (pairData == null) {
                        System.out.println("pairData = " + pairData);
                    }
                    return pairData.getPriceDiffs();
                }
            }
        };
        priceDiffCalc.calculate(s_timePoints);
        double minPriceDiff = priceDiffCalc.m_minValue;
        double maxPriceDiff = priceDiffCalc.m_maxValue;

        PaintChart.ChartAxe timeAxe = new PaintChart.ChartAxe(minTimestamp, maxTimestamp, WIDTH);
        PaintChart.ChartAxe priceDiffAxe = new PaintChart.ChartAxe(minPriceDiff-4, maxPriceDiff, HEIGHT);

        // paint border
        g.setPaint(Color.black);
        g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);

        int priceDiffStep = 2;
        int priceDiffStart = ((int)minPriceDiff) / priceDiffStep * priceDiffStep;

        // paint left axe
        BaseChartPaint.paintLeftAxeAndGrid(minPriceDiff, maxPriceDiff, priceDiffAxe, g, priceDiffStep, priceDiffStart, WIDTH);
        // paint left axe labels
        BaseChartPaint.paintLeftAxeLabels(minPriceDiff, maxPriceDiff, priceDiffAxe, g, priceDiffStep, priceDiffStart, XFACTOR);

        paintPoints(g, timeAxe, priceDiffAxe);

        g.dispose();
        BaseChartPaint.writeAndShowImage(image);
    }

    private static void paintPoints(Graphics2D g, PaintChart.ChartAxe timeAxe, PaintChart.ChartAxe priceDiffAxe) {
        int x0 = -1;
        int y0 = 0;
        int y0a = 0;
        int y0p = 0;
        for (BiLogData timePoint : s_timePoints) {
            long millis = timePoint.m_time;
            int x = timeAxe.getPoint(millis);

            PairData pd = timePoint.m_pairs.get(PAIR);
            Map<String, ExchData> tops = timePoint.m_tops;
            if (pd != null) {
                Double diff = pd.getDiff();
                int y = priceDiffAxe.getPointReverse(diff);

                if (x0 != -1) {
                    g.setPaint(LIGHT_RED);
                    g.drawLine(x0, y0, x, y);
                }

                // -------------
                Double avgDiff = pd.getAvgDiff();
                int ya = priceDiffAxe.getPointReverse(avgDiff);
                if (x0 != -1) {
                    g.setPaint(LIGHT_BLUE);
                    g.drawLine(x0, y0a, x, ya);
                }

                // +-s_level
                ExchData top1 = tops.get(EXCH1);
                ExchData top2 = tops.get(EXCH2);

                double mid1 = top1.getMid();
                double mid2 = top2.getMid();
                double mid = (mid1 + mid2) / 2;
                double levelDelta = mid * s_level;

                int yad = priceDiffAxe.getPointReverse(avgDiff + levelDelta);
                int dy = yad - ya;
                if (x0 != -1) {
                    g.setPaint(LIGHT_ORANGE);
                    g.drawLine(x0, y0a + dy, x, ya + dy);
                    g.drawLine(x0, y0a - dy, x, ya - dy);
                }

                // -------------
                Double bidAskDiff = pd.getBidAskDiff();
                double diffDiff = diff - avgDiff;
                Double plus = (diffDiff > 0) ? diff - bidAskDiff / 2 : diff + bidAskDiff / 2;
                int yp = priceDiffAxe.getPointReverse(plus);
                if (x0 != -1) {
                    g.setPaint(Color.orange);
                    g.drawLine(x0, y0p, x, yp);
                }

                y0 = y;
                y0a = ya;
                y0p = yp;
            }
            x0 = x;
        }

        double totalRatio = 1;
        double totalBalance = 0;
        int runsNum = 0;
        List<StartEndData> startEnds = s_startEndsMap.get(PAIR);
        if(startEnds != null) {
            runsNum = startEnds.size();
            for (StartEndData startEnd : startEnds) {
                BiLogData start = startEnd.m_start;
                BiLogData end = startEnd.m_end;

                long millis1 = start.m_time;
                long millis2 = end.m_time;

                int x1 = timeAxe.getPoint(millis1);
                int x2 = timeAxe.getPoint(millis2);

                PairData pd1 = start.m_pairs.get(PAIR);
                PairData pd2 = end.m_pairs.get(PAIR);

                Double diff1 = pd1.getDiff();
                Double diff2 = pd2.getDiff();

                int y1 = priceDiffAxe.getPointReverse(diff1);
                int y2 = priceDiffAxe.getPointReverse(diff2);

                g.setPaint(Color.green);
                g.drawLine(x1, y1, x2, y2);

                Double avgDiff1 = pd1.getAvgDiff();
                Double avgDiff2 = pd2.getAvgDiff();

                double diffDiff1 = diff1 - avgDiff1;
                double diffDiff2 = diff2 - avgDiff2;

                Double bidAskDiff1 = pd1.getBidAskDiff();
                Double bidAskDiff2 = pd2.getBidAskDiff();

                boolean aboveAverage = (diffDiff1 > 0);
                Double plus1 = aboveAverage ? diff1 - bidAskDiff1 / 2 : diff1 + bidAskDiff1 / 2;
                Double plus2 = (diffDiff2 > 0) ? diff2 - bidAskDiff2 / 2 : diff2 + bidAskDiff2 / 2;

                int y1p = priceDiffAxe.getPointReverse(plus1);
                int y2p = priceDiffAxe.getPointReverse(plus2);

                g.setPaint(Color.gray);
                g.drawLine(x1, y1p, x2, y2p);

                //------------------------
                int dy = aboveAverage ? 50 : -50;
                int dyl = aboveAverage ? 20 : -20;
                int ys = y1 - dy;
                g.drawString("" + diff1, x1, ys);
                String startStr = roundStr(plus1);
                g.drawString(startStr, x1, ys - dyl);

                int ye = y2 + dy;
                String endStr = "" + diff2;
                Rectangle2D bounds = g.getFont().getStringBounds(endStr, g.getFontRenderContext());
                g.drawString(endStr, (float) (x2 - bounds.getWidth()), ye);
                endStr = roundStr(plus2);
                bounds = g.getFont().getStringBounds(endStr, g.getFontRenderContext());
                g.drawString(endStr, (float) (x2 - bounds.getWidth()), ye + dyl);

                int yu = (aboveAverage ? ye: ys) - 17;
                g.drawLine(x1, yu, x2, yu);

                ExchData startTop1 = start.m_tops.get(EXCH1);
                ExchData startTop2 = start.m_tops.get(EXCH2);
                ExchData endTop1 = end.m_tops.get(EXCH1);
                ExchData endTop2 = end.m_tops.get(EXCH2);

                boolean up = !aboveAverage;

                Double s1b = startTop1.getBid();
                Double e1a = endTop1.getAsk();
                Double s1a = startTop1.getAsk();
                Double e1b = endTop1.getBid();
                double balance1 = up ? e1b - s1a : s1b - e1a;

                Double s2b = startTop2.getBid();
                Double e2a = endTop2.getAsk();
                Double s2a = startTop2.getAsk();
                Double e2b = endTop2.getBid();
                double balance2 = up ? s2b - e2a : e2b - s2a;

                double balance = balance1 + balance2;

                String b1 = roundStr(balance1);
                if(PAINT_BALANCE_CALCULATION) {
                    b1 += "=" + (up ? roundStr(e1b) + "-" + roundStr(s1a) : roundStr(s1b) + "-" + roundStr(e1a));
                }
                g.drawString(b1, x1, yu + 60);
                String b2 = roundStr(balance2);
                if(PAINT_BALANCE_CALCULATION) {
                    b2 += "=" + (up ? roundStr(s2b) + "-" + roundStr(e2a) : roundStr(e2b) + "-" + roundStr(s2a));
                }
                g.drawString(b2, x1, yu + 80);
                String b = roundStr(balance);
                g.setColor((balance > 0) ? Color.green : Color.red);
                g.drawString(b, x1, yu + 100);

    //            System.out.println("balance1="+b1);
    //            System.out.println("balance2="+b2);
    //            System.out.println("balance ="+b);

                totalBalance += balance;

                Double s1m = (startTop1.getAsk() + startTop1.getBid()) / 2;
                Double e1m = (endTop1.getAsk() + endTop1.getBid()) / 2;
                Double m1 = (s1m + e1m) / 2;
                Double s2m = (startTop2.getAsk() + startTop2.getBid()) / 2;
                Double e2m = (endTop2.getAsk() + endTop2.getBid()) / 2;
                Double m2 = (s2m + e2m) / 2;
                Double mid = (m1 + m2) / 2;
                double mid2 = BALANCED_ACCT ? mid * 2 : 0;

                double aa = up ? s1a + e2a : e1a + s2a;
                double bb = up ? e1b + s2b : s1b + e2b;
                double ratio = (bb + mid2) / (aa  + mid2);
                totalRatio *= ratio;
            }
        }

        double timeDays = timeAxe.m_max / Utils.ONE_DAY_IN_MILLIS;
        double dayRatio = Math.pow(totalRatio, 1/timeDays);

        System.out.println("runs=" + runsNum + ", totalBalance=" + totalBalance +
                ", time=" + Utils.millisToDHMSStr((long) timeAxe.m_max) + ", totalRatio=" + totalRatio +
                ", timeDays=" + timeDays + "; dayRatio=" + dayRatio);
    }

    private static String roundStr(double balance1) {
        return ROUND_EXCH.roundPriceStr(balance1, Pair.BTC_CNH);
    }
}
