package bthdg.osc;

import bthdg.BaseChartPaint;
import bthdg.PaintChart;
import bthdg.exch.BaseExch;
import bthdg.exch.OrderSide;
import bthdg.exch.TradeData;
import bthdg.util.Utils;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OscLogProcessor extends BaseChartPaint {
    private static final int WIDTH = 20000;
    public static final int HEIGHT = 1000;
    public static final int X_FACTOR = 1; // more points
    public static final int DIRECTION_MARK_RADIUS = 45;
    private static final int OSCS_RADIUS = DIRECTION_MARK_RADIUS * 4;
    private static final int OSCS_OFFSET = DIRECTION_MARK_RADIUS * 2;
    public static final long AVG_PRICE_TIME = Utils.toMillis(20, 0);

    private static final Pattern TRADE_PATTERN = Pattern.compile("(\\d+): State.onTrade\\(tData=TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+).+");
    private static final Pattern DIRECTION_ADJUSTED_PATTERN = Pattern.compile("(\\d+):   directionAdjusted=([\\+\\-]?\\d+\\.\\d+);.*");
    private static final Pattern PLACE_ORDER_PATTERN = Pattern.compile("(\\d+):    orderData=OrderData\\{status=NEW,.*side=(.*), amount=.*, price=(\\d+\\.\\d+),.*");
    private static final Pattern TOP_DATA_PATTERN = Pattern.compile("(\\d+):  topsData'=\\w+\\[\\w+=Top\\{bid=([\\d,\\.]+)\\, ask=([\\d,\\.]+)\\, last.*");
    private static final Color[] OSC_COLORS = new Color[]{Color.ORANGE, Color.BLUE, Color.MAGENTA, Color.PINK, Color.CYAN, Color.GRAY, Color.YELLOW, Color.GREEN};

    private static final List<TradeData> s_trades = new ArrayList<TradeData>();
    private static HashMap<Long, Double> s_directions = new HashMap<Long, Double>();
    private static HashMap<Long, Double> s_directionsBasePrice = new HashMap<Long, Double>();
    private static ArrayList<PlaceOrderData> s_placeOrders = new ArrayList<PlaceOrderData>();
    private static HashMap<Long, Double> s_bidPrice = new HashMap<Long, Double>();
    private static HashMap<Long, Double> s_askPrice = new HashMap<Long, Double>();
    private static Double s_lastMidPrice;
    private static ArrayList<OscData> s_oscs = new ArrayList<OscData>();
    private static int s_maxOscIndex = 0;
    private static TreeMap<Long,Double> s_avgPrice = new TreeMap<Long, Double>();

    public static void main(String[] args) {
        try {
            Properties keys = BaseExch.loadKeys();
            String logFile = keys.getProperty("osc.log_processor.file");
            System.out.println("logFile: " + logFile);
            File file = new File(logFile);
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
        postProcess();
        paint();
    }

    private static void postProcess() {
        Utils.AverageCounter averageCounter = new Utils.AverageCounter(AVG_PRICE_TIME);
        for (TradeData trade : s_trades) {
            long timestamp = trade.m_timestamp;
            double price = trade.m_price;
            double avg = averageCounter.add(timestamp, price);
            s_avgPrice.put(timestamp, avg);
        }
    }

    private static void paint() {
        Utils.DoubleMinMaxCalculator<TradeData> priceCalc = new Utils.DoubleMinMaxCalculator<TradeData>(s_trades) {
            @Override public Double getValue(TradeData tick) { return tick.m_price; }
        };
        double minPrice = priceCalc.m_minValue;
        double maxPrice = priceCalc.m_maxValue;

        Utils.LongMinMaxCalculator<TradeData> timeCalc = new Utils.LongMinMaxCalculator<TradeData>(s_trades) {
            @Override public Long getValue(TradeData tick) { return tick.m_timestamp; }
        };
        long minTimestamp = timeCalc.m_minValue;
        long maxTimestamp = timeCalc.m_maxValue;

        long timeDiff = maxTimestamp - minTimestamp;
        double priceDiff = maxPrice - minPrice;
        System.out.println("min timestamp: " + minTimestamp + ", max timestamp: " + maxTimestamp + ", timestamp diff: " + timeDiff);
        System.out.println("minPrice = " + minPrice + ", maxPrice = " + maxPrice + ", priceDiff = " + priceDiff);

        ChartAxe timeAxe = new PaintChart.ChartAxe(minTimestamp, maxTimestamp, WIDTH);
        ChartAxe priceAxe = new PaintChart.ChartAxe(minPrice, maxPrice, HEIGHT - OSCS_RADIUS);
        priceAxe.m_offset = OSCS_RADIUS;

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

        paintDirections(priceAxe, timeAxe, g);
        paintTops(priceAxe, timeAxe, g);
        paintTrades(priceAxe, timeAxe, g);
        paintPlaceOrder(priceAxe, timeAxe, g);
        paintAvgPrice(priceAxe, timeAxe, g);
        paintOscs(priceAxe, timeAxe, g);

        writeAndShowImage(image);
    }

    private static void paintOscs(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        int oscNum = s_maxOscIndex + 1;
        int[] lastX = new int[oscNum];
        int[] lastY1 = new int[oscNum];
        int[] lastY2 = new int[oscNum];
        for (OscData osc : s_oscs) {
            int index = osc.m_index;
            long time = osc.m_millis;
            Map.Entry<Long, Double> entry = s_avgPrice.floorEntry(time);
            if (entry != null) {
                Double avgPrice = entry.getValue();
                int x = timeAxe.getPoint(time);
                int y = priceAxe.getPointReverse(avgPrice);

                double osc1 = osc.m_osc1;
                double osc2 = osc.m_osc2;
                int y1 = (int) (y - (OSCS_OFFSET + OSCS_RADIUS * osc1));
                int y2 = (int) (y - (OSCS_OFFSET + OSCS_RADIUS * osc2));
                if (lastX[index] != 0) {
                    Color color = OSC_COLORS[index % OSC_COLORS.length];
                    g.setPaint(color);
                    int prevX = lastX[index];
                    g.drawLine(prevX, lastY1[index], x, y1);
                    g.drawLine(lastX[index], lastY2[index], x, y2);
                    g.drawRect(x - 1, y1 - 1, 2, 2);
                    g.drawRect(x - 1, y2 - 1, 2, 2);
                }
                lastX[index] = x;
                lastY1[index] = y1;
                lastY2[index] = y2;
            }
        }
    }

    private static void paintAvgPrice(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        int lastX = -1;
        int lastY = -1;
        int dy1 = OSCS_OFFSET;
        int dy2 = dy1 + OSCS_RADIUS;
        for (Map.Entry<Long, Double> entry : s_avgPrice.entrySet()) {
            Long time = entry.getKey();
            Double price = entry.getValue();
            int x = timeAxe.getPoint(time);
            int y = priceAxe.getPointReverse(price);
            if (lastX != -1) {
                g.setPaint(Color.orange);
                g.drawLine(lastX, lastY, x, y);
                g.setPaint(Color.lightGray);
                g.drawLine(lastX, lastY - dy1, x, y - dy1);
                g.drawLine(lastX, lastY - dy2, x, y - dy2);
            }
            lastX = x;
            lastY = y;
        }
    }

    private static void paintTops(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setPaint(Color.black);
        for (Map.Entry<Long, Double> entry : s_bidPrice.entrySet()) {
            Long stamp = entry.getKey();
            Double bidPrice = entry.getValue();
            Double askPrice = s_askPrice.get(stamp);
            int x = timeAxe.getPoint(stamp);
            int y1 = priceAxe.getPointReverse(bidPrice);
            int y2 = priceAxe.getPointReverse(askPrice);
            g.drawLine(x, y1, x, y1);
            g.drawLine(x, y2, x, y2);
        }
    }

    private static void paintPlaceOrder(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        for (PlaceOrderData placeOrder : s_placeOrders) {
            Long stamp = placeOrder.m_placeMillis;
            Double price = placeOrder.m_price;
            OrderSide orderSide = placeOrder.m_side;
            int x = timeAxe.getPoint(stamp);
            int y = priceAxe.getPointReverse(price);
            g.setPaint(orderSide.isBuy() ? Color.green : Color.red);
            g.drawRect(x - 2, y - 2, 4, 4);

            Long fillTime = placeOrder.m_fillTime;
            if (fillTime != null) {
                int x2 = timeAxe.getPoint(fillTime);
                g.drawLine(x, y, x2, y);
            }
        }
    }

    private static void paintDirections(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        for (Map.Entry<Long, Double> entry : s_directions.entrySet()) {
            Long stamp = entry.getKey();
            Double direction = entry.getValue();
            Double basePrice = s_directionsBasePrice.get(stamp);
            int x = timeAxe.getPoint(stamp);
            int y = priceAxe.getPointReverse(basePrice);
            g.setPaint(Color.lightGray);
            g.drawLine(x, y - DIRECTION_MARK_RADIUS, x, y + DIRECTION_MARK_RADIUS);
            if (direction > 0) {
                g.setPaint(Color.green);
                g.drawLine(x, y, x, y - (int) (DIRECTION_MARK_RADIUS * direction));
            } else if (direction < 0) {
                g.setPaint(Color.red);
                g.drawLine(x, y, x, y - (int) (DIRECTION_MARK_RADIUS * direction));
            }
        }
    }

    private static void paintTrades(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setPaint(Color.blue);
        for (TradeData trade: s_trades) {
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
        } else if(line.contains(":  topsData'=TopsData[")) { // 1421196514606:  topsData'=TopsData[BTC_CNH=Top{bid=1,326.2100, ask=1,326.2400, last=0.0000}; }
            processTopDataLine(line, blr);
        } else if(line.contains("] bar")) { // 1421372554483:  ------------ [3] bar    1421372505000   0.6458513387891542       0.5664781116721086
            processOscBar(line);
// 1421373132043:   directionAdjusted=-0.25; needBuyBtc=-0.05826512553126485; needSellCnh=-75.331272125
        } else {
            System.out.println("-------- skip line: " + line);
        }
    }

    private static final Pattern OSC_BAR_PATTERN = Pattern.compile("(\\d+).*\\[(\\d+)\\] \\w+\\s+\\d+\\s+(\\d\\.[\\d\\-E]+)\\s+(\\d\\.[\\d\\-E]+)");
    private static void processOscBar(String line1) {
        // 1421372554483:  ------------ [3] bar    1421372505000   0.6458513387891542       0.5664781116721086
        // 1421388199512:  ------------ [2] bar	1421388150000	5.493781175722471E-4	 1.8312603919074902E-4
        Matcher matcher = OSC_BAR_PATTERN.matcher(line1);
        if(matcher.matches()) {
            String millisStr = matcher.group(1);
            String indexStr = matcher.group(2);
            String osc1str = matcher.group(3);
            String osc2str = matcher.group(4);
            System.out.println("GOT OSC_BAR: millisStr=" + millisStr + "; indexStr=" + indexStr + "; osc1str=" + osc1str + "; osc2str=" + osc2str);
            long millis = Long.parseLong(millisStr);
            int index = Integer.parseInt(indexStr);
            double osc1 = Double.parseDouble(osc1str);
            double osc2 = Double.parseDouble(osc2str);
            OscData osc = new OscData(millis, index, osc1, osc2);
            s_oscs.add(osc);
            s_maxOscIndex = Math.max(s_maxOscIndex, index);
        } else {
            throw new RuntimeException("not matched OSC_BAR_PATTERN line: " + line1);
        }
    }

    private static void processTopDataLine(String line1, BufferedLineReader blr) {
        // 1421196514606:  topsData'=TopsData[BTC_CNH=Top{bid=1,326.2100, ask=1,326.2400, last=0.0000}; }
        Matcher matcher = TOP_DATA_PATTERN.matcher(line1);
        if(matcher.matches()) {
            String millisStr = matcher.group(1);
            String bidStr = matcher.group(2);
            String askStr = matcher.group(3);
            System.out.println("GOT TOP_DATA: millisStr=" + millisStr + "; bidStr=" + bidStr + "; askStr=" + askStr);
            long millis = Long.parseLong(millisStr);
            double bid = Double.parseDouble(bidStr.replace(",", ""));
            double ask = Double.parseDouble(askStr.replace(",", ""));
            s_bidPrice.put(millis, bid);
            s_askPrice.put(millis, ask);
            s_lastMidPrice = (bid+ask)/2;
        } else {
            throw new RuntimeException("not matched TOP_DATA_PATTERN line: " + line1);
        }
    }

    private static void processPlaceOrderLine(String line1, BufferedLineReader blr) throws IOException {
        // 1421193272478:    orderData=OrderData{status=NEW, pair=BTC_CNH, side=BUY, amount=0.08500, price=1347.99000, state=NONE, filled=0.00000}
        Matcher matcher = PLACE_ORDER_PATTERN.matcher(line1);
        if (matcher.matches()) {
            String millisStr = matcher.group(1);
            String sideStr = matcher.group(2);
            String priceStr = matcher.group(3);
            System.out.println("GOT PLACE_ORDER: millisStr=" + millisStr + "; sideStr=" + sideStr + "; priceStr=" + priceStr);
            long millis = Long.parseLong(millisStr);
            OrderSide side = OrderSide.getByName(sideStr);
            double price = Double.parseDouble(priceStr);
            PlaceOrderData opd = new PlaceOrderData(millis, price, side);
            s_placeOrders.add(opd);

            String line2 = waitLineContained(blr, "PlaceOrderData: PlaceOrderData{");
            if (line2 != null) {
                System.out.println("GOT PlaceOrderData: " + line2);
//                // PlaceOrderData: PlaceOrderData{orderId=96465911, remains=0.0, received=0.0}
                int indx1 = line2.indexOf("orderId=");
                if (indx1 != -1) {
                    int indx2 = line2.indexOf(",", indx1);
                    String orderId = line2.substring(indx1 + 8, indx2);
                    System.out.println(" orderId: " + orderId);
                    opd.m_orderId = orderId;

                    // 1421411215853: $$$$$$   order FILLED: OrderData{id=96872920 status=FILLED, pair=BTC_CNH, side=BUY, amount=0.05580, price=1279.27000, state=NONE, filled=0.05580}
                    String line3 = waitLineContained(blr, "order FILLED: OrderData{id=" + orderId);
                    if (line3 != null) {
                        System.out.println("  GOT order FILLED: " + line3);
                        int indx3 = line3.indexOf(":");
                        if (indx3 != -1) {
                            String time = line3.substring(0, indx3);
                            Long millis2 = Long.parseLong(time);
                            System.out.println("   extracted time : " + millis2);
                            opd.m_fillTime = millis2;
                        }
                    }
                } else {
                    // PlaceOrderData: PlaceOrderData{error='place order error: javax.net.ssl.SSLHandshakeException: Remote host closed connection during handshake'}
                    // PlaceOrderData: PlaceOrderData{error='place order error: javax.net.ssl.SSLHandshakeException: Remote host closed connection during handshake'}
                    int indx2 = line2.indexOf("error='");
                    if (indx2 != -1) {
                        int indx3 = line2.indexOf("'", indx2 + 7);
                        String error = line2.substring(indx2 + 7, indx3);
                        System.out.println(" error: " + error);
                        opd.m_error = error;
                    }
                }
            }
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
                s_directions.put(millis, directionAdjusted);
                s_directionsBasePrice.put(millis, s_lastMidPrice);
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
            s_trades.add(tradeData);
        } else {
            throw new RuntimeException("not matched TRADE_PATTERN line: " + line);
        }
    }

    private static class BufferedLineReader {
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
            index=0;
        }
    }

    private static class PlaceOrderData {
        private final long m_placeMillis;
        private final double m_price;
        private final OrderSide m_side;
        public String m_orderId;
        public String m_error;
        public Long m_fillTime;

        public PlaceOrderData(long millis, double price, OrderSide side) {
            m_placeMillis = millis;
            m_price = price;
            m_side = side;
        }
    }

    private static class OscData {
        private final long m_millis;
        private final int m_index;
        private final double m_osc1;
        private final double m_osc2;

        public OscData(long millis, int index, double osc1, double osc2) {
            m_millis = millis;
            m_index = index;
            m_osc1 = osc1;
            m_osc2 = osc2;
        }
    }
}
