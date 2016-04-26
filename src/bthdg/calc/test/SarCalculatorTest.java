package bthdg.calc.test;

import bthdg.calc.SarCalculator;
import bthdg.util.Utils;

public class SarCalculatorTest {
    private static final int BAR_SIZE = 100;
    private static float[] HIGH = new float[]{ 46.59f, 46.55f, 46.30f, 45.43f, 44.55f, 44.84f, 44.80f, 44.38f, 43.97f, 43.23f, 43.73f, 43.92f, 43.61f, 42.97f, 43.13f, 43.46f, 43.26f, 43.74f, 43.83f, 44.30f, 44.52f, 44.88f, 45.00f, 44.98f, 44.69f, 44.74f, 44.65f, 44.81f, 45.44f, 45.80f, 45.77f, 45.76f, 46.49f, 46.59f, 47.00f, 47.23f, 47.30f, 47.48f, 47.33f, 47.56f, 47.85f, 47.83f, 47.95f, 48.11f, 48.30f, 48.17f, 48.60f, 48.33f, 48.40f, 48.55f, 48.45f, 48.70f, 48.72f, 48.90f, 48.87f, 48.82f, 49.05f, 49.20f, 49.35f };
    private static float[] LOW  = new float[]{ 45.90f, 45.38f, 45.25f, 43.99f, 44.07f, 44.00f, 43.96f, 43.27f, 42.58f, 42.83f, 42.98f, 43.37f, 42.57f, 42.07f, 42.59f, 42.71f, 42.70f, 42.71f, 43.11f, 43.80f, 44.21f, 44.40f, 44.57f, 44.51f, 43.90f, 44.27f, 43.78f, 44.35f, 44.90f, 45.40f, 45.38f, 45.35f, 45.94f, 46.39f, 46.35f, 46.75f, 46.93f, 47.05f, 46.86f, 47.18f, 47.48f, 47.55f, 47.32f, 47.25f, 47.77f, 47.91f, 47.90f, 47.74f, 48.10f, 48.06f, 48.07f, 47.79f, 48.14f, 48.39f, 48.37f, 48.24f, 48.64f, 48.94f, 48.86f };
    private static float[] VAL  = new float[]{ 46.59000f, 46.59000f, 46.55000f, 46.39640f, 46.25202f, 46.11630f, 45.94379f, 45.67641f, 45.30484f, 44.97786f, 44.69012f, 44.43690f, 44.17554f, 43.83865f, 43.55567f, 43.46000f, 42.07000f, 42.10340f, 42.17246f, 42.30012f, 42.47771f, 42.71794f, 42.99178f, 43.23277f, 43.44484f, 43.63146f, 43.78000f, 43.78000f, 44.01240f, 44.29842f, 44.53867f, 44.74048f, 45.05540f, 45.36232f, 45.68985f, 45.99788f, 46.25831f, 46.50264f, 46.69812f, 46.86000f, 47.05800f, 47.21640f, 48.11000f, 47.25000f, 47.25000f, 47.27100f, 47.32416f, 47.37519f, 47.42419f, 47.47122f, 47.51637f, 47.58739f, 47.67800f, 47.80020f, 47.91018f, 48.00916f, 48.13406f, 48.28329f };

    public static void main(String[] argv) {
        SarCalculator sarCalculator = new SarCalculator(BAR_SIZE, 0) {
            int m_indx = 0;

            @Override protected void finishCurrentBar(long time, double price) {
                if(m_indx==26) {
                    System.out.println("26");
                }
                super.finishCurrentBar(time, price);
                if (m_lastSar != null) {
                    float val = VAL[m_indx];
                    double diff = m_lastSar - val;
                    System.out.println("bar[" + m_indx + "]\tlastSAR=" + Utils.format8(m_lastSar) + "; diff=" + Utils.format8(diff));
                    if (Math.abs(diff) > 0.001) {
                        System.out.println("  !!!! SAR not matched. expected " + VAL[m_indx]);
                    }
                    m_indx++;
                }
            }
        };
        long time = System.currentTimeMillis();
        for (int i = 0, highLength = HIGH.length; i < highLength; i++) {
            sarCalculator.update(time, HIGH[i]);
            sarCalculator.update(time, LOW[i]);
            time += BAR_SIZE;
        }
        sarCalculator.updateCurrentBar(time, 0);
    }
}
