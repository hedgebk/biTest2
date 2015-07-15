package bthdg.osc;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.util.Queue;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;

class OscProcessor {
    private final IWs m_ws;
    final OscExecutor m_executor;
    private final int m_calcsNum;
    private final PhasedOscCalculator[] m_calcs;
    private final Queue<TradeData> m_tradesQueue;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public OscProcessor(IWs ws) {
        m_ws = ws;
        m_executor = new OscExecutor(ws);
        int barSizesNum = Osc.BAR_SIZES.length;
        m_calcsNum = Osc.PHASES * barSizesNum;
        m_calcs = new PhasedOscCalculator[m_calcsNum];
        int indx = 0;
        for (int k = 0; k < barSizesNum; k++) {
            long barSize = Osc.BAR_SIZES[k];
            for (int i = 0; i < Osc.PHASES; i++) {
                m_calcs[indx] = new PhasedOscCalculator(indx, barSize, this, Osc.STICK_TOP_BOTTOM);
                indx++;
            }
        }
        m_tradesQueue = new Queue<TradeData>("OscProcessor") {
            @Override protected void processItem(TradeData tData) {
                process(tData);
            }
        };
        m_tradesQueue.start();
    }

    public void gotTrade(TradeData tdata) {
        m_tradesQueue.addItem(tdata);
    }

    private void process(TradeData tData) {
//            log("OscProcessor.process() tData=" + tData);
        m_executor.onTrade(tData);
        long timestamp = tData.m_timestamp;
        double price = tData.m_price;
        for (int i = 0; i < m_calcsNum; i++) {
            m_calcs[i].update(timestamp, price);
        }
    }

    public void stop() throws Exception {
        m_executor.stop();
        m_tradesQueue.stopQueue();
    }

    public void start() throws Exception {
        m_ws.subscribeTrades(Osc.PAIR, new ITradesListener() {
            @Override public void onTrade(TradeData tdata) {
                gotTrade(tdata);
            }
        });
    }

    public void onBar() {
        double avgStoch = 0;
        for (int i = 0; i < m_calcsNum; i++) {
            double lastStoch = m_calcs[i].m_lastStoch;
            if (lastStoch == -1) {
                return; // all not yet calculated
            }
            avgStoch += lastStoch;
        }
        avgStoch /= m_calcsNum;
        m_executor.onAvgStoch(avgStoch);
    }
}
