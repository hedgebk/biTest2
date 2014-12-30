package bthdg.osc;

import bthdg.Fetcher;
import bthdg.exch.*;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;
import bthdg.ws.ITopListener;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;
import bthdg.ws.OkCoinWs;

import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Properties;

public class Osc {
    private static final long BAR_SIZE = Utils.toMillis("3s");
    public static final int LEN1 = 14;
    public static final int LEN2 = 14;
    public static final int K = 3;
    public static final int D = 3;
    public static final int PREHEAT_BARS_NUM = LEN1 + LEN2 + (K - 1) + (D - 1);
    public static final int INIT_BARS_BEFORE = 2;
    public static final int PHASES = 1;
    public static final double START_LEVEL = 0.01;
    public static final double STOP_LEVEL = 0.005;
    public static final Pair PAIR = Pair.BTC_CNH;

    private Processor m_processor;

    public static void main(String[] args) {
        try {
            new Osc().run();

            new ConsoleReader() {
                @Override protected void beforeLine() {
                    System.out.print(">");
                }
                @Override protected boolean processLine(String line) throws Exception {
                    return onConsoleLine(line);
                }
            }.run();

            Thread thread = Thread.currentThread();
            synchronized (thread) {
                thread.wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean onConsoleLine(String line) {
        System.out.println("onConsoleLine: " + line);
        return false;
    }

    private void run() throws IOException {
        Properties keys = BaseExch.loadKeys();
//        Btce.init(keys);
//        Bitstamp.init(keys);
        OkCoin.init(keys);
//        Btcn.init(keys);
//        Huobi.init(keys);

        IWs ws = OkCoinWs.create();
//        HuobiWs.main(args);
//        BtcnWs.main(args);
//        BitstampWs.main(args);

        m_processor = new Processor(ws);

        ws.subscribeTrades(PAIR, new ITradesListener() {
            @Override public void onTrade(TradeData tdata) {
//System.out.println("got Trade=" + tdata);
                m_processor.onTrade(tdata);
            }
        });
    }

    private static class Processor implements Runnable {
        private final OscExecutor m_executor;
        private final OscCalculator[] m_calcs = new OscCalculator[PHASES];
        private final Thread m_thread;
        private final LinkedList<TradeData> m_queue = new LinkedList<TradeData>();
        private boolean m_run = true;

        public Processor(IWs ws) {
            m_executor = new OscExecutor(ws);
            for (int i = 0; i < PHASES; i++) {
                m_calcs[i] = new PhasedOscCalculator(i, m_executor);
            }
            m_thread = new Thread(this);
            m_thread.setName("Processor");
            m_thread.start();
        }

        public void onTrade(TradeData tdata) {
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
                    System.out.println("error processing tData=" + tData);
                    e.printStackTrace();
                }
            }
        }

        private void process(TradeData tData) {
            System.out.println("process() tData=" + tData);
            m_executor.process(tData);
            for (int i = 0; i < PHASES; i++) {
                m_calcs[i].update(tData.m_timestamp, tData.m_price);
            }
        }
    }

    private static class OscExecutor implements Runnable{
        private final IWs m_ws;
        private final Thread m_thread;
        private int m_direction;
        private boolean m_run = true;
        private boolean m_changed;
        private boolean m_initialized;
        private OscOrderWatcher m_orderWatcher;

        public OscExecutor(IWs ws) {
            m_ws = ws;
            m_thread = new Thread(this);
            m_thread.setName("OscExecutor");
            m_thread.start();
        }

        public void update(int delta) {
            synchronized (this) {
                int direction = m_direction;
                m_direction += delta;
                System.out.println("direction updated from " + direction + " -> " + m_direction);
                m_changed = true;
                notify();
            }
        }

        @Override public void run() {
            while (m_run) {
                try {
                    boolean changed;
                    int direction;
                    synchronized (this) {
                        if (!m_changed) {
                            System.out.println("waiting for updated osc direction");
                            wait();
                        }
                        changed = m_changed;
                        direction = m_direction;
                        m_changed = false;
                    }
                    if (changed) {
                        System.out.println("process updated osc direction=" + direction);
                        process(direction);
                    }
                } catch (Exception e) {
                    System.out.println("error in OscExecutor");
                    e.printStackTrace();
                }
            }
        }

        private void init() {
            if (!m_initialized) {
                System.out.println("not initialized - added InitTask to queue");
                getOrderWatcher().addTask(new InitTask(this));
                m_initialized = true;
            }
        }

        private void initImpl() throws Exception {
            System.out.println("initImpl()");

            Exchange exchange = m_ws.exchange();
            AccountData account = Fetcher.fetchAccount(exchange);
            if (account != null) {
                System.out.println("account=" + account);
//                double valuateEur = account.evaluateEur(tops);
//                double valuateUsd = account.evaluateUsd(tops);
//                System.out.println("account=" + account + "; valuateEur=" + valuateEur + " EUR; valuateUsd=" + valuateUsd + " USD");
            } else {
                System.err.println("account request error");
            }

            System.out.println("initImpl() continue: subscribeTrades()");
            m_ws.subscribeTop(PAIR, new ITopListener() {
                @Override public void onTop(long timestamp, double buy, double sell) {
                    getTop(timestamp, buy, sell);
                }
            });
        }

        private void getTop(long timestamp, double buy, double sell) {
            System.out.println("gatTop() timestamp=" + timestamp + "; buy=" + buy + "; sell=" + sell);
            System.out.println(" queue.add TopTask");
            getOrderWatcher().addTask(new TopTask(timestamp, buy, sell), TopTask.class);
        }

        private void process(int direction) {
            getOrderWatcher().addTask(new DirectionTask(direction));
        }

        private OscOrderWatcher getOrderWatcher() {
            if (m_orderWatcher == null) {
                m_orderWatcher = new OscOrderWatcher();
            }
            return m_orderWatcher;
        }

        public void process(TradeData tData) {
            getOrderWatcher().addTask(new TradeTask(tData));
        }

        private static class OscOrderWatcher implements Runnable {
            private final LinkedList<IOrderTask> m_tasksQueue = new LinkedList<IOrderTask>();
            private Thread m_thread;
            private boolean m_run = true;

            public void addTask(IOrderTask task) {
                addTask(task, null);
            }

            public void addTask(IOrderTask task, Class toRemove) {
                synchronized (m_tasksQueue) {
                    if (toRemove != null) {
                        for (ListIterator<IOrderTask> listIterator = m_tasksQueue.listIterator(); listIterator.hasNext(); ) {
                            IOrderTask nextTask = listIterator.next();
                            if (toRemove.isInstance(nextTask)) {
                                System.out.println("OscOrderWatcher.queue: found existing task to remove of class " + toRemove);
                                listIterator.remove();
                            }
                        }
                    }
                    m_tasksQueue.addLast(task);
                    System.out.println("OscOrderWatcher.queue: task added " + task + "; notify...");
                    m_tasksQueue.notify();
                    if (m_thread == null) {
                        m_thread = new Thread(this);
                        m_thread.setName("OscOrderWatcher");
                        m_thread.start();
                    }
                }
            }

            @Override public void run() {
                System.out.println("OscOrderWatcher.queue: started thread");
                while (m_run) {
                    try {
                        IOrderTask task;
                        synchronized (m_tasksQueue) {
                            if (m_tasksQueue.isEmpty()) {
                                System.out.println("OscOrderWatcher.queue: empty queue, wait...");
                                m_tasksQueue.wait();
//System.out.println("OscOrderWatcher.queue: waked up");
                            }
                            task = m_tasksQueue.pollFirst();
                        }
                        if (task != null) {
                            System.out.println("OscOrderWatcher.queue: process task " + task);
                            task.process();
                        }
                    } catch (Exception e) {
                        System.out.println("error in OscOrderWatcher");
                        e.printStackTrace();
                    }
                }
            }
        }

        private interface IOrderTask {
            void process() throws Exception;
        }

        private static class DirectionTask implements IOrderTask {
            private final int m_direction;

            public DirectionTask(int direction) {
                m_direction = direction;
            }

            @Override public void process() {
                System.out.println("DirectionTask.process() direction=" + m_direction);
            }
        }

        private static class TradeTask implements IOrderTask {
            private final TradeData m_tData;

            public TradeTask(TradeData tData) {
                m_tData = tData;
            }

            @Override public void process() {
                System.out.println("TradeTask.process() tData=" + m_tData);
            }
        }

        private static class TopTask implements IOrderTask {
            public TopTask(long timestamp, double buy, double sell) {
            }

            @Override public void process() {
                System.out.println("TopTask.process()");
            }
        }

        private static class InitTask implements IOrderTask {
            private final OscExecutor m_oscExecutor;

            public InitTask(OscExecutor oscExecutor) {
                m_oscExecutor = oscExecutor;
            }

            @Override public void process() throws Exception {
                System.out.println("InitTask.process()");
                m_oscExecutor.initImpl();
            }
        }
    }

    private static class PhasedOscCalculator extends OscCalculator {
        private final OscExecutor m_executor;
        private final int m_index;
        private State m_state = State.NONE;
        private int m_barNum = 0;

        public PhasedOscCalculator(int index, OscExecutor executor) {
            super(Osc.LEN1, Osc.LEN2, Osc.K, Osc.D, Osc.BAR_SIZE, getOffset(index));
            m_executor = executor;
            m_index = index;
        }

        private static long getOffset(int index) {
            return BAR_SIZE / PHASES * index;
        }

        @Override protected void update(long stamp, boolean finishBar) {
            super.update(stamp, finishBar);
            if(finishBar) {
System.out.println(" bar " + m_barNum + "; PREHEAT_BARS_NUM=" + PREHEAT_BARS_NUM);
                if (m_barNum++ == PREHEAT_BARS_NUM - INIT_BARS_BEFORE) {
                    m_executor.init();
                }
            }
        }

        @Override public void fine(long stamp, double stoch1, double stoch2) {
//System.out.println(" fine " + stamp + ": " + stoch1 + "; " + stoch2);
        }

        @Override public void bar(long barStart, double stoch1, double stoch2) {
            System.out.println(" ------------ [" + m_index + "] bar\t" + barStart + "\t" + stoch1 + "\t " + stoch2);
            m_state = m_state.process(this, stoch1, stoch2);
        }

        public void start(OrderSide orderSide) {
            m_executor.update((orderSide == OrderSide.BUY) ? 1 : -1);
        }

        public void stop(OrderSide orderSide) {
            m_executor.update((orderSide == OrderSide.BUY) ? 1 : -1);
        }
    }

    private enum State {
        NONE {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                double stochDiff = stoch2 - stoch1;
                if (stochDiff > startLevel(stoch1, stoch2)) {
                    System.out.println("start level reached for SELL; stochDiff="+stochDiff);
                    calc.start(OrderSide.SELL);
                    return DOWN;
                }
                if (-stochDiff > startLevel(stoch1, stoch2)) {
                    System.out.println("start level reached for BUY; stochDiff="+stochDiff);
                    calc.start(OrderSide.BUY);
                    return UP;
                }
                return this;
            }
        },
        UP {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                double stochDiff = stoch2 - stoch1;
                boolean reverseDiff = stochDiff > stopLevel(stoch1, stoch2);
                if (reverseDiff) {
                    calc.stop(OrderSide.SELL);
                    return NONE;
                }
                return this;
            }
        },
        DOWN {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                double stochDiff = stoch2 - stoch1;
                boolean reverseDiff = -stochDiff > stopLevel(stoch1, stoch2);
                if (reverseDiff) {
                    calc.stop(OrderSide.BUY);
                    return NONE;
                }
                return this;
            }
        };

        public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
            throw new RuntimeException("must be overridden");
        }

        private static double startLevel(double stoch1, double stoch2) {
            return START_LEVEL;
        }

        private static double stopLevel(double stoch1, double stoch2) {
            return STOP_LEVEL;
        }
    }
}
