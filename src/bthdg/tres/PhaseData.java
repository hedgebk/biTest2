package bthdg.tres;

import bthdg.calc.OscTick;
import bthdg.exch.TradeData;

import java.util.LinkedList;

public class PhaseData {
    public static double LOCK_OSC_LEVEL = 0.09;

    final TresExchData m_exchData;
    final int m_phaseIndex;
    final TresOscCalculator m_oscCalculator;
    final TresOHLCCalculator m_ohlcCalculator;
    final TresMaCalculator m_maCalculator;
    private double m_direction = 0; // // [-1 ... 1].   parked initially

    public PhaseData(TresExchData exchData, int phaseIndex) {
        m_exchData = exchData;
        m_phaseIndex = phaseIndex;
        m_oscCalculator = new TresOscCalculator(exchData, phaseIndex) {
            @Override public void bar(long barStart, double stoch1, double stoch2) {
                super.bar(barStart, stoch1, stoch2);
                onOscBar();
            }
        };
        m_ohlcCalculator = new TresOHLCCalculator(exchData.m_tres, phaseIndex);
        m_maCalculator = new TresMaCalculator(this, phaseIndex);
    }

    protected void onOscBar() {}

    public boolean update(TradeData tdata) {
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        m_oscCalculator.update(timestamp, price);
        boolean updated1 = m_ohlcCalculator.update(tdata);
        boolean updated2 = m_maCalculator.update(tdata);
        return updated1 || updated2;
    }

    public void getState(StringBuilder sb) {
        m_oscCalculator.getState(sb);
    }

    protected Boolean calcOscDirection(boolean useLastFineTick, long timestamp) {
        if (useLastFineTick) {
            LinkedList<TresMaCalculator.MaCrossData> maCrossDatas = m_maCalculator.m_maCrossDatas;
            TresMaCalculator.MaCrossData lastMaCrossData;
            synchronized (maCrossDatas) {
                lastMaCrossData = maCrossDatas.peekLast();
            }
            if (lastMaCrossData != null) {
                long lastMaCrossTime = lastMaCrossData.m_timestamp;
                long passed = timestamp - lastMaCrossTime;
                if (passed < m_exchData.m_tres.m_barSizeMillis / 10) {
                    return null;
                }
            }
        }

        OscTick newestTick = useLastFineTick ? m_oscCalculator.m_blendedLastFineTick : m_oscCalculator.m_lastBar;
        if (newestTick != null) {
            double newestMid = newestTick.getMid();
            if (newestMid < LOCK_OSC_LEVEL) {
                return false; // oscDown
            } else if (newestMid > 1 - LOCK_OSC_LEVEL) {
                return true; // oscUp
            }
            double newestVal1 = newestTick.m_val1;
            double newestVal2 = newestTick.m_val2;
//                if( (newestVal1 < 0.1) && (newestVal2 < 0.1) ) {
//                    return false; // oscDown
//                }
//                if( (newestVal1 > 0.9) && (newestVal2 > 0.9) ) {
//                    return true; // oscUp
//                }
            if (newestVal1 != newestVal2) { // have direction
                double newestValDiff = Math.abs(newestVal1 - newestVal2);
                if (newestValDiff > 0.002) {
                    boolean newestOscUp = (newestVal1 > newestVal2);
                    OscTick oldBar = useLastFineTick ? m_oscCalculator.m_lastBar : m_oscCalculator.m_prevBar;
                    if (oldBar != null) {
                        double oldOsc1 = oldBar.m_val1;
                        double oldOsc2 = oldBar.m_val2;
                        if (oldOsc1 != oldOsc2) { // had direction
                            Boolean oldOscUp = (oldOsc1 > oldOsc2);
                            if (oldOscUp == newestOscUp) {
                                if(false/*useLastFineTick && (newestMid>0.3) && (newestMid <0.7)*/) {
                                    OscTick oldestBar = m_oscCalculator.m_prevBar;
                                    if (oldestBar != null) {
                                        double oldestOsc1 = oldestBar.m_val1;
                                        double oldestOsc2 = oldestBar.m_val2;
                                        if (oldestOsc1 != oldestOsc2) { // had direction
                                            Boolean oldestOscUp = (oldestOsc1 > oldestOsc2);
                                            if (oldOscUp == oldestOscUp) {
                                                return newestOscUp;
                                            }
                                        }
                                    }
                                } else {
                                    return newestOscUp;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null; // meant undefined direction
    }

    public double getDirection() { // [-1 ... 1]
        Boolean oscUp = calcOscDirection(true, System.currentTimeMillis());
        if (oscUp != null) {
            m_direction = oscUp ? 1 : -1;
        }
        return m_direction;
    }

    public double getAvgOsc() {
        return m_oscCalculator.m_lastBar == null ? 0 : m_oscCalculator.m_lastBar.getMid();
    }
}
