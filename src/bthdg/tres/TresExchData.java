package bthdg.tres;

import bthdg.Log;
import bthdg.exch.*;
import bthdg.osc.BaseExecutor;
import bthdg.tres.alg.BaseAlgoWatcher;
import bthdg.tres.alg.FineAlgoWatcher;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Queue;
import bthdg.ws.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TresExchData {
    public final Tres m_tres;
    public final IWs m_ws;
    final LinkedList<TradeDataLight> m_trades = new LinkedList<TradeDataLight>();
    public final PhaseData[] m_phaseDatas;
    final TresExecutor m_executor;
    private boolean m_hasTopIndicators;
    Queue<Runnable> m_tradesQueue;
    final LinkedList<OrderPoint> m_orders = new LinkedList<OrderPoint>();
    public double m_lastPrice;
    long m_startTickMillis = Long.MAX_VALUE;
    public long m_lastTickMillis = 0;
    long m_tickCount;
    final List<BaseAlgoWatcher> m_playAlgos = new ArrayList<BaseAlgoWatcher>();
    protected BaseAlgoWatcher m_runAlgoWatcher;
    TresAlgo.TresAlgoListener m_algoListener;
    final TresOHLCCalculator m_ohlcCalculator;

    public void setFeeding() { m_executor.m_feeding = true; }
    public void stop() { m_ws.stop(); }
    public TresExchData cloneClean() { return new TresExchData(m_tres, m_ws); }

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    @Override public String toString() {
        return "TresExchData[" + m_ws.exchange().name() + "]";
    }

    public TresExchData(Tres tres, IWs ws) {
        m_tres = tres;
        m_ws = ws;
        m_executor = new TresExecutor(this, ws, Tres.PAIR);

        m_ohlcCalculator = new TresOHLCCalculator(tres, 0);

        if (tres.m_algosArr != null) {
            for (String algoName : tres.m_algosArr) {
                TresAlgo algo = TresAlgo.get(algoName, this);
                m_hasTopIndicators |= !algo.m_topIndicators.isEmpty();
//                TresAlgoWatcher algoWatcher = new TresAlgoWatcher(this, algo);
                BaseAlgoWatcher algoWatcher = new FineAlgoWatcher(this, algo);
                m_playAlgos.add(algoWatcher);
            }
        }

        if (m_playAlgos.isEmpty()) {
            throw new RuntimeException("playAlgos not specified");
        }

        BaseAlgoWatcher algoWatcher = m_playAlgos.get(0);
        m_runAlgoWatcher = algoWatcher;

        int phasesNum = tres.m_phases;
        m_phaseDatas = new PhaseData[phasesNum];
        for (int i = 0; i < phasesNum; i++) {
            m_phaseDatas[i] = new PhaseData(this, i);
        }

        if (BaseExecutor.DO_TRADE) {
            m_algoListener = new TresAlgo.TresAlgoListener() {
                @Override public void onValueChange() {
                    if (m_executor.m_initialized) {
                        m_executor.postRecheckDirection();
                    } else {
                        if (!m_tres.m_logProcessing) {
                            m_executor.init();
                        }
                        setFeeding();
                    }
                }
            };
            m_runAlgoWatcher.setListener(m_algoListener);

            m_tradesQueue = new Queue<Runnable>("tradesQueue") {
                @Override protected void processItem(Runnable task) {
                    task.run();
                }
            };
            m_tradesQueue.start();
        }
    }

    public void start() {
        log("start() on " + this);
        m_ws.connect(new Runnable() {
            @Override public void run() {
                log(" connected on " + TresExchData.this);

                try {
                    m_ws.subscribeTrades(Tres.PAIR, new ITradesListener() {
                        @Override public void onTrade(final TradeData tdata) {
                            // do not hold connected thread - finish quickly - just put trade into queue
                            // will be processed later as possible
                            m_tradesQueue.addItem(new Runnable() {
                                @Override public void run() {
                                    try {
                                        processTrade(tdata);
                                    } catch (Exception e) {
                                        err("error processing trade : " + tdata + ": " + e, e);
                                    }
                                }
                            });
                        }
                    });

                    m_executor.initImpl();

                    m_ws.subscribeExecs(Tres.PAIR, new IExecsListener() {
                        @Override public void onExec(Exec exec) {
                            m_executor.onExec(exec);
                        }
                    });

                    m_ws.subscribeAcct(new IAcctListener() {
                        @Override public void onAccount(AccountData accountData) {
                            m_executor.onAccount(accountData);
                        }
                    });

                    m_ws.subscribeTop(Tres.PAIR, new ITopListener() {
                        @Override public void onTop(long timestamp, double buy, double sell) {
                            m_executor.onTopInt(timestamp, buy, sell, BaseExecutor.TopSource.top_subscribe);
                        }
                    });
                } catch (Exception e) {
                    err("ERROR subscribe/init [" + m_ws.exchange() + "]: " + e, e);
                }
            }
        });
    }

    public void processTrade(TradeDataLight tdata) {
        m_tickCount++;
        if (!m_tres.m_silentConsole) {
            log("onTrade[" + m_ws.exchange() + "]: " + tdata);
        }
        if (m_tres.m_collectPoints) {
            synchronized (m_trades) {
                m_trades.add(tdata);
            }
        }
        m_lastPrice = tdata.m_price;
        long timestamp = tdata.m_timestamp;

        m_ohlcCalculator.update(timestamp, m_lastPrice);

        for (BaseAlgoWatcher algoWatcher : m_playAlgos) {
            algoWatcher.m_algo.preUpdate(tdata);
        }

        for (PhaseData phaseData : m_phaseDatas) {
            phaseData.update(tdata);
        }

        for (BaseAlgoWatcher algoWatcher : m_playAlgos) {
            algoWatcher.m_algo.postUpdate(tdata);
        }

        if (!m_tres.m_logProcessing) {
            m_executor.onTrade(tdata);
        }

        long min = Math.min(m_startTickMillis, timestamp);
//        if ((min < m_startTickMillis) && (m_startTickMillis != Long.MAX_VALUE)) {
//            TimeZone TZ = TimeZone.getTimeZone("Asia/Hong_Kong"); // utc+08:00 Beijing, Hong Kong, Urumqi
//            Calendar NOW_CALENDAR = Calendar.getInstance(TZ, Locale.ENGLISH);
//            NOW_CALENDAR.setTimeInMillis(timestamp);
//            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z, zzzz");
//            simpleDateFormat.setTimeZone(TZ);
//            String str = simpleDateFormat.format(NOW_CALENDAR.getTime());
//            log("str="+str);
//            log("GOT");
//        } else {
            m_startTickMillis = min;
//        }
        m_lastTickMillis = Math.max(m_lastTickMillis, timestamp);

        m_tres.onTrade(tdata);
    }

    public void onTop(final BaseExecutor.TopDataPoint topDataPoint) {
        for (BaseAlgoWatcher algoWatcher : m_playAlgos) {
            algoWatcher.m_algo.preUpdate(topDataPoint);
        }
        if (m_hasTopIndicators) {
            m_tradesQueue.addItem(new Runnable() {
                @Override public void run() {
                    processTop(topDataPoint);
                }
            });
        }
        for (BaseAlgoWatcher algoWatcher : m_playAlgos) {
            algoWatcher.m_algo.postUpdate(topDataPoint);
        }
    }

    private void processTop(BaseExecutor.TopDataPoint topDataPoint) {
        for (PhaseData phaseData : m_phaseDatas) {
            phaseData.update(topDataPoint);
        }
    }

    public void getState0(StringBuilder sb) {
        sb.append("[").append(m_ws.exchange()).append("]: last=").append(m_lastPrice);
    }

    public double getDirectionAdjusted() { // [-1 ... 1]
        return m_runAlgoWatcher.getDirectionAdjusted();
    }

    public void addOrder(OrderData order, long tickAge, double buy, double sell, BaseExecutor.TopSource topSource, double gainAvg) {
        synchronized (m_orders) {
            m_orders.add(new OrderPoint(order, tickAge, buy, sell, topSource, gainAvg));
        }
        m_tres.postFrameRepaint();
    }

    public JComponent getController(TresCanvas canvas) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 1, 1));

        AlgoComboBox combo = new AlgoComboBox(m_playAlgos);
        panel.add(combo);
        for (BaseAlgoWatcher algoWatcher : m_playAlgos) {
            panel.add(Box.createHorizontalStrut(2));
            panel.add(algoWatcher.getController(canvas));
        }
        return panel;
    }

    public String getRunAlgoParams() {
        return m_runAlgoWatcher.getRunAlgoParams();
    }

    public void sendStopTask() {
        m_executor.postStopTask();
    }

    public void reset() {
        m_executor.postResetTask();
    }

    public void reconnect() {
        m_ws.reconnect();
    }

    private static String[] getComboItems(List<BaseAlgoWatcher> playAlgos) {
        String[] comboItems = new String[playAlgos.size()];
        int index= 0;
        for (BaseAlgoWatcher algoWatcher : playAlgos) {
            String name = algoWatcher.m_algo.m_name;
            comboItems[index++] = name;
        }
        return comboItems;
    }

    private void selectAlgo(String algoName) {
        for (BaseAlgoWatcher algoWatcher : m_playAlgos) {
            String name = algoWatcher.m_algo.m_name;
            if (name.equals(algoName)) {
                if (BaseExecutor.DO_TRADE) {
                    m_runAlgoWatcher.setListener(null);
                    algoWatcher.setListener(m_algoListener);
                }
                m_runAlgoWatcher = algoWatcher;
                log("runAlgo changed to  " + algoName);

                break;
            }
        }
        log("ERROR: no algo with name " + algoName);
    }


    //=============================================================================================
    public static class OrderPoint {
        public final OrderData m_order;
        public final long m_tickAge;
        public final double m_buy;
        public final double m_sell;
        public final BaseExecutor.TopSource m_topSource;
        public final double m_gainAvg;

        public OrderPoint(OrderData order, long tickAge, double buy, double sell, BaseExecutor.TopSource topSource, double gainAvg) {
            m_order = order;
            m_tickAge = tickAge;
            m_buy = buy;
            m_sell = sell;
            m_topSource = topSource;
            m_gainAvg = gainAvg;
        }
    }


    //=============================================================================================
    private class AlgoComboBox extends JComboBox<String> {
        public AlgoComboBox(List<BaseAlgoWatcher> playAlgos) {
            super(getComboItems(playAlgos));
            String runAlgoName = m_runAlgoWatcher.m_algo.m_name;
            setSelectedItem(runAlgoName);
        }

        @Override protected void fireItemStateChanged(ItemEvent e) {
            super.fireItemStateChanged(e);
            int selectedIndex = getSelectedIndex();
            String selectedAlgo = getItemAt(selectedIndex);
            selectAlgo(selectedAlgo);
        }
    }
}
