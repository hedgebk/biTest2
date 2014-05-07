package bthdg.triplet;

import bthdg.BaseChartPaint;
import bthdg.Currency;
import bthdg.PaintChart;
import bthdg.Utils;
import bthdg.exch.Pair;
import bthdg.exch.PairDirection;
import bthdg.exch.TopData;
import bthdg.exch.TopsData;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;

public class PaintInterExchange extends BaseChartPaint {
    private static final int XFACTOR = 2;
    private static final int WIDTH = 1620 * XFACTOR * 8;
    public static final int HEIGHT = 900 * XFACTOR;
    public static final DecimalFormat RATIO_FORMAT = new DecimalFormat("0.0000");
    public static final double ZERO_PROFIT = 0.0055;
    public static final double EXPECTED_GAIN = 0.005;

    public static void main(String[] args) {
        System.out.println("Started");
        long millis = logTimeMemory();

        long fromMillis = (args.length > 0) ? Utils.toMillis(args[0]) : /*0*/ Utils.toMillis("-8h");
        paint(fromMillis);

        System.out.println("done in " + Utils.millisToDHMSStr(System.currentTimeMillis() - millis));
    }

    private static void paint(final long fromMillis) {
        IDbRunnable runnable = new IDbRunnable() {
            public void run(Connection connection) throws SQLException {
                System.out.println("selecting ticks");
                TreeMap<Long,TopsData> topsMap = selectTops(connection, fromMillis);

                drawTraces(topsMap);
                System.out.println("--- Complete ---");
            }
        };

        goWithDb(runnable);
    }

    public static final String CREATE_TOPS_SQL = "CREATE TABLE IF NOT EXISTS Tops ( " +
            " stamp BIGINT NOT NULL, " +
            " src   INTEGER NOT NULL, " +
            " pair  INTEGER NOT NULL, " +
            " bid   DOUBLE, " +
            " ask   DOUBLE, " +
            " last  DOUBLE " +
            ")";


    private static TreeMap<Long, TopsData> selectTops(Connection connection, long fromMillis) throws SQLException {
        long three = System.currentTimeMillis();
        TreeMap<Long, TopsData> ret = new TreeMap<Long, TopsData>();
        PreparedStatement statement = connection.prepareStatement(
                "   SELECT stamp, pair, bid, ask " +
                        "    FROM Tops " +
                        "    WHERE stamp > ? " +
                        "    ORDER BY stamp ASC ");
        try {
            statement.setLong(1, fromMillis);

            ResultSet result = statement.executeQuery();
            long four = System.currentTimeMillis();
            System.out.println("traces selected in " + Utils.millisToDHMSStr(four - three));
            try {
                while (result.next()) {
                    long stamp = result.getLong(1);
                    int pairId = result.getInt(2);
                    double bid = result.getDouble(3);
                    double ask = result.getDouble(4);

                    TopsData topsData = ret.get(stamp);
                    if (topsData == null) {
                        topsData = new TopsData();
                        ret.put(stamp, topsData);
                    }

                    Pair pair = Pair.getById(pairId);

                    TopData topData = new TopData(bid, ask);
                    topsData.put(pair, topData);
                }
                long five = System.currentTimeMillis();
                System.out.println(ret.size() + " tops read in " + Utils.millisToDHMSStr(five - four));
            } finally {
                result.close();
            }
        } finally {
            statement.close();
        }
        return ret;
    }

    private static void drawTraces(TreeMap<Long, TopsData> topsMap) {
        TreeMap<Long, TriData> triDataMap = buildTriDataMap(topsMap);
        Collection<TriData> triDatas = triDataMap.values();

        Utils.DoubleMinMaxCalculator<TriData> priceCalc = new Utils.DoubleMinMaxCalculator<TriData>() {
            Double[] m_ar = new Double[Triada.values().length];
            public Double getValue(TriData trace) {return null;};
            @Override public Double[] getValues(TriData triData) {
                int indx = 0;
                for(Triada triada: Triada.values()) {
                    m_ar[indx++] = triData.m_ratioMap.get(triada);
                }
                return m_ar;
            }
        };
        priceCalc.calculate(triDatas);

        Utils.LongMinMaxCalculator<Long> timeCalc = new Utils.LongMinMaxCalculator<Long>(triDataMap.keySet()) {
            @Override public Long getValue(Long val) { return val; }
        };
        long minTimestamp = timeCalc.m_minValue;
        long maxTimestamp = timeCalc.m_maxValue;
        long timeDiff = maxTimestamp - minTimestamp;
        System.out.println("min timestamp: " + minTimestamp + ", max timestamp: " + maxTimestamp + ", timestamp diff: " + Utils.millisToDHMSStr(timeDiff));

        double minRatio = priceCalc.m_minValue;
        double maxRatio = priceCalc.m_maxValue;
        double ratioDiff = maxRatio - minRatio;
        System.out.println("minRatio=" + minRatio + ", maxRatio=" + maxRatio + ", ratioDiff = " + ratioDiff);

        PaintChart.ChartAxe timeAxe = new PaintChart.ChartAxe(minTimestamp, maxTimestamp, WIDTH);
        PaintChart.ChartAxe ratioAxe = new PaintChart.ChartAxe(minRatio - ratioDiff * 0.05, maxRatio + ratioDiff * 0.05, HEIGHT);

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        setupGraphics(g);

        // paint border
        g.setPaint(Color.black);
        g.drawRect(0, 0, WIDTH - 1, HEIGHT - 1);

        // paint left axe
        double ratioStep = 0.001;
        double ratioStart = (((int)(minRatio / ratioStep))+1)* ratioStep;
        paintLeftAxeAndGrid(minRatio, maxRatio, ratioAxe, g, ratioStep, ratioStart, WIDTH, 1.0);

        paintLevels(ratioAxe, g);

        // paint points
        paintPoints(triDataMap, timeAxe, ratioAxe, g);
        g.setPaint(Color.BLACK);

        // paint left axe labels
        paintLeftAxeLabels(minRatio, maxRatio, ratioAxe, g, ratioStep, ratioStart, XFACTOR, RATIO_FORMAT);

        // paint time axe labels
        paintTimeAxeLabels(minTimestamp, maxTimestamp, timeAxe, g, HEIGHT, XFACTOR);

        g.dispose();
        writeAndShowImage(image);
    }

    private static void paintLevels(PaintChart.ChartAxe ratioAxe, Graphics2D g) {
        Stroke oldStroke = g.getStroke();
        g.setStroke(DASHED_STROKE);

        g.setPaint(Color.RED);
        int y = ratioAxe.getPointReverse(1 + ZERO_PROFIT);
        g.drawLine(0, y, WIDTH - 1, y);
        y = ratioAxe.getPointReverse(1 - ZERO_PROFIT);
        g.drawLine(0, y, WIDTH - 1, y);

        g.setPaint(Color.darkGray);
        y = ratioAxe.getPointReverse(1 + ZERO_PROFIT + EXPECTED_GAIN);
        g.drawLine(0, y, WIDTH - 1, y);
        y = ratioAxe.getPointReverse(1 - ZERO_PROFIT - EXPECTED_GAIN);
        g.drawLine(0, y, WIDTH - 1, y);

        g.setStroke(oldStroke);
    }

    private static void paintPoints(TreeMap<Long, TriData> triDataMap, PaintChart.ChartAxe timeAxe, PaintChart.ChartAxe ratioAxe, Graphics2D g) {
        Map<Triada,Integer> prevYmap = new HashMap<Triada, Integer>();
        int prevX = -1;
        for (Map.Entry<Long, TriData> entry : triDataMap.entrySet()) {
            long millis = entry.getKey();
            TriData triData = entry.getValue();
            Map<Triada, Double> ratioMap = triData.m_ratioMap;

            int x = timeAxe.getPoint(millis);
            for(Triada triada: Triada.values()) {
                double ratio = ratioMap.get(triada);
                int y = ratioAxe.getPointReverse(ratio);

                Integer prevY = prevYmap.get(triada);
                if ((prevX != -1) && (prevY != null)) {
                    g.setPaint(triada.m_lineColor);
                    g.drawLine(prevX, prevY, x, y);
                }
                prevYmap.put(triada, y);

//                g.setPaint(Color.red);
//                g.drawRect(x - 1, y - 1, 2, 2);
            }
            prevX = x;
        }
    }

    private static TreeMap<Long, TriData> buildTriDataMap(TreeMap<Long, TopsData> topsMap) {
        TreeMap<Long, TriData> triDataMap = new TreeMap<Long, TriData>();
        for (Map.Entry<Long, TopsData> entry : topsMap.entrySet()) {
            long millis = entry.getKey();
            TopsData topsData = entry.getValue();
            TriData triData = new TriData(topsData);
            triDataMap.put(millis, triData);
        }
        return triDataMap;
    }

    private static enum Triada {
        USD_BTC_LTC(Currency.USD, Currency.BTC, Currency.LTC, Color.gray),
////        LTC_USD_BTC(Currency.LTC, Currency.USD, Currency.BTC, Color.gray),
////        BTC_LTC_USD(Currency.BTC, Currency.LTC, Currency.USD, Color.gray),
//
//        USD_LTC_BTC(Currency.USD, Currency.LTC, Currency.BTC, Color.blue),
////        BTC_USD_LTC(Currency.BTC, Currency.USD, Currency.LTC, Color.blue),
////        LTC_BTC_USD(Currency.LTC, Currency.BTC, Currency.USD, Color.blue),
//
        EUR_BTC_LTC(Currency.EUR, Currency.BTC, Currency.LTC, Color.CYAN),
////        LTC_EUR_BTC(Currency.LTC, Currency.EUR, Currency.BTC, Color.CYAN),
////        BTC_LTC_EUR(Currency.BTC, Currency.LTC, Currency.EUR, Color.CYAN),
//
//        EUR_LTC_BTC(Currency.EUR, Currency.LTC, Currency.BTC, Color.magenta),
////        BTC_EUR_LTC(Currency.BTC, Currency.EUR, Currency.LTC, Color.magenta),
////        LTC_BTC_EUR(Currency.LTC, Currency.BTC, Currency.EUR, Color.magenta),
//
        EUR_USD_BTC(Currency.EUR, Currency.USD, Currency.BTC, Color.green),
//
        EUR_LTC_USD(Currency.EUR, Currency.LTC, Currency.USD, Color.orange),
        ;

        private Currency m_start;
        private Currency m_end1;
        private Currency m_end2;
        private PairDirection m_pd1;
        private PairDirection m_pd2;
        private PairDirection m_pd;
        private Color m_lineColor;

        private static boolean s_gotUp;
        private static boolean s_gotDown;

        Triada(Currency start, Currency end1, Currency end2, Color lineColor) {
            m_lineColor = lineColor;
            m_start = start;
            m_end1 = end1;
            m_end2 = end2;
            m_pd1 = PairDirection.get(start, end1);
            m_pd2 = PairDirection.get(start, end2);
            m_pd = PairDirection.get(end1, end2);
        }

        public double calculate(TopsData topsData) {
            TopData b = topsData.get(m_pd1.m_pair); // Pair.BTC_USD
            TopData l = topsData.get(m_pd2.m_pair); // Pair.LTC_USD
            TopData lb = topsData.get(m_pd.m_pair); // Pair.LTC_BTC
            double midB = b.getMid();   // 475.100
            double midL = l.getMid();   // 11.884000
            double midLb = lb.getMid(); // 0.02514
            if(!m_pd1.isForward()) {
                midB = 1/midB;
            }
            if(!m_pd2.isForward()) {
                midL = 1/midL;
            }
            if(!m_pd.isForward()) {
                midLb = 1/midLb;
            }

            double midLLb = midL/midLb;
            double ratio = midB/midLLb;
if(this == EUR_BTC_LTC){
    if(ratio > 1.005) {
        if(!s_gotUp) {
            System.out.println("> " + ratio);
            System.out.println(" " + m_pd1.m_pair + " " + b);
            System.out.println(" " + m_pd2.m_pair + " " + l);
            System.out.println(" " + m_pd.m_pair + " " + lb);
            s_gotUp = true;
        }
    } else if(ratio < 0.995) {
        if(!s_gotDown) {
            System.out.println("< " + ratio);
            System.out.println(" " + m_pd1.m_pair + " " + b);
            System.out.println(" " + m_pd2.m_pair + " " + l);
            System.out.println(" " + m_pd.m_pair + " " + lb);
            s_gotDown = true;
        }
    }
}
            return ratio;
        }
    }

    private static class TriData {
        private TopsData m_topsData;
        private Map<Triada, Double> m_ratioMap = new HashMap<Triada, Double>(Triada.values().length);

        public TriData(TopsData topsData) {
            m_topsData = topsData;
            for(Triada triada: Triada.values()) {
                double ratio = triada.calculate(topsData);
                m_ratioMap.put(triada, ratio);
            }
        }
    }
}
