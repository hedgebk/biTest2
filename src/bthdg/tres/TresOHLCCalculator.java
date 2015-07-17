package bthdg.tres;

public class TresOHLCCalculator extends OHLCCalculator {
    private final PhaseData m_phaseData;
    private final int m_phaseIndex;

    public TresOHLCCalculator(PhaseData phaseData, int phaseIndex) {
        super(phaseData.m_exchData.m_tres.m_barSizeMillis, getOffset(phaseIndex, phaseData.m_exchData.m_tres));
        m_phaseData = phaseData;
        m_phaseIndex = phaseIndex;
    }

    private static long getOffset(int index, Tres tres) {
        return tres.m_barSizeMillis * (index % tres.m_phases) / tres.m_phases;
    }

    @Override protected void onBarStarted(OHLCTick tick) {
        m_phaseData.onOHLCTickStart(tick);
    }
}
