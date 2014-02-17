import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class PaintChart extends DbReady {
    private static final int PERIOD_END_OFFSET_DAYS = 0; // minus days from last tick
    public static final int PERIOD_LENGTH_DAYS = 3; // the period width
    private static final int MOVING_AVERAGE_POINTS = 300; // 300
    private static final double EXPECTED_GAIN = 5; // 5
    public static final int MIN_CONFIRMED_DIFFS = 5;
    // BITSTAMP, BTCE, MTGOX, CAMPBX
    private static final Exchange EXCH1 = Exchange.BITSTAMP;
    private static final Exchange EXCH2 = Exchange.CAMPBX;
    public static final boolean DO_DROP = true;
    public static final double DROP_LEVEL = 0.8;
    public static final boolean LOCK_DIRECTION_ON_DROP = true;
    private static final boolean VOLUME_AVERAGE = false;
    public static final int MAX_NEXT_POINTS_TO_CONFIRM = 4; // look to next points for prices to confirm
    private static final boolean PAINT_PRICE = true;
    private static final boolean PAINT_DIFF = true;
    // chart area
    public static final int X_FACTOR = 4;
    private static final int WIDTH = 1680 * X_FACTOR;
    public static final int HEIGHT = 1000 * 2;

    public static final DecimalFormat XX_YYYYY = new DecimalFormat("#,##0.0####");

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = System.currentTimeMillis();
        System.out.println("timeMills: " + millis);
        long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("maxMemory: " + maxMemory + ", k:"+(maxMemory/=1024) + ": m:" + (maxMemory/=1024) );

        paint();

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void paint() {
        IDbRunnable runnable = new IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                long now = System.currentTimeMillis();
                long end = now - PERIOD_END_OFFSET_DAYS * Utils.ONE_DAY_IN_MILLIS;
                long start = end - PERIOD_LENGTH_DAYS * Utils.ONE_DAY_IN_MILLIS;

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

        @SuppressWarnings("ConstantConditions")
        TickList[] ticksPerPoints = PAINT_PRICE ? calculateTicksPerPoints(ticks, timeAxe) : null;

        // older first
        @SuppressWarnings("ConstantConditions")
        PriceDiffList[] diffsPerPoints = PAINT_DIFF ? calculateDiffsPerPoints(difMap, timeAxe) : null;

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

        // older first
        @SuppressWarnings("ConstantConditions")
        double[] movingAverage = PAINT_DIFF ? calculateMovingAverage(diffsPerPoints) : null;

        int priceStep = 10;
        int priceStart = ((int)minPrice) / priceStep * priceStep;

        if (PAINT_PRICE) {
            // paint left axe
            paintLeftAxeAndGrid(minPrice, maxPrice, priceAxe, g, priceStep, priceStart);

            // paint candles
            paintCandles(ticksPerPoints, priceAxe, g);

            // paint points
            paintPoints(ticksPerPoints, priceAxe, g, exch1id);
        }

        double exch1commission = calculateExchCommission(ticks, exch1);
        double exch2commission = calculateExchCommission(ticks, exch2);
        double runComission = 2*(exch1commission + exch2commission);
        double targetDelta = runComission + EXPECTED_GAIN;
        System.out.println("runComission=" + runComission + ", targetDelta=" + targetDelta);
        double halfTargetDelta = targetDelta/2;

        if (PAINT_DIFF) {
            // paint pricediffs
            paintPriceDiffs(diffsPerPoints, difAxe, g, movingAverage, minDif, maxDif, halfTargetDelta);

            new ChartSimulator().simulate(diffsPerPoints, difAxe, g, movingAverage, halfTargetDelta, runComission);

            // paint price diff moving average
            paintPriceDiffMovingAverage(difAxe, g, movingAverage, halfTargetDelta);

            // paint average price diff line
            int yy = difAxe.getPointReverse(averagePriceDif);
            g.drawLine(0, yy, WIDTH - 1, yy);
        }

        if (PAINT_PRICE) {
            // paint left axe labels
            paintLeftAxeLabels(minPrice, maxPrice, priceAxe, g, priceStep, priceStart);
        }

        if (PAINT_DIFF) {
            // paint right axe labels
            paintRightAxeLabels(minDif, maxDif, difAxe, g);
        }

        // paint time axe labels
        paintTimeAxeLabels(minTimestamp, maxTimestamp, timeAxe, g);

        if (PAINT_DIFF) {
            // paint moving average time range
            long movingAverageMillis = (long) timeAxe.m_scale * MOVING_AVERAGE_POINTS;
            g.drawLine(6, 6, 6 + MOVING_AVERAGE_POINTS, 6);
            g.setFont(g.getFont().deriveFont(12.0f* X_FACTOR));
            String str = timePpStr + "\n movingAverage:" + Utils.millisToDHMSStr(movingAverageMillis);
            FontMetrics fontMetrics = g.getFontMetrics();
            Rectangle2D bounds = fontMetrics.getStringBounds(str, g);
            g.drawString(str, 45, (int) (bounds.getHeight()*1.1));
        }

        paintLegend(exch1, exch2, g);

        g.dispose();

        try {
            long millis = System.currentTimeMillis();

            File output = new File("imgout/"+Long.toString(millis,32)+".png");
            ImageIO.write(image, "png", output);

            System.out.println("write done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));

            Desktop.getDesktop().open(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static double calculateExchCommission(List<Tick> ticks, Exchange exch) {
        double avgPrice1 = calculateAveragePrice(ticks, exch);
        return avgPrice1 * exch.m_fee;
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

    private static void paintLeftAxeAndGrid(double minPrice, double maxPrice, ChartAxe priceAxe, Graphics2D g, int priceStep, int priceStart) {
        g.setPaint(new Color(128, 128, 128, 128)); // Color.gray
        final float dash1[] = {10.0f};
        BasicStroke dashedStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash1, 0.0f);
        Stroke oldStroke = g.getStroke();
        g.setStroke(dashedStroke);
        for (int price = priceStart; price < maxPrice; price += priceStep) {
            if (price > minPrice) {
                int y = priceAxe.getPointReverse(price);
                g.drawLine(0, y, WIDTH - 1, y);
            }
        }
        g.setStroke(oldStroke);
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
    private static double[] calculateMovingAverage(PriceDiffList[] diffsPerPoints) {
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
            for (int j = 0; j < MOVING_AVERAGE_POINTS; j++) {
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
    private static void paintPriceDiffMovingAverage(ChartAxe difAxe, Graphics2D g, double[] movingAverage, double halfTargetDelta) {
        g.setPaint(Color.orange);
        for (int x = 0; x < WIDTH; x++) {
            double avg = movingAverage[x];
            if (avg != Double.MAX_VALUE) {
                int y = difAxe.getPointReverse(avg);
                g.drawRect(x - 1, y - 1, 3, 3);
            }
        }
        g.setPaint(Color.lightGray);
        for (int x = 0; x < WIDTH; x++) {
            double avg = movingAverage[x];
            if (avg != Double.MAX_VALUE) {
                int y = difAxe.getPointReverse(avg+halfTargetDelta);
                g.drawRect(x, y, 1, 1);
                y = difAxe.getPointReverse(avg-halfTargetDelta);
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
                                        double minDif, double maxDif, double halfTargetDelta) {
        for (int x = 0; x < WIDTH; x++) {
            PriceDiffList diffsPerPoint = diffsPerPoints[x];
            if( diffsPerPoint != null ) {
                double movingAvg = movingAverage[x];
                double movingAvgUp = movingAvg + halfTargetDelta;
                double movingAvgLow = movingAvg - halfTargetDelta;
                for (double priceDif : diffsPerPoint) {
                    int y = difAxe.getPointReverse(priceDif);
                    boolean highlight = (priceDif > movingAvgUp) || (priceDif < movingAvgLow);
                    g.setPaint(highlight ? Color.red : Color.green);
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

    private static void paintLeftAxeLabels(double minPrice, double maxPrice, ChartAxe priceAxe, Graphics2D g, int priceStep, int priceStart) {
        g.setPaint(Color.black);
        g.setFont(g.getFont().deriveFont(20.0f));
        for (int price = priceStart; price < maxPrice; price += priceStep) {
            if (price > minPrice) {
                int y = priceAxe.getPointReverse(price);
                g.drawString(Integer.toString(price), 2, y - 1);
            }
        }
    }

    private static void paintRightAxeLabels(double minDif, double maxDif, ChartAxe difAxe, Graphics2D g) {
        int priceDifStep = 5;
        int priceDifStart = ((int) minDif) / priceDifStep * priceDifStep;
        System.out.println("priceDifStart=" + priceDifStart);
        g.setFont(g.getFont().deriveFont(9.0f * X_FACTOR));
        FontMetrics fontMetrics = g.getFontMetrics();
        for (int price = priceDifStart; price < maxDif; price += priceDifStep) {
            if (price > minDif) {
                int y = difAxe.getPointReverse(price);
                g.drawLine(WIDTH - 20, y, WIDTH - 1, y);
                String str = Integer.toString(price);
                Rectangle2D bounds = fontMetrics.getStringBounds(str, g);
                g.drawString(str, (float) (WIDTH - 20 - bounds.getWidth()), y - 1);
            }
        }
    }

    private static void paintTimeAxeLabels(long minTimestamp, long maxTimestamp, ChartAxe timeAxe, Graphics2D g) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(minTimestamp);
        Utils.setToDayStart(cal);

        long timePeriod = maxTimestamp - minTimestamp;
        int timePeriodDays = (int) (timePeriod / Utils.ONE_DAY_IN_MILLIS);

        // paint DAYS
        g.setFont(g.getFont().deriveFont(30.0f));
        SimpleDateFormat format = new SimpleDateFormat("d MMM");
        while (true) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            long millis = cal.getTimeInMillis();
            if (millis >= minTimestamp) {
                if (millis > maxTimestamp) {
                    break;
                }
                String label = format.format(cal.getTime());
                int x = timeAxe.getPoint(millis);
                g.drawLine(x, HEIGHT - 10, x, HEIGHT - 1);
                g.drawString(label, x + 2, HEIGHT - 8);
            }
        }

        // paint HOURS
        if (timePeriodDays <= 15) {
            cal.setTimeInMillis(minTimestamp);
            Utils.setToDayStart(cal);

            int hourPlus = (timePeriodDays > 6) ? 6 : 2;

            g.setFont(g.getFont().deriveFont(20.0f));
            format = new SimpleDateFormat("HH:mm");
            while (true) {
                cal.add(Calendar.HOUR_OF_DAY, hourPlus);
                int hour = cal.get(Calendar.HOUR_OF_DAY);
                if (hour != 0) {
                    long millis = cal.getTimeInMillis();
                    if (millis >= minTimestamp) {
                        if (millis > maxTimestamp) {
                            break;
                        }
                        String label = format.format(cal.getTime());
                        int x = timeAxe.getPoint(millis);
                        g.drawLine(x, HEIGHT - 10, x, HEIGHT - 1);
                        g.drawString(label, x + 2, HEIGHT - 4);
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
        private final double m_min;
        private final double m_scale;
        private final int m_size;

        public ChartAxe(double min, double max, int size) {
            m_min = min;
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

}
