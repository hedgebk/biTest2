package bthdg.osc;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;

import java.util.LinkedList;

class OscProcessor implements Runnable {
    private final IWs m_ws;
    private final OscExecutor m_executor;
    private final OscCalculator[] m_calcs = new OscCalculator[Osc.PHASES];
    private final LinkedList<TradeData> m_queue = new LinkedList<TradeData>();
    private boolean m_run = true;

    private static void log(String s) { Log.log(s); }

    public OscProcessor(IWs ws) {
        m_ws = ws;
        m_executor = new OscExecutor(ws);
        for (int i = 0; i < Osc.PHASES; i++) {
            m_calcs[i] = new PhasedOscCalculator(i, m_executor, Osc.STICK_TOP_BOTTOM, Osc.DELAY_REVERSE_START);
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
        for (int i = 0; i < Osc.PHASES; i++) {
            m_calcs[i].update(tData.m_timestamp, tData.m_price);
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
}
