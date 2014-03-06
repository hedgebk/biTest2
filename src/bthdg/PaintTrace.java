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
import java.util.ArrayList;
import java.util.List;

public class PaintTrace extends BaseChartPaint {

    private static final int XFACTOR = 4;
    private static final int WIDTH = 1620 * XFACTOR * 2;
    public static final int HEIGHT = 900 * XFACTOR;
    public static final Color LIGHT_RED = new Color(255, 0, 0, 32);
    public static final Color LIGHT_BLUE = new Color(0, 0, 255, 32);
    public static final Color DARK_GREEN = new Color(0, 80, 0);
    public static final Color LIGHT_X = new Color(60, 60, 60, 12);
    public static final double EXPECTED_GAIN = 3.0; // 5
    public static final double COMMISSION_SUMM = 0.008;
    public static final long MOVING_AVERAGE = Fetcher.MOVING_AVERAGE * 4;

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
            Double[] m_ar1 = new Double[1];
            public Double getValue(TraceData trace) { return null; }
            @Override public Double[] getValues(TraceData trace) {
                if ((trace.m_bid1 != 0) && (trace.m_ask1 != 0) && (trace.m_bid2 != 0) && (trace.m_ask2 != 0)) {
                    m_ar1[0] = (trace.m_bid1 + trace.m_ask1) / 2 - (trace.m_bid2 + trace.m_ask2) / 2;
                } else {
                    m_ar1[0] = null;
                }
                return m_ar1;
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

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON );
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY );

        // paint border
        g.setPaint(Color.black);
        g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);

        int priceStep = 5;
        int priceStart = ((int)minPrice) / priceStep * priceStep;

        // paint left axe
        paintLeftAxeAndGrid(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, WIDTH);

        // paint points
        paintPoints(traces, timeAxe, priceAxe, priceDiffAxe, g);

        g.setPaint(Color.LIGHT_GRAY);

        // paint left axe labels
        paintLeftAxeLabels(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, XFACTOR);

        // paint right axe labels
        paintRightAxeLabels(minPriceDiff, maxPriceDiff, priceDiffAxe, g, WIDTH, 1, XFACTOR);

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
                                    PaintChart.ChartAxe priceDiffAxe, Graphics2D g) {
        Utils.AverageCounter diffAverageCounter = new Utils.AverageCounter(MOVING_AVERAGE);

        int avg1X = -1, avg1Y = -1;
        int avg2X = -1, avg2Y = -1;
        int diffX = -1, diffY = -1;
        int diffAvg = -1;

        double commissionHalf = 0;
        double half = 0;

        for (TraceData trace : traces) {
            long millis = trace.m_stamp;
            int x = timeAxe.getPoint(millis);
            double bidAsk1 = 0;
            if ((trace.m_bid1 != 0) && (trace.m_ask1 != 0)) {
                int y1 = priceAxe.getPointReverse(trace.m_bid1);
                g.setPaint(LIGHT_RED);
                g.drawLine(x-1, y1, x+1, y1);

                int y2 = priceAxe.getPointReverse(trace.m_ask1);
                g.drawLine(x-1, y2, x+1, y2);

                g.drawLine(x, y1, x, y2);

                bidAsk1 = trace.m_ask1 - trace.m_bid1;
            }
            double bidAsk2 = 0;
            if ((trace.m_bid2 != 0) && (trace.m_ask2 != 0)) {
                int y1 = priceAxe.getPointReverse(trace.m_bid2);
                g.setPaint(LIGHT_BLUE);
                g.drawLine(x - 1, y1, x + 1, y1);

                int y2 = priceAxe.getPointReverse(trace.m_ask2);
                g.drawLine(x - 1, y2, x + 1, y2);

                g.drawLine(x, y1, x, y2);

                bidAsk2 = trace.m_ask2 - trace.m_bid2;
            }
            if (trace.m_buy1 != 0) {
                int y = priceAxe.getPointReverse(trace.m_buy1);
                if (bidAsk1 > 0) {
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
                if (bidAsk2 > 0) {
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
                if (bidAsk2 > 0) {
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
                if (bidAsk1 > 0) {
                    g.setPaint(LIGHT_X);
                    int y2 = priceAxe.getPointReverse(trace.m_sell2 - bidAsk1);
                    g.drawLine(x, y, x, y2);
                }
                g.setPaint(Color.GREEN);
                g.drawLine(x, y, x, y);
                g.drawRect(x - 1, y - 1, 2, 2);
            }
            if ((trace.m_bid1 != 0) && (trace.m_ask1 != 0) && (trace.m_bid2 != 0) && (trace.m_ask2 != 0)) {
                double priceDiff = (trace.m_bid1 + trace.m_ask1) / 2 - (trace.m_bid2 + trace.m_ask2) / 2;

                if(commissionHalf == 0) {
                    commissionHalf = (trace.m_bid1 + trace.m_ask1 + trace.m_bid2 + trace.m_ask2) / 4 * COMMISSION_SUMM / 2;
                    half = commissionHalf + EXPECTED_GAIN / 2;
                }

                int y = priceDiffAxe.getPointReverse(priceDiff);


                double avg = diffAverageCounter.add(millis, priceDiff);
                double avgUp = avg + commissionHalf;
                double avgDown = avg - commissionHalf;
                boolean exceed = (priceDiff > avgUp) || (priceDiff < avgDown);

                int y2 = priceDiffAxe.getPointReverse(avg);
                int y3 = priceDiffAxe.getPointReverse(avg + half);
                int y4 = priceDiffAxe.getPointReverse(avg - half);
                int y5 = priceDiffAxe.getPointReverse(avgUp);
                int y6 = priceDiffAxe.getPointReverse(avgDown);

                if ((diffX != -1) && (diffAvg != -1)) {
                    g.setPaint(Color.red);
                    g.drawLine(diffX, diffAvg, x, y2);
                    g.drawLine(x, y3, x, y3);
                    g.drawLine(x, y4, x, y4);
                    g.drawLine(x, y5, x, y5);
                    g.drawLine(x, y6, x, y6);
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
        }
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
    }
}
