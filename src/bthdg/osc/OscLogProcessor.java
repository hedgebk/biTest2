package bthdg.osc;

import bthdg.BaseChartPaint;
import bthdg.PaintChart;
import bthdg.exch.BaseExch;
import bthdg.exch.Direction;
import bthdg.exch.OrderSide;
import bthdg.exch.TradeData;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OscLogProcessor extends BaseChartPaint {
    private static int WIDTH = 16000;
    public static int HEIGHT = 1000;
    public static final int X_FACTOR = 5; // more points
    public static int DIRECTION_MARK_RADIUS = 50;
    private static int OSCS_RADIUS;
    private static int OSCS_OFFSET;
    public static final long AVG_PRICE_TIME = Utils.toMillis(20, 0);
    public static final double FILTER_GAIN_SPIKES_RATIO = 1.02; // filter out >2% gain spikes

    private static final Color[] OSC_COLORS = new Color[]{Color.ORANGE, Color.BLUE, Color.MAGENTA, Color.PINK, Color.CYAN, Color.GRAY, Color.YELLOW, Color.GREEN};

    private static final Pattern TRADE_PATTERN = Pattern.compile("(\\d+): State.onTrade\\(tData=TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+).+");
    private static final Pattern DIRECTION_ADJUSTED_PATTERN = Pattern.compile("(\\d+):   directionAdjusted=([\\+\\-]?\\d+\\.[\\d\\-E]+);.*");
    private static final Pattern PLACE_ORDER_PATTERN = Pattern.compile("(\\d+):    orderData=OrderData\\{status=NEW,.*side=(.*), amount=(\\d+\\.\\d+), price=(\\d+\\.\\d+),.*");
    private static final Pattern TOP_DATA_PATTERN = Pattern.compile("(\\d+):  topsData'=\\w+\\[\\w+=Top\\{bid=([\\d,\\.]+)\\, ask=([\\d,\\.]+)\\, last.*");
    private static final Pattern OSC_BAR_PATTERN = Pattern.compile("(\\d+).*\\[(\\d+)\\] bar\\s+\\d+\\s+(\\d\\.[\\d\\-E]+)\\s+(\\d\\.[\\d\\-E]+)");
    private static final Pattern AVG_TREND_PATTERN = Pattern.compile("(\\d+):\\s+avg1=(\\d+\\.\\d+)\\savg2=(\\d+\\.\\d+);\\slast=(\\d+\\.\\d+);\\soldest=(\\d+\\.\\d+); trend=([-\\d]+\\.[\\d-E]+)");
    private static final Pattern GAIN_PATTERN = Pattern.compile("(\\d+):\\s+GAIN: Btc=(\\d+\\.\\d+); Cnh=(\\d+\\.\\d+) CNH; avg=(\\d+\\.\\d+); projected=(\\d+\\.\\d+).*");
    private static final Pattern BOOSTED_PATTERN = Pattern.compile("(\\d+):\\s+boosted from ([\\+\\-]?\\d\\.[\\d+\\-E]+) to ([\\+\\-]?\\d\\.[\\d+\\-E]+)");
    private static final Pattern CHILLED_PATTERN = Pattern.compile("(\\d+):\\s+direction chilled from ([\\+\\-]?\\d\\.[\\d+\\-E]+) to ([\\+\\-]?\\d\\.[\\d+\\-E]+)");
    private static final Pattern START_LEVEL_PATTERN = Pattern.compile("(\\d+): \\[(\\d+)\\] start level reached for (\\w+); .*");

    public static final String DIRECTION_ADJUSTED = "   directionAdjusted=";
    public static final String CHILLED_FROM = "chilled from";
    public static final String[] AFTER_DIRECTION = new String[]{"boosted from ", CHILLED_FROM, DIRECTION_ADJUSTED};
    public static final String[] AFTER_BOOSTED = new String[]{CHILLED_FROM, DIRECTION_ADJUSTED};

    private static final List<TradeData> s_trades = new ArrayList<TradeData>();
    private static ArrayList<PlaceOrderData> s_placeOrders = new ArrayList<PlaceOrderData>();
    private static HashMap<Long, Double> s_bidPrice = new HashMap<Long, Double>();
    private static HashMap<Long, Double> s_askPrice = new HashMap<Long, Double>();
    private static Double s_lastMidPrice;
    private static ArrayList<OscData> s_oscs = new ArrayList<OscData>();
    private static int s_maxOscIndex = 0;
    private static TreeMap<Long,Double> s_avgPrice = new TreeMap<Long, Double>();
    private static TreeMap<Long, Double> s_avg1 = new TreeMap<Long, Double>();
    private static TreeMap<Long, Double> s_avg2 = new TreeMap<Long, Double>();
    private static TreeMap<Long, Double> s_gain = new TreeMap<Long, Double>();
    private static Utils.DoubleMinMaxCalculator<Double> gainCalc = new Utils.DoubleMinMaxCalculator<Double>() {
        public Double getValue(Double val) {
            if ((m_maxValue != null) && (val > m_maxValue * FILTER_GAIN_SPIKES_RATIO)) {
                return m_maxValue; // filter huge jumps
            }
            if ((m_minValue != null) && (val < m_minValue / FILTER_GAIN_SPIKES_RATIO)) {
                return m_minValue; // filter huge jumps
            }
            return val;
        }
    };
    private static ArrayList<DirectionsData> s_directions = new ArrayList<DirectionsData>();
    private static Map<Integer, OscData> s_lastOscs = new HashMap<Integer, OscData>();

    private static void initOscsRadiusOffset() {
        OSCS_RADIUS = DIRECTION_MARK_RADIUS * 4;
        OSCS_OFFSET = DIRECTION_MARK_RADIUS * 2;
    }

    public static void main(String[] args) {
        try {
            Properties keys = BaseExch.loadKeys();

            String logFile = init(keys);

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

    private static String init(Properties keys) {

        String widthStr = keys.getProperty("osc.log_processor.width");
        if (widthStr != null) {
            System.out.println("widthStr: " + widthStr);
            WIDTH = Integer.parseInt(widthStr);
        }
        String heightStr = keys.getProperty("osc.log_processor.height");
        if (heightStr != null) {
            System.out.println("heightStr: " + heightStr);
            HEIGHT = Integer.parseInt(heightStr);
        }
        String radiusStr = keys.getProperty("osc.log_processor.radius");
        if (radiusStr != null) {
            System.out.println("radiusStr: " + radiusStr);
            DIRECTION_MARK_RADIUS = Integer.parseInt(radiusStr);
        }

        initOscsRadiusOffset();

        String logFile = keys.getProperty("osc.log_processor.file");
        System.out.println("logFile: " + logFile);
        return logFile;
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
        ChartAxe priceAxe = new PaintChart.ChartAxe(minPrice, maxPrice, HEIGHT - OSCS_RADIUS * 3);
        priceAxe.m_offset = OSCS_RADIUS * 3 / 2;
        Double minGain = gainCalc.m_minValue;
        Double maxGain = gainCalc.m_maxValue;
        ChartAxe gainAxe = new PaintChart.ChartAxe(minGain, maxGain, OSCS_RADIUS);

        System.out.println("minGain: " + minGain + "; maxGain=" + maxGain);
        System.out.println("time per pixel: " + Utils.millisToDHMSStr((long) timeAxe.m_scale));

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB ); /*TYPE_USHORT_565_RGB*/
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

        paintOscs(priceAxe, timeAxe, g);
        paintDirections(priceAxe, timeAxe, g);
        paintTops(priceAxe, timeAxe, g);
        paintTrades(priceAxe, timeAxe, g);
        paintAvg(priceAxe, timeAxe, g);
        paintPlaceOrder(priceAxe, timeAxe, g);
        paintAvgPrice(priceAxe, timeAxe, g, gainAxe);
        paintGain(priceAxe, timeAxe, gainAxe, g);

        writeAndShowImage(image);
    }

    private static void paintAvg(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        BasicStroke gainStroke = new BasicStroke(2);
        Stroke old = g.getStroke();
        g.setStroke(gainStroke);
        paintAvg(s_avg1, priceAxe, timeAxe, g, Color.CYAN);
        paintAvg(s_avg2, priceAxe, timeAxe, g, Color.ORANGE);
        g.setStroke(old);
    }

    private static void paintAvg(TreeMap<Long, Double> avgs, ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g, Color cyan) {
        g.setPaint(cyan);
        int lastX = -1;
        int lastY = -1;
        for (Map.Entry<Long, Double> entry : avgs.entrySet()) {
            Long stamp = entry.getKey();
            Double avgPrice = entry.getValue();
            int x = timeAxe.getPoint(stamp);
            int y = priceAxe.getPointReverse(avgPrice);
            if (lastX != -1) {
                g.drawLine(lastX, lastY, x, y);
            }
            lastX = x;
            lastY = y;
        }
    }

    private static void paintGain(ChartAxe priceAxe, ChartAxe timeAxe, ChartAxe gainAxe, Graphics2D g) {
        BasicStroke gainStroke = new BasicStroke(2);
        Stroke old = g.getStroke();
        g.setStroke(gainStroke);
        int lastX = -1;
        int lastY = -1;
        int lastGainDeltaY = -1;
        for (Map.Entry<Long, Double> entry : s_gain.entrySet()) {
            Long time = entry.getKey();
            Double gain = entry.getValue();

            Map.Entry<Long, Double> avgEntry = s_avgPrice.floorEntry(time);
            if (avgEntry != null) {
                Double avgPrice = avgEntry.getValue();
                int avgY = priceAxe.getPointReverse(avgPrice);
                int gainDeltaY = gainAxe.getPointReverse(gain);

                int x = timeAxe.getPoint(time);
                int y = avgY + OSCS_OFFSET + gainDeltaY;

                if (lastX != -1) {
                    Color color = (gainDeltaY > lastGainDeltaY) ? Color.RED : (gainDeltaY < lastGainDeltaY) ? Color.GREEN : Color.GRAY;
                    g.setPaint(color);
                    g.drawLine(lastX, lastY, x, y);
                    g.drawRect(x - 2, y - 2, 4, 4);
                }
                lastX = x;
                lastY = y;
                lastGainDeltaY = gainDeltaY;
            }
        }
        g.setStroke(old);
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
                Boolean startLevelBuy = osc.m_startLevelBuy;
                int y1 = (int) (y - (OSCS_OFFSET + OSCS_RADIUS * osc1));
                int y2 = (int) (y - (OSCS_OFFSET + OSCS_RADIUS * osc2));
                if (lastX[index] != 0) {
                    Color color = OSC_COLORS[index % OSC_COLORS.length];
                    g.setPaint(color);
                    g.drawLine(lastX[index], lastY1[index], x, y1);
                    g.drawLine(lastX[index], lastY2[index], x, y2);
                    g.drawRect(x - 1, y1 - 1, 2, 2);
                    g.drawRect(x - 1, y2 - 1, 2, 2);
                    if (startLevelBuy != null) {
                        g.drawLine(x, y1, x, y1 + (startLevelBuy ? -1 : 1) * OSCS_RADIUS / 3);
                    }
                }
                lastX[index] = x;
                lastY1[index] = y1;
                lastY2[index] = y2;
            }
        }
        TrendWatcher trendWatcher = new TrendWatcher(0.01);
        Double[] midOscs = new Double[oscNum];
        Integer prevAvgOscY = null;
        int prevAvgOscX = 0;
        Double prevPrevAvgOsc = null;
//        Double prevBlendAvgOsc = null;
        Double prevAvgOsc = null;
        BasicStroke avgOscStroke = new BasicStroke(4);
        Stroke old = g.getStroke();
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
                double oscMid = (osc1 + osc2) / 2;
                midOscs[index] = oscMid;
                Double avgOsc = 0.0;
                for (int i = 0; i < oscNum; i++) {
                    Double mid = midOscs[i];
                    if (mid == null) {
                        avgOsc = null;
                        break;
                    }
                    avgOsc += mid;
                }
                if (avgOsc != null) {
                    avgOsc /= oscNum;
                    trendWatcher.update(avgOsc);
                    if ((prevAvgOsc != null) && (prevPrevAvgOsc != null)) {
                        double blendAvgOsc = (avgOsc + prevAvgOsc + prevPrevAvgOsc) / 3; // blend 3 last values
                        int avgOscY = (int) (y - (OSCS_OFFSET + OSCS_RADIUS * blendAvgOsc));
                        Direction direction = trendWatcher.m_direction;
                        if (direction != null) {
                            g.setStroke(avgOscStroke);
                            g.setPaint((direction == Direction.FORWARD) ? Colors.DARK_GREEN : Color.red);
                            g.drawLine(prevAvgOscX, prevAvgOscY, x, avgOscY);
                            g.setStroke(old);
                        }

//                        if (prevBlendAvgOsc != null) {
//                            g.setStroke(avgOscStroke);
//                            Boolean down = (blendAvgOsc < prevBlendAvgOsc) ? Boolean.TRUE : (blendAvgOsc > prevBlendAvgOsc) ? Boolean.FALSE : null;
//                            g.setPaint((down == null) ? Color.gray : down ? Color.red : Colors.DARK_GREEN);
//                            g.drawLine(prevAvgOscX, prevAvgOscY, x, avgOscY);
//                            g.setStroke(old);
//                            g.drawLine(x, avgOscY, x, avgOscY + ((down == null) ? 0 : down ? 1 : -1) * OSCS_OFFSET / 10);
//                        }
//                        prevBlendAvgOsc = blendAvgOsc;

                        prevAvgOscY = avgOscY;
                        prevAvgOscX = x;
                    }
                    prevPrevAvgOsc = prevAvgOsc;
                    prevAvgOsc = avgOsc;
                }
            }
        }
        g.setStroke(old);
    }

    private static void paintAvgPrice(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g, ChartAxe gainAxe) {
        double m_max = gainAxe.m_max;
        double m_min = gainAxe.m_min; // [min ... 1 ... max]

        int from = (int) ((m_min - 1) / 0.001);
        int to = (int) ((m_max - 1) / 0.001);

        int lastX = -1;
        int lastY = -1;
        int dy1 = OSCS_OFFSET;
        int dy2 = dy1 + OSCS_RADIUS;
        int dy3 = dy1 + OSCS_RADIUS * 2 / 10;
        int dy4 = dy1 + OSCS_RADIUS * 8 / 10;
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
                g.drawLine(lastX, lastY - dy3, x, y - dy3);
                g.drawLine(lastX, lastY - dy4, x, y - dy4);
                g.setPaint(Color.magenta);
                g.drawLine(lastX, lastY + dy1, x, y + dy1);
                g.drawLine(lastX, lastY + dy2, x, y + dy2);
                for (int k = from; k <= to; k++) {
                    if (k != 1) {
                        int offset = gainAxe.getPoint(1 + k * 0.001);
                        g.drawLine(lastX, lastY + dy2 - offset, x, y + dy2 - offset);
                    }
                }
                g.setPaint(Colors.LIGHT_BLUE);
                int zeroGainOffset = gainAxe.getPoint(1);
                g.drawLine(lastX, lastY + dy2 - zeroGainOffset, x, y + dy2 - zeroGainOffset);
            }
            lastX = x;
            lastY = y;
        }
    }

    private static void paintTops(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setPaint(Color.orange);
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
        g.setFont(g.getFont().deriveFont(9f));
        FontMetrics fontMetrics = g.getFontMetrics();
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

            String orderId = placeOrder.m_orderId;
            if (orderId != null) {
                double size = placeOrder.m_size;
                String str = size + " " + orderId;
                int strWidth = fontMetrics.stringWidth(str);
                int textX = x + 2;
                int textY = y + DIRECTION_MARK_RADIUS + 10 + strWidth;

                AffineTransform orig = g.getTransform();
                g.translate(textX, textY);
                g.rotate(-Math.PI / 2);
                g.setColor(Color.darkGray);
                g.drawString(str, 0, 0);
                g.setTransform(orig);
            }
        }
    }

    private static void paintDirections(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setFont(g.getFont().deriveFont(9f));
        Integer prevX = null;
        Integer prevY = null;
        for (DirectionsData dData : s_directions) {
            Long stamp = dData.m_millis;
            Double direction = dData.m_directionAdjusted;
            Double boostedFrom = dData.m_boostedFrom;
            Double boostedTo = dData.m_boostedTo;
            Double chilledFrom = dData.m_chilledFrom;
            Double chilledTo = dData.m_chilledTo;
            Double basePrice = dData.m_lastMidPrice;
            if(basePrice == null) {
                continue;
            }
            int x = timeAxe.getPoint(stamp);
            int y = priceAxe.getPointReverse(basePrice);
            g.setPaint(Color.lightGray);
            g.drawLine(x, y - DIRECTION_MARK_RADIUS, x, y + DIRECTION_MARK_RADIUS);
            g.drawLine(x - 1, y, x + 1, y);
            int y2 = y - (int) (DIRECTION_MARK_RADIUS * direction);
            if (prevX != null) {
                g.drawLine(prevX, prevY, x, y2);
            }
            g.setPaint((direction > 0) ? Color.green : Color.red);
            g.drawLine(x, y, x, y2);
            int dx = 1;
            String str = "";
            if ((boostedFrom != null) && (boostedTo != null)) {
                g.setPaint(Color.blue);
                int y3 = y - (int) (DIRECTION_MARK_RADIUS * boostedFrom);
                int y4 = y - (int) (DIRECTION_MARK_RADIUS * boostedTo);
                int x1 = x + dx;
                g.drawLine(x1, y3, x1, y4);
                dx++;
                str += (Utils.format5(boostedFrom) + "->" + Utils.format5(boostedTo) + " |");
            }
            if ((chilledFrom != null) && (chilledTo != null)) {
                g.setPaint(Color.red);
                int y3 = y - (int) (DIRECTION_MARK_RADIUS * chilledFrom);
                int y4 = y - (int) (DIRECTION_MARK_RADIUS * chilledTo);
                int x1 = x + dx;
                g.drawLine(x1, y3, x1, y4);
                dx++;
                str += (Utils.format5(chilledFrom) + "->" + Utils.format5(chilledTo) + " |");
            }
            prevY = y2;
            prevX = x;
            str += Utils.format5(direction);

            //----------------
            int textX = x;
            int textY = y - DIRECTION_MARK_RADIUS - 10;

            AffineTransform orig = g.getTransform();
            g.translate(textX, textY);
            g.rotate(-Math.PI / 2);
            g.setColor(Color.darkGray);
            g.drawString(str, 0, 0);
            g.setTransform(orig);
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
        } else if(line.contains("] bar") && !line.contains("PREHEAT_BARS_NUM")) { // 1421372554483:  ------------ [3] bar    1421372505000   0.6458513387891542       0.5664781116721086
            processOscBar(line);
        } else if(line.contains("; oldest=")) { // 1421691271077:  avg=1291.1247878050997; last=1291.1247878050997; oldest=1289.7241994383135; trend=1.4005883667862236
            processAvgAndTrend(line);
        } else if(line.contains("GAIN: ")) { // 1422133198048:   GAIN: Btc=0.975798088760157; Cnh=1.0289787120270006 CNH; avg=1.0023884003935788; projected=1.0051339148741418
            processGain(line);
        } else if(line.contains("start level reached for")) { // 1423187861490: [2] start level reached for SELL; stochDiff=0.02202571880404658; startLevel=0.11617121375916264
            processStartLevel(line);
        } else {
            System.out.println("-------- skip line: " + line);
        }
    }

    private static void processGain(String line1) {
        // 1422133198048:   GAIN: Btc=0.975798088760157; Cnh=1.0289787120270006 CNH; avg=1.0023884003935788; projected=1.0051339148741418
        Matcher matcher = GAIN_PATTERN.matcher(line1);
        if (matcher.matches()) {
            String millisStr = matcher.group(1);
            String btcStr = matcher.group(2);
            String cnhStr = matcher.group(3);
            String avgStr = matcher.group(4);
            String projectedStr = matcher.group(5);
            System.out.println("GOT OSC_BAR: millisStr=" + millisStr + "; btcStr=" + btcStr + "; cnhStr=" + cnhStr + "; avgStr=" + avgStr + "; projectedStr=" + projectedStr);
            long millis = Long.parseLong(millisStr);
            double avg = Double.parseDouble(avgStr);
            s_gain.put(millis, avg);
            gainCalc.calculate(avg);
        } else {
            throw new RuntimeException("not matched AVG_TREND line: " + line1);
        }
    }

    private static void processAvgAndTrend(String line1) {
        // 1421691271077:  avg=1291.1247878050997; last=1291.1247878050997; oldest=1289.7241994383135; trend=1.4005883667862236
        // line: 1423530655990:  avg1=1370.9866478873241 avg2=1370.986655462185; last=1370.9866086956522; oldest=1371.0; trend=-0.013391304347805999
        Matcher matcher = AVG_TREND_PATTERN.matcher(line1);
        if (matcher.matches()) {
            String millisStr = matcher.group(1);
            String avgStr1 = matcher.group(2);
            String avgStr2 = matcher.group(3);
            String lastStr = matcher.group(4);
            String oldestStr = matcher.group(5);
            String trendStr = matcher.group(6);
            System.out.println("GOT OSC_BAR: millisStr=" + millisStr + "; avgStr1=" + avgStr1 + "; avgStr2=" + avgStr2 +
                               "; lastStr=" + lastStr + "; oldestStr=" + oldestStr + "; trendStr=" + trendStr);
            long millis = Long.parseLong(millisStr);
            double avg1 = Double.parseDouble(avgStr1);
            double avg2 = Double.parseDouble(avgStr2);
//            double last = Double.parseDouble(lastStr);
//            double oldest = Double.parseDouble(oldestStr);
//            double trend = Double.parseDouble(trendStr);
            s_avg1.put(millis, avg1);
            s_avg2.put(millis, avg2);
        } else {
            throw new RuntimeException("not matched AVG_TREND line: " + line1);
        }
    }

    private static void processStartLevel(String line1) {
        // 1423187861490: [2] start level reached for SELL; stochDiff=0.02202571880404658; startLevel=0.11617121375916264
        Matcher matcher = START_LEVEL_PATTERN.matcher(line1);
        if(matcher.matches()) {
            String millisStr = matcher.group(1);
            String indexStr = matcher.group(2);
            String sideStr = matcher.group(3);
            System.out.println("GOT START_LEVEL: millisStr=" + millisStr + "; indexStr=" + indexStr + "; sideStr=" + sideStr);
//            long millis = Long.parseLong(millisStr);
            int index = Integer.parseInt(indexStr);
            boolean isBuy = sideStr.equals("BUY");
            OscData oscData = s_lastOscs.get(index);
            if (oscData != null) {
                oscData.setStartLevelBuy(isBuy);
            }
        } else {
            throw new RuntimeException("not matched OSC_BAR_PATTERN line: " + line1);
        }
    }

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
            s_lastOscs.put(index, osc);
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
            String amountStr = matcher.group(3);
            String priceStr = matcher.group(4);
            System.out.println("GOT PLACE_ORDER: millisStr=" + millisStr + "; sideStr=" + sideStr + "; amountStr=" + amountStr + "; priceStr=" + priceStr);
            long millis = Long.parseLong(millisStr);
            OrderSide side = OrderSide.getByName(sideStr);
            double size = Double.parseDouble(amountStr);
            double price = Double.parseDouble(priceStr);
            PlaceOrderData opd = new PlaceOrderData(millis, price, side, size);
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
            throw new RuntimeException("not matched PLACE_ORDER_PATTERN line: " + line1);
        }
    }

    private static void processDirectionLine(String line0, BufferedLineReader blr) throws IOException {
        // 1421192392632: processDirection() direction=-2)

        // 1422502222987:  boosted from 0.9166666666666666 to 0.7333861125264423
        // 1422986746884:   direction chilled from -0.5599999999999999 to -0.33599999999999997
        // 1421192392632:   directionAdjusted=-1.0; needBuyBtc=-0.3281232434687124; needSellCnh=-442.251070012
        Map.Entry<Integer, String> entry = waitLineContained(blr, AFTER_DIRECTION);
        if (entry != null) {
            String boostedFromStr;
            String boostedToStr;
            int index = entry.getKey();
            if (index == 0) { // found 'boosted' first
                String line2 = entry.getValue();
                Matcher matcher = BOOSTED_PATTERN.matcher(line2);
                if (matcher.matches()) {
                    boostedFromStr = matcher.group(2);
                    boostedToStr = matcher.group(3);
                    System.out.println("GOT BOOSTED: boostedFromStr=" + boostedFromStr + "; boostedToStr=" + boostedToStr);
                } else {
                    throw new RuntimeException("not matched BOOSTED_PATTERN line: " + line2);
                }
            } else {
                boostedFromStr = null;
                boostedToStr = null;
            }

            entry = waitLineContained(blr, AFTER_BOOSTED);
            if (entry != null) {
                String chilledFromStr;
                String chilledToStr;
                index = entry.getKey();
                if (index == 0) { // found 'chilled' first
                    String line2 = entry.getValue();
                    Matcher matcher = CHILLED_PATTERN.matcher(line2);
                    if (matcher.matches()) {
                        chilledFromStr = matcher.group(2);
                        chilledToStr = matcher.group(3);
                        System.out.println("GOT CHILLED: chilledFromStr=" + chilledFromStr + "; chilledToStr=" + chilledToStr);
                    } else {
                        throw new RuntimeException("not matched CHILLED_PATTERN line: " + line2);
                    }
                } else {
                    chilledFromStr = null;
                    chilledToStr = null;
                }

                String line1 = waitLineContained(blr, DIRECTION_ADJUSTED);
                if (line1 != null) {
                    Matcher matcher = DIRECTION_ADJUSTED_PATTERN.matcher(line1);
                    if(matcher.matches()) {
                        String millisStr = matcher.group(1);
                        String directionAdjustedStr = matcher.group(2);
                        System.out.println("GOT DIRECTION_ADJUSTED: millisStr=" + millisStr + "; directionAdjustedStr=" + directionAdjustedStr);
                        Double boostedFrom = (boostedFromStr != null) ? Double.parseDouble(boostedFromStr) : null;
                        Double boostedTo = (boostedToStr != null) ? Double.parseDouble(boostedToStr) : null;
                        Double chilledFrom = (chilledFromStr != null) ? Double.parseDouble(chilledFromStr) : null;
                        Double chilledTo = (chilledToStr != null) ? Double.parseDouble(chilledToStr) : null;
                        long millis = Long.parseLong(millisStr);
                        double directionAdjusted = Double.parseDouble(directionAdjustedStr);
                        s_directions.add(new DirectionsData(millis, boostedFrom, boostedTo, chilledFrom, chilledTo, directionAdjusted, s_lastMidPrice));
                    } else {
                        throw new RuntimeException("not matched DIRECTION_ADJUSTED_PATTERN line: " + line1);
                    }
                } else {
                    System.out.println("ERROR: not found expected 'directionAdjusted'");
                }
            } else {
                System.out.println("ERROR: not found expected 'directionAdjusted' or 'chilled'");
            }
        } else {
            System.out.println("ERROR: not found expected 'directionAdjusted' or 'boosted' or 'chilled'");
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

    private static Map.Entry<Integer, String> waitLineContained(BufferedLineReader blr, String[] toSearch) throws IOException {
        String line;
        while( (line = blr.getLine()) != null ) {
            for (int i = 0; i < toSearch.length; i++) {
                String searchStr = toSearch[i];
                if(line.contains(searchStr)) {
                    final int finalI = i;
                    final String finalLine = line;
                    return new Map.Entry<Integer,String>() {
                        @Override public Integer getKey() {
                            return finalI;
                        }
                        @Override public String getValue() {
                            return finalLine;
                        }
                        @Override public String setValue(String value) {
                            return null;
                        }
                    };
                }
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
        private final double m_size;
        public String m_orderId;
        public String m_error;
        public Long m_fillTime;

        public PlaceOrderData(long millis, double price, OrderSide side, double size) {
            m_placeMillis = millis;
            m_price = price;
            m_side = side;
            m_size = size;
        }
    }

    private static class OscData {
        private final long m_millis;
        private final int m_index;
        private final double m_osc1;
        private final double m_osc2;
        private Boolean m_startLevelBuy;

        public OscData(long millis, int index, double osc1, double osc2) {
            m_millis = millis;
            m_index = index;
            m_osc1 = osc1;
            m_osc2 = osc2;
        }

        public void setStartLevelBuy(boolean isBuy) {
            m_startLevelBuy = isBuy;
        }
    }

    private static class DirectionsData {
        private final long m_millis;
        private final Double m_boostedFrom;
        private final Double m_boostedTo;
        private final Double m_chilledFrom;
        private final Double m_chilledTo;
        private final double m_directionAdjusted;
        private final Double m_lastMidPrice;

        public DirectionsData(long millis,
                              Double boostedFrom, Double boostedTo,
                              Double chilledFrom, Double chilledTo,
                              double directionAdjusted, Double lastMidPrice) {
            m_millis = millis;
            m_boostedFrom = boostedFrom;
            m_boostedTo = boostedTo;
            m_chilledFrom = chilledFrom;
            m_chilledTo = chilledTo;
            m_directionAdjusted = directionAdjusted;
            m_lastMidPrice = lastMidPrice;
        }
    }

    public static class TrendWatcher {
        protected final double m_tolerance;
        Double m_peak;
        private Double m_peakCandidate;
        Direction m_direction;

        public TrendWatcher(double tolerance) {
            m_tolerance = tolerance;
        }

        public void update(double value) {
            if (m_peak == null) {
                m_peak = value;
            } else {
                double tolerance = getTolerance(value);
                if (m_direction == null) {
                    double diff = value - m_peak;
                    if (diff > tolerance) {
                        m_direction = Direction.FORWARD;
                        m_peakCandidate = value;
                    } else if (diff < -tolerance) {
                        m_direction = Direction.BACKWARD;
                        m_peakCandidate = value;
                    }
                } else {
                    double diff = value - m_peakCandidate;
                    if (m_direction == Direction.FORWARD) {
                        if (diff > 0) {
                            m_peakCandidate = value;
                        } else if (diff < -tolerance) {
                            m_direction = Direction.BACKWARD;
                            m_peak = m_peakCandidate;
                            m_peakCandidate = value;
                        }
                    } else if (m_direction == Direction.BACKWARD) {
                        if (diff < 0) {
                            m_peakCandidate = value;
                        } else if (diff > tolerance) {
                            m_direction = Direction.FORWARD;
                            m_peak = m_peakCandidate;
                            m_peakCandidate = value;
                        }
                    }
                }
            }
        }

        protected double getTolerance(double value) {
            return m_tolerance;
        }
    }
}
