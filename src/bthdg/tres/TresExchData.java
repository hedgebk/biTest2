package bthdg.tres;

import bthdg.Log;
import bthdg.exch.OrderData;
import bthdg.exch.TradeData;
import bthdg.util.Queue;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;

import java.text.SimpleDateFormat;
import java.util.*;

public class TresExchData {
    final Tres m_tres;
    final IWs m_ws;
    final List<TradeData> m_trades = new ArrayList<TradeData>();
    final PhaseData[] m_phaseDatas;
    final TresExecutor m_executor;
    final Queue<TradeData> m_tradesQueue;
    final LinkedList<OrderPoint> m_orders = new LinkedList<OrderPoint>();
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
            m_phaseDatas[i] = new PhaseData(this, i);
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
        m_trades.add(tdata);
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
            TimeZone TZ = TimeZone.getTimeZone("Asia/Hong_Kong"); // utc+08:00 Beijing, Hong Kong, Urumqi
            Calendar NOW_CALENDAR = Calendar.getInstance(TZ, Locale.ENGLISH);
            NOW_CALENDAR.setTimeInMillis(timestamp);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z, zzzz");
            simpleDateFormat.setTimeZone(TZ);
            String str = simpleDateFormat.format(NOW_CALENDAR.getTime());
            log("str="+str);
            log("GOT");
        } else {
            m_startTickMillis = min;
        }
        m_lastTickMillis = Math.max(m_lastTickMillis, timestamp);

        if (m_updated) {
            m_tres.onTrade(tdata);
        }
    }

    public void getState(StringBuilder sb) {
        sb.append("[").append(m_ws.exchange()).append("]: last=").append(m_lastPrice);
        for (PhaseData phaseData : m_phaseDatas) {
            phaseData.getState(sb);
        }
    }

    public double getDirectionAdjusted() { // [-1 ... 1]
        double directionAdjusted = 0;
        for (PhaseData phaseData : m_phaseDatas) {
            double direction = phaseData.getDirection();
            directionAdjusted += direction;
        }
        return directionAdjusted/m_phaseDatas.length;
    }

    public void addOrder(OrderData order, long tickAge) {
        synchronized (m_orders) {
            m_orders.add(new OrderPoint(order, tickAge));
        }
        m_tres.postFrameRepaint();
    }

    public static class OrderPoint {
        public final OrderData m_order;
        public final long m_tickAge;

        public OrderPoint(OrderData order, long tickAge) {
            m_order = order;
            m_tickAge = tickAge;
        }
    }
}
