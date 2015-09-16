package bthdg.tres;

import bthdg.Log;
import bthdg.calc.OscTick;
import bthdg.exch.Direction;
import bthdg.exch.OrderData;
import bthdg.exch.TradeData;
import bthdg.osc.BaseExecutor;
import bthdg.osc.TrendWatcher;
import bthdg.util.Queue;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;

import java.util.LinkedList;

public class TresExchData {
    public static final double AVG_OSC_PEAK_TOLERANCE = 0.05;
    public static final double AVG_COPPOCK_PEAK_TOLERANCE = 0.005;
    public static final double AVG_CCI_PEAK_TOLERANCE = 0.05;

    final Tres m_tres;
    final IWs m_ws;
    final LinkedList<TradeData> m_trades = new LinkedList<TradeData>();
    final PhaseData[] m_phaseDatas;
    final TresExecutor m_executor;
    final Queue<TradeData> m_tradesQueue;
    final LinkedList<OrderPoint> m_orders = new LinkedList<OrderPoint>();
    final LinkedList<ChartPoint> m_avgOscs = new LinkedList<ChartPoint>();
    final LinkedList<ChartPoint> m_avgCoppock = new LinkedList<ChartPoint>();
    final LinkedList<ChartPoint> m_avgCci = new LinkedList<ChartPoint>();
    final public LinkedList<ChartPoint> m_avgOscsPeaks = new LinkedList<ChartPoint>();
    final public LinkedList<ChartPoint> m_avgCoppockPeaks = new LinkedList<ChartPoint>();
    final public LinkedList<ChartPoint> m_avgCciPeaks = new LinkedList<ChartPoint>();
    final public LinkedList<CoppockSymData> m_сoppockSym = new LinkedList<CoppockSymData>();
    final TrendWatcher<ChartPoint> m_avgOscsPeakCalculator = new TrendWatcher<ChartPoint>(AVG_OSC_PEAK_TOLERANCE) {
        @Override protected double toDouble(ChartPoint chartPoint) { return chartPoint.m_value; }
        @Override protected void onNewPeak(ChartPoint peak, ChartPoint last) {
            synchronized (m_avgOscsPeaks) {
                m_avgOscsPeaks.add(peak);
                m_executor.postRecheckDirection();
            }
        }
    };
    final AvgCoppockPeakCalculator m_avgCoppockPeakCalculator = new AvgCoppockPeakCalculator();
    final TrendWatcher<ChartPoint> m_avgCciPeakCalculator = new TrendWatcher<ChartPoint>(AVG_CCI_PEAK_TOLERANCE) {
        @Override protected double toDouble(ChartPoint chartPoint) { return chartPoint.m_value; }
        @Override protected void onNewPeak(ChartPoint peak, ChartPoint last) {
            synchronized (m_avgCciPeaks) {
                m_avgCciPeaks.add(peak);
                m_executor.postRecheckDirection();
            }
        }
    };
    double m_lastPrice;
    private boolean m_updated;
    long m_startTickMillis = Long.MAX_VALUE;
    long m_lastTickMillis = 0;
    long m_tickCount;

    public void setUpdated() { m_updated = true; }
    public void setFeeding() { m_executor.m_feeding = true; }
    public void stop() { m_ws.stop(); }
    public TresExchData cloneClean() { return new TresExchData(m_tres, m_ws); }

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public TresExchData(Tres tres, IWs ws) {
        m_tres = tres;
        m_ws = ws;
        m_executor = new TresExecutor(this, ws, m_tres.PAIR);
        int phasesNum = tres.m_phases;
        m_phaseDatas = new PhaseData[phasesNum];
        for (int i = 0; i < phasesNum; i++) {
            m_phaseDatas[i] = new PhaseData(this, i) {
                @Override protected void onOscBar() {
                    ChartPoint chartPoint = calcAvgOsc();
                    if (chartPoint != null) {
                        synchronized (m_avgOscs) {
                            m_avgOscs.add(chartPoint);
                            m_avgOscsPeakCalculator.update(chartPoint);
                        }
                    }
                }

                @Override protected void onCoppockBar() {
                    ChartPoint chartPoint = calcAvgCoppock();
                    if (chartPoint != null) {
                        synchronized (m_avgCoppock) {
                            m_avgCoppock.add(chartPoint);
                            m_avgCoppockPeakCalculator.update(chartPoint);
                        }
                    }
                }

                @Override protected void onCciBar() {
                    ChartPoint chartPoint = calcAvgCci();
                    if (chartPoint != null) {
                        synchronized (m_avgCci) {
                            m_avgCci.add(chartPoint);
                            m_avgCciPeakCalculator.update(chartPoint);
                        }
                    }
                }
            };
        }
        m_tradesQueue = new Queue<TradeData>("tradesQueue") {
            @Override protected void processItem(TradeData tData) { processTrade(tData); }
        };
        m_tradesQueue.start();
    }

    public void start() {
        try {
            m_ws.subscribeTrades(Tres.PAIR, new ITradesListener() {
                @Override public void onTrade(TradeData tdata) {
                    // do not hold connected thread - done quickly - just put trade into queue
                    // will be processed later as possible
                    m_tradesQueue.addItem(tdata);
                }
            });

//            m_ws.subscribeTop(Tres.PAIR, new ITopListener() {
//                @Override public void onTop(long timestamp, double buy, double sell) {
////                    log("onTop() timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
//                    if (buy > sell) {
//                        log("ERROR: ignored invalid top data. buy > sell: timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
//                        return;
//                    }
//                    TradeData tradeData = new TradeData(0, (buy + sell) / 2, timestamp);
//                    onTrade(tradeData);
//                }
//            });

        } catch (Exception e) {
            err("error subscribeTrades[" + m_ws.exchange() + "]: " + e, e);
        }
    }

    public void processTrade(TradeData tdata) {
        m_tickCount++;
        if (!m_tres.m_silentConsole) {
            log("onTrade[" + m_ws.exchange() + "]: " + tdata);
        }
        synchronized (m_trades) {
            m_trades.add(tdata);
        }
        m_lastPrice = tdata.m_price;
        long timestamp = tdata.m_timestamp;

        m_updated = false;
        for (PhaseData phaseData : m_phaseDatas) {
            boolean updated = phaseData.update(tdata);
            if (updated) {
                m_updated = true;
            }
        }

        if (!m_tres.m_logProcessing) {
            m_executor.onTrade(tdata);
        }

        long min = Math.min(m_startTickMillis, timestamp);
        if ((min < m_startTickMillis) && (m_startTickMillis != Long.MAX_VALUE)) {
//            TimeZone TZ = TimeZone.getTimeZone("Asia/Hong_Kong"); // utc+08:00 Beijing, Hong Kong, Urumqi
//            Calendar NOW_CALENDAR = Calendar.getInstance(TZ, Locale.ENGLISH);
//            NOW_CALENDAR.setTimeInMillis(timestamp);
//            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z, zzzz");
//            simpleDateFormat.setTimeZone(TZ);
//            String str = simpleDateFormat.format(NOW_CALENDAR.getTime());
//            log("str="+str);
//            log("GOT");
        } else {
            m_startTickMillis = min;
        }
        m_lastTickMillis = Math.max(m_lastTickMillis, timestamp);

        m_tres.onTrade(tdata);
    }

    public void getState(StringBuilder sb) {
        sb.append("[").append(m_ws.exchange()).append("]: last=").append(m_lastPrice);
        for (PhaseData phaseData : m_phaseDatas) {
            phaseData.getState(sb);
        }
    }

    public void getState0(StringBuilder sb) {
        sb.append("[").append(m_ws.exchange()).append("]: last=").append(m_lastPrice);
        m_phaseDatas[0].getState(sb);
    }

    public double getDirectionAdjusted() { // [-1 ... 1]
        double directionAdjusted = 0;
        for (PhaseData phaseData : m_phaseDatas) {
            double direction = phaseData.getDirection();
            directionAdjusted += direction;
        }
        return directionAdjusted/m_phaseDatas.length;
    }

    public ChartPoint calcAvgOsc() {
        double ret = 0;
        long time = 0;
        for (PhaseData phaseData : m_phaseDatas) {
            OscTick lastBar = phaseData.m_oscCalculator.m_lastBar;
            if (lastBar == null) {
                return null;  // not fully ready
            }
            long startTime = lastBar.m_startTime;
            time = Math.max(time, startTime);

            double avgOsc = lastBar.getMid();
            ret += avgOsc;
        }
        long endTime = time + m_tres.m_barSizeMillis;
        double avgValue = ret / m_phaseDatas.length;
        return new ChartPoint(endTime, avgValue);
    }

    public ChartPoint calcAvgCoppock() {
        double ret = 0;
        long maxBarStart = 0;
        for (PhaseData phaseData : m_phaseDatas) {
            ChartPoint lastCoppock = phaseData.getLastCoppock();
            if (lastCoppock == null) {
                return null; // not fully ready
            }
            double lastValue = lastCoppock.m_value;
            long barStart = lastCoppock.m_millis;
            maxBarStart = Math.max(maxBarStart, barStart);
            ret += lastValue;
        }
        double avgValue = ret / m_phaseDatas.length;
        return new ChartPoint(maxBarStart, avgValue);
    }

    private ChartPoint calcAvgCci() {
        double ret = 0;
        long maxBarEnd = 0;
        for (PhaseData phaseData : m_phaseDatas) {
            TresCciCalculator.CciTick lastCci = phaseData.getLastCci();
            if (lastCci == null) {
                return null; // not fully ready
            }
            double lastValue = lastCci.m_value;
            long barEnd = lastCci.m_barEnd;
            maxBarEnd = Math.max(maxBarEnd, barEnd);
            ret += lastValue;
        }
        double avgValue = ret / m_phaseDatas.length;
        return new ChartPoint(maxBarEnd, avgValue);
    }

    public void addOrder(OrderData order, long tickAge, double buy, double sell, BaseExecutor.TopSource topSource) {
        synchronized (m_orders) {
            m_orders.add(new OrderPoint(order, tickAge, buy, sell, topSource));
        }
        m_tres.postFrameRepaint();
    }

    public static class OrderPoint {
        public final OrderData m_order;
        public final long m_tickAge;
        public final double m_buy;
        public final double m_sell;
        public final BaseExecutor.TopSource m_topSource;

        public OrderPoint(OrderData order, long tickAge, double buy, double sell, BaseExecutor.TopSource topSource) {
            m_order = order;
            m_tickAge = tickAge;
            m_buy = buy;
            m_sell = sell;
            m_topSource = topSource;
        }
    }

    private class AvgCoppockPeakCalculator extends TrendWatcher<ChartPoint> {
        Double m_lastPeakPrice = null;
        double m_totalPriceRatio = 1;

        public AvgCoppockPeakCalculator() {
            super(TresExchData.AVG_COPPOCK_PEAK_TOLERANCE);
        }

        @Override protected double toDouble(ChartPoint chartPoint) { return chartPoint.m_value; }

        @Override protected void onNewPeak(ChartPoint peak, ChartPoint last) {
            synchronized (m_avgCoppockPeaks) {
                m_avgCoppockPeaks.add(peak);
                m_executor.postRecheckDirection();

log("new peak: " + peak.m_value + "; direction=" + m_direction + "; lastPeakPrice=" + m_lastPeakPrice + "; lastPrice=" + m_lastPrice);
                if (m_lastPeakPrice != null) {
                    double priceRatio;
                    if (m_direction == Direction.FORWARD) { // up
                        priceRatio = m_lastPeakPrice / m_lastPrice;
                    } else {
                        priceRatio = m_lastPrice / m_lastPeakPrice;
                    }
                    m_totalPriceRatio *= priceRatio;
log(" priceRatio=" + priceRatio + "; m_totalPriceRatio=" + m_totalPriceRatio);

                    CoppockSymData data = new CoppockSymData(m_lastTickMillis, m_lastPrice, priceRatio, m_totalPriceRatio);
                    synchronized (m_сoppockSym) {
                        m_сoppockSym.add(data);
                    }
                }
                m_lastPeakPrice = m_lastPrice;
            }
        }
    }

    public static class CoppockSymData {
        public final long m_millis;
        public final double m_price;
        public final double m_priceRatio;
        public final double m_totalPriceRatio;

        public CoppockSymData(long millis, double price, double priceRatio, double totalPriceRatio) {
            m_millis = millis;
            m_price = price;
            m_priceRatio = priceRatio;
            m_totalPriceRatio = totalPriceRatio;
        }
    }
}
