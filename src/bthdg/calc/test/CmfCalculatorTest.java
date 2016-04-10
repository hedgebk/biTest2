package bthdg.calc.test;

import bthdg.calc.CmfCalculator;
import bthdg.exch.TradeData;
import bthdg.util.Utils;

public class CmfCalculatorTest {
    private static final int BAR_SIZE = 100;
    private static final int LENGTH = 20;
    private static float[] HIGH =   new float[]{62.34f, 62.05f, 62.27f, 60.79f, 59.93f, 61.75f, 60.00f, 59.00f, 59.07f, 59.22f, 58.75f, 58.65f, 58.47f, 58.25f, 58.35f, 59.86f, 59.53f, 62.10f, 62.16f, 62.67f, 62.38f, 63.73f, 63.85f, 66.15f, 65.34f, 66.48f, 65.23f, 63.40f, 63.18f, 62.70f};
    private static double[] LOW =    new double[]{61.37, 60.69, 60.10, 58.61, 58.71, 59.86, 57.97, 58.02, 57.48, 58.30, 57.83, 57.86, 57.91, 57.83, 57.53, 58.58, 58.30, 58.53, 59.80, 60.93, 60.15, 62.26, 63.00, 63.58, 64.07, 65.20, 63.21, 61.88, 61.11, 61.25};
    private static double[] CLOSE =  new double[]{62.15, 60.81, 60.45, 59.18, 59.24, 60.20, 58.48, 58.24, 58.69, 58.65, 58.47, 58.02, 58.17, 58.07, 58.13, 58.94, 59.10, 61.92, 61.37, 61.68, 62.09, 62.89, 63.53, 64.01, 64.77, 65.22, 63.28, 62.40, 61.55, 62.69};
    private static float[] VOLUME = new float[]{7849.025f, 11692.075f, 10575.307f, 13059.128f, 20733.508f, 29630.096f, 17705.294f, 7259.203f, 10474.629f, 5203.714f, 3422.865f, 3962.150f, 4095.905f, 3766.006f, 4239.335f, 8039.979f, 6956.717f, 18171.552f, 22225.894f, 14613.509f, 12319.763f, 15007.690f, 8879.667f, 22693.812f, 10191.814f, 10074.152f, 9411.620f, 10391.690f, 8926.512f, 7459.575f};
    private static double[] VAL =    new double[]{-0.1213542, -0.0999473, -0.0662693, -0.0260313, -0.0620334, -0.0482827, -0.0087899, -0.0089052, -0.0052496, -0.0576088, -0.0149874};

    public static void main(String[] argv) {
        CmfCalculator cmfCalculator = new CmfCalculator(LENGTH, BAR_SIZE, 0) {
            int m_indx = 0;

            @Override protected void finishCurrentBar(long time, double price) {
                super.finishCurrentBar(time, price);
                if (m_lastCmf != null) {
                    System.out.println("bar[" + m_indx + "]\tlastCmf=" + m_lastCmf);
                    double diff = m_lastCmf - VAL[m_indx];
                    if (Math.abs(diff) > 0.001) {
                        System.out.println("  !!!! CMF not matched. expected " + VAL[m_indx] + "; diff=" + Utils.format8(diff));
                    }
                    m_indx++;
                }
            }
        };
        long time = System.currentTimeMillis();
        for (int i = 0, highLength = HIGH.length; i < highLength; i++) {
            TradeData tdata = new TradeData(VOLUME[i], HIGH[i], time);
            cmfCalculator.update(tdata);
            cmfCalculator.update(time, LOW[i]);
            cmfCalculator.update(time, CLOSE[i]);
            time += BAR_SIZE;
        }
        cmfCalculator.updateCurrentBar(time, 0);
    }
}
