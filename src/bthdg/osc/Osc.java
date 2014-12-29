package bthdg.osc;

import bthdg.exch.OrderSide;
import bthdg.exch.Pair;
import bthdg.exch.TradeData;
import bthdg.util.Utils;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;
import bthdg.ws.OkCoinWs;

import java.util.LinkedList;

public class Osc {
    private static final long BAR_SIZE = Utils.toMillis("15s");
    public static final int LEN1 = 14;
    public static final int LEN2 = 14;
    public static final int K = 3;
    public static final int D = 3;
    public static final int PHASES = 3;
    public static final double START_LEVEL = 0.01;

    private Processor m_processor;

    public static void main(String[] args) {
        new Osc().run();
        try {
            Thread thread = Thread.currentThread();
            synchronized (thread) {
                thread.wait();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void run() {
        IWs ws = OkCoinWs.create();
//        HuobiWs.main(args);
//        BtcnWs.main(args);
//        BitstampWs.main(args);

        m_processor = new Processor();

        ws.subscribeTrades(Pair.BTC_CNH, new ITradesListener() {
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

        public Processor() {
            m_executor = new OscExecutor();
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
                        for (int i = 0; i < PHASES; i++) {
                            m_calcs[i].update(tData.m_timestamp, tData.m_price);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("error processing tData=" + tData);
                    e.printStackTrace();
                }
            }
        }
    }

    private static class OscExecutor implements Runnable{
        private final Thread m_thread;
        private int m_direction;
        private boolean m_run = true;
        private boolean m_changed;

        public OscExecutor() {
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
            init();
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

        }

        private void process(int direction) {

        }
    }

    private static class PhasedOscCalculator extends OscCalculator {
        private final OscExecutor m_executor;
        private final int m_index;
        private State m_state = State.NONE;

        public PhasedOscCalculator(int index, OscExecutor executor) {
            super(Osc.LEN1, Osc.LEN2, Osc.K, Osc.D, Osc.BAR_SIZE, getOffset(index));
            m_executor = executor;
            m_index = index;
        }

        private static long getOffset(int index) {
            return BAR_SIZE / PHASES * index;
        }

        @Override public void fine(long stamp, double stoch1, double stoch2) {
//System.out.println(" fine " + stamp + ": " + stoch1 + "; " + stoch2);
        }

        @Override public void bar(long barStart, double stoch1, double stoch2) {
            System.out.println(" ------------ [" + m_index + "] bar " + barStart + ": " + stoch1 + "; " + stoch2);
            m_state = m_state.process(this, stoch1, stoch2);
        }

        public void start(OrderSide orderSide) {
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

            private double startLevel(double stoch1, double stoch2) {
                return START_LEVEL;
            }
        },
        UP {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                return this;
            }
        },
        DOWN {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                return this;
            }
        };

        public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
            throw new RuntimeException("must be overridden");
        }
    }
}
