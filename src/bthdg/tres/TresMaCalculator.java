package bthdg.tres;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.osc.OscTick;

import java.util.LinkedList;

public class TresMaCalculator extends MaCalculator {
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
        if ((m_lastMaCrossUp != null) && (m_lastOscUp != null)) {
            if (m_lastMaCrossUp != m_lastOscUp) {
                Boolean oscUp = calcOscDirection();
                if (oscUp != null) {
                    if (m_lastMaCrossUp == oscUp) {
                        addNewMaCrossData(tdata, oscUp);
                    }
                }
            }
        }
    }

    private void onMaCross(TradeData tdata, boolean maCrossUp) {
        m_tick.m_maCrossed = true;
        Boolean oscUp = calcOscDirection();
        if (oscUp != null) {
            addNewMaCrossData(tdata, oscUp);
            m_lastMaCrossUp = maCrossUp;
        }
    }

    private void addNewMaCrossData(TradeData tdata, Boolean oscUp) {
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        MaCrossData maCrossData = new MaCrossData(timestamp, oscUp, price);
        m_maCrossDatas.add(maCrossData);
        m_lastOscUp = oscUp;
    }

    private Boolean calcOscDirection() {
        TresOscCalculator oscCalculator = m_phaseData.m_oscCalculator;
        OscTick blendedLastFineTick = oscCalculator.m_blendedLastFineTick;
        if (blendedLastFineTick != null) {
            double valMid = blendedLastFineTick.getMid();
            if (valMid < 0.1) {
                return false; // oscDown
            }
            if (valMid > 0.9) {
                return true; // oscUp
            }
            double val1 = blendedLastFineTick.m_val1;
            double val2 = blendedLastFineTick.m_val2;
//                if( (val1 < 0.1) && (val2 < 0.1) ) {
//                    return false; // oscDown
//                }
//                if( (val1 > 0.9) && (val2 > 0.9) ) {
//                    return true; // oscUp
//                }
            boolean oscUp = (val1 > val2);

            OscTick lastBar = oscCalculator.m_lastBar;
            if (lastBar != null) {
                double lastOsc1 = lastBar.m_val1;
                double lastOsc2 = lastBar.m_val2;
                boolean lastOscUp = (lastOsc1 > lastOsc2);
                if (lastOscUp == oscUp) {
                    return oscUp;
                }
            }
        }
        return null;
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
