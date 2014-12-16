package bthdg.osc;

import bthdg.BaseChartPaint;
import bthdg.PaintChart;
import bthdg.exch.Exchange;
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
import java.util.List;

public class PaintOsc extends BaseChartPaint {
    private static final int BAR_NUM = 108;
    private static final long BAR_SIZE = Utils.toMillis("1h");
    private static final Exchange EXCHANGE = Exchange.BTCN;
    private static final Color TRANSP_GRAY = new Color(100,100,100,50);

    // chart area
    public static final int X_FACTOR = 1; // more points
    private static final int WIDTH = 3200;
    public static final int HEIGHT = 900;
    public static final int LEN1 = 14;
    public static final int LEN2 = 14;
    public static final int K = 3;
    public static final int D = 3;

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = System.currentTimeMillis();
        System.out.println("timeMills: " + millis);
        System.out.println("BAR_SIZE: " + BAR_SIZE + " ms; ="+Utils.millisToDHMSStr(BAR_SIZE));
        long maxMemory = Runtime.getRuntime().maxMemory();
        System.out.println("maxMemory: " + maxMemory + ", k:" + (maxMemory /= 1024) + ": m:" + (maxMemory /= 1024));

        paint();

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void paint() {
        IDbRunnable runnable = new IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                long now = System.currentTimeMillis();

                long timestamp = getMaxTimestamp(connection, EXCHANGE);
                System.out.println("max timestamp="+timestamp);

                long timeFrame = BAR_SIZE * BAR_NUM;
                System.out.println("timeFrame: " + timeFrame + " ms; =" + Utils.millisToDHMSStr(timeFrame));
                long start = timestamp - timeFrame;

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

        Bar[] bars = calBars(ticks, minBarTimestamp, maxBarTimestamp);
        List<Osc> oscs = calOsc(ticks, minBarTimestamp, maxBarTimestamp);

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
        // paint left axe labels
        paintLeftAxeLabels(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, X_FACTOR);

        // paint time axe labels
        paintTimeAxeLabels(minTimestamp, maxTimestamp, timeAxe, g, HEIGHT, X_FACTOR);

        // paint points
        paintPoints(ticks, priceAxe, timeAxe, g);

        // paint bars
        paintBars(bars, priceAxe, timeAxe, g);

        // paint ocs
        paintOscs(oscs, oscAxe, timeAxe, g);

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

    private static double max(List<Double> vals) {
        double res = 0;
        for (Double val : vals) {
            res = Math.max(res, val);
        }
        return res;
    }

    private static double min(List<Double> vals) {
        Double res = null;
        for (Double val : vals) {
            res = (res == null) ? val : Math.min(res, val);
        }
        return res;
    }

    private static double avg(List<Double> vals) {
        double sum = 0;
        for (Double val : vals) {
            sum += val;
        }
        return sum / vals.size();
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

    private static List<Osc> calOsc(List<Tick> ticks, long minBarTimestamp, long maxBarTimestamp) {
        OscCalculator calc = new OscCalculator(BAR_SIZE);
        for (Tick tick : ticks) {
            calc.update(tick);
        }
        return calc.ret();
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

    private static void paintOscs(List<Osc> oscs, ChartAxe oscAxe, ChartAxe timeAxe, Graphics2D g) {
        Integer prevRight = null;
        Integer prevVal1Y = null;
        Integer prevVal2Y = null;

        for (Osc osc : oscs) {
            long startTime = osc.m_startTime;
            long barEndTime = startTime + BAR_SIZE;
            int right = timeAxe.getPoint(barEndTime);

            double val1 = osc.m_val1;
            int val1Y = oscAxe.getPointReverse(val1);
            double val2 = osc.m_val2;
            int val2Y = oscAxe.getPointReverse(val2);

            g.setPaint(Color.CYAN);
            g.drawRect(right - 1, val1Y - 1, 3, 3);
            g.setPaint(Color.GREEN);
            g.drawRect(right - 1, val2Y - 1, 3, 3);
            if (prevRight != null) {
                g.setPaint(Color.CYAN);
                g.drawLine(prevRight, prevVal1Y, right, val1Y);
                g.setPaint(Color.GREEN);
                g.drawLine(prevRight, prevVal2Y, right, val2Y);
            }
            prevRight = right;
            prevVal1Y = val1Y;
            prevVal2Y = val2Y;
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

    private static class Osc {
        private final long m_startTime;
        private final double m_val1;
        private final double m_val2;

        public Osc(long startTime, double val1, double val2) {
            m_startTime = startTime;
            m_val1 = val1;
            m_val2 = val2;
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
        private long m_closeStamp;

        public OscCalculator(long barSize) {
            m_barSize = barSize;
        }

        public void update(Tick tick) {
            long stamp = tick.m_stamp;
            if (m_currBarStart == null) {
                startNewBar(stamp, null, 0);
            }
            if (stamp < m_currBarEnd) { // one more tick in current bar
                m_close = tick.m_price;
                m_closeStamp = stamp;
            } else { // bar fully defined
                if (m_prevBarClose != null) {
                    finishBar();
                }
                startNewBar(stamp, m_close, tick.m_price);
            }
        }

        private void finishBar() {
            if (m_gains.size() == LEN1) {
                m_gains.remove(0);
                m_losss.remove(0);
            }
            double change = m_close - m_prevBarClose;
            double gain = (change > 0) ? change : 0d;
            double loss = (change < 0) ? -change : 0d;
            if (m_avgGain == null) {
                m_gains.add(gain);
                m_losss.add(loss);
                if (m_gains.size() == LEN1) {
                    m_avgGain = avg(m_gains);
                    m_avgLoss = avg(m_losss);
                }
            } else {
                m_avgGain = (m_avgGain * (LEN1 - 1) + gain) / LEN1;
                m_avgLoss = (m_avgLoss * (LEN1 - 1) + loss) / LEN1;
            }
            if (m_avgGain != null) {
                double rsi = (m_avgLoss == 0) ? 100 : 100 - (100 / (1 + m_avgGain / m_avgLoss));
                if (rsis.size() == LEN2) {
                    rsis.remove(0);
                }
                rsis.add(rsi);
                if (rsis.size() == LEN2) {
                    double highest = max(rsis);
                    double lowest = min(rsis);
                    double stoch = (rsi - lowest) / (highest - lowest);
                    if (stochs.size() == K) {
                        stochs.remove(0);
                    }
                    stochs.add(stoch);
                    if (stochs.size() == K) {
                        double stoch1 = avg(stochs);
                        if (stoch1s.size() == D) {
                            stoch1s.remove(0);
                        }
                        stoch1s.add(stoch1);
                        if (stoch1s.size() == D) {
                            double stoch2 = avg(stoch1s);
                            Osc osc = new Osc(m_currBarStart, stoch1, stoch2);
                            ret.add(osc);
                        }
                    }
                }
            }
        }

        public List<Osc> ret() {
            if (m_prevBarClose != null) {
                finishBar();
            }
            return ret;
        }

        private void startNewBar(long stamp, Double prevBarClose, double close) {
            m_currBarStart = stamp / m_barSize * m_barSize;
            m_currBarEnd = m_currBarStart + m_barSize;
            m_prevBarClose = prevBarClose;
            m_close = close;
            m_closeStamp = stamp;
        }
    }
}
