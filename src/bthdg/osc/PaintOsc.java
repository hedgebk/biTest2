package bthdg.osc;

import bthdg.BaseChartPaint;
import bthdg.PaintChart;
import bthdg.exch.Exchange;
import bthdg.exch.OrderSide;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PaintOsc extends BaseChartPaint {
    private static final long TIME_FRAME = Utils.toMillis("5d");
    private static final long BAR_SIZE = Utils.toMillis("30s");
    private static final Exchange EXCHANGE = Exchange.BTCN;
    public static final int LEN1 = 14;
    public static final int LEN2 = 14;
    public static final int K = 3;
    public static final int D = 3;
//    private static final Exchange EXCHANGE = Exchange.OKCOIN;
//    private static final Exchange EXCHANGE = Exchange.BTCE;
//    private static final Exchange EXCHANGE = Exchange.BITSTAMP;

    // chart area
    public static final boolean PAINT = false;
    private static final int WIDTH = 30000;
    public static final int HEIGHT = 800;
    public static final int OFFSET_BAR_PARTS = 10;
    private static final boolean DBL_CONFIRM_IN_MIDDLE = false;
    private static final boolean STICK_TOP_BOTTOM = false;
    private static final double STICK_TOP_BOTTOM_LEVEL = 0.05;
    private static final boolean PAINT_VALUES = false;

    private static final double FINE_START_DIFF_LEVEL = 0.02;
    private static final double FINE_START_DIFF_LEVEL_MUL = 4;
    private static final long FINE_AVG_TIME = BAR_SIZE * 8;
    private static final boolean NOT_SAME_DIRECTION_START_STOP = true;
    private static final long START_STOP_AVG_TIME = BAR_SIZE * 7;

    public static final int X_FACTOR = 1; // more points
    private static final int MARK_DIAMETER = 5;
    public static final BasicStroke TR_STROKE = new BasicStroke(3);

    static {
        OscCalculator.BLEND_AVG = true;
    }

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
        ChartAxe priceAxe = new PaintChart.ChartAxe(priceCalc, HEIGHT);
        PaintChart.ChartAxe oscAxe = new PaintChart.ChartAxe(0, 1, HEIGHT);
        System.out.println("time per pixel: " + Utils.millisToDHMSStr((long) timeAxe.m_scale));

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage./*TYPE_USHORT_565_RGB*/ TYPE_INT_ARGB );
        Graphics2D g = image.createGraphics();
        setupGraphics(g);

        if(PAINT) {
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
        }

        //---------------------
        Bar[] bars = calBars(ticks, minBarTimestamp, maxBarTimestamp);
        if(PAINT) {
            // paint bars
            paintBars(bars, priceAxe, timeAxe, g);
        }

        OscCalculator.SimpleOscCalculator calc = new OscCalculator.SimpleOscCalculator(LEN1, LEN2, K, D, BAR_SIZE, 0);
        calc.setCalcFine(true);
        for (Tick tick : ticks) {
            calc.update(tick.m_stamp, tick.m_price);
        }
//        List<Osc> oscs = calc.ret();
//        paintOscs(oscs, oscAxe, timeAxe, g, Color.GRAY/*MAGENTA*/, ticks, priceAxe);
        List<OscTick> fine = calc.fine();
        paintFine(fine, oscAxe, timeAxe, g, ticks, priceAxe);

//        List<Osc> cont = calcCont(ticks);
//        paintFine(cont, oscAxe, timeAxe, g);

//        List<Osc> forBars = calcForBars(ticks, minBarTimestamp, maxBarTimestamp, 0);
//        List<Osc> forBars = new ArrayList<Osc>();

        calcAndPaint(ticks, minBarTimestamp, maxBarTimestamp, timeAxe, priceAxe, oscAxe, g);

//        Collections.sort(forBars);
//        paintOscs(forBars, oscAxe, timeAxe, g, 0);

        g.dispose();

        if (PAINT) {
            writeAndShowImage(image);
        }
    }

    private static void calcAndPaint(List<Tick> ticks, long minBarTimestamp, long maxBarTimestamp, ChartAxe timeAxe, ChartAxe priceAxe, ChartAxe oscAxe, Graphics2D g) {
        Color[] colors = new Color[]{Color.ORANGE, Color.BLUE, Color.MAGENTA, Color.PINK, Color.CYAN, Color.GRAY, Color.YELLOW, Color.GREEN};
        long step = BAR_SIZE / OFFSET_BAR_PARTS;
        int index = 0;
        double cummCumm = 0;
        for (long offset = 0; offset < BAR_SIZE; offset += step) {
            List<OscTick> forBars2 = calcForBars(ticks, minBarTimestamp, maxBarTimestamp, offset);
            Color color = colors[(index++) % colors.length];
            double cumm = paintOscs(forBars2, oscAxe, timeAxe, g, color, ticks, priceAxe);
            System.out.println("cumm = " + cumm);
            cummCumm += cumm;
        }
        cummCumm /= index;
        double timeFrameDays = ((double) TIME_FRAME) / Utils.ONE_DAY_IN_MILLIS;
        double pow = 1.0 / timeFrameDays;
        double aDay = Math.pow(cummCumm, pow);
        System.out.println("cummCumm = " + cummCumm + "timeFrameDays = " + timeFrameDays + " /day = " + aDay);
    }

    private static List<OscTick> calcForBars(List<Tick> ticks, long minBarTimestamp, long maxBarTimestamp, long timeOffset) {
//        System.out.println("----------------------------------------------------------------------");
        OscCalculator.SimpleOscCalculator calc = new OscCalculator.SimpleOscCalculator(LEN1, LEN2, K, D, BAR_SIZE, timeOffset);

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
                boolean newBarStarted = calc.update(tick.m_stamp, tick.m_price);
                if (!newBarStarted) {
                    throw new RuntimeException("newBar NOT Started. closeTicks index=" + i + "; tick=" + tick);
                }
            }
            i++;
        }
        return calc.ret();
    }

    private static List<OscTick> calcCont(List<Tick> ticks) {
        List<OscTick> ret = new ArrayList<OscTick>();
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
            long barsMillisOffset = oscTickTime + 1 - (oscTickTime + 1) / BAR_SIZE * BAR_SIZE;
            OscCalculator.SimpleOscCalculator calc = new OscCalculator.SimpleOscCalculator(LEN1, LEN2, K, D, BAR_SIZE, barsMillisOffset);
            for (int i = 0; i < closeTicks.length; i++) {
                Tick closeTick = closeTicks[i];
                boolean newBarStarted = calc.update(closeTick.m_stamp, closeTick.m_price);
                if (!newBarStarted) {
                    throw new RuntimeException("newBar NOT Started. closeTicks index=" + i);
                }
            }
            List<OscTick> oscs = calc.ret();
            if (oscs.size() != 1) {
                throw new RuntimeException("oscs.size()!=1");
            }
            OscTick osc = oscs.get(0);
            ret.add(osc);
        }
        return ret;
    }

    private static int binarySearch(List<Tick> ticks, long stamp, boolean ceil) {
        int i = Collections.binarySearch(ticks, stamp);
        if (i < 0) {
            int insertionPoint = -i - 1;
            if (ceil) {
                i = insertionPoint;
            } else {
                i = insertionPoint - 1;
            }
        }
        return i;
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

                if (PAINT) {
                    g.setPaint(Colors.TRANSP_GRAY);
                    g.drawLine(left, 0, left, HEIGHT);
                    g.fillRect(left, highY, right - left, lowY - highY);
                }

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

    private static double paintOscs(List<OscTick> oscs, ChartAxe oscAxe, ChartAxe timeAxe, Graphics2D g, Color color, List<Tick> ticks, ChartAxe priceAxe) {
//        System.out.println("------------------------------------------------");
        Integer prevRight = null;
        Integer prevVal1Y = null;
        Integer prevVal2Y = null;

        boolean lastCrossUp = false;
        boolean lastCrossDown = false;
        double cummDiff = 0.0;
        double cummRatio = 1.0;
        OrderSide side = null;
        OscTick tradeOsc = null;
        OscTick prevOsc = null;
        int indx = 0;
        boolean waitingDblConfirm = false;
        boolean stickTopBottom = false;
        for (OscTick osc : oscs) {
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
                                cummDiff += priceDiff;
                                double ratio =  (side == OrderSide.BUY) ? thisPrice / prevPrice : prevPrice / thisPrice;
                                cummRatio *= ratio;
                                side = null;
// System.out.println("indx="+indx+" prev["+prevStartTime+"; "+prevBarEndTime+"; "+prevBarRight+"; "+prevTickIndex+"] this["+startTime+"; "+barEndTime+"; "+right+"; "+thisTickIndex+"]");

                                if (PAINT) {
                                    int thisY = priceAxe.getPointReverse(thisPrice);
                                    int prevY = priceAxe.getPointReverse(prevPrice);

                                    boolean positive = (priceDiff > 0);
                                    g.setPaint(positive ? Color.GREEN : Color.RED);
                                    Stroke stroke = g.getStroke();
                                    g.setStroke(TR_STROKE);
                                    g.drawLine(prevBarRight + 4, prevY, right - 4, thisY);
                                    g.setStroke(stroke);

                                    g.setPaint(Color.GRAY);
                                    int fontSize = g.getFont().getSize();
                                    String label = String.format("%1$,.4f", priceDiff);
                                    g.drawString(label, right, thisY - fontSize);
//                                    String label2 = String.format("%1$,.5f", ratio);
//                                    g.drawString(label2, right, thisY - 2 * fontSize);
                                    String label3 = String.format("%1$,.5f", cummDiff);
                                    g.drawString(label3, right, thisY - 2 * fontSize);
//                                    String label4 = String.format("%1$,.5f", cummRatio);
//                                    g.drawString(label4, right, thisY - 4 * fontSize);
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
        return cummRatio;
    }

    private static void paintFine(List<OscTick> fine, ChartAxe oscAxe, ChartAxe timeAxe, Graphics2D g, List<Tick> ticks, ChartAxe priceAxe) {
        Utils.AverageCounter avgCounter1 = new Utils.FadingAverageCounter(FINE_AVG_TIME);
        Utils.AverageCounter avgCounter2 = new Utils.FadingAverageCounter(FINE_AVG_TIME);
        Utils.AverageCounter valCounter1 = new Utils.FadingAverageCounter(START_STOP_AVG_TIME);
        Utils.AverageCounter valCounter2 = new Utils.FadingAverageCounter(START_STOP_AVG_TIME);
        int prevVal1Y = 0;
        int prevVal2Y = 0;
        int prevVal1Yavg = 0;
        int prevVal2Yavg = 0;
        Integer prevX = null;
        FineState state = FineState.NONE;
        for (OscTick fin : fine) {
            long startTime = fin.m_startTime;
            int x = timeAxe.getPoint(startTime);

            double fine1 = fin.m_val1;
            double fine2 = fin.m_val2;

            double val1 = valCounter1.add(startTime, fine1);
            double val2 = valCounter2.add(startTime, fine2);
            int val1Y = oscAxe.getPointReverse(val1);
            int val2Y = oscAxe.getPointReverse(val2);

            double val1avg = avgCounter1.add(startTime, fine1);
            double val2avg = avgCounter2.add(startTime, fine2);
            int val1Yavg = oscAxe.getPointReverse(val1avg);
            int val2Yavg = oscAxe.getPointReverse(val2avg);

            if (PAINT) {
                int fine1Y = oscAxe.getPointReverse(fine1);
                int fine2Y = oscAxe.getPointReverse(fine2);
                g.setPaint(fine1Y > fine2Y ? Color.pink : Colors.TRANSP_LIGHT_CYAN);
                g.drawLine(x, fine1Y, x, fine2Y);

                if (prevX != null) {
                    g.setPaint(Colors.DARK_BLUE);
                    g.drawLine(prevX, prevVal1Yavg, x, val1Yavg);
                    g.setPaint(Colors.DARK_GREEN);
                    g.drawLine(prevX, prevVal2Yavg, x, val2Yavg);

                    g.setPaint(Color.lightGray);
                    g.drawLine(prevX, prevVal1Y, x, val1Y);
                    g.drawLine(prevX, prevVal2Y, x, val2Y);
                }
            }

            prevX = x;
            prevVal1Y = val1Y;
            prevVal2Y = val2Y;
            prevVal1Yavg = val1Yavg;
            prevVal2Yavg = val2Yavg;

            state = state.process(val1, val2, val1avg, val2avg, startTime, ticks, g, timeAxe, priceAxe, oscAxe);
        }
        double totalRatio = FineState.s_totalRatio;
        double timeFrameDays = ((double) TIME_FRAME) / Utils.ONE_DAY_IN_MILLIS;
        double pow = 1.0 / timeFrameDays;
        double aDay = Math.pow(totalRatio, pow);
        System.out.println("fine:  totalRatio = " + totalRatio + "; timeFrameDays = " + timeFrameDays + "; /day = " + aDay);
    }

    private static enum FineState {
        NONE {
            @Override public FineState process(double val1, double val2, double val1avg, double val2avg, long startTime, List<Tick> ticks,
                                               Graphics2D g, ChartAxe timeAxe, ChartAxe priceAxe, ChartAxe oscAxe) {
                double diffAvg = val2avg - val1avg;
                double diff = val2 - val1;
                if ((diffAvg > startLevel(val1avg, val2avg)
                    && (!NOT_SAME_DIRECTION_START_STOP || (diffAvg + diff > 0)))) {
                    Tick tick = getNextTick(startTime, ticks);
                    start(tick, OrderSide.SELL, g, timeAxe, oscAxe, val1avg, val2avg);
                    return DOWN;
                }
                if ((-diffAvg > startLevel(val1avg, val2avg)
                    && (!NOT_SAME_DIRECTION_START_STOP || (diffAvg + diff < 0)))) {
                    Tick tick = getNextTick(startTime, ticks);
                    start(tick, OrderSide.BUY, g, timeAxe, oscAxe, val1avg, val2avg);
                    return UP;
                }
                return this;
            }
        },
        UP {
            @Override public FineState process(double val1, double val2, double val1avg, double val2avg, long startTime, List<Tick> ticks,
                                               Graphics2D g, ChartAxe timeAxe, ChartAxe priceAxe, ChartAxe oscAxe) {
                double diffAvg = val2avg - val1avg;
                double diff = val2 - val1;
                boolean reverseDiff = diffAvg > FINE_START_DIFF_LEVEL;
                boolean notSameDirStop = NOT_SAME_DIRECTION_START_STOP && (diffAvg + diff > FINE_START_DIFF_LEVEL/2);
                if (reverseDiff || notSameDirStop) {
                    Tick tick = getNextTick(startTime, ticks);
                    finish(tick, g, timeAxe, priceAxe, oscAxe, reverseDiff, val1avg, val2avg, notSameDirStop, val1, val2);
                    return NONE;
                }
                return this;
            }
        },
        DOWN {
            @Override public FineState process(double val1, double val2, double val1avg, double val2avg, long startTime, List<Tick> ticks,
                                               Graphics2D g, ChartAxe timeAxe, ChartAxe priceAxe, ChartAxe oscAxe) {
                double diffAvg = val2avg - val1avg;
                double diff = val2 - val1;
                boolean reverseDiff = -diffAvg > FINE_START_DIFF_LEVEL;
                boolean notSameDirStop = NOT_SAME_DIRECTION_START_STOP && (diffAvg + diff < -FINE_START_DIFF_LEVEL/2);
                if (reverseDiff || notSameDirStop) {
                    Tick tick = getNextTick(startTime, ticks);
                    finish(tick, g, timeAxe, priceAxe, oscAxe, reverseDiff, val1avg, val2avg, notSameDirStop, val1, val2);
                    return NONE;
                }
                return this;
            }
        };

        private static double startLevel(double val1avg, double val2avg) {
            double mid = (val1avg + val2avg) / 2;       // [0   ... 0.5 ... 1  ]
            double centerToMid = Math.abs(mid - 0.5);   // [0.5 ... 0   ... 0.5]
            double ratio = FINE_START_DIFF_LEVEL_MUL - ((FINE_START_DIFF_LEVEL_MUL-1)*2) * centerToMid; // [1   ... 3   ... 1  ]
            return FINE_START_DIFF_LEVEL * ratio;
        }

        private static Tick s_startTick;
        private static OrderSide s_orderSide;
        public static double s_totalRatio = 1;
        public static double s_totalGain = 0;

        public FineState process(double val1, double val2, double val1avg, double val2avg, long startTime, List<Tick> ticks,
                                 Graphics2D g, ChartAxe timeAxe, ChartAxe priceAxe, ChartAxe oscAxe) { return this; }

        private static void start(Tick tick, OrderSide orderSide, Graphics2D g, ChartAxe timeAxe, ChartAxe oscAxe, double val1avg, double val2avg) {
            s_startTick = tick;
            s_orderSide = orderSide;
            if (PAINT) {
                g.setPaint((orderSide == OrderSide.BUY) ? Colors.LIGHT_CYAN : Color.pink);
                int x = timeAxe.getPoint(tick.m_stamp);
                g.drawLine(x, 0, x, HEIGHT);

                int val1avgY = oscAxe.getPointReverse(val1avg);
                int val2avgY = oscAxe.getPointReverse(val2avg);
                g.setPaint(Color.RED);
                g.drawRect(x-1, Math.min(val1avgY, val2avgY), 2, Math.abs(val1avgY - val2avgY));
            }
        }

        private static void finish(Tick tick, Graphics2D g, ChartAxe timeAxe, ChartAxe priceAxe, ChartAxe oscAxe,
                                   boolean reverseDiff, double val1avg, double val2avg,
                                   boolean notSameDirStop, double val1, double val2) {
            double startPrice = s_startTick.m_price;
            double finishPrice = tick.m_price;
            double gain = (s_orderSide == OrderSide.BUY) ? finishPrice - startPrice : startPrice - finishPrice;
            double ratio = (s_orderSide == OrderSide.BUY) ? finishPrice / startPrice : startPrice / finishPrice;
            s_totalGain += gain;
            s_totalRatio *= ratio;

            if (PAINT) {
                g.setPaint((s_orderSide == OrderSide.BUY) ? Colors.LIGHT_CYAN : Color.pink);
                int finishX = timeAxe.getPoint(tick.m_stamp);
                g.drawLine(finishX, 0, finishX, HEIGHT);

                int startX = timeAxe.getPoint(s_startTick.m_stamp);
                int startY = priceAxe.getPointReverse(startPrice);
                int finishY = priceAxe.getPointReverse(finishPrice);
                g.setPaint((gain > 0) ? Color.green : Color.red);
                g.drawLine(startX, startY, finishX, finishY);

                g.setPaint(Color.MAGENTA);
                int fontSize = g.getFont().getSize();
                String label = String.format("%1$,.4f", gain);
                g.drawString(label, finishX, finishY + 2 * fontSize);
//                String label2 = String.format("%1$,.5f", ratio);
//                g.drawString(label2, finishX, finishY + 3 * fontSize);
                String label3 = String.format("%1$,.5f", s_totalGain);
                g.drawString(label3, finishX, finishY + 3 * fontSize);
//                String label4 = String.format("%1$,.5f", s_totalRatio);
//                g.drawString(label4, finishX, finishY + 5 * fontSize);

                if (reverseDiff) {
                    int val1avgY = oscAxe.getPointReverse(val1avg);
                    int val2avgY = oscAxe.getPointReverse(val2avg);
                    g.setPaint(Color.RED);
                    g.drawRect(finishX - 1, Math.min(val1avgY, val2avgY), 2, Math.abs(val1avgY - val2avgY));
                } else if (notSameDirStop) {
                    int val1Y = oscAxe.getPointReverse(val1);
                    int val2Y = oscAxe.getPointReverse(val2);
                    g.setPaint(Color.RED);
//                    g.drawRect(finishX - 1, Math.min(val1Y, val2Y), 2, Math.abs(val1Y - val2Y));
                    g.drawLine(finishX, val1Y, finishX + 3, val1Y + 3);
                    g.drawLine(finishX, val1Y, finishX - 3, val1Y + 3);
                    g.drawLine(finishX, val2Y, finishX + 3, val2Y - 3);
                    g.drawLine(finishX, val2Y, finishX - 3, val2Y - 3);

//                    double diffAvg = val2avg - val1avg;
//                    double diff = val2 - val1;
//                    String labelX = String.format("diff=%1$,.5f; diffAvg=%2$,.5f", diff, diffAvg);
//                    g.drawString(labelX, finishX, finishY + 4 * fontSize);
                }
            }
            s_startTick = null;
            s_orderSide = null;
        }

        private static Tick getNextTick(long startTime, List<Tick> ticks) {
            int tickIndex = binarySearch(ticks, startTime, true);
            Tick tick = ticks.get(tickIndex);
            return tick;
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

}
