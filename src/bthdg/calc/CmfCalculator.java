package bthdg.calc;

import bthdg.Log;
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

    private static void log(String s) { Log.log(s); }

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

    @Override protected void finishCurrentBar(long time, double price) {
        double close = m_tick.m_close;
        double low = m_tick.m_low;
        double high = m_tick.m_high;

        double mfMultiplier = (2 * close - low - high) / (high - low);

        m_volumesSum -= m_volumes[m_lastIndex];
        m_volumesSum += m_volume;
        System.arraycopy(m_volumes, 0, m_volumes, 1, m_lastIndex);
        m_volumes[0] = m_volume;
        double mfVolume = mfMultiplier * m_volume;
        m_MfVolumesSum -= m_MfVolumes[m_lastIndex];
        m_MfVolumesSum += mfVolume;
        System.arraycopy(m_MfVolumes, 0, m_MfVolumes, 1, m_lastIndex);
        m_MfVolumes[0] = mfVolume;

        m_counter++;
        if (m_counter >= m_length) {
            m_lastCmf = (m_volumesSum == 0) ? 0.0 : m_MfVolumesSum / m_volumesSum;
            if (Double.isNaN(m_lastCmf)) {
                log("NAN");
            }
        }
    }
}