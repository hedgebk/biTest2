package bthdg;

import bthdg.exch.Exchange;
import bthdg.util.Utils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// - CALC COMMISSION BASED ON each TRADE - not by average trade price
// - check fading moving average
public class PaintChart extends BaseChartPaint {
    private static VaryProfile PROFILE = VaryProfile.NONE;
//    private static VaryProfile PROFILE = VaryProfile.AVG_GAIN_1;
//    private static VaryProfile PROFILE = VaryProfile.DROP;
//    private static VaryProfile PROFILE = VaryProfile.AVG_GAIN_2;

    public static final ExchangePair PAIR = ExchangePair.BTCN_OKCOIN;       // 1d  236     2d  51     7d  11      14d  34

//    public static final ExchangePair PAIR = ExchangePair.BITSTAMP_BTCE;     // 1d  1.49     2d  1.42
//    public static final ExchangePair PAIR = ExchangePair.BITSTAMP_CAMPBX;   // 1d  1

//    public static final ExchangePair PAIR = ExchangePair.BTCE_BITFINEX;     // 1d  1.4      2d  1.22
//    public static final ExchangePair PAIR = ExchangePair.BITSTAMP_BITFINEX; // 1d  1.3

//    public static final ExchangePair PAIR = ExchangePair.BITSTAMP_HITBTC;     // 1d 2.04
//    public static final ExchangePair PAIR = ExchangePair.BTCE_HITBTC;         // 1d  2.8   2d  2.73
//    public static final ExchangePair PAIR = ExchangePair.BITFINEX_HITBTC;     // 1d  1.3

//    public static final ExchangePair PAIR = ExchangePair.BITSTAMP_LAKEBTC;
//    public static final ExchangePair PAIR = ExchangePair.BTCE_LAKEBTC;
//    public static final ExchangePair PAIR = ExchangePair.BITFINEX_LAKEBTC;
//    public static final ExchangePair PAIR = ExchangePair.HITBTC_LAKEBTC;

//    public static final ExchangePair PAIR = ExchangePair.BITSTAMP_ITBIT;      // 1d  1.3
//    public static final ExchangePair PAIR = ExchangePair.BTCE_ITBIT;

    private static Vary VARY = Vary.NONE;
//    private static Vary VARY = Vary.MOVING_AVERAGE_LEN_AND_EXPECTED_GAIN;
//    private static Vary VARY = Vary.DROP;
    public static int STEP_RATIO =     1; // >1 to less accurate calc
    public static int DISTANCE_RATIO = 1; // >1 less distance calc

    private static final int PERIOD_END_OFFSET_DAYS = 0; // minus days from last tick
    public static final double PERIOD_LENGTH_DAYS = 1.0; // the period width - days
    private static final long MOVING_AVERAGE_MILLIS = PAIR.m_movingAverage;
    private static final double EXPECTED_GAIN = PAIR.m_expectedGain;
    private static final Exchange EXCH1 = PAIR.m_exch1;
    private static final Exchange EXCH2 = PAIR.m_exch2;
    private static final boolean VOLUME_AVERAGE = true;
    // chart area
    public static final int X_FACTOR = 1; // more points
    public static final int WIDTH_HEIGHT_RATIO_EXTRA = 6; // more points on X axe
                                                                                 // note: better simulation when time per pixel: 25sec
    private static final int WIDTH = (int)(1680 * X_FACTOR * WIDTH_HEIGHT_RATIO_EXTRA * (PERIOD_LENGTH_DAYS * 2)); // PERIOD_LENGTH_DAYS: 30->60; 45->90; 60->120; 90->200
    public static final int HEIGHT = 1000 * X_FACTOR * 2;
    static final boolean PAINT_PRICE = false;
    public static final int MAX_CHART_DELTA = 60;
    public static final int MIN_CHART_DELTA = -45;
    // drop
    public static final boolean DO_DROP = true;
    public static final double DROP_LEVEL = PAIR.m_dropLevel;
    public static final boolean LOCK_DIRECTION_ON_DROP = false;
    public static final boolean DROP_ONLY_IN_REVERSE_FROM_AVG = true;

    static {
        if (PROFILE != null) {
            VARY = PROFILE.m_vary;
            STEP_RATIO = PROFILE.m_stepRatio;
            DISTANCE_RATIO = PROFILE.m_distanceRatio;
        }
    }

    static final boolean PAINT_DIFF = (VARY == Vary.NONE);

    private static final boolean VARY_MOVING_AVERAGE_LEN = (VARY == Vary.MOVING_AVERAGE_LEN || VARY == Vary.MOVING_AVERAGE_LEN_AND_EXPECTED_GAIN );
    private static final int MOVING_AVERAGE_VARY_STEPS = 120/DISTANCE_RATIO;
    private static final double MOVING_AVERAGE_VARY_STEPS_VALUE = 0.01*STEP_RATIO;

    private static final boolean VARY_EXPECTED_GAIN = (VARY == Vary.EXPECTED_GAIN || VARY == Vary.MOVING_AVERAGE_LEN_AND_EXPECTED_GAIN);
    private static final int EXPECTED_GAIN_VARY_STEPS = 124/DISTANCE_RATIO;
    private static final double EXPECTED_GAIN_VARY_STEPS_VALUE = 0.025*STEP_RATIO;

    private static final boolean VARY_DROP = (VARY == Vary.DROP);
    private static final int DROP_VARY_STEPS = 100/DISTANCE_RATIO;
    private static final double DROP_VARY_STEPS_VALUE = 0.005*STEP_RATIO;

    public static final int MIN_CONFIRMED_DIFFS = 2;
    public static final int MAX_NEXT_POINTS_TO_CHECK = 10; // look to next points for prices to confirm

    public static final DecimalFormat XX_YYYYY = new DecimalFormat("#,##0.0####");

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = System.currentTimeMillis();
        System.out.println("timeMills: " + millis);
        long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("maxMemory: " + maxMemory + ", k:" + (maxMemory /= 1024) + ": m:" + (maxMemory /= 1024));

        paint();

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void paint() {
        IDbRunnable runnable = new IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                long now = System.currentTimeMillis();
                long end = now - PERIOD_END_OFFSET_DAYS * Utils.ONE_DAY_IN_MILLIS;
                long start = end - (int)(PERIOD_LENGTH_DAYS * Utils.ONE_DAY_IN_MILLIS);

                System.out.println("selecting ticks");
                List<Tick> ticks = selectTicks(connection, now, end, start, EXCH1, EXCH2);

                drawTicks(ticks, EXCH1, EXCH2);

                System.out.println("--- Complete ---");
            }
        };

        goWithDb(runnable);
    }

    private static void drawTicks(List<Tick> ticks, final Exchange exch1, final Exchange exch2) {
        final int exch1id = exch1.m_databaseId;
        long one = System.currentTimeMillis();
        int ticksNum = ticks.size();
        System.out.println("ticks count = " + ticksNum);

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

        long timeDiff = maxTimestamp - minTimestamp;
        double priceDiff = maxPrice - minPrice;
        System.out.println("min timestamp: " + minTimestamp + ", max timestamp: " + maxTimestamp + ", timestamp diff: " + timeDiff);
        System.out.println("minPrice = " + minPrice + ", maxPrice = " + maxPrice + ", priceDiff = " + priceDiff);

        Map<Long, Double> difMap = calculatePriceDiffs(ticks, exch1id, exch2.m_databaseId);
        long two = System.currentTimeMillis();
        System.out.println("PriceDiffs calculated in "+ Utils.millisToDHMSStr(two - one));

        Utils.DoubleMinMaxCalculator<Double> priceDifCalc = new Utils.DoubleMinMaxCalculator<Double>(difMap.values()) {
            @Override public Double getValue(Double priceDif) { return priceDif; }
        };
        double minDif = priceDifCalc.m_minValue;
        double maxDif = priceDifCalc.m_maxValue;
        if (maxDif > MAX_CHART_DELTA) {
            System.out.println("maxDif=" + maxDif + " -> limiting to " + MAX_CHART_DELTA);
            maxDif = MAX_CHART_DELTA;
        }
        if (minDif < MIN_CHART_DELTA) {
            System.out.println("minDif=" + minDif + " -> limiting to " + MIN_CHART_DELTA);
            minDif = MIN_CHART_DELTA;
        }

        double priceDifSum = 0;
        for(double priceDif: difMap.values()) {
            priceDifSum += priceDif;
        }

        double difDiff = maxDif-minDif;
        double averagePriceDif = priceDifSum / difMap.size();
        System.out.println("min priceDif: " + minDif + ", max priceDif: " + maxDif + ", priceDif diff: " + difDiff + ", averagePriceDif = " + averagePriceDif);

        ChartAxe timeAxe = new ChartAxe(minTimestamp, maxTimestamp, WIDTH);
        ChartAxe priceAxe = new ChartAxe(minPrice, maxPrice, HEIGHT);
        ChartAxe difAxe = new ChartAxe(minDif, maxDif, HEIGHT);
        String timePpStr = "time per pixel: " + Utils.millisToDHMSStr((long) timeAxe.m_scale);
        System.out.println(timePpStr);

        TickList[] ticksPerPoints = PAINT_PRICE ? calculateTicksPerPoints(ticks, timeAxe) : null;

        // older first
        PriceDiffList[] diffsPerPoints = calculateDiffsPerPoints(difMap, timeAxe);

        BufferedImage image = (PAINT_PRICE || PAINT_DIFF)
                ? new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_USHORT_565_RGB /*TYPE_INT_ARGB*/ )
                : new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB );
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

        if (PAINT_PRICE) {
            // paint left axe
            paintLeftAxeAndGrid(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, WIDTH);

            // paint candles
            paintCandles(ticksPerPoints, priceAxe, g);

            // paint points
            paintPoints(ticksPerPoints, priceAxe, g, exch1id);
        }

        double avgPrice1 = calculateAveragePrice(ticks, exch1);
        double exch1commission = avgPrice1 * exch1.m_baseFee;
        double avgPrice2 = calculateAveragePrice(ticks, exch2);
        double exch2commission = avgPrice2 * exch2.m_baseFee;
        double runComission = 2 * (exch1commission + exch2commission);
        double targetDelta = runComission + EXPECTED_GAIN;
        System.out.println("runComission=" + runComission + ", targetDelta=" + targetDelta);
        double halfTargetDelta = targetDelta / 2;
        double halfRunCommission = runComission / 2;
        double avgPrice = (avgPrice1 + avgPrice2) / 2;

        // older first
        int movingAveragePoints = (int)(MOVING_AVERAGE_MILLIS /timeAxe.m_scale);
        double[] movingAverage = calculateMovingAverage(diffsPerPoints, movingAveragePoints);

        if (PAINT_DIFF) {
            // paint pricediffs
            paintPriceDiffs(diffsPerPoints, difAxe, g, movingAverage, minDif, maxDif, halfTargetDelta, halfRunCommission);

            // paint price diff moving average
            paintPriceDiffMovingAverage(difAxe, g, movingAverage, halfTargetDelta, halfRunCommission);
        }

        if (VARY_MOVING_AVERAGE_LEN && VARY_EXPECTED_GAIN) {
            double max = 0;
            long bestMillis = 0;
            double bestGain = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = -MOVING_AVERAGE_VARY_STEPS; i <= MOVING_AVERAGE_VARY_STEPS; i++) {
                long movingAverageMillis = MOVING_AVERAGE_MILLIS + (long) (i * MOVING_AVERAGE_VARY_STEPS_VALUE * MOVING_AVERAGE_MILLIS);
                if (movingAverageMillis < 60000) {
                    continue;
                }
                movingAveragePoints = (int) (movingAverageMillis / timeAxe.m_scale);
                movingAverage = calculateMovingAverage(diffsPerPoints, movingAveragePoints);

                for (int j = -EXPECTED_GAIN_VARY_STEPS; j <= EXPECTED_GAIN_VARY_STEPS; j++) {
                    double expectedGain = EXPECTED_GAIN + j * EXPECTED_GAIN_VARY_STEPS_VALUE;
                    if( expectedGain > 0 ) {
                        halfTargetDelta = (runComission + expectedGain) / 2;

                        double complex1m = new ChartSimulator().simulate(diffsPerPoints, difAxe, g, movingAverage, halfTargetDelta, runComission, avgPrice, DROP_LEVEL);
//                    sb.append("\"" + Utils.millisToDHMSStr(movingAverageMillis) + "\"\t\"" + expectedGain + "\"\t\"" + complex1m + "\"\n");
                        if (complex1m > max) {
                            bestGain = expectedGain;
                            bestMillis = movingAverageMillis;
                            max = complex1m;
                        }
                    }
                }
            }
//            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
//            System.out.println(sb.toString());
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            System.out.println("Best moving average=" + Utils.millisToDHMSStr(bestMillis) + "; Best expected gain=" + bestGain + "; 1M gain=" + max);
        } else if (VARY_MOVING_AVERAGE_LEN) {
            double max = 0;
            long bestMillis = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = -MOVING_AVERAGE_VARY_STEPS; i <= MOVING_AVERAGE_VARY_STEPS; i++) {
                long movingAverageMillis = MOVING_AVERAGE_MILLIS + (long) (i * MOVING_AVERAGE_VARY_STEPS_VALUE * MOVING_AVERAGE_MILLIS);
                if (movingAverageMillis < 60000) {
                    continue;
                }
                movingAveragePoints = (int) (movingAverageMillis / timeAxe.m_scale);
                movingAverage = calculateMovingAverage(diffsPerPoints, movingAveragePoints);
                double complex1m = new ChartSimulator().simulate(diffsPerPoints, difAxe, g, movingAverage, halfTargetDelta, runComission, avgPrice, DROP_LEVEL);
                sb.append("\"" + Utils.millisToDHMSStr(movingAverageMillis) + "\"\t\"" + complex1m + "\"\n");
                if (complex1m > max) {
                    max = complex1m;
                    bestMillis = movingAverageMillis;
                }
            }
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            System.out.println(sb.toString());
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            System.out.println("Best moving average=" + Utils.millisToDHMSStr(bestMillis) + "; 1M gain=" + max);
        } else if (VARY_EXPECTED_GAIN) {
            double max = 0;
            double bestGain = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = -EXPECTED_GAIN_VARY_STEPS; i <= EXPECTED_GAIN_VARY_STEPS; i++) {
                double expectedGain = EXPECTED_GAIN + i * EXPECTED_GAIN_VARY_STEPS_VALUE;
                halfTargetDelta = (runComission + expectedGain) / 2;
                double complex1m = new ChartSimulator().simulate(diffsPerPoints, difAxe, g, movingAverage, halfTargetDelta, runComission, avgPrice, DROP_LEVEL);
                sb.append("\"" + expectedGain + "\"\t\"" + complex1m + "\"\n");
                if (complex1m > max) {
                    bestGain = expectedGain;
                    max = complex1m;
                }
            }
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            System.out.println(sb.toString());
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            System.out.println("Best expected gain=" + bestGain + "; 1M gain=" + max);
        } else if (DO_DROP && VARY_DROP) {
            double max = 0;
            double bestDrop = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = -DROP_VARY_STEPS; i <= DROP_VARY_STEPS; i++) {
                double dropLevel = DROP_LEVEL + i * DROP_VARY_STEPS_VALUE;
                double complex1m = new ChartSimulator().simulate(diffsPerPoints, difAxe, g, movingAverage, halfTargetDelta, runComission, avgPrice, dropLevel);
                sb.append("\"" + dropLevel + "\"\t\"" + complex1m + "\"\n");
                if (complex1m > max) {
                    bestDrop = dropLevel;
                    max = complex1m;
                }
            }
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            System.out.println(sb.toString());
            System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
            System.out.println("Best drop level=" + bestDrop + "; 1M gain=" + max);
        } else {
            new ChartSimulator().simulate(diffsPerPoints, difAxe, g, movingAverage, halfTargetDelta, runComission, avgPrice, DROP_LEVEL);
        }

        if (PAINT_PRICE) {
            // paint left axe labels
            paintLeftAxeLabels(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, X_FACTOR);
        }

        if (PAINT_DIFF) {
            // paint right axe labels
            paintRightAxeLabels(minDif, maxDif, difAxe, g, WIDTH, 5, X_FACTOR, 0);
        }

        // paint time axe labels
        paintTimeAxeLabels(minTimestamp, maxTimestamp, timeAxe, g, HEIGHT, X_FACTOR);

        System.out.println("movingAverage:" + Utils.millisToDHMSStr(MOVING_AVERAGE_MILLIS));

        if (PAINT_DIFF) {
            // paint moving average time range
            g.drawLine(6, 6, 6 + movingAveragePoints, 6);
            g.setFont(g.getFont().deriveFont(12.0f * X_FACTOR));
            String str = timePpStr + "\n movingAverage:" + Utils.millisToDHMSStr(MOVING_AVERAGE_MILLIS);
            FontMetrics fontMetrics = g.getFontMetrics();
            Rectangle2D bounds = fontMetrics.getStringBounds(str, g);
            g.drawString(str, 45, (int) (bounds.getHeight() * 1.1));
        }

        paintLegend(exch1, exch2, g);

        g.dispose();

        if (PAINT_PRICE || PAINT_DIFF) {
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
    }

    private static double calculateAveragePrice(List<Tick> ticks, final Exchange exch) {
        return new Utils.DoubleAverageCalculator<Tick>() {
                @Override public double getDoubleValue(Tick tick) { return tick.m_price; }
                @Override protected double getWeight(Tick tick) {
                    return tick.m_src == exch.m_databaseId ? tick.m_volume : 0;
                }
            }.getAverage(ticks);
    }

    private static void paintLegend(Exchange exch1, Exchange exch2, Graphics2D g) {
        g.setFont(g.getFont().deriveFont(7.0f* X_FACTOR));

        FontMetrics fontMetrics = g.getFontMetrics();
        Rectangle2D bounds = fontMetrics.getStringBounds("XXX", g);

        int x = WIDTH - (int)bounds.getWidth();
        bounds = fontMetrics.getStringBounds(exch2.m_name, g);
        x -= bounds.getWidth();
        g.setPaint(Color.blue);
        g.drawString(exch2.m_name, x, 40);

        bounds = fontMetrics.getStringBounds(" vs ", g);
        x -= bounds.getWidth();
        g.setPaint(Color.black);
        g.drawString(" vs ", x, 40);

        bounds = fontMetrics.getStringBounds(exch1.m_name, g);
        x -= bounds.getWidth();
        g.setPaint(Color.red);
        g.drawString(exch1.m_name, x, 40);
    }

    // return older first
    private static TickList[] calculateTicksPerPoints(List<Tick> ticks, ChartAxe timeAxe) {
        TickList[] ticksPerPoints = new TickList[WIDTH];
        for(Tick tick: ticks) {
            long stamp = tick.m_stamp;
            int x = timeAxe.getPoint(stamp);
            if (x >= WIDTH) {
                x = WIDTH - 1;
                System.out.println("pixelIndex adjusted");
            }
            TickList pixelTicks = ticksPerPoints[x];
            if (pixelTicks == null) {
                pixelTicks = new TickList();
                ticksPerPoints[x] = pixelTicks;
            }
            pixelTicks.add(tick);
        }
        return ticksPerPoints;
    }

    // return older first
    private static PriceDiffList[] calculateDiffsPerPoints(Map<Long, Double> difMap, ChartAxe timeAxe) {
        PriceDiffList[] diffsPerPoints = new PriceDiffList[WIDTH];
        for (Map.Entry<Long, Double> entry : difMap.entrySet()) {
            long stamp = entry.getKey();
            double pDiff = entry.getValue();
            int pixelIndex = timeAxe.getPoint(stamp);
            PriceDiffList pixelDiffs = diffsPerPoints[pixelIndex];
            if (pixelDiffs == null) {
                pixelDiffs = new PriceDiffList();
                diffsPerPoints[pixelIndex] = pixelDiffs;
            }
            pixelDiffs.add(pDiff);
        }
        return diffsPerPoints;
    }

    private static void paintCandles(TickList[] ticksPerPoints, ChartAxe priceAxe, Graphics2D g) {
        g.setPaint(new Color(255, 255, 0, 35)); // Color.yellow
        for (int x = 0; x < WIDTH; x++) {
            TickList ticksPerPoint = ticksPerPoints[x];
            if( ticksPerPoint != null ) {
                int size = ticksPerPoint.size();
                if(size > 0) {
                    Utils.DoubleMinMaxCalculator<Tick> candleCalc = new Utils.DoubleMinMaxCalculator<Tick>(ticksPerPoint) {
                        @Override public Double getValue(Tick tick) { return tick.m_price; }
                    };
                    int y1 = priceAxe.getPointReverse(candleCalc.m_minValue);
                    int y2 = priceAxe.getPointReverse(candleCalc.m_maxValue);
                    g.drawLine(x, y1, x, y2);
                }
            }
        }
    }

    private static void paintPoints(TickList[] ticksPerPoints, ChartAxe priceAxe, Graphics2D g, int exch1id) {
        for (int x = 0; x < WIDTH; x++) {
            TickList ticksPerPoint = ticksPerPoints[x];
            if( ticksPerPoint != null ) {
                for (Tick tick : ticksPerPoint) {
                    double price = tick.m_price;
                    int y = priceAxe.getPointReverse(price);
                    g.setPaint((tick.m_src == exch1id) ? Color.red : Color.blue);
                    g.drawLine(x, y, x, y);
                }
            }
        }
    }

    // older first
    private static double[] calculateMovingAverage(PriceDiffList[] diffsPerPoints, int movingAveragePoints) {
        double movingAverage[] = new double[WIDTH];
        double avgPriceDiffs[] = new double[WIDTH];
        int length = diffsPerPoints.length;
        for (int i = 0; i < length; i++) {
            PriceDiffList diffsPerPoint = diffsPerPoints[i];
            if (diffsPerPoint != null) {
                int size = diffsPerPoint.size();
                double sum = 0;
                for (double diff : diffsPerPoint) {
                    sum += diff;
                }
                avgPriceDiffs[i] = sum / size;
            } else {
                avgPriceDiffs[i] = Double.MAX_VALUE; // no data marker
            }

            int count = 0;
            double sum = 0.0;
            for (int j = 0; j < movingAveragePoints; j++) {
                int indx = i - j;
                if (indx < 0 ) {
                    break;
                }
                double avg = avgPriceDiffs[indx];
                if (avg != Double.MAX_VALUE) {
                    sum += avg;
                    count++;
                }
            }
            if (count > 0) {
                movingAverage[i] = sum / count;
            } else {
                movingAverage[i] = Double.MAX_VALUE; // no data marker
            }
        }
        return movingAverage;
    }

    // earliest first
    private static void paintPriceDiffMovingAverage(ChartAxe difAxe, Graphics2D g, double[] movingAverage,
                                                    double halfTargetDelta, double halfRunCommission) {
        g.setPaint(Color.orange);
        for (int x = 0; x < WIDTH; x++) {
            double avg = movingAverage[x];
            if (avg != Double.MAX_VALUE) {
                int y = difAxe.getPointReverse(avg);
                g.drawRect(x - 1, y - 1, 3, 3);
            }
        }
        for (int x = 0; x < WIDTH; x++) {
            double avg = movingAverage[x];
            if (avg != Double.MAX_VALUE) {
                g.setPaint(Color.GRAY);
                int y = difAxe.getPointReverse(avg+halfTargetDelta);
                g.drawRect(x, y, 1, 1);
                y = difAxe.getPointReverse(avg-halfTargetDelta);
                g.drawRect(x, y, 1, 1);
                g.setPaint(Color.lightGray);
                y = difAxe.getPointReverse(avg+halfRunCommission);
                g.drawRect(x, y, 1, 1);
                y = difAxe.getPointReverse(avg-halfRunCommission);
                g.drawRect(x, y, 1, 1);
            }
        }
        g.setPaint(Color.PINK);
        for (int x = 0; x < WIDTH; x++) {
            if(x > 3) {
                double avg = movingAverage[x];
                double avgOld = movingAverage[x-4];
                if (avg != Double.MAX_VALUE && avgOld != Double.MAX_VALUE) {
                    double avgDelta = avg - avgOld;
                    if( avgDelta > 0 ) {
                        int y = difAxe.getPointReverse(avg+halfTargetDelta + avgDelta*6);
                        g.drawRect(x, y, 1, 1);
                    }
                    if( avgDelta < 0 ) {
                        int y = difAxe.getPointReverse(avg-halfTargetDelta + avgDelta*6);
                        g.drawRect(x, y, 1, 1);
                    }
                }
            }
        }
    }

    // older first
    private static void paintPriceDiffs(PriceDiffList[] diffsPerPoints, ChartAxe difAxe, Graphics2D g, double[] movingAverage,
                                        double minDif, double maxDif, double halfTargetDelta, double halfComission) {
        for (int x = 0; x < WIDTH; x++) {
            PriceDiffList diffsPerPoint = diffsPerPoints[x];
            if( diffsPerPoint != null ) {
                double movingAvg = movingAverage[x];
                double movingAvgUp = movingAvg + halfTargetDelta;
                double movingAvgLow = movingAvg - halfTargetDelta;
                double movingAvgUp0 = movingAvg + halfComission;
                double movingAvgLow0 = movingAvg - halfComission;
                for (double priceDif : diffsPerPoint) {
                    int y = difAxe.getPointReverse(priceDif);
                    boolean highlight = (priceDif > movingAvgUp) || (priceDif < movingAvgLow);
                    boolean highlight0 = (priceDif > movingAvgUp0) || (priceDif < movingAvgLow0);
                    g.setPaint(highlight ? Color.red : highlight0 ? Color.ORANGE : Color.green);
                    if( highlight ) {
                        g.fillRect(x - 1, y - 1, 3, 3);
                    } else {
                        g.drawRect(x - 1, y - 1, 3, 3);
                    }
                    if((priceDif == minDif) || (priceDif == maxDif)) {
                        g.drawLine(x - 20, y - 20, x + 20, y + 20);
                        g.drawLine(x + 20, y - 20, x - 20, y + 20);
                    }
                }
            }
        }
    }

    private static Map<Long, Double> calculatePriceDiffs(List<Tick> ticks, int exch0id, int exch1id) {
        Map<Long, Double> difMap = new HashMap<Long, Double>();
        double lastPrice0 = 0.0;
        double lastPrice1 = 0.0;
        TickList oneSecondTicks0 = new TickList();
        TickList oneSecondTicks1 = new TickList();
        int noPriceCounter0 = 0;
        int noPriceCounter1 = 0;
        long lastStamp = 0;
        // from the oldest tick
        for (int i = ticks.size() - 1; i >= 0; i--) {
            Tick tick = ticks.get(i);
            long stamp = tick.m_stamp;
            if (stamp != lastStamp) { // time changed from last ticks
                if (lastStamp != 0) {
                    double price0 = calcAverage(oneSecondTicks0);
                    if (price0 == 0.0) {
                        price0 = lastPrice0;
                        noPriceCounter0++;
                    } else {
                        noPriceCounter0 = 0;
                    }
                    double price1 = calcAverage(oneSecondTicks1);
                    if (price1 == 0.0) {
                        price1 = lastPrice1;
                        noPriceCounter1++;
                    } else {
                        noPriceCounter1 = 0;
                    }
                    if ((price0 != 0.0) && (price1 != 0.0)) {
                        if ((noPriceCounter0 < 10) && (noPriceCounter1 < 10)) {
                            double priceDif = price0 - price1;
                            difMap.put(lastStamp, priceDif);
                        }
                    }
                    oneSecondTicks0.clear();
                    oneSecondTicks1.clear();
                    lastPrice0 = price0;
                    lastPrice1 = price1;
                }
                lastStamp = stamp;
            }
            if (tick.m_src == exch0id) {
                oneSecondTicks0.add(tick);
            } else if (tick.m_src == exch1id) {
                oneSecondTicks1.add(tick);
            }
        }
        return difMap;
    }

    private static double calcAverage(List<Tick> ticks) {
        if(ticks.isEmpty()) {
            return 0.0;
        }
        if(VOLUME_AVERAGE) {
            double volume = 0.0;
            double sum = 0.0;
            for(Tick tick: ticks) {
                sum += tick.m_price * tick.m_volume;
                volume += tick.m_volume;
            }
            return sum / volume;
        }
        double sum = 0.0;
        for(Tick tick: ticks) {
            sum += tick.m_price;
        }
        return sum / ticks.size();
    }

    private static List<Tick> selectTicks(Connection connection, long three, long end, long start, Exchange exch1, Exchange exch2) throws SQLException {
        List<Tick> ticks = new ArrayList<Tick>();
        PreparedStatement statement = connection.prepareStatement(
                        "   SELECT stamp, price, src, volume " +
                        "    FROM Ticks " +
                        "    WHERE stamp > ? " +
                        "     AND stamp < ? " +
                        "     AND (src = ? OR src = ?) " +
                        "    ORDER BY stamp DESC ");
        try {
            statement.setLong(1, start);
            statement.setLong(2, end);
            statement.setInt(3, exch1.m_databaseId);
            statement.setInt(4, exch2.m_databaseId);

            ResultSet result = statement.executeQuery();
            long four = System.currentTimeMillis();
            System.out.println("Ticks selected in "+ Utils.millisToDHMSStr(four - three));
            try {
                while (result.next()) {
                    long stamp = result.getLong(1);
                    double price = result.getDouble(2);
                    int src = result.getInt(3);
                    double volume = result.getDouble(4);
                    Tick tick = new Tick(stamp, price, src, volume);
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

    public static class Tick {
        private final int m_src;
        public final long m_stamp;
        public final double m_price;
        private final double m_volume;

        public Tick(long stamp, double price, int src, double volume) {
            m_stamp = stamp;
            m_price = price;
            m_src = src;
            m_volume = volume;
        }
    }

    private static class TickList extends ArrayList<Tick> {}
    public static class PriceDiffList extends ArrayList<Double> {}

    public static class ChartAxe {
        public final double m_min;
        public final double m_max;
        private final int m_size;
        private final double m_scale;

        public ChartAxe(double min, double max, int size) {
            m_min = min;
            m_max = max;
            m_size = size;
            double diff = max - min;
            m_scale = diff / size;
        }

        public int getPoint(double value) {
            double offset = value - m_min;
            return (int) (offset / m_scale);
        }

        public int getPointReverse(double value) {
            int point = getPoint(value);
            return m_size - 1 - point;
        }
    }

    public enum Vary {
        NONE, MOVING_AVERAGE_LEN, EXPECTED_GAIN, DROP, MOVING_AVERAGE_LEN_AND_EXPECTED_GAIN;
    }

    private enum VaryProfile {
        NONE(Vary.NONE, 1, 1),
        AVG_GAIN_1(Vary.MOVING_AVERAGE_LEN_AND_EXPECTED_GAIN, 5, 1),
        DROP(Vary.DROP, 1, 1),
        AVG_GAIN_2(Vary.MOVING_AVERAGE_LEN_AND_EXPECTED_GAIN, 1, 5);

        private Vary m_vary;
        private int m_stepRatio;
        private int m_distanceRatio;

        VaryProfile(Vary vary, int stepRatio, int distanceRatio) {
            m_vary = vary;
            m_stepRatio = stepRatio;
            m_distanceRatio = distanceRatio;
        }
    }

    // BITSTAMP, BTCE, CAMPBX
    private static enum ExchangePair {
        BITSTAMP_BTCE(Exchange.BITSTAMP, Exchange.BTCE,         89 * 60 + 39, 1.858, -0.051),
        BITSTAMP_CAMPBX(Exchange.BITSTAMP, Exchange.CAMPBX,     51 * 60 + 51, 1.175,  0.805),

        BTCE_BITFINEX(Exchange.BTCE, Exchange.BITFINEX,          9 * 60 + 2,  1.255,  1.475),
        BITSTAMP_BITFINEX(Exchange.BITSTAMP, Exchange.BITFINEX, 74 * 60 + 17, 6.63,  -0.215),

        BITSTAMP_HITBTC(Exchange.BITSTAMP, Exchange.HITBTC,     14 * 60 + 39, 0.425,  1.035 ),
        BTCE_HITBTC(Exchange.BTCE, Exchange.HITBTC,            413 * 60 + 3,  0.42,   0.196 ),
        BITFINEX_HITBTC(Exchange.BITFINEX, Exchange.HITBTC,    112 * 60 + 51, 1.5,    0.565 ),

        BITSTAMP_LAKEBTC(Exchange.BITSTAMP, Exchange.LAKEBTC,   87 * 60 + 19, 3.25,   0.05 ),
        BTCE_LAKEBTC(Exchange.BTCE, Exchange.LAKEBTC,          144 * 60 + 34, 5.1,    1.13 ),
        BITFINEX_LAKEBTC(Exchange.BITFINEX, Exchange.LAKEBTC,  126 * 60 + 18, 6.315, -0.155 ),
        HITBTC_LAKEBTC(Exchange.HITBTC, Exchange.LAKEBTC,      134 * 60 + 45, 7.915, -0.22 ),

        BITSTAMP_ITBIT(Exchange.BITSTAMP, Exchange.ITBIT,      132 * 60 + 12, 9.225, -0.05 ),
        BTCE_ITBIT(Exchange.BTCE, Exchange.ITBIT,              310 * 60 + 7,  3.825,  0.195 ),

        BTCN_OKCOIN(Exchange.BTCN, Exchange.OKCOIN,              2 * 60 + 50, 0.575, -0.165 ),
        ;

        public final Exchange m_exch1;
        public final Exchange m_exch2;
        public final long m_movingAverage;
        public final double m_expectedGain;
        public final double m_dropLevel;

        ExchangePair(Exchange exch1, Exchange exch2, int movingAverageSeconds, double expectedGain, double dropLevel) {
            m_exch1 = exch1;
            m_exch2 = exch2;
            m_movingAverage = movingAverageSeconds * 1000;
            m_expectedGain = expectedGain;
            m_dropLevel = dropLevel;
        }
    }
}
