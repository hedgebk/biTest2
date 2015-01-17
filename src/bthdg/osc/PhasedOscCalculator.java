package bthdg.osc;

import bthdg.Log;
import bthdg.exch.OrderSide;

class PhasedOscCalculator extends OscCalculator {
    private final Osc.OscExecutor m_executor;
    private final int m_index;
    private State m_state = State.NONE;
    private int m_barNum = 0;

    private static void log(String s) { Log.log(s); }

    public PhasedOscCalculator(int index, Osc.OscExecutor executor) {
        super(Osc.LEN1, Osc.LEN2, Osc.K, Osc.D, Osc.BAR_SIZE, getOffset(index));
        m_executor = executor;
        m_index = index;
    }

    private static long getOffset(int index) {
        return Osc.BAR_SIZE / Osc.PHASES * index;
    }

    @Override protected void update(long stamp, boolean finishBar) {
        super.update(stamp, finishBar);
        if(finishBar) {
            log(" bar " + m_barNum + "; PREHEAT_BARS_NUM=" + Osc.PREHEAT_BARS_NUM);
            if (m_barNum++ == Osc.PREHEAT_BARS_NUM - Osc.INIT_BARS_BEFORE) {
                m_executor.init();
            }
        }
    }

    @Override public void fine(long stamp, double stoch1, double stoch2) {
//log(" fine " + stamp + ": " + stoch1 + "; " + stoch2);
    }

    @Override public void bar(long barStart, double stoch1, double stoch2) {
        log(" ------------ [" + m_index + "] bar\t" + barStart + "\t" + stoch1 + "\t " + stoch2);
        m_state = m_state.process(this, stoch1, stoch2);
    }

    public void start(OrderSide orderSide) {
        log("start() bar " + m_barNum + "; orderSide=" + orderSide);
        m_executor.update((orderSide == OrderSide.BUY) ? 1 : -1);
    }

    public void stop(OrderSide orderSide) {
        log("stop() bar " + m_barNum + "; orderSide=" + orderSide);
        m_executor.update((orderSide == OrderSide.BUY) ? 1 : -1);
    }

    private enum State {
        NONE {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                double stochDiff = stoch2 - stoch1;
                if (stochDiff > startLevel(stoch1, stoch2)) {
                    log("start level reached for SELL; stochDiff=" + stochDiff);
                    calc.start(OrderSide.SELL);
                    return DOWN;
                }
                if (-stochDiff > startLevel(stoch1, stoch2)) {
                    log("start level reached for BUY; stochDiff=" + stochDiff);
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
            return Osc.START_LEVEL * startStopLevelMultiply(stoch1, stoch2);
        }

        private static double stopLevel(double stoch1, double stoch2) {
            return Osc.STOP_LEVEL * startStopLevelMultiply(stoch1, stoch2);
        }

        private static double startStopLevelMultiply(double stoch1, double stoch2) {
            double mid = (stoch1 + stoch2) / 2;            // [0    ... 0.5 ... 1   ]
            double centerToMid = mid - 0.5;                // [-0.5 ... 0   ... 0.5 ]
            double absCenterToMid = Math.abs(centerToMid); // [0.5  ... 0   ... 0.5 ]
            double ratio = (-absCenterToMid * 2) * (Osc.START_STOP_LEVEL_MULTIPLY - 1) + Osc.START_STOP_LEVEL_MULTIPLY;
                                                           // [1    ... 3   ... 1   ]
            return ratio;
        }
    }
}
