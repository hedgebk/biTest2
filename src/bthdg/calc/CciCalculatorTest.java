package bthdg.calc;

public class CciCalculatorTest {
    public static final int BAR_SIZE = 100;
    private static double[] DATA = new double[]{23.982633, 23.916367, 23.787167, 23.674533, 23.542000, 23.361467, 23.651300, 23.720867, 24.164867, 23.913067, 23.810367, 23.923033, 23.744133, 24.678400, 24.936767, 24.931800, 25.095800, 25.122300, 25.198533, 25.062667, 24.496133, 24.310600, 24.567367, 24.619567, 24.492000, 24.370267, 24.410000, 24.350367, 23.747433, 24.088667};
    private static double[] VAL = new double[]{102.314959, 30.735992, 6.551595, 33.296414, 34.951377, 13.840116, -10.747813, -11.582114, -29.347178, -129.355664, -73.069595};

    public static void main(String[] argv) {
        CciCalculator cciCalculator = new CciCalculator(20, BAR_SIZE, 0) {
            int m_indx = 0;
            @Override public void bar(long barEnd, double value) {
                System.out.println("bar\t" + value);
                if (Math.abs(value - VAL[m_indx]) > 0.001) {
                    System.out.println("  !!!! CCI not matched. expected " + VAL[m_indx]);
                }
                m_indx++;
            }
        };
        long time = System.currentTimeMillis();
        for (double value : DATA) {
            cciCalculator.update(time, value);
            time += BAR_SIZE;
        }
        cciCalculator.updateCurrentBar(time, 0);
    }
}
