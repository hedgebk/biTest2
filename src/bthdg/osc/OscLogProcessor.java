package bthdg.osc;

import bthdg.BaseChartPaint;
import bthdg.PaintChart;
import bthdg.exch.*;
import bthdg.util.Colors;
import bthdg.util.Utils;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OscLogProcessor extends BaseChartPaint {
    private static final Color[] CHILL_COLORS = new Color[] {Color.red, Color.CYAN, Color.ORANGE, Color.white};
    private static int WIDTH = 16000;
    public static int HEIGHT = 1100;
    public static final int X_FACTOR = 8; // more points
    public static int DIRECTION_MARK_RADIUS = 50;
    private static int OSCS_RADIUS;
    private static int OSCS_OFFSET;
    public static final long AVG_PRICE_TIME = Utils.toMillis(7, 0);
    public static final double FILTER_GAIN_SPIKES_RATIO = 1.009; // filter out >0.9% gain spikes
    public static final double DELTA_THREZHOLD = 0.0008;

    private static final Color[] OSC_COLORS = new Color[]{
            Colors.setAlpha(Color.ORANGE, 100),
            Colors.setAlpha(Color.BLUE, 100),
            Colors.setAlpha(Color.MAGENTA, 100),
            Colors.setAlpha(Color.PINK, 100),
            Colors.setAlpha(Color.CYAN, 100),
            Colors.setAlpha(Color.GRAY, 100),
            Colors.setAlpha(Color.YELLOW, 100),
            Colors.setAlpha(Color.GREEN, 100)
    };

    private static final Pattern TRADE_PATTERN = Pattern.compile("(\\d+): State.onTrade\\(tData=TradeData\\{amount=\\d+\\.\\d+, price=(\\d+\\.\\d+).+");
    private static final Pattern DIRECTION_ADJUSTED_PATTERN = Pattern.compile("(\\d+):   directionAdjusted=([\\+\\-]?\\d+\\.[\\d\\-E]+);.*");
    private static final Pattern TOP_DATA_PATTERN = Pattern.compile("(\\d+):  topsData'=\\w+\\[\\w+=Top\\{bid=([\\d,\\.]+)\\, ask=([\\d,\\.]+)\\, last.*");
    private static final Pattern OSC_BAR_PATTERN = Pattern.compile("(\\d+).*\\[(\\d+)\\] bar\\s+\\d+\\s+(\\d\\.[\\d\\-E]+)\\s+(\\d\\.[\\d\\-E]+)");
    private static final Pattern AVG_TREND_PATTERN = Pattern.compile("(\\d+):\\s+avg1=(\\d\\.[\\d+\\-E]+)\\savg2=(\\d\\.[\\d+\\-E]+)\\savg3=(\\d\\.[\\d+\\-E]+)\\savg4=(\\d\\.[\\d+\\-E]+)\\savg5=(\\d\\.[\\d+\\-E]+);\\slast=(\\d+\\.\\d+);\\soldest=([\\+\\-]?\\d+\\.[\\d+\\-E]+); trend=([-\\d]+\\.[\\d-E]+)");
    private static final Pattern GAIN_PATTERN = Pattern.compile("(\\d+):\\s+GAIN: Btc=(\\d+\\.\\d+); Cnh=(\\d+\\.\\d+) CNH; avg=(\\d+\\.\\d+); projected=(\\d+\\.\\d+).*");
    private static final Pattern BOOSTED_PATTERN = Pattern.compile("(\\d+):\\s+boosted from ([\\+\\-]?\\d\\.[\\d+\\-E]+) to ([\\+\\-]?\\d\\.[\\d+\\-E]+)");
    private static final Pattern CHILLED_PATTERN = Pattern.compile("(\\d+):\\s+direction chilled(\\w) from ([\\+\\-]?\\d\\.[\\d+\\-E]+) to ([\\+\\-]?\\d\\.[\\d+\\-E]+)");
    private static final Pattern START_LEVEL_PATTERN = Pattern.compile("(\\d+): \\[(\\d+)\\] start level reached for (\\w+); .*");
    private static final Pattern AVG_STOCH_PATTERN = Pattern.compile("(\\d+):\\s+updateAvgStoch\\(avgStoch\\=(\\d\\.[\\d+\\-E]+)\\)\\s+blend\\=(\\d\\.[\\d+\\-E]+);.*");
    private static final Pattern PEAK_PATTERN = Pattern.compile("(\\d+):\\s+peak\\=(\\d\\.[\\d+\\-E]+); direction\\=(\\w+)");
    private static final Pattern AVG_STOCH_DELTA_BLEND_PATTERN = Pattern.compile("(\\d+):\\s+avgStochDeltaBlend\\=([\\+\\-]?\\d\\.[\\d+\\-E]+)");
    private static final Pattern AVG_STOCH_DELTA_PATTERN = Pattern.compile("(\\d+):.+; avgStochDelta\\=([\\+\\-]?\\d\\.[\\d+\\-E]+)");

    public static final String DIRECTION_ADJUSTED = "   directionAdjusted=";
    public static final String DIRECTION_CHILLED = "direction chilled";
    public static final String[] AFTER_DIRECTION = new String[]{"boosted from ", DIRECTION_CHILLED, DIRECTION_ADJUSTED};

    private static final List<TradeData> s_trades = new ArrayList<TradeData>();
    private static ArrayList<PlaceOrderData> s_placeOrders = new ArrayList<PlaceOrderData>();
    private static ArrayList<OscData> s_oscs = new ArrayList<OscData>();
    private static int s_maxOscIndex = 0;
    private static Utils.AverageCounter s_averageCounter = new Utils.AverageCounter(AVG_PRICE_TIME);
    private static TreeMap<Long,Double> s_avgPrice = new TreeMap<Long, Double>();
    private static TreeMap<Long, Double> s_avg1 = new TreeMap<Long, Double>(); // parsed avg price 1
    private static TreeMap<Long, Double> s_avg2 = new TreeMap<Long, Double>(); // parsed avg price 2
    private static TreeMap<Long, Double> s_avg3 = new TreeMap<Long, Double>(); // parsed avg price 3
    private static TreeMap<Long, Double> s_avg4 = new TreeMap<Long, Double>(); // parsed avg price 4
    private static TreeMap<Long, Double> s_avg5 = new TreeMap<Long, Double>(); // parsed avg price 5
    private static TreeMap<Long, Double> s_gain = new TreeMap<Long, Double>(); // parsed gains
    private static Utils.DoubleMinMaxCalculator<Double> gainCalc = new Utils.DoubleDoubleMinMaxCalculator() { // calc min/max gain
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
    private static List<AvgStochData> s_avgStochs = new ArrayList<AvgStochData>();
    private static Utils.DoubleDoubleMinMaxCalculator s_avgStochDeltaBlendsMinMaxCalc = new Utils.DoubleDoubleMinMaxCalculator();
    private static TreeMap<Long, double[]> s_avgStochDeltas = new TreeMap<Long, double[]>();
    private static List<Throwable> s_errors = new ArrayList<Throwable>();
    private static TreeMap<Long, TopData> s_topData = new TreeMap<Long, TopData>();

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
                LineReader reader = new LineReader(fis);
                try {
                    processLines(reader);
                } finally {
                    reader.close();
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

    private static void processLines(LineReader reader) throws IOException, InterruptedException {
        long startTime = System.currentTimeMillis();

        AtomicInteger semafore = new AtomicInteger();

        new LineReaderThread(reader, semafore) {
            @Override protected void processTheLine(String line, BufferedLineReader blr) {
                if(line.contains("State.onTrade(tData=TradeData{amount=")) { // 1421188431875: State.onTrade(tData=TradeData{amount=0.50000, price=1350.00000, time=1421188436000, tid=0, type=BID}) on NONE *********************************************
                    processTradeLine(line);
                }
            }
        };
        new LineReaderThread(reader, semafore) {
            @Override protected void processTheLine(String line, BufferedLineReader blr) throws IOException {
                if(line.contains("processDirection() direction=")) { // 1421192392632: processDirection() direction=-2)
                    processDirectionLine(line, blr);
                }
            }
        };
        new LineReaderThread(reader, semafore) {
            @Override protected void processTheLine(String line, BufferedLineReader blr) throws IOException {
                if(line.contains("OrderData{status=NEW,")) { // 1421193272478:    orderData=OrderData{status=NEW, pair=BTC_CNH, side=BUY, amount=0.08500, price=1347.99000, state=NONE, filled=0.00000}
                    processPlaceOrderLine(line, blr);        // // 1425778629766:   placing closeOrder=OrderData{status=NEW, pair=BTC_CNH, side=BUY, amount=0.04465, price=1717.40000, state=NONE, filled=0.00000}
                }
            }
        };
        new LineReaderThread(reader, semafore) {
            @Override protected void processTheLine(String line, BufferedLineReader blr) throws IOException {
                if(line.contains(":  topsData'=TopsData[")) { // 1421196514606:  topsData'=TopsData[BTC_CNH=Top{bid=1,326.2100, ask=1,326.2400, last=0.0000}; }
                    processTopDataLine(line, blr);
                }
            }
        };
        new LineReaderThread(reader, semafore) {
            @Override protected void processTheLine(String line, BufferedLineReader blr) throws IOException {
                if (line.contains("] bar") && !line.contains("PREHEAT_BARS_NUM")) { // 1421372554483:  ------------ [3] bar    1421372505000   0.6458513387891542       0.5664781116721086
                    processOscBar(line);
                } else if (line.contains("start level reached for")) { // 1423187861490: [2] start level reached for SELL; stochDiff=0.02202571880404658; startLevel=0.11617121375916264
                    processStartLevel(line);
                }
            }
        };
        new LineReaderThread(reader, semafore) {
            @Override protected void processTheLine(String line, BufferedLineReader blr) throws IOException {
                // 1421691271077:  avg=1291.1247878050997; last=1291.1247878050997; oldest=1289.7241994383135; trend=1.4005883667862236
                if(line.contains("; oldest=") && !line.contains("Infinity")) {
                    processAvgAndTrend(line);
                }
            }
        };
        new LineReaderThread(reader, semafore) {
            @Override protected void processTheLine(String line, BufferedLineReader blr) throws IOException {
                if(line.contains("GAIN: ")) { // 1422133198048:   GAIN: Btc=0.975798088760157; Cnh=1.0289787120270006 CNH; avg=1.0023884003935788; projected=1.0051339148741418
                    processGain(line);
                }
            }
        };
        new LineReaderThread(reader, semafore) {
            @Override protected void processTheLine(String line, BufferedLineReader blr) throws IOException {
                if(line.contains("updateAvgStoch(")) { // 1424179425710: updateAvgStoch(avgStoch=0.17086689019047155) blend=0.17095343524129952; full=true
                    processAvgStoch(line, blr);
                }
            }
        };

        while (true) {
            synchronized (semafore) {
                int i = semafore.get();
                if (i == 0) {
                    break;
                }
                semafore.wait();
            }
        }

        int linesReaded = reader.m_linesReaded;
        long takes = System.currentTimeMillis() - startTime;
        System.out.println("readed/processed " + linesReaded + " lines in " + takes + " ms (" + (linesReaded / (takes / 1000)) + " l/s)");

        if (s_errors.isEmpty()) {
            paint();
        } else {
            System.out.println("got errors:");
            for (Throwable error : s_errors) {
                System.out.println("error:" + error);
                error.printStackTrace();
            }
        }
    }

    private static void paint() {
        Utils.DoubleMinMaxCalculator<TradeData> priceCalc = new Utils.DoubleMinMaxCalculator<TradeData>(s_trades) {
            @Override public Double getValue(TradeData tick) { return tick.m_price; }
        };

        Utils.LongMinMaxCalculator<TradeData> timeCalc = new Utils.LongMinMaxCalculator<TradeData>(s_trades) {
            @Override public Long getValue(TradeData tick) { return tick.m_timestamp; }
        };
        long minTimestamp = timeCalc.m_minValue;
        long maxTimestamp = timeCalc.m_maxValue;

        long timeDiff = maxTimestamp - minTimestamp;
        double minPrice = priceCalc.m_minValue;
        double maxPrice = priceCalc.m_maxValue;
        double priceDiff = maxPrice - minPrice;
        System.out.println("min timestamp: " + minTimestamp + ", max timestamp: " + maxTimestamp + ", timestamp diff: " + timeDiff);
        System.out.println("minPrice = " + minPrice + ", maxPrice = " + maxPrice + ", priceDiff = " + priceDiff);

        ChartAxe timeAxe = new PaintChart.ChartAxe(minTimestamp, maxTimestamp, WIDTH);
        ChartAxe priceAxe = new PaintChart.ChartAxe(priceCalc, HEIGHT - OSCS_RADIUS * 3 - DIRECTION_MARK_RADIUS);
        priceAxe.m_offset = OSCS_RADIUS * 3 / 2 + DIRECTION_MARK_RADIUS;
        ChartAxe gainAxe = new PaintChart.ChartAxe(gainCalc, OSCS_RADIUS);

        Double minGain = gainCalc.m_minValue;
        Double maxGain = gainCalc.m_maxValue;
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

        int priceStep = 1;
        int priceStart = ((int)minPrice) / priceStep * priceStep;

        // paint left axe
        paintLeftAxeAndGrid(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, WIDTH);
        // paint left axe labels
        paintLeftAxeLabels(minPrice, maxPrice, priceAxe, g, priceStep, priceStart, X_FACTOR);

        // paint time axe labels
        paintTimeAxeLabels(minTimestamp, maxTimestamp, timeAxe, g, HEIGHT, X_FACTOR);

        paintOscs(priceAxe, timeAxe, g);
        System.out.println("paintOscs DONE");
        paintAvgStochs(priceAxe, timeAxe, g);
        System.out.println("paintAvgStochs DONE");
        paintDirections(priceAxe, timeAxe, g);
        System.out.println("paintDirections DONE");
        paintTops(priceAxe, timeAxe, g);
        System.out.println("paintTops DONE");
        paintTrades(priceAxe, timeAxe, g);
        System.out.println("paintTrades DONE");
        paintAvg(priceAxe, timeAxe, g);
        System.out.println("paintAvg DONE");
        paintPlaceOrder(priceAxe, timeAxe, g);
        System.out.println("paintPlaceOrder DONE");
        paintAvgPrice(priceAxe, timeAxe, g, gainAxe);
        System.out.println("paintAvgPrice DONE");
        paintGain(priceAxe, timeAxe, gainAxe, g);
        System.out.println("paintGain DONE");
        paintAvgStochDelta(priceAxe, timeAxe, g);
        System.out.println("paintAvgStochDelta DONE");

        writeAndShowImage(image);
    }

    private static void paintAvg(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        BasicStroke gainStroke = new BasicStroke(2);
        Stroke old = g.getStroke();
        g.setStroke(gainStroke);
        paintAvg(s_avg1, priceAxe, timeAxe, g, Color.CYAN);
        paintAvg(s_avg2, priceAxe, timeAxe, g, Color.ORANGE);
        paintAvg(s_avg3, priceAxe, timeAxe, g, Colors.LIGHT_BLUE);
        paintAvg(s_avg4, priceAxe, timeAxe, g, Colors.BEGIE);
        paintAvg(s_avg5, priceAxe, timeAxe, g, Colors.DARK_RED);
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
        g.setFont(g.getFont().deriveFont(10f));
        FontMetrics fontMetrics = g.getFontMetrics();
        BasicStroke gainStroke = new BasicStroke(2);
        Stroke old = g.getStroke();
        g.setStroke(gainStroke);
        int lastX = -1;
        int lastY = -1;
        int lastGainDeltaY = -1;
        Double lastGain = null;
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

                String str = gain.toString();
                int strWidth = fontMetrics.stringWidth(str);
                int textX = x;
                int textY = y + 10 + strWidth;

                g.setColor(Color.darkGray);
                paint90GradRotatedString(g, str, textX, textY);

                if (lastX != -1) {
                    Color color = (gain > lastGain) ? Color.GREEN : (gain < lastGain) ? Color.RED : Color.GRAY;
                    g.setPaint(color);
                    g.drawLine(lastX, lastY, x, y);
                    g.drawRect(x - 2, y - 2, 4, 4);
                    int y2 = avgY + OSCS_OFFSET + lastGainDeltaY;
                    g.drawLine(x, y2, x, y);
                }
                lastX = x;
                lastY = y;
                lastGainDeltaY = gainDeltaY;
            }
            lastGain = gain;
        }
        g.setStroke(old);
    }


    private static void paintOscs(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setFont(g.getFont().deriveFont(10f));
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
    }

    private static void paintAvgStochs(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        Stroke old = g.getStroke();
        BasicStroke avgStochsStroke = new BasicStroke(4);
        g.setStroke(avgStochsStroke);
//g.setPaint(Color.orange);
        Integer prevAvgStochBlendY = null;
        Integer prevAvgStochX = null;
        for (AvgStochData avgStochData : s_avgStochs) {
            Long time = avgStochData.m_millis;
            Map.Entry<Long, Double> entry2 = s_avgPrice.floorEntry(time);
            if (entry2 != null) {
                Double avgPrice = entry2.getValue();
                int x = timeAxe.getPoint(time);
                int y = priceAxe.getPointReverse(avgPrice);
                Double avgStochBlend = avgStochData.m_blend;
                int avgStochBlendY = (int) (y - (OSCS_OFFSET + OSCS_RADIUS * avgStochBlend));
                if (prevAvgStochBlendY != null) {
                    boolean directionForward = avgStochData.m_directionForward;
                    g.setPaint(directionForward? Colors.DARK_GREEN : Color.red);
                    g.drawLine(prevAvgStochX, prevAvgStochBlendY, x, avgStochBlendY);
                }
                prevAvgStochBlendY = avgStochBlendY;
                prevAvgStochX = x;
            }
        }
        g.setStroke(old);
    }

    private static void paintAvgStochDelta(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setFont(g.getFont().deriveFont(10f));
        Stroke old = g.getStroke();
        BasicStroke doubleStroke = new BasicStroke(2);

        Double minAvgStochDeltaBlend = s_avgStochDeltaBlendsMinMaxCalc.m_minValue;
        Double maxAvgStochDeltaBlend = s_avgStochDeltaBlendsMinMaxCalc.m_maxValue;
        System.out.println("minAvgStochDeltaBlend = " + Utils.format8(minAvgStochDeltaBlend) + ", maxAvgStochDeltaBlend = " + Utils.format8(maxAvgStochDeltaBlend));
        ChartAxe avgStochDeltaBlendAxe = new PaintChart.ChartAxe(s_avgStochDeltaBlendsMinMaxCalc, DIRECTION_MARK_RADIUS * 9 / 4);

        int yDeltaZero = avgStochDeltaBlendAxe.getPoint(0);
        int yDeltaTop = avgStochDeltaBlendAxe.getPoint(DELTA_THREZHOLD);
        int yDeltaBtm = avgStochDeltaBlendAxe.getPoint(-DELTA_THREZHOLD);
        int yDeltaMin = avgStochDeltaBlendAxe.getPoint(minAvgStochDeltaBlend);
        int yDeltaMax = avgStochDeltaBlendAxe.getPoint(maxAvgStochDeltaBlend);
        Integer prevX = null;
        int prevPaintY = 0;
        int prevPaintYblend = 0;
        int prevPaintZeroY = 0;
        int prevPaintTopY = 0;
        int prevPaintBtmY = 0;
        int prevPaintMinY = 0;
        int prevPaintMaxY = 0;
        Integer nextTextX = null;
        for (Map.Entry<Long, double[]> entry : s_avgStochDeltas.entrySet()) {
            Long time = entry.getKey();
            Map.Entry<Long, Double> entry2 = s_avgPrice.floorEntry(time);
            if (entry2 != null) {
                Double avgPrice = entry2.getValue();
                int x = timeAxe.getPoint(time);
                int y = priceAxe.getPointReverse(avgPrice);

                double[] array = entry.getValue();
                double avgStochDelta = array[0];
                double avgStochDeltaBlend = array[1];
                int yDelta = avgStochDeltaBlendAxe.getPoint(avgStochDelta);
                int yDeltaBlend = avgStochDeltaBlendAxe.getPoint(avgStochDeltaBlend);

                int basePaintY = y - OSCS_OFFSET - OSCS_RADIUS - DIRECTION_MARK_RADIUS * 2;
                int paintY = basePaintY - yDelta;
                int paintYblend = basePaintY - yDeltaBlend;
                int paintZeroY = basePaintY - yDeltaZero;
                int paintTopY = basePaintY - yDeltaTop;
                int paintBtmY = basePaintY - yDeltaBtm;
                int paintMinY = basePaintY - yDeltaMin;
                int paintMaxY = basePaintY - yDeltaMax;
                if (prevX != null) {
                    //----------------
                    g.setPaint(Colors.DARK_BLUE);
                    g.drawLine(prevX, prevPaintZeroY, x, paintZeroY);
                    g.drawLine(prevX, prevPaintTopY, x, paintTopY);
                    g.drawLine(prevX, prevPaintBtmY, x, paintBtmY);
                    g.drawLine(prevX, prevPaintMinY, x, paintMinY);
                    g.drawLine(prevX, prevPaintMaxY, x, paintMaxY);
                    g.setPaint((avgStochDelta > 0) ? Color.green : Color.red);
                    g.drawLine(prevX, prevPaintY, x, paintY);
                    g.setStroke(doubleStroke);
                    g.setPaint((avgStochDeltaBlend > 0) ? Colors.DARK_GREEN: Colors.DARK_RED);
                    g.drawLine(prevX, prevPaintYblend, x, paintYblend);
                    g.setStroke(old);

                    int textX = x + 2;
                    if ((nextTextX == null) || (textX >= nextTextX)) {
                        int textY = paintMaxY - 6;
                        g.setColor(Color.darkGray);
                        String str = Utils.format8(avgStochDelta) + "; " + Utils.format8(avgStochDeltaBlend);
                        paint90GradRotatedString(g, str, textX, textY);
                        nextTextX = textX + 11;
                    }
                }
                prevX = x;
                prevPaintY = paintY;
                prevPaintYblend = paintYblend;
                prevPaintZeroY = paintZeroY;
                prevPaintTopY = paintTopY;
                prevPaintBtmY = paintBtmY;
                prevPaintMinY = paintMinY;
                prevPaintMaxY = paintMaxY;
            }
        }
    }

    private static void paintAvgPrice(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g, ChartAxe gainAxe) {
        Stroke old = g.getStroke();
        BasicStroke dblStroke = new BasicStroke(2);
        BasicStroke quadStroke = new BasicStroke(4);
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
        int dy5 = dy2 + DIRECTION_MARK_RADIUS * 2;
        for (Map.Entry<Long, Double> entry : s_avgPrice.entrySet()) {
            Long time = entry.getKey();
            Double price = entry.getValue();
            int x = timeAxe.getPoint(time);
            int y = priceAxe.getPointReverse(price);
            if (lastX != -1) {
                {
                    g.setStroke(dblStroke);
                    g.setPaint(Color.orange); // avg price
                    g.drawLine(lastX, lastY, x, y);
                    g.setPaint(Color.lightGray);
                    g.drawLine(lastX, lastY - dy3, x, y - dy3); // osc borders
                    g.drawLine(lastX, lastY - dy4, x, y - dy4);
                    g.setPaint(Color.black);
                    g.drawLine(lastX, lastY - dy1, x, y - dy1); // osc levels
                    g.drawLine(lastX, lastY - dy2, x, y - dy2);
                    g.setStroke(old);
                }
//                g.setPaint(Color.gray);
//                g.drawLine(lastX, lastY - dy5, x, y - dy5);
                g.setPaint(Colors.LIGHT_MAGNETA);
                g.drawLine(lastX, lastY + dy1, x, y + dy1); // gain borders
                g.drawLine(lastX, lastY + dy2, x, y + dy2);
                for (int k = from; k <= to; k++) {
                    if (k != 1) {
                        int offset = gainAxe.getPoint(1 + k * 0.001);
                        g.drawLine(lastX, lastY + dy2 - offset, x, y + dy2 - offset);
                    }
                }
                g.setPaint(Colors.LIGHT_BLUE);
                int zeroGainOffset = gainAxe.getPoint(1);
                {
                    g.setStroke(quadStroke);
                    g.drawLine(lastX, lastY + dy2 - zeroGainOffset, x, y + dy2 - zeroGainOffset);
                    g.setStroke(old);
                }
            }
            lastX = x;
            lastY = y;
        }
    }

    private static void paintTops(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setPaint(Color.orange);
        for (Map.Entry<Long, TopData> entry : s_topData.entrySet()) {
            Long stamp = entry.getKey();
            TopData topData = entry.getValue();
            double bidPrice = topData.m_bid;
            double askPrice = topData.m_ask;
            int x = timeAxe.getPoint(stamp);
            int y1 = priceAxe.getPointReverse(bidPrice);
            int y2 = priceAxe.getPointReverse(askPrice);
            drawX(g, x, y1, 1);
            drawX(g, x, y2, 1);
        }
    }

    private static void paintPlaceOrder(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setFont(g.getFont().deriveFont(10f));
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
            } else {
                Long cancelTime = placeOrder.m_cancelTime;
                if (cancelTime != null) {
                    int x2 = timeAxe.getPoint(cancelTime);
                    g.setPaint(Color.gray);
                    g.drawLine(x, y, x2, y);
                    g.drawLine(x2 - 1, y - 1, x2 + 1, y + 1);
                    g.drawLine(x2-1, y+1, x2+1, y-1);
                }
            }

            String orderId = placeOrder.m_orderId;
            if (orderId != null) {
                double size = placeOrder.m_size;
                String str = size + " " + orderId;
                int strWidth = fontMetrics.stringWidth(str);
                int textX = x + 2;
                int textY = y + DIRECTION_MARK_RADIUS + 10 + strWidth;
                g.setColor(Color.darkGray);
                paint90GradRotatedString(g, str, textX, textY);
            }
        }
    }

    private static void paint90GradRotatedString(Graphics2D g, String str, double textX, double textY) {
        AffineTransform orig = g.getTransform();
        g.translate(textX, textY);
        g.rotate(-Math.PI / 2);
        g.drawString(str, 0, 0);
        g.setTransform(orig);
    }

    private static void paintDirections(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setFont(g.getFont().deriveFont(10f));
        FontMetrics fontMetrics = g.getFontMetrics();
        Integer prevX = null;
        Integer prevY = null;
        for (DirectionsData dData : s_directions) {
            Long stamp = dData.m_millis;
            Map.Entry<Long, TopData> entry = s_topData.floorEntry(stamp);
            if (entry == null) {
                continue;
            }
            TopData topData = entry.getValue();
            double basePrice = topData.getMid();
            Double direction = dData.m_directionAdjusted;
            Double boostedFrom = dData.m_boostedFrom;
            Double boostedTo = dData.m_boostedTo;
            List<ChilledData> chilled = dData.m_chilled;
            int x = timeAxe.getPoint(stamp);
            int y = priceAxe.getPointReverse(basePrice);
            g.setPaint(Color.lightGray);
            g.drawLine(x, y - DIRECTION_MARK_RADIUS, x, y + DIRECTION_MARK_RADIUS);
            g.drawLine(x - 2, y, x + 2, y);
            int y2 = y - (int) (DIRECTION_MARK_RADIUS * direction);
            if (prevX != null) {
                g.drawLine(prevX, prevY, x, y2);
            }
            g.setPaint((direction > 0) ? Colors.DARK_GREEN: Color.red);
            g.drawLine(x, y, x, y2);
            int dx = 1;
            StringBuilder buff = new StringBuilder();
            if ((boostedFrom != null) && (boostedTo != null)) {
                g.setPaint(Color.blue);
                int y3 = y - (int) (DIRECTION_MARK_RADIUS * boostedFrom);
                int y4 = y - (int) (DIRECTION_MARK_RADIUS * boostedTo);
                int x1 = x + dx;
                g.drawLine(x1, y3, x1, y4);
                dx++;
                buff.append(" b ").append(Utils.format5(boostedFrom)).append("->").append(Utils.format5(boostedTo)).append(" |");
            }
            if(chilled != null) {
                for (int i = 0; i < chilled.size(); i++) {
                    ChilledData chillData = chilled.get(i);
                    String index = chillData.m_index;
                    double chilledFrom = chillData.m_chilledFrom;
                    double chilledTo = chillData.m_chilledTo;
                    Color color = CHILL_COLORS[i % CHILL_COLORS.length];
                    g.setPaint(color);
                    int y3 = y - (int) (DIRECTION_MARK_RADIUS * chilledFrom);
                    int y4 = y - (int) (DIRECTION_MARK_RADIUS * chilledTo);
                    int x1 = x + dx;
                    g.drawLine(x1, y3, x1, y4);
                    dx++;
                    buff.append(" c").append(index).append(" ").append(Utils.format5(chilledFrom)).append("->").append(Utils.format5(chilledTo)).append(" |");
                }
            }

            prevY = y2;
            prevX = x;
            buff.append(" ").append(Utils.format5(direction));

            //----------------
            int textX = x;
            int textY = y - DIRECTION_MARK_RADIUS - 10;
            g.setColor(Color.darkGray);
            String str = buff.toString();
            paint90GradRotatedString(g, str, textX, textY);

            str = "<" + stamp + ">";
            int strWidth = fontMetrics.stringWidth(str);
            textY = y + DIRECTION_MARK_RADIUS * 2 + strWidth + 15;
            g.setColor(Color.darkGray);
            paint90GradRotatedString(g, str, textX, textY);
        }
    }

    private static void paintTrades(ChartAxe priceAxe, ChartAxe timeAxe, Graphics2D g) {
        g.setPaint(Color.blue);
        for (TradeData trade: s_trades) {
            double price = trade.m_price;
            int y = priceAxe.getPointReverse(price);
            long stamp = trade.m_timestamp;
            int x = timeAxe.getPoint(stamp);
            g.drawLine(x-1, y-1, x+1, y+1);
            g.drawLine(x+1, y-1, x-1, y+1);
        }
    }

    private static void processAvgStoch(String line1, BufferedLineReader blr) throws IOException {
//        1424179425710: updateAvgStoch(avgStoch=0.17086689019047155) blend=0.17095343524129952; full=true
//        1424179425710:  peak=0.012103220500638723; direction=FORWARD
//        1424800508226:   m_prevBlend2=0.34408374628441835; blend2=0.34427002718034433; avgStochDelta=0.00019
//        1424800508226:    avgStochDeltaBlend=0.00021488

        Matcher matcher = AVG_STOCH_PATTERN.matcher(line1);
        if (matcher.matches()) {
            String millisStr = matcher.group(1);
            String avgStochStr = matcher.group(2);
            String blendStr = matcher.group(3);
            System.out.println("GOT AVG_STOCH: millisStr=" + millisStr + "; avgStochStr=" + avgStochStr + "; blendStr=" + blendStr);
            String line2 = waitLineContained(blr, ":  peak=");
            if (line2 != null) {
                System.out.println("GOT peak: " + line2);
                matcher = PEAK_PATTERN.matcher(line2);
                if (matcher.matches()) {
                    matcher.group(1);
                    String peakStr = matcher.group(2);
                    String directionStr = matcher.group(3);
                    System.out.println(" GOT PEAK_PATTERN: peakStr=" + peakStr + "; directionStr=" + directionStr);

                    long millis = Long.parseLong(millisStr);
                    double avgStoch = Double.parseDouble(avgStochStr);
                    double blend = Double.parseDouble(blendStr);
                    boolean directionForward = directionStr.equals("FORWARD");
                    AvgStochData avgStochData = new AvgStochData(millis, avgStoch, blend, directionForward);
                    s_avgStochs.add(avgStochData);

                    String line3 = waitLineContained(blr, "; avgStochDelta=");
                    if (line3 != null) {
                        System.out.println("GOT avgStochDelta: " + line3);
                        matcher = AVG_STOCH_DELTA_PATTERN.matcher(line3);
                        if (matcher.matches()) {
                            millisStr = matcher.group(1);
                            String avgStochDeltaStr = matcher.group(2);
                            System.out.println(" GOT AVG_STOCH_DELTA: millisStr=" + millisStr + "; avgStochDeltaStr=" + avgStochDeltaStr);
                            millis = Long.parseLong(millisStr);
                            double avgStochDelta = Double.parseDouble(avgStochDeltaStr);

                            String line4 = waitLineContained(blr, "avgStochDeltaBlend=");
                            if (line4 != null) {
                                System.out.println("GOT avgStochDeltaBlend: " + line4);
                                matcher = AVG_STOCH_DELTA_BLEND_PATTERN.matcher(line4);
                                if (matcher.matches()) {
                                    millisStr = matcher.group(1);
                                    String avgStochDeltaBlendStr = matcher.group(2);
                                    System.out.println(" GOT AVG_STOCH_DELTA_BLEND: millisStr=" + millisStr + "; avgStochDeltaBlendStr=" + avgStochDeltaBlendStr);
                                    millis = Long.parseLong(millisStr);
                                    double avgStochDeltaBlend = Double.parseDouble(avgStochDeltaBlendStr);
                                    s_avgStochDeltas.put(millis, new double[] {avgStochDelta, avgStochDeltaBlend});
                                    s_avgStochDeltaBlendsMinMaxCalc.calculate(avgStochDeltaBlend);
                                } else {
                                    throw new RuntimeException("not matched AVG_STOCH_DELTA_BLEND_PATTERN line: " + line4);
                                }
                            }
                        } else {
                            throw new RuntimeException("not matched AVG_STOCH_DELTA_PATTERN line: " + line3);
                        }
                    }
                } else {
                    throw new RuntimeException("not matched PEAK_PATTERN line: " + line2);
                }
            }
        } else {
            throw new RuntimeException("not matched AVG_STOCH line: " + line1);
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
            throw new RuntimeException("not matched GAIN_PATTERN line: " + line1);
        }
    }

    private static void processAvgAndTrend(String line1) {
        // 1421691271077:  avg=1291.1247878050997; last=1291.1247878050997; oldest=1289.7241994383135; trend=1.4005883667862236
        // 1423530655990:  avg1=1370.9866478873241 avg2=1370.986655462185; last=1370.9866086956522; oldest=1371.0; trend=-0.013391304347805999
        // 1423792012676:  avg1=1371.4557287278853 avg2=1371.3990983606557; last=1371.460410958904; oldest=-4.320119642545493E14; trend=4.3201196425592075E14
        // 1423792012676:  avg1=1371.4557287278853 avg2=1371.3990983606557 avg3=1371.3990983606557; last=1371.460410958904; oldest=-4.320119642545493E14; trend=4.3201196425592075E14
        Matcher matcher = AVG_TREND_PATTERN.matcher(line1);
        if (matcher.matches()) {
            String millisStr = matcher.group(1);
            String avgStr1 = matcher.group(2);
            String avgStr2 = matcher.group(3);
            String avgStr3 = matcher.group(4);
            String avgStr4 = matcher.group(5);
            String avgStr5 = matcher.group(6);
            String lastStr = matcher.group(7);
            String oldestStr = matcher.group(8);
            String trendStr = matcher.group(9);
            System.out.println("GOT OSC_BAR: millisStr=" + millisStr +
                    "; avgStr1=" + avgStr1 + "; avgStr2=" + avgStr2 + "; avgStr3=" + avgStr3 + "; avgStr4=" + avgStr4 + "; avgStr5=" + avgStr5 +
                               "; lastStr=" + lastStr + "; oldestStr=" + oldestStr + "; trendStr=" + trendStr);
            long millis = Long.parseLong(millisStr);
            double avg1 = Double.parseDouble(avgStr1);
            double avg2 = Double.parseDouble(avgStr2);
            double avg3 = Double.parseDouble(avgStr3);
            double avg4 = Double.parseDouble(avgStr4);
            double avg5 = Double.parseDouble(avgStr5);
//            double last = Double.parseDouble(lastStr);
//            double oldest = Double.parseDouble(oldestStr);
//            double trend = Double.parseDouble(trendStr);
            s_avg1.put(millis, avg1);
            s_avg2.put(millis, avg2);
            s_avg3.put(millis, avg3);
            s_avg4.put(millis, avg4);
            s_avg5.put(millis, avg5);
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
            double bid = parseDouble(bidStr);
            double ask = parseDouble(askStr);
            s_topData.put(millis, new TopData(bid, ask));
        } else {
            throw new RuntimeException("not matched TOP_DATA_PATTERN line: " + line1);
        }
    }

    private static double parseDouble(String str) {
        if (str.indexOf(',') == -1) {
            return Double.parseDouble(str);
        }
        return Double.parseDouble(str.replace(",", ""));
    }

    private static final Pattern PLACE_ORDER_PATTERN = Pattern.compile("(\\d+):.*=OrderData\\{status=NEW,.*side=(.*), amount=(\\d+\\.\\d+), price=(\\d+\\.\\d+),.*");

    private static void processPlaceOrderLine(String line1, BufferedLineReader blr) throws IOException {
        // 1421193272478:    orderData=OrderData{status=NEW, pair=BTC_CNH, side=BUY, amount=0.08500, price=1347.99000, state=NONE, filled=0.00000}
        // 1425778629766:   placing closeOrder=OrderData{status=NEW, pair=BTC_CNH, side=BUY, amount=0.04465, price=1717.40000, state=NONE, filled=0.00000}
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

            // PlaceOrderData: PlaceOrderData{orderId=96465911, remains=0.0, received=0.0}
            String line2 = waitLineContained(blr, "PlaceOrderData: PlaceOrderData{");
            if (line2 != null) {
                System.out.println("GOT PlaceOrderData: " + line2);
                int indx1 = line2.indexOf("orderId=");
                if (indx1 != -1) {
                    int indx2 = line2.indexOf(",", indx1);
                    String orderId = line2.substring(indx1 + 8, indx2);
                    System.out.println(" orderId: " + orderId);
                    opd.m_orderId = orderId;

                    // 1424436729381: cancelOrder() OrderData{id=346405765 status=SUBMITTED, pair=BTC_CNH, side=BUY, amount=0.09700, price=1518.16000, state=LIMIT_PLACED, filled=0.00000}
                    // 1421411215853: $$$$$$   order FILLED: OrderData{id=96872920 status=FILLED, pair=BTC_CNH, side=BUY, amount=0.05580, price=1279.27000, state=NONE, filled=0.05580}
                    Map.Entry<Integer, String> wait = waitLineContained(blr, new String[]{"order FILLED: OrderData{id=" + orderId,
                                                                                          "cancelOrder() OrderData{id=" + orderId});
                    if (wait != null) {
                        Integer key = wait.getKey();
                        String line3 = wait.getValue();
                        if (key == 0) {
                            System.out.println("  GOT order FILLED: " + line3);
                            int indx3 = line3.indexOf(":");
                            if (indx3 != -1) {
                                String time = line3.substring(0, indx3);
                                Long millis2 = Long.parseLong(time);
                                System.out.println("   extracted time : " + millis2);
                                opd.m_fillTime = millis2;
                            }
                        } if (key == 1) {
                            System.out.println("  GOT order CANCEL: " + line3);
                            int indx3 = line3.indexOf(":");
                            if (indx3 != -1) {
                                String time = line3.substring(0, indx3);
                                Long millis2 = Long.parseLong(time);
                                System.out.println("   extracted time : " + millis2);
                                opd.m_cancelTime = millis2;
                            }
                        }
                    }
                } else {
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
        List<ChilledData> chilled = null; // lazzy
        Double boostedFrom = null;
        Double boostedTo = null;
        // 1421192392632: processDirection() direction=-2)

        // 1422502222987:  boosted from 0.9166666666666666 to 0.7333861125264423
        // 1422986746884:   direction chilledX from -0.5599999999999999 to -0.33599999999999997
        // 1421192392632:   directionAdjusted=-1.0; needBuyBtc=-0.3281232434687124; needSellCnh=-442.251070012
        while(true) {
            Map.Entry<Integer, String> entry = waitLineContained(blr, AFTER_DIRECTION); // boosted | chilled* | adjusted
            if (entry != null) {
                int index = entry.getKey();
                String line2 = entry.getValue();
                if (index == 0) { // got 'boosted'
                    Matcher matcher = BOOSTED_PATTERN.matcher(line2);
                    if (matcher.matches()) {
                        String boostedFromStr = matcher.group(2);
                        String boostedToStr = matcher.group(3);
                        System.out.println("GOT BOOSTED: boostedFromStr=" + boostedFromStr + "; boostedToStr=" + boostedToStr);
                        boostedFrom = Double.parseDouble(boostedFromStr);
                        boostedTo = Double.parseDouble(boostedToStr);
                    } else {
                        throw new RuntimeException("not matched BOOSTED_PATTERN line: " + line2);
                    }
                } else if (index == 1) { // got 'chilled'
                    Matcher matcher = CHILLED_PATTERN.matcher(line2);
                    if (matcher.matches()) {
                        String indexStr = matcher.group(2);
                        String chilled1FromStr = matcher.group(3);
                        String chilled1ToStr = matcher.group(4);
                        System.out.println("GOT CHILLED: indexStr=" + indexStr + "; chilledFromStr=" + chilled1FromStr + "; chilledToStr=" + chilled1ToStr);
                        double chilledFrom = Double.parseDouble(chilled1FromStr);
                        double chilledTo = Double.parseDouble(chilled1ToStr);
                        ChilledData chilledData = new ChilledData(indexStr, chilledFrom, chilledTo);
                        if(chilled == null) {
                            chilled = new ArrayList<ChilledData>();
                        }
                        chilled.add(chilledData);
                    } else {
                        throw new RuntimeException("not matched CHILLED_PATTERN line: " + line2);
                    }
                } else if (index == 2) { // got 'adjusted'
                    Matcher matcher = DIRECTION_ADJUSTED_PATTERN.matcher(line2);
                    if(matcher.matches()) {
                        String millisStr = matcher.group(1);
                        String directionAdjustedStr = matcher.group(2);
                        System.out.println("GOT DIRECTION_ADJUSTED: millisStr=" + millisStr + "; directionAdjustedStr=" + directionAdjustedStr);
                        long millis = Long.parseLong(millisStr);
                        double directionAdjusted = Double.parseDouble(directionAdjustedStr);
                        s_directions.add(new DirectionsData(millis, boostedFrom, boostedTo, chilled, directionAdjusted));
                        break;
                    } else {
                        throw new RuntimeException("not matched DIRECTION_ADJUSTED_PATTERN line: " + line2);
                    }
                } else {
                    break;
                }
            } else {
                break;
            }
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

            double avg = s_averageCounter.add(millis, price);
            s_avgPrice.put(millis, avg);
        } else {
            throw new RuntimeException("not matched TRADE_PATTERN line: " + line);
        }
    }

    private static class BufferedLineReader {
        private final LineReader m_reader;
        private int m_start = 0;
        private int m_index = 0;

        public BufferedLineReader(LineReader reader) {
            m_reader = reader;
        }

        public void close() throws IOException {
            m_reader.close();
        }

        public String getLine() throws IOException {
            String line = m_reader.getLine(m_index);
            if (line != null) {
                m_index++;
            }
            return line;
        }

        public void removeLine() {
            m_start++;
            m_index = m_start;
        }
    }

    private static class LineReader {
        private final BufferedReader m_bis;
        private final ArrayList<String> m_buffer = new ArrayList<String>();
        public int m_linesReaded = 0;

        public LineReader(InputStream is) {
            m_bis = new BufferedReader(new InputStreamReader(is));
        }

        public void close() throws IOException {
            m_bis.close();
        }

        public synchronized String getLine(int index) throws IOException {
            if (index < m_buffer.size()) {
                return m_buffer.get(index);
            }
            return readLine();
        }

        private String readLine() throws IOException {
            String line = m_bis.readLine();
            if (line != null) {
                m_buffer.add(line);
                m_linesReaded++;
            }
            return line;
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
        public Long m_cancelTime;

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

    public static class ChilledData {
        public final String m_index;
        public final double m_chilledFrom;
        public final double m_chilledTo;

        public ChilledData(String index, double chilledFrom, double chilledTo) {
            m_index = index;
            m_chilledFrom = chilledFrom;
            m_chilledTo = chilledTo;
        }
    }

    private static class DirectionsData {
        private final long m_millis;
        private final Double m_boostedFrom;
        private final Double m_boostedTo;
        private final List<ChilledData> m_chilled;
        private final double m_directionAdjusted;

        public DirectionsData(long millis,
                              Double boostedFrom, Double boostedTo,
                              List<ChilledData> chilled,
                              double directionAdjusted) {
            m_millis = millis;
            m_boostedFrom = boostedFrom;
            m_boostedTo = boostedTo;
            m_chilled = chilled;
            m_directionAdjusted = directionAdjusted;
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

    private static class AvgStochData {
        long m_millis;
        double m_avgStoch;
        double m_blend;
        boolean m_directionForward;

        public AvgStochData(long millis, double avgStoch, double blend, boolean directionForward) {
            m_millis = millis;
            m_avgStoch = avgStoch;
            m_blend = blend;
            m_directionForward = directionForward;
        }
    }

    private static abstract class LineReaderThread extends Thread {
        private final BufferedLineReader m_blr;
        private final AtomicInteger m_semafore;

        public LineReaderThread(LineReader reader, AtomicInteger semafore) {
            m_blr = new BufferedLineReader(reader);
            m_semafore = semafore;
            synchronized (semafore) {
                semafore.incrementAndGet();
            }
            start();
        }

        @Override public void run() {
            try {
                String line;
                while( (line = m_blr.getLine()) != null ) {
                    processTheLine(line, m_blr);
                    m_blr.removeLine();
                }
            } catch (Throwable t) {
                System.out.println("Error processing line: " + t);
                t.printStackTrace();
                s_errors.add(t);
            } finally {
                synchronized (m_semafore) {
                    m_semafore.decrementAndGet();
                    m_semafore.notify();
                }
            }
        }

        protected abstract void processTheLine(String line, BufferedLineReader blr) throws IOException;
    }
}
