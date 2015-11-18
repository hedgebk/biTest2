package bthdg.tres.ind;

import bthdg.tres.ChartPoint;
import bthdg.tres.TresExchData;
import bthdg.tres.alg.TresAlgo;
import bthdg.util.Colors;

import java.awt.*;

public class VelocityRateIndicator extends TresIndicator {
    public final VelocityIndicator.VelocityRateCalculator m_velRateCalc;

    public VelocityRateIndicator(TresAlgo algo, TresIndicator baseIndicator) {
        super("R", 0.1, algo);
        m_velRateCalc = new VelocityIndicator.VelocityRateCalculator(baseIndicator);
    }

    @Override public TresPhasedIndicator createPhasedInt(TresExchData exchData, int phaseIndex) { return null; }
    @Override public Color getColor() { return Colors.LIGHT_BLUE; }
    @Override protected boolean countPeaks() { return false; }

    @Override public void addBar(ChartPoint velocityPoint) {
        ChartPoint ratePoint = m_velRateCalc.update(velocityPoint);
        super.addBar(ratePoint);
    }
}
