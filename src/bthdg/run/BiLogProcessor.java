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
    private static final String LOG_FILE = "logs\\runner.012.log";

    private static long s_time;
    private static Map<String, PairData> s_pairs = new HashMap<String, PairData>();
    private static Map<String, ExchData> s_tops = new HashMap<String, ExchData>();
    private static List<BiLogData> s_timePoints = new ArrayList<BiLogData>();
    private static List<StartEndData> s_startEnds = new ArrayList<StartEndData>();
    private static BiLogData s_start;

    private static final int XFACTOR = 1;
    private static final int WIDTH = 1620 * XFACTOR * 16;
    public static final int HEIGHT = 900 * XFACTOR;
    public static final Color LIGHT_RED = new Color(255, 0, 0, 70);
    public static final Color LIGHT_BLUE = new Color(0, 0, 255, 70);

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = Utils.logStartTimeMemory();

        importFromFile();
        paintChart();

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void importFromFile() {
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private enum State {
        START {
            @Override public State process(String line) {
                if (line.contains("iteration ")) {
                    // iteration 66 ---------------------- elapsed: 3min 18sec 38ms
                    Matcher matcher = ELAPSED.matcher(line);
                    if(matcher.matches()) {
                        String elapsed = matcher.group(1); // 3min 18sec 38ms
                        long millis = Utils.parseHMSMtoMillis(elapsed);
                        System.out.println("----------------");
                        System.out.println("elapsed=" + elapsed + ", millis=" + millis);
                        s_time = millis;
                        return TOP;
                    }
                }
                return null; // do not change state
            }
        },
        TOP {
            @Override public State process(String line) {
                // BTCN  : Top{bid=3,778.6300, ask=3,782.2100, last=3,778.6000}
                Matcher matcher = TOP_.matcher(line);
                if(matcher.matches()) {
                    String exch = matcher.group(1);
                    String bidStr = matcher.group(2);
                    String askStr = matcher.group(3);
                    String lastStr = matcher.group(4);
                    System.out.println("exch=" + exch + ", bidStr=" + bidStr + ", askStr=" + askStr + ", lastStr=" + lastStr);

                    s_tops.put(exch, new ExchData(exch, bidStr, askStr, lastStr));
                } else if( line.contains("diffDiff=") ) {
                    return tryPair(line, null);
                } else {
                    System.out.println("waiting top but got: " + line);
                }
                return null; // do not change state
            }
        },
        PAIR {
            @Override public State process(String line) {
                return tryPair(line, s_start == null ? GOT_START : GOT_END);
            }
        },
        GOT_START {
            @Override public State process(String line) {
                if( line.contains("GOT START") ) {
                    System.out.println("GOT START: " + line);
                    s_start = s_data;
                    return START;
                }
                State nextState = START.process(line);
                return (nextState == null) ? GOT_START : nextState;
            }
        },
        GOT_END {
            @Override public State process(String line) {
                if( line.contains("GOT END") ) {
                    System.out.println("GOT END: " + line);
                    BiLogData start = s_start;
                    s_start = null;

                    StartEndData sed = new StartEndData(start, s_data);
                    s_startEnds.add(sed);
                    return START;
                }
                State nextState = START.process(line);
                return (nextState == null) ? GOT_END : nextState;
            }
        }
        ;
        private static BiLogData s_data;

        private static State tryPair(String line, State next) {
            // BTCN_OKCOIN diff=12.12, avgDiff=8.05, diffDiff=-4.07, bidAskDiff=4.12
            // BTCN_OKCOIN diff=12.12, avgDiff=8.05, diffDiff=4.07, bidAskDiff=4.12
            // BTCN_OKCOIN diff=12.12, avgDiff=8.05, diffDiff=0, bidAskDiff=4.12
            // BTCN_OKCOIN diff=5.52, avgDiff=8, diffDiff=-2.48, bidAskDiff=4.7
            // BTCN_OKCOIN diff=4, avgDiff=7.41, diffDiff=-3.41, bidAskDiff=1.9
            // BTCN_OKCOIN diff=7.38, avgDiff=5.91, diffDiff=1.47, bidAskDiff=1

            Matcher matcher = PAIR_.matcher(line);
            if(!matcher.matches()) {
                if(next == null) {
                    throw new RuntimeException("not matches PAIR_: " + line);
                }
                record();
                return next.process(line);
            }
            String pair = matcher.group(1);
            String diffStr = matcher.group(2);
            String avgDiffStr = matcher.group(3);
            String diffDiffStr = matcher.group(4);
            String bidAskDiffStr = matcher.group(5);
            System.out.println("pair=" + pair + ", diffStr=" + diffStr + ", avgDiffStr=" + avgDiffStr + ", diffDiffStr=" + diffDiffStr + ", bidAskDiffStr=" + bidAskDiffStr);

            s_pairs.put(pair, new PairData(pair, diffStr, avgDiffStr, diffDiffStr, bidAskDiffStr));

            return PAIR;
        }

        private static void record() {
            s_data = new BiLogData(s_time, s_tops, s_pairs);
            s_timePoints.add(s_data);
            s_tops.clear();
            s_pairs.clear();
        }

        public static final Pattern ELAPSED = Pattern.compile("iteration .* elapsed\\: (.*)"); // (\d+)min (\d+)sec (\d+)ms
        public static final Pattern TOP_ = Pattern.compile("(\\w+).*Top\\{bid\\=([\\d,]+\\.\\d+), ask\\=([\\d,]+\\.\\d+), last\\=([\\d,]+\\.\\d+)\\}");
        public static final Pattern PAIR_ = Pattern.compile("(\\w+) diff=(\\d.*), avgDiff=(\\d.*), diffDiff=(-?\\d.*), bidAskDiff=(-?\\d+.*)");

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
    }

    private static class BiLogData {
        final long m_time;
        final Map<String, ExchData> m_tops;
        final Map<String, PairData> m_pairs;

        public BiLogData(long time, Map<String, ExchData> tops, Map<String, PairData> pairs) {
            m_time = time;
            m_tops = new HashMap<String, ExchData>(tops);
            m_pairs = new HashMap<String, PairData>(pairs);
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
                return (pairs == null) ? null : pairs.get("BTCN_OKCOIN").getPriceDiffs();
            }
        };
        priceDiffCalc.calculate(s_timePoints);
        double minPriceDiff = priceDiffCalc.m_minValue;
        double maxPriceDiff = priceDiffCalc.m_maxValue;

        PaintChart.ChartAxe timeAxe = new PaintChart.ChartAxe(minTimestamp, maxTimestamp, WIDTH);
        PaintChart.ChartAxe priceDiffAxe = new PaintChart.ChartAxe(minPriceDiff-5, maxPriceDiff, HEIGHT);

        // paint border
        g.setPaint(Color.black);
        g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);

        int priceDiffStep = 5;
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

            PairData pd = timePoint.m_pairs.get("BTCN_OKCOIN");
            if (pd != null) {
                Double diff = pd.getDiff();
                int y = priceDiffAxe.getPointReverse(diff);

                if (x0 != -1) {
                    g.setPaint(LIGHT_RED);
                    g.drawLine(x0, y0, x, y);
                }
                x0 = x;
                y0 = y;

                Double avgDiff = pd.getAvgDiff();
                int ya = priceDiffAxe.getPointReverse(avgDiff);
                if (x0 != -1) {
                    g.setPaint(LIGHT_BLUE);
                    g.drawLine(x0, y0a, x, ya);
                }
                y0a = ya;

                Double bidAskDiff = pd.getBidAskDiff();
                double diffDiff = diff - avgDiff;
                Double plus = (diffDiff > 0) ? diff - bidAskDiff / 2 : diff + bidAskDiff / 2;
                int yp = priceDiffAxe.getPointReverse(plus);
                if (x0 != -1) {
                    g.setPaint(Color.orange);
                    g.drawLine(x0, y0p, x, yp);
                }
                y0p = yp;
            }
        }

        double totalRatio = 1;
        double totalBalance = 0;
        for (StartEndData startEnds : s_startEnds) {
            BiLogData start = startEnds.m_start;
            BiLogData end = startEnds.m_end;

            long millis1 = start.m_time;
            long millis2 = end.m_time;

            int x1 = timeAxe.getPoint(millis1);
            int x2 = timeAxe.getPoint(millis2);

            PairData pd1 = start.m_pairs.get("BTCN_OKCOIN");
            PairData pd2 = end.m_pairs.get("BTCN_OKCOIN");

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
            String startStr = Exchange.BTCN.roundPriceStr(plus1, Pair.BTC_CNH);
            g.drawString(startStr, x1, ys - dyl);

            int ye = y2 + dy;
            String endStr = "" + diff2;
            Rectangle2D bounds = g.getFont().getStringBounds(endStr, g.getFontRenderContext());
            g.drawString(endStr, (float) (x2 - bounds.getWidth()), ye);
            endStr = Exchange.BTCN.roundPriceStr(plus2, Pair.BTC_CNH);
            bounds = g.getFont().getStringBounds(endStr, g.getFontRenderContext());
            g.drawString(endStr, (float) (x2 - bounds.getWidth()), ye + dyl);

            int yu = (aboveAverage ? ye: ys) - 17;
            g.drawLine(x1, yu, x2, yu);

            ExchData startTop1 = start.m_tops.get("BTCN");
            ExchData startTop2 = start.m_tops.get("OKCOIN");
            ExchData endTop1 = end.m_tops.get("BTCN");
            ExchData endTop2 = end.m_tops.get("OKCOIN");

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

            String b1 = Exchange.BTCN.roundPriceStr(balance1, Pair.BTC_CNH);
            g.drawString(b1, x1, yu + 60);
            String b2 = Exchange.BTCN.roundPriceStr(balance2, Pair.BTC_CNH);
            g.drawString(b2, x1, yu + 80);
            String b = Exchange.BTCN.roundPriceStr(balance, Pair.BTC_CNH);
            g.drawString(b, x1, yu + 100);

            totalBalance += balance;

            Double s1m = (startTop1.getAsk() + startTop1.getBid()) / 2;
            Double e1m = (endTop1.getAsk() + endTop1.getBid()) / 2;
            Double m1 = (s1m + e1m) / 2;
            Double s2m = (startTop2.getAsk() + startTop2.getBid()) / 2;
            Double e2m = (endTop2.getAsk() + endTop2.getBid()) / 2;
            Double m2 = (s2m + e2m) / 2;
            Double mid = (m1 + m2) / 2;

            double aa = up ? s1a + e2a : e1a + s2a;
            double bb = up ? e1b + s2b : s1b + e2b;
            double ratio = (bb + mid*2) / (aa  + mid*2);
            totalRatio *= ratio;
        }
        System.out.println("runs=" + s_startEnds.size() + ", totalBalance=" + totalBalance +
                ", time=" + Utils.millisToDHMSStr((long) timeAxe.m_max) + ", totalRatio="+totalRatio);

        int x = timeAxe.getPoint(0);
        int y = priceDiffAxe.getPointReverse(7.62);
        g.setPaint(Color.CYAN);
        BaseChartPaint.drawX(g, x, y, 50);
    }
}
