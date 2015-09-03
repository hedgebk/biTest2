package bthdg.calc;

public class OscCalculatorTest {

    public static final int BAR_SIZE = 100;
    private static double[] DATA = new double[]{2166.24, 2156.17, 2121.99, 2143.58, 2149.95, 2150.49, 2145.94, 2148.44, 2144.6, 2151.04, 2140.03, 2144.3, 2148.1, 2146.8, 2149.44, 2148.03, 2149.37, 2149.65, 2146.3, 2151.47, 2149.99, 2148.96, 2149.18, 2134.41, 2140.34, 2147.55, 2149.66, 2149.91, 2147.37, 2158.96, 2157.9, 2151.11, 2151.48, 2154.86, 2157.56, 2154.93, 2155.03, 2158.43, 2152.2, 2152.05, 2153.06, 2155.91, 2164.23, 2176.77, 2176.98, 2167.35, 2171.24, 2174.3, 2172.67, 2165.09, 2164.99, 2167.38, 2162, 2165.83, 2163.38, 2154, 2156.24, 2151.64};
    private static double[] STOCH1 = new double[]{0.8816416799, 0.7828876543, 0.7465023873, 0.8371690288, 0.8820707221, 0.8806107772, 0.8938471344, 0.7232315771, 0.5458513352, 0.3562832991, 0.5043390452, 0.7340198204, 0.9235878565, 1, 0.8281752111, 0.7016436885, 0.6094865643, 0.6599372211, 0.53945477, 0.3830258222, 0.2905265809, 0.2351553598, 0.2262605678, 0.1264290139, 0.09548087539, 0.0495603686, 0.02993196263};
    private static double[] STOCH2 = new double[]{0.9230474127, 0.8670173176, 0.8036772405, 0.7888530235, 0.8219140461, 0.8666168427, 0.8855095446, 0.8325631629, 0.7209766822, 0.5417887372, 0.4688245599, 0.5315473883, 0.7206489074, 0.8858692256, 0.9172543559, 0.8432729665, 0.7131018213, 0.6570224913, 0.6029595185, 0.5274726044, 0.4043357244, 0.3029025876, 0.2506475028, 0.1959483138, 0.1493901524, 0.09049008596, 0.05832440221};

    public static void main(String[] argv) {
        OscCalculator oscCalculator = new OscCalculator(14, 14, 3, 3, BAR_SIZE, 0) {
            int m_indx = 0;
            @Override public void bar(long barStart, double stoch1, double stoch2) {
                System.out.println("bar\t" + stoch1 + "\t" + stoch2);
                if (Math.abs(stoch1 - STOCH1[m_indx]) > 0.000001) {
                    System.out.println("  !!!! STOCH1 not matched. expected " + STOCH1[m_indx]);
                }
                if (Math.abs(stoch2 - STOCH2[m_indx]) > 0.000001) {
                    System.out.println("  !!!! STOCH2 not matched. expected " + STOCH2[m_indx]);
                }
                m_indx++;
            }
        };
        long time = System.currentTimeMillis();
        for (double value : DATA) {
            oscCalculator.update(time, value);
            time += BAR_SIZE;
        }
        oscCalculator.updateCurrentBar(0, true);
    }
}
