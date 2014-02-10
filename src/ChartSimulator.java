import java.awt.*;

public class ChartSimulator {
    private double m_deltaSum = 0;
    private int m_runs = 0;
    private int m_drops = 0;
    private double m_lastDif = Double.MAX_VALUE;
    private int m_lastPoint = -1;
    private STATE m_state = STATE.NONE;
    private STATE m_lockedDirection;

    enum STATE { NONE, BOUGHT, SOLD }

    // earliest first
    public void simulate(PaintChart.PriceDiffList[] diffsPerPoints, PaintChart.Axe difAxe, Graphics2D g, double[] movingAverage) {
        //Map<Integer,Double> graph = new HashMap<Integer, Double>();
        int pause = 0;
        int length = diffsPerPoints.length;
        for (int i = 0; i < length; i++) {
            if(pause-- > 0) {
                continue;
            }
            PaintChart.PriceDiffList diffsPerPoint = diffsPerPoints[i];
            if (diffsPerPoint != null) {
                double movingAvg = movingAverage[i];
                double movingAvgUp = movingAvg + PaintChart.HALF_TARGET_DELTA;
                double movingAvgDown = movingAvg - PaintChart.HALF_TARGET_DELTA;
                int diffsPerPointSize = diffsPerPoint.size();
                for (int j = 0; j < diffsPerPointSize; j++) {
                    double priceDif = diffsPerPoint.get(j);
                    if (m_state == STATE.NONE) {
                        if ((priceDif > movingAvgUp) && (m_lockedDirection != STATE.SOLD)) {
                            if(hasConfirmedUp(PaintChart.MIN_CONFIRMED_DIFFS, movingAvgUp, diffsPerPoints, i, j, PaintChart.MAX_NEXT_POINTS_TO_CONFIRM)) {
                                System.out.println("point " + i + " - SOLD at " + priceDif);
                                setState(i, priceDif, STATE.SOLD);
                                break;
                            }
                        }
                        if ((priceDif < movingAvgDown) && (m_lockedDirection != STATE.BOUGHT)) {
                            if(hasConfirmedDown(PaintChart.MIN_CONFIRMED_DIFFS, movingAvgDown, diffsPerPoints, i, j, PaintChart.MAX_NEXT_POINTS_TO_CONFIRM)) {
                                System.out.println("point " + i + " - BOUGHT at " + priceDif);
                                setState(i, priceDif, STATE.BOUGHT);
                                break;
                            }
                        }
                    } else if (m_state == STATE.BOUGHT) {
                        double okDif = m_lastDif + PaintChart.TARGET_DELTA;
                        if (priceDif > okDif) {
                            if(hasConfirmedUp(PaintChart.MIN_CONFIRMED_DIFFS, okDif, diffsPerPoints, i, j, PaintChart.MAX_NEXT_POINTS_TO_CONFIRM)) {
                                double delta = priceDif - m_lastDif;
                                closeRun(difAxe, g, i, priceDif, delta, false);
                                System.out.println("point " + i + " - SOLD at " + priceDif + ", delta=" + delta);
                                break;
                            }
                        }
                        if (PaintChart.DO_DROP) {
                            if (movingAvg < m_lastDif - PaintChart.HALF_TARGET_DELTA* PaintChart.DROP_LEVEL) {
                                double delta = priceDif - m_lastDif;
                                closeRun(difAxe, g, i, priceDif, delta, true);
                                System.out.println("point " + i + " - DROP SOLD at " + priceDif + ", delta=" + delta);
                                pause = 1;
                                break;
                            }
                        }
                    } else if (m_state == STATE.SOLD) {
                        double okDif = m_lastDif - PaintChart.TARGET_DELTA;
                        if (priceDif < okDif) {
                            if(hasConfirmedDown(PaintChart.MIN_CONFIRMED_DIFFS, okDif, diffsPerPoints, i, j, PaintChart.MAX_NEXT_POINTS_TO_CONFIRM)) {
                                double delta = m_lastDif - priceDif;
                                closeRun(difAxe, g, i, priceDif, delta, false);
                                System.out.println("point " + i + " - BOUGHT at " + priceDif + ", delta=" + delta);
                                break;
                            }
                        }
                        if (PaintChart.DO_DROP) {
                            if (movingAvg > m_lastDif + PaintChart.HALF_TARGET_DELTA* PaintChart.DROP_LEVEL) {
                                double delta = m_lastDif - priceDif;
                                closeRun(difAxe, g, i, priceDif, delta, true);
                                System.out.println("point " + i + " - DROP SOLD at " + priceDif + ", delta=" + delta);
                                pause = 1;
                                break;
                            }
                        }
                    }
                }
            }
        }
        double commissions = PaintChart.COMMISSION_AMOUNT * m_runs;
        double gain = m_deltaSum - commissions;
        double perDay = gain / PaintChart.PERIOD_LENGTH_DAYS;
        double dayMult = 1 + perDay / 803 / 4;
        double complex = Math.pow(dayMult, PaintChart.PERIOD_LENGTH_DAYS);
        double complex1m = Math.pow(dayMult, 30);

        System.out.println("--- got " + m_runs + " runs, sum=" + m_deltaSum + ", commissions=" + commissions + ", hedged=" + gain + ", a day=" + perDay + ", period " + PaintChart.PERIOD_LENGTH_DAYS +", complex="+complex);
        g.setColor(Color.DARK_GRAY);
        g.setFont(g.getFont().deriveFont(15.0f* PaintChart.X_FACTOR));
        String str = PaintChart.XX_YYYYY.format(gain) + ", " +
                "runs=" + m_runs + ", " +
                "drops=" + m_drops + ", " +
                "period " + PaintChart.PERIOD_LENGTH_DAYS + "d, " +
                "AVG=" + PaintChart.XX_YYYYY.format(perDay) + "/day, " +
                "dayMult=" + PaintChart.XX_YYYYY.format(dayMult) + ", " +
                "complex=" + PaintChart.XX_YYYYY.format(complex) + ", " +
                "complex1m=" + PaintChart.XX_YYYYY.format(complex1m);
        g.drawString(str, 40, PaintChart.HEIGHT - 34);
    }

    private void closeRun(PaintChart.Axe difAxe, Graphics2D g, int i, double priceDiff, double delta, boolean drop) {
        m_deltaSum += delta;
        m_runs++;
        if( drop ) {
            m_drops++;
            if(PaintChart.LOCK_DIRECTION_ON_DROP) {
                m_lockedDirection = m_state;
            }
        } else {
            m_lockedDirection = null;
        }
        m_state = STATE.NONE;
        int x1 = m_lastPoint;
        int y1 = difAxe.getPointReverse(m_lastDif);
        int x2 = i;
        int y2 = difAxe.getPointReverse(priceDiff);
        g.setPaint(drop ? Color.CYAN : Color.blue);
        g.drawLine(x1, y1, x2, y2);
    }

    private void setState(int i, double priceDiff, STATE st) {
        m_state = st;
        m_lastDif = priceDiff;
        m_lastPoint = i;
    }

    private boolean hasConfirmedUp(int expectedConfirmed, double movingAvgUp,
                                   PaintChart.PriceDiffList[] diffsPerPoints, int i, int j, int maxNextPoints) {
        return hasConfirmed(expectedConfirmed, movingAvgUp, diffsPerPoints, i, j, maxNextPoints, true);
    }

    private boolean hasConfirmedDown(int expectedConfirmed, double movingAvgUp,
                                     PaintChart.PriceDiffList[] diffsPerPoints, int i, int j, int maxNextPoints) {
        return hasConfirmed(expectedConfirmed, movingAvgUp, diffsPerPoints, i, j, maxNextPoints, false);
    }

    // earliest first
    private boolean hasConfirmed(int expectedConfirmed, double movingAvg,
                                 PaintChart.PriceDiffList[] diffsPerPoints, int i, int j, int maxNextPoints, boolean up) {
        int confirmed = 0;
        int secondIndexStart = j + 1;
        int length = diffsPerPoints.length;
        for(int k = 0, indx = i; (k <= maxNextPoints) && (indx < length); k++, indx++ ) {
            PaintChart.PriceDiffList diffsPerPoint = diffsPerPoints[indx];
            if(diffsPerPoint != null) {
                int size = diffsPerPoint.size();
                for(int l = secondIndexStart; l < size; l++) {
                    double priceDiff = diffsPerPoint.get(l);
                    if ( (up && priceDiff > movingAvg) || (!up && priceDiff < movingAvg) ) {
                        confirmed++;
                        if(confirmed >= expectedConfirmed) {
                            return true;
                        }
                    }
                }
                secondIndexStart = 0;
            }
        }
        return false;
    }
}
