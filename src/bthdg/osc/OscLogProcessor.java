package bthdg.osc;

import bthdg.BaseChartPaint;
import bthdg.PaintChart;
import bthdg.exch.OrderSide;
import bthdg.exch.TradeData;
import bthdg.util.Utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OscLogProcessor extends BaseChartPaint {
    private static final String LOG_FILE = "osc.O.log";
    private static final int WIDTH = 3000;
    public static final int HEIGHT = 800;
    public static final int X_FACTOR = 1; // more points

    private static final Pattern TRADE_PATTERN = Pattern.compile("(\\d+): State.onTrade\\(tData=TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+).+");
    private static final Pattern DIRECTION_ADJUSTED_PATTERN = Pattern.compile("(\\d+):   directionAdjusted=([\\+\\-]?\\d+\\.\\d+);.*");
    private static final List<TradeData> m_trades = new ArrayList<TradeData>();
    private static HashMap<Long, Double> m_directions = new HashMap<Long, Double>();
    private static HashMap<Long, Double> m_placeOrdersPrice = new HashMap<Long, Double>();
    private static HashMap<Long, OrderSide> m_placeOrdersSide = new HashMap<Long, OrderSide>();

    public static void main(String[] args) {
        try {
            File file = new File(LOG_FILE);
            FileInputStream fis = new FileInputStream(file);
            try {
                BufferedLineReader blr = new BufferedLineReader(fis);
                try {
                    processLines(blr);
                } finally {
                    blr.close();
                }
            } finally {
                fis.close();
            }
        } catch (Exception e) {
            System.err.println("GOT ERROR: " + e);
            e.printStackTrace();
        }
    }

    private static void processLines(BufferedLineReader blr) throws IOException {
        String line;
        while( (line = blr.getLine()) != null ) {
            processLine(line, blr);
            blr.removeLine();
        }
        paint();
    }

    private static void paint() {
        Utils.DoubleMinMaxCalculator<TradeData> priceCalc = new Utils.DoubleMinMaxCalculator<TradeData>(m_trades) {
            @Override public Double getValue(TradeData tick) { return tick.m_price; }
        };
        double minPrice = priceCalc.m_minValue;
        double maxPrice = priceCalc.m_maxValue;

        Utils.LongMinMaxCalculator<TradeData> timeCalc = new Utils.LongMinMaxCalculator<TradeData>(m_trades) {
            @Override public Long getValue(TradeData tick) { return tick.m_timestamp; }
        };
        long minTimestamp = timeCalc.m_minValue;
        long maxTimestamp = timeCalc.m_maxValue;

        long timeDiff = maxTimestamp - minTimestamp;
        double priceDiff = maxPrice - minPrice;
        System.out.println("min timestamp: " + minTimestamp + ", max timestamp: " + maxTimestamp + ", timestamp diff: " + timeDiff);
        System.out.println("minPrice = " + minPrice + ", maxPrice = " + maxPrice + ", priceDiff = " + priceDiff);

        ChartAxe timeAxe = new PaintChart.ChartAxe(minTimestamp, maxTimestamp, WIDTH);
        ChartAxe priceAxe = new PaintChart.ChartAxe(minPrice, maxPrice, HEIGHT);
        System.out.println("time per pixel: " + Utils.millisToDHMSStr((long) timeAxe.m_scale));

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage./*TYPE_USHORT_565_RGB*/ TYPE_INT_ARGB );
        Graphics2D g = image.createGraphics();
        setupGraphics(g);

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

        paintDirections(timeAxe, g);
        paintTrades(priceAxe, timeAxe, g);
        paintPlaceOrder(priceAxe, timeAxe, g);

        writeAndShowImage(image);
    }

    private static void paintPlaceOrder(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        for (Map.Entry<Long, Double> entry : m_placeOrdersPrice.entrySet()) {
            Long stamp = entry.getKey();
            Double price = entry.getValue();
            OrderSide orderSide = m_placeOrdersSide.get(stamp);
            int x = timeAxe.getPoint(stamp);
            int y = priceAxe.getPointReverse(price);
            g.setPaint(orderSide.isBuy() ? Color.green : Color.red);
            g.drawRect(x - 2, y - 2, 4, 4);
        }
    }

    private static void paintDirections(ChartAxe timeAxe, Graphics2D g) {
        for (Map.Entry<Long, Double> entry: m_directions.entrySet()) {
            Long stamp = entry.getKey();
            Double direction = entry.getValue();
            int x = timeAxe.getPoint(stamp);
            g.setPaint(Color.lightGray);
            g.drawLine(x, 0, x, HEIGHT);
            if( direction > 0 ) {
                g.setPaint(Color.green);
                g.drawLine(x, (int) (HEIGHT - direction*(HEIGHT/2)), x, HEIGHT);
            } else {
                g.setPaint(Color.red);
                g.drawLine(x, (int) (- direction*(HEIGHT/2)), x, 0);
            }
        }
    }

    private static void paintTrades(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setPaint(Color.red);
        for (TradeData trade: m_trades) {
            double price = trade.m_price;
            int y = priceAxe.getPointReverse(price);
            long stamp = trade.m_timestamp;
            int x = timeAxe.getPoint(stamp);
            g.drawLine(x, y, x, y);
        }
    }

    private static void processLine(String line, BufferedLineReader blr) throws IOException {
        if(line.contains("State.onTrade(tData=TradeData{amount=")) { // 1421188431875: State.onTrade(tData=TradeData{amount=0.50000, price=1350.00000, time=1421188436000, tid=0, type=BID}) on NONE *********************************************
            processTradeLine(line);
        } else if(line.contains("processDirection() direction=")) { // 1421192392632: processDirection() direction=-2)
            processDirectionLine(line, blr);
        } else if(line.contains(":    orderData=OrderData{status=NEW,")) { // 1421193272478:    orderData=OrderData{status=NEW, pair=BTC_CNH, side=BUY, amount=0.08500, price=1347.99000, state=NONE, filled=0.00000}
            processPlaceOrderLine(line, blr);
        } else {
            System.out.println("-------- skip line: " + line);
        }
    }

    private static final Pattern PLACE_ORDER_PATTERN = Pattern.compile("(\\d+):    orderData=OrderData\\{status=NEW,.*side=(.*), amount=.*, price=(\\d+\\.\\d+),.*");

    private static void processPlaceOrderLine(String line1, BufferedLineReader blr) {
        // 1421193272478:    orderData=OrderData{status=NEW, pair=BTC_CNH, side=BUY, amount=0.08500, price=1347.99000, state=NONE, filled=0.00000}
        Matcher matcher = PLACE_ORDER_PATTERN.matcher(line1);
        if(matcher.matches()) {
            String millisStr = matcher.group(1);
            String sideStr = matcher.group(2);
            String priceStr = matcher.group(3);
            System.out.println("GOT PLACE_ORDER: millisStr=" + millisStr + "; sideStr=" + sideStr + "; priceStr=" + priceStr);
            long millis = Long.parseLong(millisStr);
            OrderSide side = OrderSide.getByName(sideStr);
            double price = Double.parseDouble(priceStr);
            m_placeOrdersPrice.put(millis, price);
            m_placeOrdersSide.put(millis, side);
        } else {
            throw new RuntimeException("not matched DIRECTION_ADJUSTED_PATTERN line: " + line1);
        }
    }

    private static void processDirectionLine(String line0, BufferedLineReader blr) throws IOException {
        // 1421192392632: processDirection() direction=-2)

        // 1421192392632:   directionAdjusted=-1.0; needBuyBtc=-0.3281232434687124; needSellCnh=-442.251070012
        String line1 = waitLineContained(blr, "   directionAdjusted=");
        if(line1 != null) {
            Matcher matcher = DIRECTION_ADJUSTED_PATTERN.matcher(line1);
            if(matcher.matches()) {
                String millisStr = matcher.group(1);
                String directionAdjustedStr = matcher.group(2);
                System.out.println("GOT DIRECTION_ADJUSTED: millisStr=" + millisStr + "; directionAdjustedStr=" + directionAdjustedStr);
                long millis = Long.parseLong(millisStr);
                double directionAdjusted = Double.parseDouble(directionAdjustedStr);
                m_directions.put(millis, directionAdjusted);
            } else {
                throw new RuntimeException("not matched DIRECTION_ADJUSTED_PATTERN line: " + line1);
            }
        } else {
            System.out.println("ERROR: not found expected directionAdjusted=");
        }
    }

    private static String waitLineContained(BufferedLineReader blr, String toSearch) throws IOException {
        String line;
        while( (line = blr.getLine()) != null ) {
            if(line.contains(toSearch)) {
                return line;
            }
        }
        return null;
    }

    private static void processTradeLine(String line) {
        // 1421188431875: State.onTrade(tData=TradeData{amount=0.50000, price=1350.00000, time=1421188436000, tid=0, type=BID}) on NONE *********************************************
        Matcher matcher = TRADE_PATTERN.matcher(line);
        if(matcher.matches()) {
            String millisStr = matcher.group(1);
            String priceStr = matcher.group(2);
            System.out.println("GOT TRADE: millisStr=" + millisStr + "; priceStr=" + priceStr);
            long millis = Long.parseLong(millisStr);
            double price = Double.parseDouble(priceStr);
            TradeData tradeData = new TradeData(0, price, millis);
            m_trades.add(tradeData);
        } else {
            throw new RuntimeException("not matched TRADE_PATTERN line: " + line);
        }
    }

    private static class BufferedLineReader {
        public static final int MAX_BUFFER_LINES = 1000;
        private final BufferedReader m_bis;
        private final ArrayList<String> m_buffer = new ArrayList<String>();
        private int index = 0;

        public BufferedLineReader(InputStream is) {
            m_bis = new BufferedReader(new InputStreamReader(is));
        }

        public void close() throws IOException {
            m_bis.close();
        }

        public String getLine() throws IOException {
            if(index < m_buffer.size()) {
                return m_buffer.get(index++);
            }
            if(index > MAX_BUFFER_LINES) {
                throw new RuntimeException("max buffer lines reached");
            }
            return readLine();
        }

        private String readLine() throws IOException {
            String line = m_bis.readLine();
            if(line != null) {
                m_buffer.add(line);
                index++;
            }
            return line;
        }

        public void removeLine() {
            if(m_buffer.size() < 1) {
                throw new RuntimeException("no lines to remove from buffer");
            }
            m_buffer.remove(0);
            index--;
        }
    }
}
