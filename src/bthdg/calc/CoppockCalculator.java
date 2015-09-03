package bthdg.calc;

import java.util.LinkedList;

public class CoppockCalculator extends BarCalculator {
    private final int m_wmaLength;
    private final int m_longRocLength;
    private final int m_shortRoсLength;
    private LinkedList<Double> m_closesShort = new LinkedList<Double>();
    private boolean m_closesShortFilled;
    private LinkedList<Double> m_closesLong = new LinkedList<Double>();
    private boolean m_closesLongFilled;
    private LinkedList<Double> m_rocSumms = new LinkedList<Double>();
    private boolean m_rocSummsFilled;
    private Double m_lastCoppock;

    public CoppockCalculator(int wmaLength, int longRocLength, int shortRoсLength, long barSize, long barsMillisOffset) {
        super(barSize, barsMillisOffset);
        m_wmaLength = wmaLength;
        m_longRocLength = longRocLength;
        m_shortRoсLength = shortRoсLength;
    }

    @Override protected void startNewBar(long barStart, long barEnd) {
        if (m_closesShortFilled) {
            m_closesShort.removeFirst();
            m_closesShort.addLast(0d); // add last
        } else {
            m_closesShort.addLast(0d); // add last
            int sizeShort = m_closesShort.size();
            if (sizeShort == m_shortRoсLength + 1) {
                m_closesShortFilled = true;
            }
        }

        if (m_closesLongFilled) {
            m_closesLong.removeFirst();
            m_closesLong.addLast(0d); // add last

            if (m_rocSummsFilled) {
                m_rocSumms.removeFirst();
                m_rocSumms.addLast(0d); // add last
                m_lastCoppock = null;
            } else {
                m_rocSumms.addLast(0d); // add last
                int m_rocSummsSize = m_rocSumms.size();
                if (m_rocSummsSize == m_wmaLength) {
                    m_rocSummsFilled = true;
                }
            }
        } else {
            m_closesLong.addLast(0d); // add last
            int sizeLong = m_closesLong.size();
            if (sizeLong == m_longRocLength + 1) {
                m_closesLongFilled = true;

                m_rocSumms.addLast(0d); // add last
            }
        }
    }

    @Override protected boolean updateCurrentBar(long time, double price) {
        replaceLastElement(m_closesShort, price);
        replaceLastElement(m_closesLong, price);
        if (m_closesShortFilled) {
            Double lastShort = m_closesShort.getLast();
            Double firstShort = m_closesShort.getFirst();
            double rosShort = (lastShort - firstShort) / firstShort * 100;
            if (m_closesLongFilled) {
                Double lastLong = m_closesLong.getLast();
                Double firstLong = m_closesLong.getFirst();
                double rosLong = (lastLong - firstLong) / firstLong * 100;

                double rosSum = rosShort + rosLong;
                replaceLastElement(m_rocSumms, rosSum);
                if (m_rocSummsFilled) {
                    // calc wma: =((H27*1)+(H28*2)+(H29*3)+(H30*4)+(H31*5)+(H32*6)+(H33*7)+(H34*8)+(H35*9)+(H36*10))/55
                    int weight = 1;
                    double summ = 0;
                    double weightSumm = 0;
                    for (Double val : m_rocSumms) {
                        summ += val * weight;
                        weightSumm += weight;
                        weight++;
                    }
                    double wma = summ / weightSumm;

                    fine(time, wma);
                    m_lastCoppock = wma;
                    return true;
                }
            }
        }
        return false;
    }

    private static void replaceLastElement(LinkedList<Double> list, double price) {
        list.set(list.size() - 1, price); // replace last element
    }

    @Override protected void finishCurrentBar(long barStart, long barEnd, long time, double price) {
        if (m_lastCoppock != null) {
            bar(m_currentBarStart, m_lastCoppock);
        }
    }

    protected void fine(long time, double value) {}
    protected void bar(long barStart, double value) {}
}
