package bthdg.calc;

import bthdg.exch.TradeData;
import bthdg.exch.TradeDataLight;

public class CmfCalculator extends OHLCCalculator {
    private final int m_length;
    private final int m_lastIndex;
    private double m_volume;
    private double[] m_volumes;
    private double[] m_MfVolumes;
    private double m_volumesSum;
    private double m_MfVolumesSum;
    private long m_counter;
    public Double m_lastCmf;

    public CmfCalculator(int length, long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
        m_length = length;
        m_lastIndex = length - 1;
        m_volumes = new double[length];
        m_MfVolumes = new double[length];
    }

    public boolean update(TradeDataLight tdata) {
        long timestamp = tdata.m_timestamp;
        double price = tdata.m_price;
        update(timestamp, price);

        TradeData trData = (TradeData) tdata;
        double amount = trData.m_amount;
        m_volume += amount;

        return true;
    }


    @Override protected void startNewBar(long barStart, long barEnd) {
        super.startNewBar(barStart, barEnd);
        m_volume = 0;
    }

//    @Override protected boolean updateCurrentBar(long time, double price) {
//        boolean ret = super.updateCurrentBar(time, price);
//        if (ret) { // if any ohlc field was changed
//        }
//        return ret;
//    }

    @Override protected void finishCurrentBar(long time, double price) {
//        if (m_lastCci != null) {
//            bar(m_currentBarEnd, m_lastCci);
//        }
        double Close = m_tick.m_close;
        double Low = m_tick.m_low;
        double High = m_tick.m_high;

        double F6=Close;
        double E6=Low;
        double D6=High;
        double MF_Multiplier=(2*F6-E6-D6)/(D6-E6);

        m_volumesSum -= m_volumes[m_lastIndex];
        m_volumesSum += m_volume;
        System.arraycopy(m_volumes, 0, m_volumes, 1, m_lastIndex);
        m_volumes[0] = m_volume;
        double H6=m_volume;
        double G6=MF_Multiplier;
        double MF_Volume=G6*H6;
        m_MfVolumesSum -= m_MfVolumes[m_lastIndex];
        m_MfVolumesSum += MF_Volume;
        System.arraycopy(m_MfVolumes, 0, m_MfVolumes, 1, m_lastIndex);
        m_MfVolumes[0] = MF_Volume;

        m_counter++;
        if (m_counter >= m_length) {
            m_lastCmf = m_MfVolumesSum/m_volumesSum;
        }
    }

    public void finish() {

    }
}