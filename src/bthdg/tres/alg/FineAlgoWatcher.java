package bthdg.tres.alg;

import bthdg.Log;
import bthdg.tres.TresExchData;
import bthdg.util.Utils;

public class FineAlgoWatcher extends BaseAlgoWatcher {
    public static final double MIN_MOVE = 0.033;
    private static final long MOVE_DELAY = 5000; // every 5 sec
    private double m_lastPrice;

    private Balance m_startBalance;
    private Balance m_balance;
    private long m_lastMoveMillis;


    private static void log(String s) { Log.log(s); }

    public FineAlgoWatcher(TresExchData tresExchData, TresAlgo algo) {
        super(tresExchData, algo);
    }

    @Override public void onValueChange() {
        super.onValueChange();

        double lastPrice = m_algo.lastTickPrice();
        long lastTickTime = m_algo.lastTickTime();
        log("onValueChange: lastTickTime=" + lastTickTime + "; lastPrice=" + lastPrice);

        if ((lastPrice != 0) && (lastTickTime != 0)) {
            if (m_startBalance == null) {
                m_startBalance = new Balance(lastPrice, 1);
                m_balance = new Balance(lastPrice, 1);
                log(" startBalance=" + m_startBalance);
            } else {
                double direction = m_algo.getDirectionAdjusted(); // UP/DOWN
                double distribution = m_balance.distribution(lastPrice);
                double move = direction - distribution;
                log(" direction=" + Utils.format8(direction) + "; distribution=" + Utils.format8(distribution));
                double valuateInTwo = m_balance.valuateInTwo(lastPrice);
                double moveInTwo = valuateInTwo * move / 2;
                log("  valuateInTwo=" + Utils.format8(valuateInTwo) + "; moveInTwo=" + Utils.format8(moveInTwo));

                if (moveInTwo > MIN_MOVE) {
                    long timeDiff = lastTickTime - m_lastMoveMillis;
                    if (timeDiff > MOVE_DELAY) {
                        log("   balance in: " + m_balance);
                        m_balance.moveInTwo(moveInTwo, lastPrice);
                        log("    balance out: " + m_balance);
                        m_lastMoveMillis = lastTickTime;
                    } else {
                        log("   need wait. timeDiff=" + timeDiff);
                    }
                } else {
                    log("   small amount to move: " + moveInTwo);
                }
            }
            m_lastPrice = lastPrice;
        }
    }

    // ----------------------------------------------------------
    private static class Balance {
        private double m_one;
        private double m_two;

        public Balance(double one, int two) {
            m_one = one;
            m_two = two;
        }

        @Override public String toString() {
            return "Balance[one=" + Utils.format5(m_one) + "; two=" + Utils.format5(m_two) + "]";
        }

        public double distribution(double price) {
            double one = m_one / price;
            double sum = one + m_two;
            double distribution = (one/sum)*2-1;
            return distribution;
        }

        public double valuateInTwo(double price) {
            double one = m_one / price;
            double sum = one + m_two;
            return sum;
        }

        public void moveInTwo(double move, double price) {
            double oneInTwo = m_one / price;
            double sumInTwo = oneInTwo + m_two;
            double moveInTwo = (sumInTwo * move) /2;

            double twoInOne = m_two * price;
            double sumInOne = m_one + twoInOne;
            double moveInOne = (sumInOne * move) /2;

            m_one -= moveInOne;
            m_two += moveInTwo;
        }
    }
}
