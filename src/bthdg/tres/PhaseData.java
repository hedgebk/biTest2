package bthdg.tres;

import bthdg.calc.OscTick;
import bthdg.exch.TradeData;
import bthdg.tres.alg.TresAlgo;
import bthdg.tres.ind.TresIndicator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class PhaseData {
    public static double LOCK_OSC_LEVEL = 0.09;

    final TresExchData m_exchData;
    final int m_phaseIndex;
    final TresOscCalculator m_oscCalculator;
    final TresCoppockCalculator m_coppockCalculator;
    final TresCciCalculator m_cciCalculator;
    final TresOHLCCalculator m_ohlcCalculator;
    final TresMaCalculator m_maCalculator;
    private final List<TresIndicator.TresPhasedIndicator> m_phasedIndicators = new ArrayList<TresIndicator.TresPhasedIndicator>();
    private double m_direction = 0; // // [-1 ... 1].   parked initially

    public PhaseData(TresExchData exchData, int phaseIndex) {
        m_exchData = exchData;
        m_phaseIndex = phaseIndex;
        Tres tres = m_exchData.m_tres;

        for (TresAlgo algo : exchData.m_algos) {
            for(TresIndicator indicator : algo.m_indicarors) {
                TresIndicator.TresPhasedIndicator phasedIndicator = indicator.createPhased(exchData, phaseIndex);
                m_phasedIndicators.add(phasedIndicator);
            }
        }

        m_oscCalculator = tres.m_calcOsc ? new TresOscCalculator(exchData, phaseIndex) {
            @Override public void bar(long barStart, double stoch1, double stoch2) {
                super.bar(barStart, stoch1, stoch2);
                onOscBar();
            }
        } : null;

        m_coppockCalculator = tres.m_calcCoppock ? new TresCoppockCalculator(exchData, phaseIndex) {
            @Override protected void bar(long barEnd, double value) {
                super.bar(barEnd, value);
                onCoppockBar();
            }
        } : null;
        m_cciCalculator = tres.m_calcCci ? new TresCciCalculator(exchData, phaseIndex) {
            @Override protected void bar(long barEnd, double value) {
                super.bar(barEnd, value);
                onCciBar();
            }
        } : null;
        m_ohlcCalculator = new TresOHLCCalculator(exchData.m_tres, phaseIndex);
        m_maCalculator = new TresMaCalculator(this, phaseIndex);
    }

    protected void onOscBar() {}
    protected void onCoppockBar() {}
    protected void onCciBar() {}

    public boolean update(TradeData tdata) {
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        Tres tres = m_exchData.m_tres;
        boolean updated = false;
        if (tres.m_calcOsc) {
            updated |= m_oscCalculator.update(timestamp, price);
        }
        if (tres.m_calcCoppock) {
            updated |= m_coppockCalculator.update(timestamp, price);
        }
        if (tres.m_calcCci) {
            updated |= m_cciCalculator.update(timestamp, price);
        }
        for(TresIndicator.TresPhasedIndicator phasedIndicator: m_phasedIndicators) {
            updated |= phasedIndicator.update(timestamp, price);
        }

        updated |=  m_ohlcCalculator.update(timestamp, price);
        updated |=  m_maCalculator.update(timestamp, price);
        return updated;
    }

    public void getState(StringBuilder sb) {
        m_oscCalculator.getState(sb);
    }

    protected Boolean calcOscDirection(boolean useLastFineTick, long timestamp) {
        if(!m_exchData.m_tres.m_calcOsc) {
            return null; // not calculating
        }
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

    public ChartPoint getLastCoppock() {
        return m_coppockCalculator.m_lastTick;
    }

    public ChartPoint getLastCci() {
        return m_cciCalculator.m_lastTick;
    }
}
