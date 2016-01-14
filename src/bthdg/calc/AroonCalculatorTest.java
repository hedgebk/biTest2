package bthdg.calc;

public class AroonCalculatorTest {
    public static final int BAR_SIZE = 100;
    private static double[] DATA     = new double[]{ 1, 2, 3, 4, 5,   6,   5,  4,  3,   };
    private static double[] VAL_UP   = new double[]{ 0, 0, 0, 0, 100, 100, 75, 50, 25,  };
    private static double[] VAL_DOWN = new double[]{ 0, 0, 0, 0, 0,   0,   0,  0,  100, };
    private static double[] VAL_OSC  = new double[]{ 0, 0, 0, 0, 100, 100, 75, 50, -75, };

    public static void main(String[] argv) {
        AroonCalculator aroonCalculator = new AroonCalculator(5, BAR_SIZE, 0) {
            int m_indx = 0;

            @Override protected void finishCurrentBar(long time, double price) {
                if (m_aroonUp != null) {
                    System.out.println("bar\tup=" + m_aroonUp + "; down=" + m_aroonDown + "; osc=" + m_aroonOscillator);
                    if (Math.abs(m_aroonUp - VAL_UP[m_indx]) > 0.001) {
                        System.out.println("  !!!! UP not matched. indx=" + m_indx + "; expected " + VAL_UP[m_indx]);
                    }
                    if (Math.abs(m_aroonDown - VAL_DOWN[m_indx]) > 0.001) {
                        System.out.println("  !!!! DOWN not matched. indx=" + m_indx + "; expected " + VAL_DOWN[m_indx]);
                    }
                    if (Math.abs(m_aroonOscillator - VAL_OSC[m_indx]) > 0.001) {
                        System.out.println("  !!!! OSC not matched. indx=" + m_indx + "; expected " + VAL_OSC[m_indx]);
                    }
                }
                m_indx++;
            }
        };
        long time = System.currentTimeMillis();
        for (double value : DATA) {
            aroonCalculator.update(time, value);
            time += BAR_SIZE;
        }
        aroonCalculator.update(time, 0);
    }
}
