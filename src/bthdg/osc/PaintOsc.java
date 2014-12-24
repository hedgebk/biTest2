package bthdg.osc;

import bthdg.BaseChartPaint;
import bthdg.PaintChart;
import bthdg.exch.Exchange;
import bthdg.exch.OrderSide;
import bthdg.util.Utils;

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
import java.util.Collections;
import java.util.List;

public class PaintOsc extends BaseChartPaint {
    private static final long TIME_FRAME = Utils.toMillis("25d");
    private static final long BAR_SIZE = Utils.toMillis("1m");
    private static final Exchange EXCHANGE = Exchange.BTCN;
//    private static final Exchange EXCHANGE = Exchange.OKCOIN;
//    private static final Exchange EXCHANGE = Exchange.BTCE;
//    private static final Exchange EXCHANGE = Exchange.BITSTAMP;
    private static final Color TRANSP_GRAY = new Color(100,100,100,50);

    // chart area
    public static final int X_FACTOR = 1; // more points
    private static final int WIDTH = 10000;
    public static final int HEIGHT = 800;
    public static final int LEN1 = 10;
    public static final int LEN2 = 10;
    public static final int K = 3;
    public static final int D = 3;
    private static final int MARK_DIAMETER = 5;
    public static final BasicStroke TR_STROKE = new BasicStroke(3);
    public static final int OFFSET_BAR_PARTS = 2;
    public static final boolean PAINT = false;
    private static final boolean DBL_CONFIRM_IN_MIDDLE = false;
    private static final boolean STICK_TOP_BOTTOM = false;
    private static final double STICK_TOP_BOTTOM_LEVEL = 0.05;
    private static final boolean PAINT_VALUES = false;
    private static final boolean BLEND_AVG = true;

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = System.currentTimeMillis();
        System.out.println("timeMills: " + millis);
        System.out.println("BAR_SIZE: " + BAR_SIZE + " ms; =" + Utils.millisToDHMSStr(BAR_SIZE));
        long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("maxMemory: " + maxMemory + ", k:" + (maxMemory /= 1024) + ": m:" + (maxMemory /= 1024));

        paint();

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void paint() {
        IDbRunnable runnable = new IDbRunnable() {
            @Override public void run(Connection connection) throws SQLException {
                long now = System.currentTimeMillis();

                long timestamp = getMaxTimestamp(connection, EXCHANGE);
                System.out.println("max timestamp="+timestamp);

                System.out.println("timeFrame: " + TIME_FRAME + " ms; =" + Utils.millisToDHMSStr(TIME_FRAME));
                long start = timestamp - TIME_FRAME;

                System.out.println("selecting ticks");
                List<PaintChart.Tick> ticks = selectTicks(connection, now, timestamp, start, EXCHANGE);
                System.out.println("selected " + ticks.size() + " ticks");

                drawTicks(ticks);

                System.out.println("--- Complete ---");
            }
        };

        goWithDb(runnable);
    }

    private static void drawTicks(List<Tick> ticks) {
        Utils.DoubleMinMaxCalculator<Tick> priceCalc = new Utils.DoubleMinMaxCalculator<Tick>(ticks) {
            @Override public Double getValue(Tick tick) { return tick.m_price; }
        };
        double minPrice = priceCalc.m_minValue;
        double maxPrice = priceCalc.m_maxValue;

        Utils.LongMinMaxCalculator<Tick> timeCalc = new Utils.LongMinMaxCalculator<Tick>(ticks) {
            @Override public Long getValue(Tick tick) { return tick.m_stamp; }
        };
        long minTimestamp = timeCalc.m_minValue;
        long maxTimestamp = timeCalc.m_maxValue;

        long minBarTimestamp = minTimestamp / BAR_SIZE * BAR_SIZE;
        long maxBarTimestamp = (maxTimestamp / BAR_SIZE + 1) * BAR_SIZE;

        long timeDiff = maxBarTimestamp - minBarTimestamp;
        double priceDiff = maxPrice - minPrice;
        System.out.println("min timestamp: " + minTimestamp + ", max timestamp: " + maxTimestamp + ", timestamp diff: " + timeDiff);
        System.out.println("minPrice = " + minPrice + ", maxPrice = " + maxPrice + ", priceDiff = " + priceDiff);

        ChartAxe timeAxe = new PaintChart.ChartAxe(minBarTimestamp, maxBarTimestamp, WIDTH);
        ChartAxe priceAxe = new PaintChart.ChartAxe(minPrice, maxPrice, HEIGHT);
        PaintChart.ChartAxe oscAxe = new PaintChart.ChartAxe(0, 1, HEIGHT);
        String timePpStr = "time per pixel: " + Utils.millisToDHMSStr((long) timeAxe.m_scale);
        System.out.println(timePpStr);

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage./*TYPE_USHORT_565_RGB*/ TYPE_INT_ARGB );
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC );
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY );
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON );
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY );

        g.setPaint(new Color(250, 250, 250));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // paint border
        g.setPaint(Color.black);
        g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);

        int priceStep = 10;
        int priceStart = ((int)minPrice) / priceStep * priceStep;

        // paint left axe
        paintLeftAxeAndGrid(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, WIDTH);
        g.setColor(Color.DARK_GRAY);
        g.drawLine(0, HEIGHT * 2 / 10, WIDTH, HEIGHT * 2 / 10);
        g.drawLine(0, HEIGHT * 8 / 10, WIDTH, HEIGHT * 8 / 10);
        // paint left axe labels
        paintLeftAxeLabels(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, X_FACTOR);

        // paint time axe labels
        paintTimeAxeLabels(minTimestamp, maxTimestamp, timeAxe, g, HEIGHT, X_FACTOR);

        // paint points
        paintPoints(ticks, priceAxe, timeAxe, g);

        //---------------------
        Bar[] bars = calBars(ticks, minBarTimestamp, maxBarTimestamp);
        // paint bars
        paintBars(bars, priceAxe, timeAxe, g);

//        OscCalculator calc = new OscCalculator(BAR_SIZE);
//        for (Tick tick : ticks) {
//            calc.update(tick);
//        }
//        List<Osc> oscs = calc.ret();
//        // paint ocs
//        paintOscs(oscs, oscAxe, timeAxe, g, Color.ORANGE);

//        List<Osc> fine = calc.fine();
//        paintFine(fine, oscAxe, timeAxe, g);

//        List<Osc> cont = calcCont(ticks);
//        paintFine(cont, oscAxe, timeAxe, g);

//        List<Osc> forBars = calcForBars(ticks, minBarTimestamp, maxBarTimestamp, 0);
//        List<Osc> forBars = new ArrayList<Osc>();
        Color[] colors = new Color[]{Color.BLUE, Color.MAGENTA, Color.PINK, Color.CYAN, Color.GRAY, Color.YELLOW, Color.GREEN, Color.ORANGE};
        long step = BAR_SIZE / OFFSET_BAR_PARTS;
        int index = 0;
        double cummCumm = 0;
        for (long offset = 0; offset < BAR_SIZE; offset += step) {
            List<Osc> forBars2 = calcForBars(ticks, minBarTimestamp, maxBarTimestamp, offset);
            Color color = colors[(index++) % colors.length];
            double cumm = paintOscs(forBars2, oscAxe, timeAxe, g, color, ticks, priceAxe);
            System.out.println("cumm = " + cumm);
            cummCumm += cumm;
//            forBars.addAll(forBars2);
        }
        cummCumm /= index;
        System.out.println("cummCumm = " + cummCumm);

        double timeFrameDays = ((double) TIME_FRAME) / Utils.ONE_DAY_IN_MILLIS;
        System.out.println("timeFrameDays = " + timeFrameDays);

        double pow = 1.0 / timeFrameDays;
        double aDay = Math.pow(cummCumm, pow);
        System.out.println("/day = " + aDay);

//        Collections.sort(forBars);
//        paintOscs(forBars, oscAxe, timeAxe, g, 0);

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

    private static List<Osc> calcForBars(List<Tick> ticks, long minBarTimestamp, long maxBarTimestamp, long timeOffset) {
//        System.out.println("----------------------------------------------------------------------");
        OscCalculator calc = new OscCalculator(BAR_SIZE);
        calc.setBarsMillisOffset(timeOffset);

        int i = 0;
        for (long barStartTime = minBarTimestamp + timeOffset; barStartTime < maxBarTimestamp; barStartTime += BAR_SIZE) {
            long barEndTime = barStartTime + BAR_SIZE; // excluding time
            int tickIndex = binarySearch(ticks, barEndTime - 1, false); // tick before
            Tick tick = ticks.get(tickIndex);
            long tickTime = tick.m_stamp;
//            System.out.println(i + ": barStartTime=" + barStartTime + "; barEndTime=" + barEndTime + "; tickIndex=" + tickIndex + "; tick=" + tick);
            if (tickTime < barStartTime) {
                // no tick in this bar
                //throw new RuntimeException("tickTime<barStartTime");
            } else {
                boolean newBarStarted = calc.update(tick);
                if (!newBarStarted) {
                    throw new RuntimeException("newBar NOT Started. closeTicks index=" + i + "; tick=" + tick);
                }
            }
            i++;
        }
        return calc.ret();
    }

    private static List<Osc> calcCont(List<Tick> ticks) {
        List<Osc> ret = new ArrayList<Osc>();
        int ticksNum = ticks.size();
        int barsNum = LEN1 + LEN2 + (K - 1) + (D - 1);
        Tick[] closeTicks = new Tick[barsNum];
        Tick firstTick = ticks.get(0);
        long minTickTime = firstTick.m_stamp;
        long minOscTime = minTickTime + barsNum * BAR_SIZE + 1;
        int minTickIndex = binarySearch(ticks, minOscTime, true);
        {
            Tick tick = ticks.get(minTickIndex);
            long tickTime = tick.m_stamp;
            if(tickTime<minOscTime) {
                throw new RuntimeException("tickTime<minOscTime");
            }
        }
        long lastOscTickTime = 0;
        for (int oscTickIndex = minTickIndex; oscTickIndex < ticksNum; oscTickIndex++) {
            Tick oscTick = ticks.get(oscTickIndex);
            closeTicks[barsNum - 1] = oscTick;
            long oscTickTime = oscTick.m_stamp;
            if (lastOscTickTime != oscTickTime) {
                long oscTime = oscTickTime + 1;
                for (int i = barsNum - 2; i >= 0; i--) {
                    oscTime -= BAR_SIZE;
                    int tickIndex = binarySearch(ticks, oscTime, true);
                    tickIndex--;
                    Tick tick = ticks.get(tickIndex);
                    closeTicks[i] = tick;
                }
                lastOscTickTime = oscTickTime;
            }
            OscCalculator calc = new OscCalculator(BAR_SIZE);
            calc.setBarsMillisOffset(oscTickTime + 1 - (oscTickTime + 1) / BAR_SIZE * BAR_SIZE);
            for (int i = 0; i < closeTicks.length; i++) {
                Tick closeTick = closeTicks[i];
                boolean newBarStarted = calc.update(closeTick);
                if (!newBarStarted) {
                    throw new RuntimeException("newBar NOT Started. closeTicks index=" + i);
                }
            }
            List<Osc> oscs = calc.ret();
            if (oscs.size() != 1) {
                throw new RuntimeException("oscs.size()!=1");
            }
            Osc osc = oscs.get(0);
            ret.add(osc);
        }
        return ret;
    }

    private static int binarySearch(List<Tick> ticks, long stamp, boolean ceil) {
        int i = Collections.binarySearch(ticks, stamp);
        if(i<0) {
            int insertionPoint = -i-1;
            if(ceil) {
                i = insertionPoint;
            } else {
                i = insertionPoint-1;
            }
        }
        return i;
    }

    private static double max(List<Double> vals) {
        double res = 0;
        for (Double val : vals) {
            res = Math.max(res, val);
        }
        return res;
    }

    private static double max(List<Double> vals, Double lastVal) {
        return Math.max(max(vals), lastVal);
    }

    private static double min(List<Double> vals) {
        Double res = null;
        for (Double val : vals) {
            res = (res == null) ? val : Math.min(res, val);
        }
        return (res == null) ? 0 : res;
    }

    private static double min(List<Double> vals, Double lastVal) {
        return Math.min(min(vals), lastVal);
    }

    private static double avg(List<Double> vals) {
        double sum = 0;
        for (Double val : vals) {
            sum += val;
        }
        return sum / vals.size();
    }

    private static double avg(List<Double> vals, Double lastVal) {
        double sum = lastVal;
        for (Double val : vals) {
            sum += val;
        }
        return sum / (vals.size()+1);
    }

    private static Bar[] calBars(List<Tick> ticks, long minBarTimestamp, long maxBarTimestamp) {
        long timeDiff = maxBarTimestamp - minBarTimestamp;
        int barsNum = (int) (timeDiff / BAR_SIZE);
        Bar[] bars = new Bar[barsNum];
        for (Tick tick : ticks) {
            long stamp = tick.m_stamp;
            int barIndex = (int) ((stamp - minBarTimestamp) / BAR_SIZE);
            Bar bar = bars[barIndex];
            if (bar == null) {
                bar = new Bar(minBarTimestamp + barIndex * BAR_SIZE);
                bars[barIndex] = bar;
            }
            bar.update(tick);
        }
        return bars;
    }

    private static void paintPoints(List<Tick> ticks, ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setPaint(Color.red);
        for (Tick tick : ticks) {
            double price = tick.m_price;
            int y = priceAxe.getPointReverse(price);
            long stamp = tick.m_stamp;
            int x = timeAxe.getPoint(stamp);
            g.drawLine(x, y, x, y);
        }
    }

    private static void paintBars(Bar[] bars, ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        for (Bar bar : bars) {
            if (bar != null) {
                long barStartTime = bar.m_barStartTime;
                int left = timeAxe.getPoint(barStartTime);
                long barEndTime = barStartTime + BAR_SIZE;
                int right = timeAxe.getPoint(barEndTime);
                long barMidTime = barStartTime + BAR_SIZE / 2;
                int mid = timeAxe.getPoint(barMidTime);

                double open = bar.m_open;
                int openY = priceAxe.getPointReverse(open);
                double close = bar.m_close;
                int closeY = priceAxe.getPointReverse(close);
                double high = bar.m_high;
                int highY = priceAxe.getPointReverse(high);
                double low = bar.m_low;
                int lowY = priceAxe.getPointReverse(low);

                g.setPaint(TRANSP_GRAY);
                g.drawLine(left, 0, left, HEIGHT);
                g.fillRect(left, highY, right - left, lowY - highY);

                g.setPaint(Color.BLUE);
                if(right-left >= 5) {
                    g.fillRect(mid - 1, highY, 3, lowY - highY);
                } else {
                    g.drawLine(mid, highY, mid, lowY);
                }
                g.drawLine(left, openY, mid, openY);
                g.drawLine(mid, closeY, right, closeY);
            }
        }
    }

    private static double paintOscs(List<Osc> oscs, ChartAxe oscAxe, ChartAxe timeAxe, Graphics2D g, Color color, List<Tick> ticks, ChartAxe priceAxe) {
//        System.out.println("------------------------------------------------");
        Integer prevRight = null;
        Integer prevVal1Y = null;
        Integer prevVal2Y = null;

        boolean lastCrossUp = false;
        boolean lastCrossDown = false;
        double cumm = 1.0;
        OrderSide side = null;
        Osc tradeOsc = null;
        Osc prevOsc = null;
        int indx = 0;
        boolean waitingDblConfirm = false;
        boolean stickTopBottom = false;
        for (Osc osc : oscs) {
            boolean needDblConfirm = false;
            long startTime = osc.m_startTime;
            long barEndTime = startTime + BAR_SIZE - 1;
            int right = timeAxe.getPoint(barEndTime);

            double val1 = osc.m_val1;
            double val2 = osc.m_val2;

            if (prevOsc != null) {
                double prevVal1 = prevOsc.m_val1;
                double prevVal2 = prevOsc.m_val2;
//System.out.println("indx="+indx+" prevVal1="+prevVal1+"; prevVal2="+prevVal2+"; val1="+val1+"; val2"+val2+"]");
                boolean crossUp = (prevVal1 <= prevVal2) && (val1 > val2);
                if(stickTopBottom && (val1 > STICK_TOP_BOTTOM_LEVEL) && (prevVal1 < STICK_TOP_BOTTOM_LEVEL)) {
                    crossUp = true;
                    stickTopBottom = false;
//System.out.println(" unSTICK from bottom");
                }
                boolean crossDown = (prevVal1 >= prevVal2) && (val1 < val2);
                if(stickTopBottom && (val1 < 1-STICK_TOP_BOTTOM_LEVEL) && (prevVal1 > 1-STICK_TOP_BOTTOM_LEVEL)) {
                    crossDown = true;
                    stickTopBottom = false;
//System.out.println(" unSTICK from top");
                }
                if (crossUp || crossDown) {
                    if(PAINT) {
                        g.setPaint(color);
                        g.drawLine(right, 0, right, HEIGHT);
                    }
                    int thisTickIndex = binarySearch(ticks, barEndTime, true); // nearest tick at right
                    if(thisTickIndex < ticks.size()) {
                        if (STICK_TOP_BOTTOM
                                && ((crossUp && (val1 < STICK_TOP_BOTTOM_LEVEL) && (val2 < STICK_TOP_BOTTOM_LEVEL))
                                    || (crossDown && (val1 > 1-STICK_TOP_BOTTOM_LEVEL) && (val2 > 1-STICK_TOP_BOTTOM_LEVEL)))) {
//System.out.println(" STICK_TOP_BOTTOM crossUp="+crossUp+"; crossDown="+crossDown);
                            stickTopBottom = true;
                        } else {
                            if( side != null ) {
                                long prevStartTime = tradeOsc.m_startTime;
                                long prevBarEndTime = prevStartTime + BAR_SIZE - 1;
                                int prevBarRight = timeAxe.getPoint(prevBarEndTime);
                                int prevTickIndex = binarySearch(ticks, prevBarEndTime, true);
                                Tick prevTick = ticks.get(prevTickIndex);
                                double prevPrice = prevTick.m_price;

                                Tick thisTick = ticks.get(thisTickIndex);
                                double thisPrice = thisTick.m_price;

                                double priceDiff = (side == OrderSide.BUY) ? thisPrice - prevPrice : prevPrice - thisPrice;
                                double ratio =  (side == OrderSide.BUY) ? thisPrice / prevPrice : prevPrice / thisPrice;
                                cumm *= ratio;
                                side = null;
// System.out.println("indx="+indx+" prev["+prevStartTime+"; "+prevBarEndTime+"; "+prevBarRight+"; "+prevTickIndex+"] this["+startTime+"; "+barEndTime+"; "+right+"; "+thisTickIndex+"]");

                                if(PAINT) {
                                    int thisY = priceAxe.getPointReverse(thisPrice);
                                    int prevY = priceAxe.getPointReverse(prevPrice);

                                    g.setPaint((priceDiff > 0) ? Color.GREEN : Color.RED);
                                    Stroke stroke = g.getStroke();
                                    g.setStroke(TR_STROKE);
                                    g.drawLine(prevBarRight+4, prevY, right-4, thisY);
                                    g.setStroke(stroke);
                                }
                            }
                        }
                        if(side == null) {
                            if (DBL_CONFIRM_IN_MIDDLE && ((val1 > 0.2) && (val1 < 0.8)) && ((val2 > 0.2) && (val2 < 0.8))) {
                                // cross in the middle - need 2 points to confirm
//System.out.println("DBL_CONFIRM_IN_MIDDLE");
//System.out.println("indx="+indx+" DBL_CONFIRM_IN_MIDDLE this["+startTime+"; "+barEndTime+"; "+right+"; "+thisTickIndex+"]");
                                needDblConfirm = false;
                                waitingDblConfirm = true;
                            } else {
//System.out.println("indx="+indx+" gotStartTrade this["+startTime+"; "+barEndTime+"; "+right+"; "+thisTickIndex+"]");
                                side = crossUp ? OrderSide.BUY : OrderSide.SELL;
                                tradeOsc = osc;
                                waitingDblConfirm = false;
                            }
                        }
                    }
                } else {
                    if (waitingDblConfirm) {
                        boolean up = prevVal1 < val1;
                        boolean down = prevVal1 > val1;
                        if ((side == null) && ((lastCrossUp && up) || (lastCrossDown && down))) {
//System.out.println("indx="+indx+" confirmedStartTrade this["+startTime+"; "+barEndTime+"; "+right+"]");
                            side = up ? OrderSide.BUY : OrderSide.SELL;
                            tradeOsc = osc;
                        }
                        waitingDblConfirm = false;
                    }
                }
                lastCrossUp = crossUp;
                lastCrossDown = crossDown;
            }
            int val1Y = oscAxe.getPointReverse(val1);
            int val2Y = oscAxe.getPointReverse(val2);

            if(PAINT) {
                g.setPaint(color);
                g.drawRect(right - MARK_DIAMETER / 2, val1Y - MARK_DIAMETER / 2, MARK_DIAMETER, MARK_DIAMETER);
                g.drawRect(right - MARK_DIAMETER / 2, val2Y - MARK_DIAMETER / 2, MARK_DIAMETER, MARK_DIAMETER);
                g.drawLine(right, val1Y, right, val2Y);
                if (prevRight != null) {
                    g.drawLine(prevRight, prevVal1Y, right, val1Y);
                    g.drawLine(prevRight, prevVal2Y, right, val2Y);
                }
                if (PAINT_VALUES) {
                    String cross = lastCrossUp ? " crossUp" : lastCrossDown ? " crossDown" : "";
                    String confirm = needDblConfirm ? " needConfirm" : "";
                    String val1Str = String.format("%1$,.3f", val1);
                    String val2Str = String.format("%1$,.3f", val2);
                    g.drawString(val1Str + cross, right + MARK_DIAMETER / 2, val1Y);
                    g.drawString(val2Str + confirm, right + MARK_DIAMETER / 2, val2Y);
                }
            }
            prevRight = right;
            prevVal1Y = val1Y;
            prevVal2Y = val2Y;
            prevOsc = osc;
            indx++;
        }
        return cumm;
    }

    private static void paintFine(List<Osc> fine, ChartAxe oscAxe, ChartAxe timeAxe, Graphics2D g) {
        for (Osc fin : fine) {
            long startTime = fin.m_startTime;
            int x = timeAxe.getPoint(startTime);

            double val1 = fin.m_val1;
            int val1Y = oscAxe.getPointReverse(val1);
            double val2 = fin.m_val2;
            int val2Y = oscAxe.getPointReverse(val2);

            g.setPaint(Color.gray);
            g.drawLine(x, val1Y, x, val1Y);
            g.setPaint(Color.lightGray);
            g.drawLine(x, val2Y, x, val2Y);
        }
    }

    private static List<Tick> selectTicks(Connection connection, long three, long end, long start, Exchange exch) throws SQLException {
        List<Tick> ticks = new ArrayList<Tick>();
        PreparedStatement statement = connection.prepareStatement(
                "   SELECT stamp, price, src, volume " +
                        "    FROM Ticks " +
                        "    WHERE stamp >= ? " +
                        "     AND stamp <= ? " +
                        "     AND src = ? " +
                        "    ORDER BY stamp ASC ");
        try {
            statement.setLong(1, start);
            statement.setLong(2, end);
            statement.setInt(3, exch.m_databaseId);

            ResultSet result = statement.executeQuery();
            long four = System.currentTimeMillis();
            System.out.println("Ticks selected in "+ Utils.millisToDHMSStr(four - three));
            try {
                while (result.next()) {
                    long stamp = result.getLong(1);
                    double price = result.getDouble(2);
                    int src = result.getInt(3);
                    double volume = result.getDouble(4);
                    PaintChart.Tick tick = new PaintChart.Tick(stamp, price, src, volume);
                    ticks.add(tick);
                }
                long five = System.currentTimeMillis();
                System.out.println("Ticks read in "+ Utils.millisToDHMSStr(five - four));
            } finally {
                result.close();
            }
        } finally {
            statement.close();
        }
        return ticks;
    }

    private static class Bar {
        private final long m_barStartTime;
        private double m_open;
        private double m_close;
        private double m_high;
        private double m_low;
        private long m_lastTickTime;

        public Bar(long barStartTime) {
            m_barStartTime = barStartTime;
        }

        public void update(Tick tick) {
            double price = tick.m_price;
            long stamp = tick.m_stamp;
            if (m_open == 0) {
                m_open = price;
            }
            m_high = m_high == 0 ? price : Math.max(m_high, price);
            m_low = m_low == 0 ? price : Math.min(m_low, price);
            if (m_close == 0) {
                m_close = price;
                m_lastTickTime = stamp;
            } else {
                if (stamp >= m_lastTickTime) {
                    m_close = price;
                    m_lastTickTime = stamp;
                }
            }
        }
    }

    private static class Osc implements Comparable<Osc>{
        private final long m_startTime;
        private final double m_val1;
        private final double m_val2;

        public Osc(long startTime, double val1, double val2) {
            m_startTime = startTime;
            m_val1 = val1;
            m_val2 = val2;
        }

        @Override public int compareTo(Osc other) {
            return Long.compare(m_startTime, other.m_startTime);
        }
    }

    private static class OscCalculator {
        private final long m_barSize;
        private Long m_currBarStart;
        private Long m_currBarEnd;
        private double m_close;
        private Double m_prevBarClose;
        private List<Double> m_gains = new ArrayList<Double>();
        private List<Double> m_losss = new ArrayList<Double>();
        private Double m_avgGain = null;
        private Double m_avgLoss = null;
        private List<Double> rsis = new ArrayList<Double>();
        private List<Double> stochs = new ArrayList<Double>();
        private List<Double> stoch1s = new ArrayList<Double>();

        private List<Osc> ret = new ArrayList<Osc>();
        private List<Osc> fine = new ArrayList<Osc>();
        private long m_barsMillisOffset;

        public OscCalculator(long barSize) {
            m_barSize = barSize;
        }

        public boolean update(Tick tick) {
            boolean newBarStarted = false;
            long stamp = tick.m_stamp;
            if (m_currBarStart == null) {
                startNewBar(stamp, null, 0);
                newBarStarted = true;
            }
            if (stamp < m_currBarEnd) { // one more tick in current bar
                m_close = tick.m_price;
                update(stamp, false);
            } else { // bar fully defined
                update(stamp, true);
                startNewBar(stamp, m_close, tick.m_price);
                newBarStarted = true;
            }
            return newBarStarted;
        }

        private void update(long stamp, boolean finishBar) {
//            StringBuilder sb = new StringBuilder();
            if (m_prevBarClose == null) {
//                if(finishBar) {
//                    sb.append(m_close).append("\t");
//                }
                return;
            }
            double change = m_close - m_prevBarClose;
            double gain = (change > 0) ? change : 0d;
            double loss = (change < 0) ? -change : 0d;
//            if(finishBar) {
//                sb.append(m_prevBarClose).append("\t");
//                sb.append(m_close).append("\t");
//                sb.append(gain).append("\t");
//                sb.append(loss).append("\t");
//            }
            Double avgGain = null;
            Double avgLoss = null;
            if ((m_avgGain == null) || !BLEND_AVG) {
                if (m_gains.size() == LEN1 - 1) {
                    avgGain = avg(m_gains, gain);
                    avgLoss = avg(m_losss, loss);
                }
                if (finishBar) {
                    m_gains.add(gain);
                    m_losss.add(loss);
                    if (m_gains.size() == LEN1) {
                        m_gains.remove(0);
                        m_losss.remove(0);
                    }
                }
            } else {
                avgGain = (m_avgGain * (LEN1 - 1) + gain) / LEN1;
                avgLoss = (m_avgLoss * (LEN1 - 1) + loss) / LEN1;
            }
            if (avgGain != null) {
                if(finishBar) {
                    m_avgGain = avgGain;
                    m_avgLoss = avgLoss;
//                    sb.append(m_avgGain).append("\t");
//                    sb.append(m_avgLoss).append("\t");
                }
                double rsi = (avgLoss == 0) ? 100 : 100 - (100 / (1 + avgGain / avgLoss));
//                if(finishBar) {
//                    sb.append(rsi).append("\t");
//                }
                if (rsis.size() == LEN2 - 1) {
                    double highest = max(rsis, rsi);
                    double lowest = min(rsis, rsi);
//                    if(finishBar) {
//                        sb.append(highest).append("\t");
//                        sb.append(lowest).append("\t");
//                    }
                    double stoch = (rsi - lowest) / (highest - lowest);
//                    if(finishBar) {
//                        sb.append(stoch).append("\t");
//                    }
                    if (stochs.size() == K - 1) {
                        double stoch1 = avg(stochs, stoch);
//                        if(finishBar) {
//                            sb.append(stoch1).append("\t");
//                        }
                        if (stoch1s.size() == D - 1) {
                            double stoch2 = avg(stoch1s, stoch1);
//                            if(finishBar) {
//                                sb.append(stoch2).append("\t");
//                            }
                            if(finishBar) {
                                Osc osc = new Osc(m_currBarStart, stoch1, stoch2);
                                ret.add(osc);
                            } else {
                                Osc osc = new Osc(stamp, stoch1, stoch2);
                                fine.add(osc);
                            }
                        }
                        if (finishBar) {
                            stoch1s.add(stoch1);
                            if (stoch1s.size() == D) {
                                stoch1s.remove(0);
                            }
                        }
                    }
                    if (finishBar) {
                        stochs.add(stoch);
                        if (stochs.size() == K) {
                            stochs.remove(0);
                        }
                    }
                }
                if (finishBar) {
                    rsis.add(rsi);
                    if (rsis.size() == LEN2) {
                        rsis.remove(0);
                    }
                }
            }
//            if (sb.length() > 0) {
//                System.out.println(sb.toString());
//            }
        }

        public List<Osc> ret() {
            update(0, true);
            return ret;
        }

        public List<Osc> fine() {
            return fine;
        }

        private void startNewBar(long stamp, Double prevBarClose, double close) {
            m_currBarStart = (stamp - m_barsMillisOffset) / m_barSize * m_barSize + m_barsMillisOffset;
            m_currBarEnd = m_currBarStart + m_barSize;
            m_prevBarClose = prevBarClose;
            m_close = close;
        }

        public void setBarsMillisOffset(long barsMillisOffset) {
            m_barsMillisOffset = barsMillisOffset;
        }
    }
}
