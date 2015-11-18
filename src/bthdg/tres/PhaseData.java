package bthdg.tres;

import bthdg.exch.TradeDataLight;
import bthdg.tres.alg.TresAlgo;
import bthdg.tres.alg.TresAlgoWatcher;
import bthdg.tres.ind.TresIndicator;

import java.util.ArrayList;
import java.util.List;

public class PhaseData {
    final TresExchData m_exchData;
    final int m_phaseIndex;
    final TresOHLCCalculator m_ohlcCalculator;
//    final TresMaCalculator m_maCalculator;
    private final List<TresIndicator.TresPhasedIndicator> m_phasedIndicators = new ArrayList<TresIndicator.TresPhasedIndicator>();
//    private double m_direction = 0; // // [-1 ... 1].   parked initially

    public PhaseData(TresExchData exchData, int phaseIndex) {
        m_exchData = exchData;
        m_phaseIndex = phaseIndex;
        Tres tres = m_exchData.m_tres;

        for (TresAlgoWatcher algoWatcher : exchData.m_playAlgos) {
            TresAlgo algo = algoWatcher.m_algo;
            registerPhasedIndicators(exchData, phaseIndex, algo);
        }

        m_ohlcCalculator = new TresOHLCCalculator(exchData.m_tres, phaseIndex);
//        m_maCalculator = new TresMaCalculator(this, phaseIndex);
    }

    protected void registerPhasedIndicators(TresExchData exchData, int phaseIndex, TresAlgo algo) {
        for (TresIndicator indicator : algo.m_indicators) {
            TresIndicator.TresPhasedIndicator phasedIndicator = indicator.createPhased(exchData, phaseIndex);
            if (phasedIndicator != null) {
                m_phasedIndicators.add(phasedIndicator);
            }
        }
    }

    public void update(TradeDataLight tdata) {
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        for (TresIndicator.TresPhasedIndicator phasedIndicator : m_phasedIndicators) {
            if (phasedIndicator != null) {
                phasedIndicator.update(timestamp, price);
            }
        }

        m_ohlcCalculator.update(timestamp, price);
//        updated |=  m_maCalculator.update(timestamp, price);
    }

    public void getState(StringBuilder sb) {
        //
    }

//    protected Boolean calcOscDirection(boolean useLastFineTick, long timestamp) {
//        return null; // not calculating
//
//        if (!m_exchData.m_tres.m_calcOsc) {
//            return null; // not calculating
//        }
//        if (useLastFineTick) {
//            TresMaCalculator.MaCrossData lastMaCrossData = m_maCalculator.lastMaCrossData();
//            if (lastMaCrossData != null) {
//                long lastMaCrossTime = lastMaCrossData.m_timestamp;
//                long passed = timestamp - lastMaCrossTime;
//                if (passed < m_exchData.m_tres.m_barSizeMillis / 10) {
//                    return null;
//                }
//            }
//        }
//
//        OscTick newestTick = useLastFineTick ? m_oscCalculator.m_blendedLastFineTick : m_oscCalculator.m_lastBar;
//        if (newestTick != null) {
//            double newestMid = newestTick.getMid();
//            if (newestMid < TresOscCalculator.LOCK_OSC_LEVEL) {
//                return false; // oscDown
//            } else if (newestMid > 1 - TresOscCalculator.LOCK_OSC_LEVEL) {
//                return true; // oscUp
//            }
//            double newestVal1 = newestTick.m_val1;
//            double newestVal2 = newestTick.m_val2;
////                if( (newestVal1 < 0.1) && (newestVal2 < 0.1) ) {
////                    return false; // oscDown
////                }
////                if( (newestVal1 > 0.9) && (newestVal2 > 0.9) ) {
////                    return true; // oscUp
////                }
//            if (newestVal1 != newestVal2) { // have direction
//                double newestValDiff = Math.abs(newestVal1 - newestVal2);
//                if (newestValDiff > 0.002) {
//                    boolean newestOscUp = (newestVal1 > newestVal2);
//                    OscTick oldBar = useLastFineTick ? m_oscCalculator.m_lastBar : m_oscCalculator.m_prevBar;
//                    if (oldBar != null) {
//                        double oldOsc1 = oldBar.m_val1;
//                        double oldOsc2 = oldBar.m_val2;
//                        if (oldOsc1 != oldOsc2) { // had direction
//                            Boolean oldOscUp = (oldOsc1 > oldOsc2);
//                            if (oldOscUp == newestOscUp) {
//                                if(false/*useLastFineTick && (newestMid>0.3) && (newestMid <0.7)*/) {
//                                    OscTick oldestBar = m_oscCalculator.m_prevBar;
//                                    if (oldestBar != null) {
//                                        double oldestOsc1 = oldestBar.m_val1;
//                                        double oldestOsc2 = oldestBar.m_val2;
//                                        if (oldestOsc1 != oldestOsc2) { // had direction
//                                            Boolean oldestOscUp = (oldestOsc1 > oldestOsc2);
//                                            if (oldOscUp == oldestOscUp) {
//                                                return newestOscUp;
//                                            }
//                                        }
//                                    }
//                                } else {
//                                    return newestOscUp;
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        return null; // meant undefined direction
//    }
}
