package bthdg.osc;

import bthdg.Log;
import bthdg.exch.OrderSide;

class PhasedOscCalculator extends OscCalculator {
    private static final double STICK_TOP_BOTTOM_LEVEL = 0.11;
    public static final double STICK_TOP_LEVEL = 1 - STICK_TOP_BOTTOM_LEVEL;
    public static final double STICK_DOWN_LEVEL = STICK_TOP_BOTTOM_LEVEL;
    private static final double REVERSE_TREND_LEVEL_MULTIPLIER = 1.5;

    private final OscProcessor m_processor;
    private final int m_index;
    private State m_state = State.NONE;
    private int m_barNum = 0;
    private boolean m_stickTopBottom = false;
    private boolean m_stickTop;
    private boolean m_stickDown;
    double m_lastStoch = -1;

    private static void log(String s) { Log.log(s); }

    public PhasedOscCalculator(int index, long barSize, OscProcessor processor, boolean stickTopBottom) {
        super(Osc.LEN1, Osc.LEN2, Osc.K, Osc.D, barSize, getOffset(index, barSize));
        m_processor = processor;
        m_index = index;
        m_stickTopBottom = stickTopBottom;
    }

    private static long getOffset(int index, long barSize) {
        return barSize * (index % Osc.PHASES) / Osc.PHASES;
    }

    @Override protected void update(long stamp, boolean finishBar) {
        super.update(stamp, finishBar);
        if(finishBar) {
            log(" [" + m_index + "] bar " + m_barNum + "; PREHEAT_BARS_NUM=" + Osc.PREHEAT_BARS_NUM);
            if (m_barNum++ == Osc.PREHEAT_BARS_NUM - Osc.INIT_BARS_BEFORE) {
                m_processor.m_executor.init();
            }
        }
    }

    @Override public void fine(long stamp, double stoch1, double stoch2) {}

    @Override public void bar(long barStart, double stoch1, double stoch2) {
        log(" ------------ [" + m_index + "] bar\t" + barStart + "\t" + stoch1 + "\t " + stoch2);
//        m_lastStoch = (stoch1 + stoch2) / 2;
        m_lastStoch = stoch1;
        m_processor.onBar();
        m_state = m_state.process(this, stoch1, stoch2);
    }

    public void start(OrderSide orderSide) {
        log("[" + m_index + "] start() bar " + m_barNum + "; orderSide=" + orderSide);
        m_processor.m_executor.update(orderSide.isBuy() ? 1 : -1);
    }

    public void stop(OrderSide orderSide) {
        log("[" + m_index + "] stop() bar " + m_barNum + "; orderSide=" + orderSide);
        m_processor.m_executor.update(orderSide.isBuy() ? 1 : -1);
    }

    private enum State {
        NONE {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                double stochDiff = stoch2 - stoch1;
                double startLevel = startLevel(stoch1, stoch2);
                OscExecutor executor = calc.m_processor.m_executor;
                if (stochDiff > 0) { // DOWN
                    if (executor.m_trendCounter.isTrendUp()) {
                        startLevel *= REVERSE_TREND_LEVEL_MULTIPLIER;
                    }
                    log("[" + calc.m_index + "] start level reached for SELL; stochDiff=" + stochDiff + "; startLevel=" + startLevel);
                    calc.start(OrderSide.SELL);
                    return DOWN;
                } else if (stochDiff < 0) { // UP
                    if (!executor.m_trendCounter.isTrendUp()) {
                        startLevel *= REVERSE_TREND_LEVEL_MULTIPLIER;
                    }
                    log("[" + calc.m_index + "] start level reached for BUY; stochDiff=" + stochDiff + "; startLevel=" + startLevel);
                    calc.start(OrderSide.BUY);
                    return UP;
                }
                return this;
            }
        },
        UP {
            @Override public State process(PhasedOscCalculator calc, double stoch1, double stoch2) {
                if (calc.m_stickTopBottom) {
                    if (calc.m_stickTop) {
                        if ((stoch1 < STICK_TOP_LEVEL) || (stoch2 < STICK_TOP_LEVEL)) {
                            calc.m_stickTop = false;
                            log("unstick from Top: stoch1=" + stoch1 + "; stoch2=" + stoch2);
                        } else {
                            log("ignored since stuck to Top: stoch1=" + stoch1 + "; stoch2=" + stoch2);
                            return this;
                        }
                    } else {
                        if ((stoch1 > STICK_TOP_LEVEL) && (stoch2 > STICK_TOP_LEVEL)) {
                            calc.m_stickTop = true;
                            log("stick to Top: stoch1=" + stoch1 + "; stoch2=" + stoch2);
                            return this;
                        }
                    }
                }
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
                if (calc.m_stickTopBottom) {
                    if (calc.m_stickDown) {
                        if ((stoch1 > STICK_DOWN_LEVEL) || (stoch2 > STICK_DOWN_LEVEL)) {
                            calc.m_stickDown = false;
                            log("unstick from Down: stoch1=" + stoch1 + "; stoch2=" + stoch2);
                        } else {
                            log("ignored since stuck to Down: stoch1=" + stoch1 + "; stoch2=" + stoch2);
                            return this;
                        }
                    } else {
                        if ((stoch1 < STICK_DOWN_LEVEL) && (stoch2 < STICK_DOWN_LEVEL)) {
                            calc.m_stickDown = true;
                            log("stick to Down: stoch1=" + stoch1 + "; stoch2=" + stoch2);
                            return this;
                        }
                    }
                }
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
