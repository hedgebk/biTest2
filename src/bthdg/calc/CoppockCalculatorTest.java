package bthdg.calc;

public class CoppockCalculatorTest {
    public static final int BAR_SIZE = 100;
    private static double[] DATA = new double[]{872.81, 919.14, 919.32, 987.48, 1020.62, 1057.08, 1036.19, 1095.63, 1115.1, 1073.87, 1104.49, 1169.43, 1186.69, 1089.41, 1030.71, 1101.6, 1049.33, 1141.2, 1183.26, 1180.55, 1257.64, 1286.12, 1327.22, 1325.83, 1363.61, 1345.2, 1320.64, 1292.28, 1218.89, 1131.42, 1253.3, 1246.96, 1257.6, 1312.41, 1365.68, 1408.47, 1397.91, 1310.33, 1362.16, 1379.32};
    private static double[] VAL = new double[]{23.89, 19.32, 16.35, 14.12, 12.78, 11.39, 8.37, 7.45, 8.79};

    public static void main(String[] argv) {
        CoppockCalculator coppockCalculator = new CoppockCalculator(10, 14, 11, BAR_SIZE, 0) {
//            int m_indx = 0;
            @Override public void bar(long barEnd, double value) {
                System.out.println("bar\t" + value);
//                if (Math.abs(stoch1 - STOCH1[m_indx]) > 0.000001) {
//                    System.out.println("  !!!! STOCH1 not matched. expected " + STOCH1[m_indx]);
//                }
//                if (Math.abs(stoch2 - STOCH2[m_indx]) > 0.000001) {
//                    System.out.println("  !!!! STOCH2 not matched. expected " + STOCH2[m_indx]);
//                }
//                m_indx++;
            }
        };
        long time = System.currentTimeMillis();
        for (double value : DATA) {
            coppockCalculator.update(time, value);
            time += BAR_SIZE;
        }
        coppockCalculator.updateCurrentBar(time, 0);
    }
}
