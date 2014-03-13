package bthdg;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

public class PaintTrace extends BaseChartPaint {

    private static final int XFACTOR = 4;
    private static final int WIDTH = 1620 * XFACTOR * 2;
    public static final int HEIGHT = 900 * XFACTOR;
    public static final Color LIGHT_RED = new Color(255, 0, 0, 32);
    public static final Color LIGHT_BLUE = new Color(0, 0, 255, 32);
    public static final Color DARK_GREEN = new Color(0, 80, 0);
    public static final Color LIGHT_X = new Color(60, 60, 60, 12);
    public static final double EXPECTED_GAIN = 2; // Fetcher.EXPECTED_GAIN;
    public static final double COMMISSION_SUMM = 0.008;
    public static final long MOVING_AVERAGE = Fetcher.MOVING_AVERAGE;
    public static final boolean PAINT_PRICE = true;
    public static final boolean PAINT_ORDERS = true;
    public static final boolean PAINT_ORDERS_SHADOW = false;
    public static final boolean PAINT_PRICE_DIFF_SEPARATELY = true;

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = System.currentTimeMillis();
        System.out.println("timeMills: " + millis);
        long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("maxMemory: " + maxMemory + ", k:" + (maxMemory /= 1024) + ": m:" + (maxMemory /= 1024));

        long fromMillis = (args.length > 0) ? Utils.toMillis(args[0]) : 0;
        paint(fromMillis);

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void paint(final long fromMillis) {
        IDbRunnable runnable = new IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                System.out.println("selecting ticks");
                List<TraceData> ticks = selectTraces(connection, fromMillis);
                drawTraces(ticks);
                System.out.println("--- Complete ---");
            }
        };

        goWithDb(runnable);
    }

    private static List<TraceData> selectTraces(Connection connection, long fromMillis) throws SQLException {
        long three = System.currentTimeMillis();
        List<TraceData> ticks = new ArrayList<TraceData>();
        PreparedStatement statement = connection.prepareStatement(
                        "   SELECT stamp, bid1, ask1, bid2, ask2, fork, buy1, sell1, buy2, sell2 " +
                        "    FROM Trace " +
                        "    WHERE stamp > ? " +
//                        "     AND stamp < ? " +
//                        "     AND (src = ? OR src = ?) " +
                        "    ORDER BY stamp ASC ");
        try {
            statement.setLong(1, fromMillis);

            ResultSet result = statement.executeQuery();
            long four = System.currentTimeMillis();
            System.out.println(ticks.size() + " traces selected in " + Utils.millisToDHMSStr(four - three));
            try {
                while (result.next()) {
                    long stamp = result.getLong(1);
                    double bid1 = result.getDouble(2);
                    double ask1 = result.getDouble(3);
                    double bid2 = result.getDouble(4);
                    double ask2 = result.getDouble(5);
                    long fork = result.getLong(6);
                    double buy1 = result.getDouble(7);
                    double sell1 = result.getDouble(8);
                    double buy2 = result.getDouble(9);
                    double sell2 = result.getDouble(10);

                    TraceData trace = new TraceData(stamp, bid1, ask1, bid2, ask2, fork, buy1, sell1, buy2, sell2);
                    ticks.add(trace);
                }
                long five = System.currentTimeMillis();
                System.out.println(ticks.size() + " traces read in " + Utils.millisToDHMSStr(five - four));
            } finally {
                result.close();
            }
        } finally {
            statement.close();
        }
        return ticks;
    }

    private static void drawTraces(List<TraceData> traces) {
        Utils.DoubleMinMaxCalculator<TraceData> priceCalc = new Utils.DoubleMinMaxCalculator<TraceData>() {
            Double[] m_ar = new Double[8];
            public Double getValue(TraceData trace) {return null;};
            @Override public Double[] getValues(TraceData trace) {
                m_ar[0] = (trace.m_bid1 == 0) ? null : trace.m_bid1;
                m_ar[1] = (trace.m_ask1 == 0) ? null : trace.m_ask1;
                m_ar[2] = (trace.m_bid2 == 0) ? null : trace.m_bid2;
                m_ar[3] = (trace.m_ask2 == 0) ? null : trace.m_ask2;
                m_ar[4] = (trace.m_buy1 == 0) ? null : trace.m_buy1;
                m_ar[5] = (trace.m_sell1 == 0) ? null : trace.m_sell1;
                m_ar[6] = (trace.m_buy2 == 0) ? null : trace.m_buy2;
                m_ar[7] = (trace.m_sell2 == 0) ? null : trace.m_sell2;
                return m_ar;
            }
        };
        priceCalc.calculate(traces);
        double minPrice = priceCalc.m_minValue;
        double maxPrice = priceCalc.m_maxValue;

        Utils.DoubleMinMaxCalculator<TraceData> priceDiffCalc = new Utils.DoubleMinMaxCalculator<TraceData>() {
            Double[] m_arr = new Double[3];
            public Double getValue(TraceData trace) { return null; }
            @Override public Double[] getValues(TraceData trace) {
                if ((trace.m_bid1 != 0) && (trace.m_ask1 != 0) && (trace.m_bid2 != 0) && (trace.m_ask2 != 0)) {
                    m_arr[0] = (trace.m_bid1 + trace.m_ask1) / 2 - (trace.m_bid2 + trace.m_ask2) / 2;
                } else {
                    m_arr[0] = null;
                }
                if ((trace.m_buy1 != 0) && (trace.m_sell1 != 0)) {
                    m_arr[1] = trace.m_sell1 - trace.m_buy1;
                } else {
                    m_arr[1] = null;
                }
                if ((trace.m_buy2 != 0) && (trace.m_sell2 != 0)) {
                    m_arr[2] = trace.m_sell2 - trace.m_buy2;
                } else {
                    m_arr[2] = null;
                }
                return m_arr;
            }
        };
        priceDiffCalc.calculate(traces);
        double minPriceDiff = priceDiffCalc.m_minValue;
        double maxPriceDiff = priceDiffCalc.m_maxValue;

        Utils.LongMinMaxCalculator<TraceData> timeCalc = new Utils.LongMinMaxCalculator<TraceData>(traces) {
            @Override public Long getValue(TraceData trace) { return trace.m_stamp; }
        };
        long minTimestamp = timeCalc.m_minValue;
        long maxTimestamp = timeCalc.m_maxValue;

        long timeDiff = maxTimestamp - minTimestamp;
        System.out.println("min timestamp: " + minTimestamp + ", max timestamp: " + maxTimestamp + ", timestamp diff: " + timeDiff);
        double priceDiff = maxPrice - minPrice;
        System.out.println("minPrice = " + minPrice + ", maxPrice = " + maxPrice + ", priceDiff = " + priceDiff);
        double priceDiffDiff = maxPriceDiff - minPriceDiff;
        System.out.println("minPriceDiff = " + minPriceDiff + ", maxPriceDiff = " + maxPriceDiff + ", priceDiffDiff = " + priceDiffDiff);

        PaintChart.ChartAxe timeAxe = new PaintChart.ChartAxe(minTimestamp, maxTimestamp, WIDTH);
        PaintChart.ChartAxe priceAxe = new PaintChart.ChartAxe(minPrice - priceDiff * 0.05, maxPrice + priceDiff * 0.05, HEIGHT);
        PaintChart.ChartAxe priceDiffAxe = new PaintChart.ChartAxe(minPriceDiff - priceDiffDiff * 0.05, maxPriceDiff + priceDiffDiff * 0.05, HEIGHT);

        int diffY = PAINT_PRICE_DIFF_SEPARATELY ? HEIGHT : 0;
        int height = HEIGHT + diffY;
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON );
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY );

        // paint border
        g.setPaint(Color.black);
        g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);
        if(PAINT_PRICE_DIFF_SEPARATELY) {
            g.drawRect(0, HEIGHT, WIDTH - 1, HEIGHT - 1);
        }

        int priceStep = 5;
        int priceStart = ((int)minPrice) / priceStep * priceStep;

        // paint left axe
        paintLeftAxeAndGrid(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, WIDTH);

        // paint points
        paintPoints(traces, timeAxe, priceAxe, priceDiffAxe, g, diffY);

        g.setPaint(Color.LIGHT_GRAY);

        // paint left axe labels
        paintLeftAxeLabels(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, XFACTOR);

        // paint right axe labels
        paintRightAxeLabels(minPriceDiff, maxPriceDiff, priceDiffAxe, g, WIDTH, 1, XFACTOR, diffY);

        // paint time axe labels
        paintTimeAxeLabels(minTimestamp, maxTimestamp, timeAxe, g, HEIGHT, XFACTOR);

        g.dispose();

        try {
            long millis = System.currentTimeMillis();

            File output = new File("imgout/" + Long.toString(millis, 32) + ".png");
            ImageIO.write(image, "png", output);

            System.out.println("write done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));

            Desktop.getDesktop().open(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void paintPoints(List<TraceData> traces, PaintChart.ChartAxe timeAxe, PaintChart.ChartAxe priceAxe,
                                    PaintChart.ChartAxe priceDiffAxe, Graphics2D g, int diffYoffset) {
        Utils.AverageCounter diffAverageCounter = new Utils.AverageCounter(MOVING_AVERAGE);

        int avg1X = -1, avg1Y = -1;
        int avg2X = -1, avg2Y = -1;
        int diffX = -1, diffY = -1;
        int diffAvg = -1;

        double commissionHalf = 0;
        double half = 0;

        Map<Long, TraceData> forksMap = new HashMap<Long, TraceData>();
        TraceData ghostTrace = new TraceData(0,0,0,0,0,0,0,0,0,0);
        TraceData prevTrace = null;
        for (TraceData trace : traces) {
            long millis = trace.m_stamp;
            int x = timeAxe.getPoint(millis);
            double bidAsk1 = 0;
            if ((trace.m_bid1 != 0) && (trace.m_ask1 != 0)) {
                if (PAINT_PRICE) {
                    int y1 = priceAxe.getPointReverse(trace.m_bid1);
                    g.setPaint(LIGHT_RED);
                    g.drawLine(x - 1, y1, x + 1, y1);

                    int y2 = priceAxe.getPointReverse(trace.m_ask1);
                    g.drawLine(x - 1, y2, x + 1, y2);

                    g.drawLine(x, y1, x, y2);
                }
                bidAsk1 = trace.m_ask1 - trace.m_bid1;
            }
            double bidAsk2 = 0;
            if ((trace.m_bid2 != 0) && (trace.m_ask2 != 0)) {
                if (PAINT_PRICE) {
                    int y1 = priceAxe.getPointReverse(trace.m_bid2);
                    g.setPaint(LIGHT_BLUE);
                    g.drawLine(x - 1, y1, x + 1, y1);

                    int y2 = priceAxe.getPointReverse(trace.m_ask2);
                    g.drawLine(x - 1, y2, x + 1, y2);

                    g.drawLine(x, y1, x, y2);
                }
                bidAsk2 = trace.m_ask2 - trace.m_bid2;
            }
            if (PAINT_ORDERS) {
                if (trace.m_buy1 != 0) {
                    int y = priceAxe.getPointReverse(trace.m_buy1);
                    if (PAINT_ORDERS_SHADOW && (bidAsk1 > 0)) {
                        g.setPaint(LIGHT_X);
                        int y2 = priceAxe.getPointReverse(trace.m_buy1 + bidAsk1);
                        g.drawLine(x, y, x, y2);
                    }
                    g.setPaint(Color.ORANGE);
                    g.drawLine(x, y, x, y);
                    g.drawRect(x - 1, y - 1, 2, 2);
                }
                if (trace.m_sell1 != 0) {
                    int y = priceAxe.getPointReverse(trace.m_sell1);
                    if (PAINT_ORDERS_SHADOW && (bidAsk2 > 0)) {
                        g.setPaint(LIGHT_X);
                        int y2 = priceAxe.getPointReverse(trace.m_sell1 - bidAsk2);
                        g.drawLine(x, y, x, y2);
                    }
                    g.setPaint(Color.ORANGE);
                    g.drawLine(x, y, x, y);
                    g.drawRect(x - 1, y - 1, 2, 2);
                }
                if (trace.m_buy2 != 0) {
                    int y = priceAxe.getPointReverse(trace.m_buy2);
                    if (PAINT_ORDERS_SHADOW && (bidAsk2 > 0)) {
                        g.setPaint(LIGHT_X);
                        int y2 = priceAxe.getPointReverse(trace.m_buy2 + bidAsk2);
                        g.drawLine(x, y, x, y2);
                    }
                    g.setPaint(Color.GREEN);
                    g.drawLine(x, y, x, y);
                    g.drawRect(x - 1, y - 1, 2, 2);
                }
                if (trace.m_sell2 != 0) {
                    int y = priceAxe.getPointReverse(trace.m_sell2);
                    if (PAINT_ORDERS_SHADOW && (bidAsk1 > 0)) {
                        g.setPaint(LIGHT_X);
                        int y2 = priceAxe.getPointReverse(trace.m_sell2 - bidAsk1);
                        g.drawLine(x, y, x, y2);
                    }
                    g.setPaint(Color.GREEN);
                    g.drawLine(x, y, x, y);
                    g.drawRect(x - 1, y - 1, 2, 2);
                }
            }
            if ((trace.m_bid1 != 0) && (trace.m_ask1 != 0) && (trace.m_bid2 != 0) && (trace.m_ask2 != 0)) {
                double priceDiff = (trace.m_bid1 + trace.m_ask1) / 2 - (trace.m_bid2 + trace.m_ask2) / 2;

                if(commissionHalf == 0) {
                    commissionHalf = (trace.m_bid1 + trace.m_ask1 + trace.m_bid2 + trace.m_ask2) / 4 * COMMISSION_SUMM / 2;
                    half = commissionHalf + EXPECTED_GAIN / 2;
                }

                int y = priceDiffAxe.getPointReverse(priceDiff) + diffYoffset;

                double avgDiff = diffAverageCounter.add(millis, priceDiff);
                double avgUp = avgDiff + commissionHalf;
                double avgDown = avgDiff - commissionHalf;
                boolean exceed = (priceDiff > avgUp) || (priceDiff < avgDown);

                int y2 = priceDiffAxe.getPointReverse(avgDiff) + diffYoffset;
                int y3 = priceDiffAxe.getPointReverse(avgDiff + half) + diffYoffset;
                int y4 = priceDiffAxe.getPointReverse(avgDiff - half) + diffYoffset;
                int y5 = priceDiffAxe.getPointReverse(avgUp) + diffYoffset;
                int y6 = priceDiffAxe.getPointReverse(avgDown) + diffYoffset;

                if ((diffX != -1) && (diffAvg != -1)) {
                    g.setPaint(Color.red);
                    int dy = y2 - diffAvg;
                    g.drawLine(diffX, diffAvg, x, y2);
                    g.drawLine(diffX, y3-dy, x, y3);
                    g.drawLine(diffX, y4-dy, x, y4);
                    g.drawLine(diffX, y5-dy, x, y5);
                    g.drawLine(diffX, y6-dy, x, y6);
                }

                diffAvg = y2;

                g.setPaint(exceed ? Color.MAGENTA : DARK_GREEN);
                g.drawLine(x, y, x, y);
                g.drawRect(x - 2, y - 2, 4, 4);
                if ((diffX != -1) && (diffY != -1)) {
                    g.drawLine(diffX, diffY, x, y);
                }
                diffX = x;
                diffY = y;
            }

            double avgDiff = diffAverageCounter.get()/2;
            if ((trace.m_buy1 != 0) && (trace.m_sell1 != 0)) {
                double diff = trace.m_sell1 - trace.m_buy1 + avgDiff;
                int y = priceDiffAxe.getPointReverse(diff) + diffYoffset;
                g.setPaint(Color.blue);
                drawSmallX(g, x, y);
            }
            if ((trace.m_buy2 != 0) && (trace.m_sell2 != 0)) {
                double diff = trace.m_sell2 - trace.m_buy2 - avgDiff;
                int y = priceDiffAxe.getPointReverse(diff) + diffYoffset;
                g.setPaint(Color.cyan);
                drawSmallX(g, x, y);
            }

            if (prevTrace != null) {
                double buyPrice = 0;
                if ((prevTrace.m_buy2 == 0) && (trace.m_buy2 != 0)) {
                    buyPrice = ghostTrace.m_buy1;
//                    Date date = new Date(millis);
//                    System.out.println("buyPrice=" + buyPrice + "; date " + date);
                }
                double sellPrice = 0;
                if ((prevTrace.m_sell2 == 0) && (trace.m_sell2 != 0)) {
                    sellPrice = ghostTrace.m_sell1;
//                    Date date = new Date(millis);
//                    System.out.println("sellPrice=" + sellPrice + "; date " + date);
                }
                if ((buyPrice != 0) && (sellPrice != 0)) {
                    double diff = sellPrice - buyPrice;
//                    System.out.println("diff=" + diff);
                    int y = priceDiffAxe.getPointReverse(diff) + diffYoffset;
                    g.setPaint(Color.ORANGE);
                    drawX(g, x, y);
                }
            }
            prevTrace = trace;
            ghostTrace.update(trace, avgDiff);

            long fork = trace.m_fork;
            TraceData forkGhost = forksMap.get(fork);
            if (forkGhost == null) {
                forkGhost = new TraceData(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
                forksMap.put(fork, forkGhost);
            }
            forkGhost.update(trace, avgDiff);
        }

        g.setFont(g.getFont().deriveFont(120.0f));
        //FontMetrics fontMetrics = g.getFontMetrics();
        //Rectangle2D bounds = fontMetrics.getStringBounds(str, g);

        for(TraceData fork: forksMap.values()) {
            boolean hasBuy1 = (fork.m_buy1 != 0);
            boolean hasSell1 = (fork.m_sell1 != 0);
            boolean hasBuy2 = (fork.m_buy2 != 0);
            boolean hasSell2 = (fork.m_sell2 != 0);
            if (PAINT_PRICE) {
                int y1b = 0, x1b = 0, y1s = 0, x1s = 0, y2b = 0, x2b = 0, y2s = 0, x2s = 0;
                if (hasBuy1) {
                    y1b = priceAxe.getPointReverse(fork.m_buy1);
                    x1b = timeAxe.getPoint(fork.m_buy1stamp);
                    g.setPaint(Color.BLUE);
                    drawX(g, x1b, y1b);
                    String str = Fetcher.format(fork.m_buy1);
                    g.drawString(str, x1b + 5, y1b - 5);
                }
                if (hasSell1) {
                    y1s = priceAxe.getPointReverse(fork.m_sell1);
                    x1s = timeAxe.getPoint(fork.m_sell1stamp);
                    g.setPaint(Color.RED);
                    drawX(g, x1s, y1s);
                    String str = Fetcher.format(fork.m_sell1);
                    g.drawString(str, x1s + 5, y1s - 5);
                }
                if (hasBuy2) {
                    y2b = priceAxe.getPointReverse(fork.m_buy2);
                    x2b = timeAxe.getPoint(fork.m_buy2stamp);
                    g.setPaint(Color.BLUE);
                    drawX(g, x2b, y2b);
                    String str = Fetcher.format(fork.m_buy2);
                    g.drawString(str, x2b + 5, y2b - 5);
                }
                if (hasSell2) {
                    y2s = priceAxe.getPointReverse(fork.m_sell2);
                    x2s = timeAxe.getPoint(fork.m_sell2stamp);
                    g.setPaint(Color.RED);
                    drawX(g, x2s, y2s);
                    String str = Fetcher.format(fork.m_sell2);
                    g.drawString(str, x2s + 5, y2s - 5);
                }
                double diff12 = 0, diff21 = 0;
                boolean hasBuy1Sell2 = hasBuy1 && hasSell2;
                if (hasBuy1Sell2) {
                    g.setPaint(Color.ORANGE);
                    g.drawLine(x1b, y1b, x2s, y2s);
                    diff21 = fork.m_sell2 - fork.m_buy1;
                    String str = Fetcher.format(diff21);
                    g.drawString(str, (x1b + x2s) / 2 + 5, (y1b + y2s) / 2);
                }
                boolean hasSell1Buy2 = hasSell1 && hasBuy2;
                if (hasSell1Buy2) {
                    g.setPaint(Color.ORANGE);
                    g.drawLine(x1s, y1s, x2b, y2b);
                    diff12 = fork.m_sell1 - fork.m_buy2;
                    String str = Fetcher.format(diff12);
                    g.drawString(str, (x1s + x2b) / 2 + 5, (y1s + y2b) / 2);
                }
                if (hasBuy1Sell2 && hasSell1Buy2) {
                    String str = Fetcher.format(diff12 + diff21);
                    g.drawString(str, (x1b + x2s + x1s + x2b) / 4 + 5, HEIGHT - 70);
                }
            }
            boolean hasBuySell1 = (hasBuy1 && hasSell1);
            boolean hasBuySell2 = (hasBuy2 && hasSell2);
            int y1 = 0, y2 = 0;
            int x1 = 0, x2 = 0;
            double diff1 = 0, diff2 = 0;
            if (hasBuySell1) {
                double avgDiff = (fork.m_sell1avgDiff + fork.m_buy1avgDiff) / 2;
                diff1 = fork.m_sell1 - fork.m_buy1 - avgDiff;
                long time = (fork.m_sell1stamp + fork.m_buy1stamp) / 2;
                x1 = timeAxe.getPoint(time);
                y1 = priceDiffAxe.getPointReverse(diff1) + diffYoffset;
                g.setPaint(Color.RED);
                drawX(g, x1, y1);
                String str = Fetcher.format(diff1);
                g.drawString(str, x1+5, y1-5);
            }
            if (hasBuySell2) {
                double avgDiff = (fork.m_sell2avgDiff + fork.m_buy2avgDiff) / 2;
                diff2 = fork.m_sell2 - fork.m_buy2 + avgDiff;
                long time = (fork.m_sell2stamp + fork.m_buy2stamp) / 2;
                x2 = timeAxe.getPoint(time);
                y2 = priceDiffAxe.getPointReverse(diff2) + diffYoffset;
                g.setPaint(Color.RED);
                drawX(g, x2, y2);
                String str = Fetcher.format(diff2);
                g.drawString(str, x2+5, y2-5);
            }
            if (hasBuySell1 && hasBuySell2) {
                g.setPaint(Color.RED);
                g.drawLine(x1, y1, x2, y2);
                String str = Fetcher.format(Math.abs(diff1 - diff2));
                g.drawString(str, (x1 + x2) / 2 + 5, (y1 + y2) / 2);
            }
        }
    }

    private static final int X_DIAMETER = 25;
    private static void drawX(Graphics2D g, int x, int y) {
        drawX(g, x, y, X_DIAMETER);
    }

    private static final int X_DIAMETER_SMALL = 5;
    private static void drawSmallX(Graphics2D g, int x, int y) {
        drawX(g, x, y, X_DIAMETER_SMALL);
    }

    private static void drawX(Graphics2D g, int x, int y, int d) {
        g.drawLine(x - d, y - d, x + d, y + d);
        g.drawLine(x - d, y + d, x + d, y - d);
    }

    private static class TraceData {
        public long m_stamp;
        public double m_bid1;
        public double m_ask1;
        public double m_bid2;
        public double m_ask2;
        public long m_fork;
        public double m_buy1;
        public double m_sell1;
        public double m_buy2;
        public double m_sell2;

        private long m_buy1stamp;
        private double m_buy1avgDiff;
        private long m_sell1stamp;
        private double m_sell1avgDiff;
        private long m_buy2stamp;
        private double m_buy2avgDiff;
        private long m_sell2stamp;
        private double m_sell2avgDiff;

        public TraceData(long stamp, double bid1, double ask1, double bid2, double ask2,
                         long fork, double buy1, double sell1, double buy2, double sell2) {
            m_stamp = stamp;
            m_bid1 = bid1;
            m_ask1 = ask1;
            m_bid2 = bid2;
            m_ask2 = ask2;
            m_fork = fork;
            m_buy1 = buy1;
            m_sell1 = sell1;
            m_buy2 = buy2;
            m_sell2 = sell2;
        }

        public void update(TraceData trace, double avgDiff) {
            if (trace.m_stamp != 0) {
                m_stamp = trace.m_stamp;
            }
            if (trace.m_bid1 != 0) {
                m_bid1 = trace.m_bid1;
            }
            if (trace.m_ask1 != 0) {
                m_ask1 = trace.m_ask1;
            }
            if (trace.m_bid2 != 0) {
                m_bid2 = trace.m_bid2;
            }
            if (trace.m_ask2 != 0) {
                m_ask2 = trace.m_ask2;
            }
            if (trace.m_fork != 0) {
                m_fork = trace.m_fork;
            }
            if ((trace.m_buy1 != 0) && (m_buy1 != trace.m_buy1)) {
                m_buy1 = trace.m_buy1;
                m_buy1stamp = m_stamp;
                m_buy1avgDiff = avgDiff;
            }
            if ((trace.m_sell1 != 0) && (m_sell1 != trace.m_sell1)) {
                m_sell1 = trace.m_sell1;
                m_sell1stamp = m_stamp;
                m_sell1avgDiff = avgDiff;
            }
            if ((trace.m_buy2 != 0) && (m_buy2 != trace.m_buy2)) {
                m_buy2 = trace.m_buy2;
                m_buy2stamp = m_stamp;
                m_buy2avgDiff = avgDiff;
            }
            if ((trace.m_sell2 != 0) && (m_sell2 != trace.m_sell2)) {
                m_sell2 = trace.m_sell2;
                m_sell2stamp = m_stamp;
                m_sell2avgDiff = avgDiff;
            }
        }
    }
}
