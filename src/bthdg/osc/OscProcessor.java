package bthdg.osc;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;

import java.util.LinkedList;

class OscProcessor implements Runnable {
    private final IWs m_ws;
    final OscExecutor m_executor;
    private final int m_calcsNum;
    private final PhasedOscCalculator[] m_calcs;
    private final LinkedList<TradeData> m_queue = new LinkedList<TradeData>();
    private boolean m_run = true;

    private static void log(String s) { Log.log(s); }

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
        Thread thread = new Thread(this);
        thread.setName("OscProcessor");
        thread.start();
    }

    public void gotTrade(TradeData tdata) {
        synchronized (m_queue) {
            m_queue.addLast(tdata);
            m_queue.notify();
        }
    }

    @Override public void run() {
        while (m_run) {
            TradeData tData = null;
            try {
                synchronized (m_queue) {
                    tData = m_queue.pollFirst();
                    if (tData == null) {
                        m_queue.wait();
                        tData = m_queue.pollFirst();
                    }
                }
                if (tData != null) {
                    process(tData);
                }
            } catch (Exception e) {
                log("error processing tData=" + tData);
                e.printStackTrace();
            }
        }
        log("OscProcessor thread finished");
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
        synchronized (m_queue) {
            m_run = false;
            m_queue.notify();
        }
    }

    public void start() throws Exception {
        m_ws.subscribeTrades(Osc.PAIR, new ITradesListener() {
            @Override public void onTrade(TradeData tdata) {
                gotTrade(tdata);
            }
        });
    }

    public void onBar(int index, double stoch1, double stoch2) {
        double avgStoch = 0;
        for (int i = 0; i < m_calcsNum; i++) {
            double lastStoch = m_calcs[i].m_lastStoch;
            if(lastStoch == -1) {
                return; // all not yet calculated
            }
            avgStoch += lastStoch;
        }
        avgStoch /= m_calcsNum;
        m_executor.onAvgStoch(avgStoch);
    }
}
