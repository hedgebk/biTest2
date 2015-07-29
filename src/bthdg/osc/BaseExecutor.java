package bthdg.osc;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.*;
import bthdg.ws.ITopListener;
import bthdg.ws.IWs;

public abstract class BaseExecutor implements Runnable {
    private final IWs m_ws;
    protected final Pair m_pair;
    protected TaskQueueProcessor m_taskQueueProcessor;
    private boolean m_run = true;
    private boolean m_changed;
    protected TopsData m_initTops;
    protected TopsData m_topsData;
    protected AccountData m_initAccount;
    protected AccountData m_account;
    protected double m_buy;
    protected double m_sell;
    protected boolean m_maySyncAccount = false;

    protected static void log(String s) { Log.log(s); }
    protected Exchange exchange() { return m_ws.exchange(); }

    // abstract
    protected abstract void gotTop() throws Exception;

    public BaseExecutor(IWs ws, Pair pair) {
        m_ws = ws;
        m_pair = pair;
    }

    @Override public void run() {
        while (m_run) {
            try {
                boolean changed;
                synchronized (this) {
                    if (!m_changed) {
                        log("Executor: waiting for update");
                        wait();
                    }
                    changed = m_changed;
                    m_changed = false;
                }
                if (changed) {
                    log("Executor: process update");
                    postRecheckDirection();
                }
            } catch (Exception e) {
                log("error in OscExecutor");
                e.printStackTrace();
            }
        }
    }

    protected void update() {
        synchronized (this) {
            m_changed = true;
            notify();
        }
    }

    public void stop() throws Exception {
        m_ws.stop();
        synchronized (this) {
            m_run = false;
            notify();
        }
    }

    protected TaskQueueProcessor getTaskQueueProcessor() {
        if (m_taskQueueProcessor == null) {
            m_taskQueueProcessor = new TaskQueueProcessor();
        }
        return m_taskQueueProcessor;
    }

    protected void addTask(TaskQueueProcessor.IOrderTask task) {
        getTaskQueueProcessor().addTask(task);
    }

    protected void postRecheckDirection() {

    }

    protected void initImpl() throws Exception {
        Exchange exchange = exchange();
        m_topsData = Fetcher.fetchTops(exchange, m_pair);
        log(" topsData=" + m_topsData);

        initAccount();
        m_initAccount = m_account.copy();
        m_initTops = m_topsData.copy();

        log("initImpl() continue: subscribeTrades()");
        m_ws.subscribeTop(m_pair, new ITopListener() {
            @Override public void onTop(long timestamp, double buy, double sell) {
//                    log("onTop() timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
                if (buy > sell) {
                    log("ERROR: ignored invalid top data. buy > sell: timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
                    return;
                }
                m_buy = buy;
                m_sell = sell;

                TopData topData = new TopData(buy, sell);
                m_topsData.put(m_pair, topData);
                log(" topsData'=" + m_topsData);

                addTask(new TopTask());
            }
        });
    }

    protected void initAccount() throws Exception {
        Exchange exchange = exchange();
        AccountData account = m_account;
        m_account = Fetcher.fetchAccount(exchange);
        if (m_account != null) {
            log(" account=" + m_account);
            double valuateBtc = m_account.evaluateAll(m_topsData, Currency.BTC, exchange);
            log("  valuateBtc=" + valuateBtc + " BTC");
            if (account!= null) {
                account.compareFunds(m_account);
            }
            m_maySyncAccount = false;
        } else {
            log("account request error");
        }
    }


    private class TopTask extends TaskQueueProcessor.BaseOrderTask {
        public TopTask() {}

        @Override public void process() throws Exception {
            gotTop();
        }
    }

}
