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
    private static final int XFACTOR = 2;
    private static final int WIDTH = 1620 * XFACTOR * 2;
    public static final int HEIGHT = 900 * XFACTOR;
    public static final Color LIGHT_RED = new Color(255, 0, 0, 32);
    public static final Color LIGHT_BLUE = new Color(0, 0, 255, 32);
    public static final Color DARK_GREEN = new Color(0, 80, 0);
    public static final Color LIGHT_X = new Color(60, 60, 60, 12);
    public static final double EXPECTED_GAIN = Fetcher.EXPECTED_GAIN;
    public static final double COMMISSION = 0.002;
    public static final long MOVING_AVERAGE = Fetcher.MOVING_AVERAGE;
    public static final boolean PAINT_PRICE = true;
    public static final boolean PAINT_ORDERS = true;
    public static final boolean PAINT_ORDERS_SHADOW = true;
    public static final boolean PAINT_PRICE_DIFF_SEPARATELY = true;
    public static final float CHART_FONT_SIZE_RATIO = 15.0f;

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = System.currentTimeMillis();
        System.out.println("timeMills: " + millis);
        long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("maxMemory: " + maxMemory + ", k:" + (maxMemory /= 1024) + ": m:" + (maxMemory /= 1024));

        long fromMillis = (args.length > 0) ? Utils.toMillis(args[0]) : /*0*/ Utils.toMillis("-36h");
        paint(fromMillis);

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void paint(final long fromMillis) {
        IDbRunnable runnable = new IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                System.out.println("selecting ticks");
                List<TraceData> ticks = selectTraces(connection, fromMillis);
                List<TradeData> trades = selectTrades(connection, fromMillis);

                drawTraces(ticks, trades);
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
            System.out.println("traces selected in " + Utils.millisToDHMSStr(four - three));
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

    private static List<TradeData> selectTrades(Connection connection, long fromMillis) throws SQLException {
        long three = System.currentTimeMillis();
        List<TradeData> trades = new ArrayList<TradeData>();
        PreparedStatement statement = connection.prepareStatement(
                        "SELECT stamp, exch, side, price, amount, crossId, forkId " +
                        " FROM TraceTrade " +
                        " WHERE stamp > ? " +
                        " ORDER BY stamp ASC ");
        try {
            statement.setLong(1, fromMillis);

            ResultSet result = statement.executeQuery();
            long four = System.currentTimeMillis();
            System.out.println("trades selected in " + Utils.millisToDHMSStr(four - three));
            try {
                while (result.next()) {
                    long stamp = result.getLong(1);
                    int exchId = result.getInt(2);
                    String side = result.getString(3);
                    double price = result.getDouble(4);
                    double amount = result.getDouble(5);
                    long crossId = result.getLong(6);
                    long forkId = result.getLong(7);

                    TradeData trade = new TradeData(amount, price, stamp, -1, null, OrderSide.getByCode(side), exchId, crossId, forkId);
                    trades.add(trade);
                }
                System.out.println(trades.size() + " trades read in " + Utils.millisToDHMSStr(System.currentTimeMillis() - four));
            } finally {
                result.close();
            }
        } finally {
            statement.close();
        }
        return trades;
    }

    private static void drawTraces(List<TraceData> traces, List<TradeData> trades) {
        Utils.DoubleMinMaxCalculator<TradeData> priceCalc0 = new Utils.DoubleMinMaxCalculator<TradeData>() {
            public Double getValue(TradeData trade) {return trade.m_price;};
        };
        priceCalc0.calculate(trades);

        Utils.DoubleMinMaxCalculator<TraceData> priceCalc = new Utils.DoubleMinMaxCalculator<TraceData>() {
            Double[] m_ar = new Double[8];
            public Double getValue(TraceData trace) {return null;};
            @Override public Double[] getValues(TraceData trace) {
                m_ar[0] = nullIfZero(trace.m_exch1.m_bid);
                m_ar[1] = nullIfZero(trace.m_exch1.m_ask);
                m_ar[2] = nullIfZero(trace.m_exch2.m_bid);
                m_ar[3] = nullIfZero(trace.m_exch2.m_ask);
                m_ar[4] = nullIfZero(trace.m_open.m_buy);
                m_ar[5] = nullIfZero(trace.m_open.m_sell);
                m_ar[6] = nullIfZero(trace.m_close.m_buy);
                m_ar[7] = nullIfZero(trace.m_close.m_sell);
                return m_ar;
            }

            private Double nullIfZero(double v) { return (v == 0) ? null : v; }
        };
        priceCalc.calculate(traces);
        double minPrice = Math.min(priceCalc0.m_minValue, priceCalc.m_minValue);
        double maxPrice = Math.max(priceCalc0.m_maxValue, priceCalc.m_maxValue);

        Utils.DoubleMinMaxCalculator<TraceData> priceDiffCalc = new Utils.DoubleMinMaxCalculator<TraceData>() {
            public Double getValue(TraceData trace) {
                return (trace.m_exch1.hasMid() && trace.m_exch2.hasMid())
                        ? trace.m_exch1.mid() - trace.m_exch2.mid()
                        : null;
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
        System.out.println("min timestamp: " + minTimestamp + ", max timestamp: " + maxTimestamp + ", timestamp diff: " + Utils.millisToDHMSStr(timeDiff));
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
        setupGraphics(g);

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
        paintPoints(traces, trades, timeAxe, priceAxe, priceDiffAxe, g, diffY);

        g.setPaint(Color.LIGHT_GRAY);

        // paint left axe labels
        paintLeftAxeLabels(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, XFACTOR);

        // paint right axe labels
        paintRightAxeLabels(minPriceDiff, maxPriceDiff, priceDiffAxe, g, WIDTH, 1, XFACTOR, diffY);

        // paint time axe labels
        paintTimeAxeLabels(minTimestamp, maxTimestamp, timeAxe, g, HEIGHT, XFACTOR);

        g.dispose();
        writeAndShowImage(image);
    }

    private static void paintPoints(List<TraceData> traces, List<TradeData> trades,
                                    PaintChart.ChartAxe timeAxe, PaintChart.ChartAxe priceAxe,
                                    PaintChart.ChartAxe priceDiffAxe, Graphics2D g, int diffYoffset) {
        Utils.AverageCounter diffAverageCounter = new Utils.AverageCounter(MOVING_AVERAGE);
        TreeMap<Long,Double> diffAverageMap = new TreeMap<Long, Double>();

        int diffX = -1, diffY = -1;
        int diffAvg = -1;
        double commissionHalf = 0;
        double half = 0;

        for (TraceData trace : traces) {
            long millis = trace.m_stamp;
            int x = timeAxe.getPoint(millis);
            double bidAsk1 = paintBidAsk(g, trace.m_exch1, priceAxe, x);
            double bidAsk2 = paintBidAsk(g, trace.m_exch2, priceAxe, x);
            if (PAINT_ORDERS) {
                paintOrders(g, trace.m_open, priceAxe, x, bidAsk2, bidAsk1);
                paintOrders(g, trace.m_close, priceAxe, x, bidAsk1, bidAsk2);
            }
            if ( trace.m_exch1.hasMid() && trace.m_exch2.hasMid()) {
                double priceDiff = trace.m_exch1.mid() - trace.m_exch2.mid();

                if (commissionHalf == 0) {
                    commissionHalf = (trace.m_exch1.m_bid + trace.m_exch1.m_ask + trace.m_exch2.m_bid + trace.m_exch2.m_ask) * COMMISSION / 2;
                    half = commissionHalf + EXPECTED_GAIN / 2;
                }

                int y = priceDiffAxe.getPointReverse(priceDiff) + diffYoffset;

                double avgDiff = diffAverageCounter.add(millis, priceDiff);
                diffAverageMap.put(millis, avgDiff);
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
//            double avgDiff = diffAverageCounter.get()/2;
        }

        g.setFont(g.getFont().deriveFont(CHART_FONT_SIZE_RATIO * XFACTOR));

        if (PAINT_PRICE) {
            // paint market trades
            for (TradeData trade : trades) {
                if (trade.m_orderSide == null) {
                    int x = timeAxe.getPoint(trade.m_timestamp);
                    int y = priceAxe.getPointReverse(trade.m_price);
                    g.setPaint(Color.BLACK);
                    drawX(g, x, y, 1);
                }
            }
        }

        // collect my trades
        Map<Long, TreeMap<Long, TradeData[]>> forksTrades = new HashMap<Long, TreeMap<Long, TradeData[]>>();
        for (TradeData trade : trades) {
            OrderSide orderSide = trade.m_orderSide;
            if (orderSide != null) {
                long forkId = trade.m_forkId;
                TreeMap<Long, TradeData[]> forkTrades = forksTrades.get(forkId);
                if (forkTrades == null) {
                    forkTrades = new TreeMap<Long, TradeData[]>();
                    forksTrades.put(forkId, forkTrades);
                }
                long crossId = trade.m_crossId;
                TradeData[] crossTrades = forkTrades.get(crossId);
                if (crossTrades == null) {
                    crossTrades = new TradeData[2];
                    forkTrades.put(crossId, crossTrades);
                }
                int indx = (orderSide.isBuy() ? 0 : 1);
                TradeData old = crossTrades[indx];
                if (old != null) {
                    System.out.println("duplicate trade forkId=" + forkId + ", crossId=" + crossId + ", trade=" + trade);
                }
                crossTrades[indx] = trade;
            }
        }

        // paint my trades
        for (TreeMap<Long, TradeData[]> forkTrades : forksTrades.values()) {
            Map.Entry<Long, TradeData[]> openEntry = forkTrades.firstEntry();
            Map.Entry<Long, TradeData[]> closeEntry = (forkTrades.size() > 1) ? forkTrades.lastEntry() : null;
            paintBox(g, timeAxe, priceAxe, openEntry, closeEntry);
            XY p1 = paintCross(g, timeAxe, priceAxe, priceDiffAxe, openEntry, diffAverageMap, true);
            XY p2 = paintCross(g, timeAxe, priceAxe, priceDiffAxe, closeEntry, diffAverageMap, false);
            if ((openEntry != null) && (closeEntry != null)) {
                TradeData[] open = openEntry.getValue();
                TradeData[] close = closeEntry.getValue();
                paintBuySell(g, timeAxe, priceAxe, open[0], close[1]);
                paintBuySell(g, timeAxe, priceAxe, close[0], open[1]);
            }
            if ((p1 != null) && (p2 != null)) {
                drawLine(g, p1.m_x, p1.m_y, p2.m_x, p2.m_y, Color.CYAN);
                double diffDiff = p1.m_value - p2.m_value;
                String str = Fetcher.format(diffDiff);
                paintShadedString(g, str, (p1.m_x + p2.m_x)/2 + 5, (p1.m_y + p2.m_y)/2 - 5, Color.magenta);
            }
        }
    }

    private static void paintBox(Graphics2D g, PaintChart.ChartAxe timeAxe, PaintChart.ChartAxe priceAxe,
                                 Map.Entry<Long, TradeData[]> openEntry, Map.Entry<Long, TradeData[]> closeEntry) {
        Utils.DoubleMinMaxCalculator<TradeData> priceCalc = new Utils.DoubleMinMaxCalculator<TradeData>() {
            public Double getValue(TradeData trade) {return trade.m_price;};
        };

        Utils.LongMinMaxCalculator<TradeData> timeCalc = new Utils.LongMinMaxCalculator<TradeData>() {
            @Override public Long getValue(TradeData trade) { return trade.m_timestamp; }
        };

        processEntry(openEntry, priceCalc, timeCalc);
        processEntry(closeEntry, priceCalc, timeCalc);

        double minPrice = priceCalc.m_minValue;
        double maxPrice = priceCalc.m_maxValue;
        long minTimestamp = timeCalc.m_minValue;
        long maxTimestamp = timeCalc.m_maxValue;

        int x1 = timeAxe.getPoint(minTimestamp);
        int x2 = timeAxe.getPoint(maxTimestamp);
        int y1 = priceAxe.getPointReverse(maxPrice);
        int y2 = priceAxe.getPointReverse(minPrice);
        g.setPaint(Color.lightGray);
        g.drawRect(x1, y1, x2 - x1, y2 - y1);
        g.drawLine(x1, 0, x1, WIDTH * 2);
        g.drawLine(x2, 0, x2, WIDTH * 2);
    }

    private static void processEntry(Map.Entry<Long, TradeData[]> entry,
                                     Utils.DoubleMinMaxCalculator<TradeData> priceCalc,
                                     Utils.LongMinMaxCalculator<TradeData> timeCalc) {
        if (entry != null) {
            TradeData[] trades = entry.getValue();
            priceCalc.calculate(trades[0]);
            priceCalc.calculate(trades[1]);
            timeCalc.calculate(trades[0]);
            timeCalc.calculate(trades[1]);
        }
    }

    private static void paintBuySell(Graphics2D g, PaintChart.ChartAxe timeAxe, PaintChart.ChartAxe priceAxe,
                                     TradeData buyTrade, TradeData sellTrade) {
        if ((buyTrade != null) && (sellTrade != null)) {
            double buyPrice = buyTrade.m_price;
            double sellPrice = sellTrade.m_price;
            int xb = timeAxe.getPoint(buyTrade.m_timestamp);
            int yb = priceAxe.getPointReverse(buyPrice);
            int xs = timeAxe.getPoint(sellTrade.m_timestamp);
            int ys = priceAxe.getPointReverse(sellPrice);
            drawLine(g, xb, yb, xs, ys, Color.green);
            String str = Fetcher.format(Math.abs(sellPrice - buyPrice));
            paintShadedString(g, str, (xb + xs) / 2 + 5, (yb + ys) / 2, Color.green);
        }
    }

    private static void drawLine(Graphics2D g, int xb, int yb, int xs, int ys, Color green) {
        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(3.0f));
        g.setPaint(green);
        g.drawLine(xb, yb, xs, ys);
        g.setStroke(old);
    }

    private static void paintShadedString(Graphics2D g, String str, int x, int y, Color color) {
        g.setPaint(Color.white);
        g.drawString(str, x - 1, y);
        g.drawString(str, x + 1, y);
        g.drawString(str, x, y - 1);
        g.drawString(str, x, y + 1);
        g.setPaint(color);
        g.drawString(str, x, y);
    }

    private static XY paintCross(Graphics2D g, PaintChart.ChartAxe timeAxe, PaintChart.ChartAxe priceAxe, PaintChart.ChartAxe priceDiffAxe,
                                   Map.Entry<Long, TradeData[]> entry, TreeMap<Long, Double> diffAverageMap, boolean isOpenCross) {
        if(entry != null) {
            TradeData[] crossTrades = entry.getValue();
            TradeData buyTrade = crossTrades[0];
            TradeData sellTrade = crossTrades[1];
            paintTrade(g, timeAxe, priceAxe, buyTrade);
            paintTrade(g, timeAxe, priceAxe, sellTrade);

            if ((buyTrade != null) && (sellTrade != null)) {
                double buySellPriceDiff = sellTrade.m_price - buyTrade.m_price;
                if (!isOpenCross) {
                    buySellPriceDiff = -buySellPriceDiff;
                }
                long buySellTime = (sellTrade.m_timestamp + buyTrade.m_timestamp)/2;
                int x = timeAxe.getPoint(buySellTime);
                String str = Fetcher.format(buySellPriceDiff);
                paintShadedString(g, str, x + 5, HEIGHT - 50, Color.red);
//                Map.Entry<Long, Double> avgDiffEntry = diffAverageMap.floorEntry(buySellTime);
//                Double avgDiff = avgDiffEntry.getValue();
                int y = priceDiffAxe.getPointReverse(buySellPriceDiff) + HEIGHT;
                drawX(g, x, y, 20);
                paintShadedString(g, str, x + 5, y - 5, Color.red);
                return new XY(x, y, buySellPriceDiff);
            }
        }
        return null;
    }

    private static void paintTrade(Graphics2D g, PaintChart.ChartAxe timeAxe, PaintChart.ChartAxe priceAxe, TradeData trade) {
        if (trade != null) {
            int x = timeAxe.getPoint(trade.m_timestamp);
            int y = priceAxe.getPointReverse(trade.m_price);
            Color color = trade.m_orderSide.isBuy() ? Color.BLUE : Color.RED;
            g.setPaint(color);
            drawX(g, x, y);
            String str = Fetcher.format(trade.m_price);
            paintShadedString(g, str, x + 5, y - 5, color);
        }
    }

    private static void paintOrders(Graphics2D g, BuySell cross, PaintChart.ChartAxe priceAxe, int x, double buyDelta, double sellDelta) {
        if (cross.m_buy != 0) {
            int y = priceAxe.getPointReverse(cross.m_buy);
            if (PAINT_ORDERS_SHADOW && (buyDelta > 0)) {
                g.setPaint(LIGHT_X);
                int y2 = priceAxe.getPointReverse(cross.m_buy + buyDelta);
                g.drawLine(x, y, x, y2);
            }
            g.setPaint(Color.ORANGE);
            g.drawLine(x, y, x, y);
            g.drawRect(x - 1, y - 1, 2, 2);
        }
        if (cross.m_sell != 0) {
            int y = priceAxe.getPointReverse(cross.m_sell);
            if (PAINT_ORDERS_SHADOW && (sellDelta > 0)) {
                g.setPaint(LIGHT_X);
                int y2 = priceAxe.getPointReverse(cross.m_sell - sellDelta);
                g.drawLine(x, y, x, y2);
            }
            g.setPaint(Color.ORANGE);
            g.drawLine(x, y, x, y);
            g.drawRect(x - 1, y - 1, 2, 2);
        }
    }

    private static double paintBidAsk(Graphics2D g, TraceData.BidAsk bidAsk, PaintChart.ChartAxe priceAxe, int x) {
        if ((bidAsk.m_bid != 0) && (bidAsk.m_ask != 0)) {
            if (PAINT_PRICE) {
                int y1 = priceAxe.getPointReverse(bidAsk.m_bid);
                g.setPaint(LIGHT_RED);
                g.drawLine(x - 1, y1, x + 1, y1);

                int y2 = priceAxe.getPointReverse(bidAsk.m_ask);
                g.drawLine(x - 1, y2, x + 1, y2);

                g.drawLine(x, y1, x, y2);
            }
            return bidAsk.m_ask - bidAsk.m_bid;
        }
        return 0;
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
        public BidAsk m_exch1;
        public BidAsk m_exch2;
        public long m_fork;
        public BuySell m_open;
        public BuySell m_close;

        public TraceData(long stamp, double bid1, double ask1, double bid2, double ask2,
                         long fork, double buy1, double sell1, double buy2, double sell2) {
            m_stamp = stamp;
            m_exch1 = new BidAsk(bid1, ask1);
            m_exch2 = new BidAsk(bid2, ask2);
            m_fork = fork;
            m_open = new BuySell(buy1, sell1);
            m_close = new BuySell(buy2, sell2);
        }

        private static class BidAsk {
            public double m_bid;
            public double m_ask;

            public BidAsk(double bid, double ask) {
                m_bid = bid;
                m_ask = ask;
            }

            public double mid() { return ( m_bid + m_ask) / 2; }
            public boolean hasMid() { return (m_bid != 0) && (m_ask != 0); }
        }
    }

    private static class XY {
        private int m_x;
        private int m_y;
        private double m_value;

        public XY(int x, int y, double value) {
            m_x = x;
            m_y = y;
            m_value = value;
        }
    }
}
