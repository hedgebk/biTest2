package bthdg.calc;

import bthdg.Log;
import bthdg.exch.TradeData;
import bthdg.exch.TradeDataLight;

public class CmfCalculator extends OHLCCalculator {
    private final int m_arrayLength;
    private final double m_length;
    private final int m_lastIndex;
    private final double m_extra;
    private double m_volume;
    private double[] m_volumes;
    private double[] m_MfVolumes;
    private double m_volumesSum;
    private double m_MfVolumesSum;
    private long m_counter;
    public Double m_lastCmf;

    private static void log(String s) { Log.log(s); }

    public CmfCalculator(double length, long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
        m_length = length;
        m_arrayLength = (int) length + 1;
        m_extra = (double) m_arrayLength - m_length;
        m_lastIndex = m_arrayLength - 1;
        m_volumes = new double[m_arrayLength];
        m_MfVolumes = new double[m_arrayLength];
    }

    @Override public boolean update(TradeDataLight tdata) {
        super.update(tdata);

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

        double highLowDiff = high - low;
        double mfMultiplier = (highLowDiff == 0) ? 0 : (2 * close - low - high) / highLowDiff;

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

            double volumesSum = m_volumesSum - m_extra * m_volumes[m_lastIndex];
            double mfVolumesSum = m_MfVolumesSum - m_extra * m_MfVolumes[m_lastIndex];

            m_lastCmf = (volumesSum == 0) ? 0.0 : mfVolumesSum / volumesSum;
            if (Double.isNaN(m_lastCmf)) {
                log("NAN");
            }
        }
    }
}