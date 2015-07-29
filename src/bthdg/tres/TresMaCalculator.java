package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.osc.OscTick;

import java.util.Iterator;
import java.util.LinkedList;

public class TresMaCalculator extends MaCalculator {
    public static final double LOCK_OSC_LEVEL = 0.1;
    private final PhaseData m_phaseData;
    final LinkedList<MaTick> m_maTicks = new LinkedList<MaTick>();
    final LinkedList<MaCrossData> m_maCrossDatas = new LinkedList<MaCrossData>();
    private MaTick m_tick;
    private Boolean m_lastPriceHigher;
    private Boolean m_lastMaCrossUp;
    private Boolean m_lastOscUp;

    private static void log(String s) { Log.log(s); }

    public TresMaCalculator(PhaseData phaseData, int phaseIndex) {
        super(phaseData.m_exchData, phaseIndex, MaType.CLOSE, phaseData.m_exchData.m_tres.m_ma);
        m_phaseData = phaseData;
    }

    @Override protected void startMaBar(long barEnd) {
        m_tick = new MaTick(barEnd);
        m_maTicks.add(m_tick);
    }

    @Override protected void updateMaBar(double ma) {
        m_tick.m_ma = ma;
    }

    @Override public boolean update(TradeData tdata) {
        boolean ret = super.update(tdata);

        double currentPrice = tdata.m_price;
        double currentMa = m_tick.m_ma;
        boolean currentPriceHigher = (currentPrice > currentMa);

        if ((m_lastPriceHigher != null) && (m_lastPriceHigher != currentPriceHigher)) {
            onMaCross(tdata, currentPriceHigher);
        }
        m_lastPriceHigher = currentPriceHigher;
        return ret;
    }

    @Override protected void endMaBar(long barEnd, double ma, TradeData tdata) {
        if ((m_lastMaCrossUp != null) && (m_lastOscUp != null) && (m_lastMaCrossUp != m_lastOscUp)) {
            Boolean oscUp = calcOscDirection(false);
            if (oscUp != null) { // if direction defined
                if (m_lastMaCrossUp == oscUp) {
                    addNewMaCrossData(tdata, oscUp);
                }
            }
        }
    }

    private void onMaCross(TradeData tdata, boolean maCrossUp) {
        m_tick.m_maCrossed = true;
        Boolean oscUp = calcOscDirection(true);
        if (oscUp != null) { // if direction defined
            addNewMaCrossData(tdata, oscUp);
        }
        m_lastMaCrossUp = maCrossUp;
    }

    private void addNewMaCrossData(TradeData tdata, Boolean oscUp) {
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        MaCrossData maCrossData = new MaCrossData(timestamp, oscUp, price);
        m_maCrossDatas.add(maCrossData);
        m_lastOscUp = oscUp;
    }

    private Boolean calcOscDirection(boolean useLastFineTick) {
        TresOscCalculator oscCalculator = m_phaseData.m_oscCalculator;
        OscTick newestTick = useLastFineTick ? oscCalculator.m_blendedLastFineTick : oscCalculator.m_lastBar;
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
                    OscTick oldBar = useLastFineTick ? oscCalculator.m_lastBar : oscCalculator.m_prevBar;
                    if (oldBar != null) {
                        double oldOsc1 = oldBar.m_val1;
                        double oldOsc2 = oldBar.m_val2;
                        if (oldOsc1 != oldOsc2) { // had direction
                            Boolean oldOscUp = (oldOsc1 > oldOsc2);
                            if (oldOscUp == newestOscUp) {
                                if(false/*useLastFineTick && (newestMid>0.3) && (newestMid <0.7)*/) {
                                    OscTick oldestBar = oscCalculator.m_prevBar;
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

    public double calcToTal() {
        Boolean lastOscUp = null;
        Double lastPrice = null;
        double totalPriceRatio = 1;
        for (Iterator<MaCrossData> iterator = m_maCrossDatas.iterator(); iterator.hasNext(); ) {
            TresMaCalculator.MaCrossData maCrossData = iterator.next();
            boolean oscUp = maCrossData.m_oscUp;
            double price = maCrossData.m_price;
            if (lastOscUp != null) {
                if (lastOscUp != oscUp) {
                    if (lastPrice != null) {
                        double priceRatio = price / lastPrice;
                        if (!lastOscUp) {
                            priceRatio = 1 / priceRatio;
                        }
                        totalPriceRatio *= priceRatio;
                    }
                    lastOscUp = oscUp;
                    lastPrice = price;
                }
            } else { // first
                lastOscUp = oscUp;
                lastPrice = price;
            }
        }
        return totalPriceRatio;
    }

    public static class MaCrossData {
        final long m_timestamp;
        final boolean m_oscUp;
        final double m_price;

        public MaCrossData(long timestamp, boolean oscUp, double price) {
            m_timestamp = timestamp;
            m_oscUp = oscUp;
            m_price = price;
        }
    }

    public static class MaTick {
        protected final long m_barEnd;
        protected double m_ma;
        public boolean m_maCrossed;

        public MaTick(long barEnd) {
            m_barEnd = barEnd;
        }
    }
}
