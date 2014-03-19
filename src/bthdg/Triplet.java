package bthdg;

public class Triplet {
    public static void main(String[] args) {
        System.out.println("Started");
        Fetcher.LOG_LOADING = false;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
        try {
            while(true) {
                TopData ltcBtcTop = Fetcher.fetchTop(Exchange.BTCE, Pair.LTC_BTC);
                TopData btcUsdTop = Fetcher.fetchTop(Exchange.BTCE, Pair.BTC_USD);
                TopData ltcUsdTop = Fetcher.fetchTop(Exchange.BTCE, Pair.LTC_USD);
                TopData btcEurTop = Fetcher.fetchTop(Exchange.BTCE, Pair.BTC_EUR);
                TopData ltcEurTop = Fetcher.fetchTop(Exchange.BTCE, Pair.LTC_EUR);
                TopData eurUsdTop = Fetcher.fetchTop(Exchange.BTCE, Pair.EUR_USD);
//                System.out.println("btcUsdTop: " + btcUsdTop);

                //-------------------------------------------------
                // usd -> ltc -> btc -> usd
                double usdIn = 100;
                double ltc = usdIn / ltcUsdTop.getMid();
                double btc = ltc * ltcBtcTop.getMid();
                double usdOut = btc * btcUsdTop.getMid();

                double usdIn2 = 100; // ASK > BID
                double ltc2 = usdIn2 / ltcUsdTop.m_ask;
                double btc2 = ltc2 * ltcBtcTop.m_bid;
                double usdOut2 = btc2 * btcUsdTop.m_bid;

                double usdIn3 = 100;
                double btc3 = usdIn3 / btcUsdTop.getMid();
                double ltc3 = btc3 / ltcBtcTop.getMid();
                double usdOut3 = ltc3 * ltcUsdTop.getMid();

                double usdIn4 = 100;
                double btc4 = usdIn4 / btcUsdTop.m_ask;
                double ltc4 = btc4 / ltcBtcTop.m_ask;
                double usdOut4 = ltc4 * ltcUsdTop.m_bid;

                //-------------------------------------------------
                // eur -> ltc -> btc -> eur
                double eurIn = 100;
                double ltc5 = eurIn / ltcEurTop.getMid();
                double btc5 = ltc5 * ltcBtcTop.getMid();
                double eurOut = btc5 * btcEurTop.getMid();

                double eurIn2 = 100; // ASK > BID
                double ltc6 = eurIn2 / ltcEurTop.m_ask;
                double btc6 = ltc6 * ltcBtcTop.m_bid;
                double eurOut2 = btc6 * btcEurTop.m_bid;

                double eurIn3 = 100;
                double btc7 = eurIn3 / btcEurTop.getMid();
                double ltc7 = btc7 / ltcBtcTop.getMid();
                double eurOut3 = ltc7 * ltcEurTop.getMid();

                double eurIn4 = 100;
                double btc8 = eurIn4 / btcEurTop.m_ask;
                double ltc8 = btc8 / ltcBtcTop.m_ask;
                double eurOut4 = ltc8 * ltcEurTop.m_bid;

                //-------------------------------------------------
                // usd -> ltc -> eur -> usd
                double usdIn5 = 100;
                double ltc9 = usdIn5 / ltcUsdTop.getMid();
                double eur = ltc9 * ltcEurTop.getMid();
                double usdOut5 = eur * eurUsdTop.getMid();

                double usdIn6 = 100;
                double ltc10 = usdIn6 / ltcUsdTop.m_ask;
                double eur2 = ltc10 * ltcEurTop.m_bid;
                double usdOut6 = eur2 * eurUsdTop.m_bid;

                double usdIn7 = 100;
                double eur3 = usdIn7 / eurUsdTop.getMid();
                double ltc11 = eur3 / ltcEurTop.getMid();
                double usdOut7 = ltc11 * ltcUsdTop.getMid();

                double usdIn8 = 100;
                double eur4 = usdIn8 / eurUsdTop.m_ask;
                double ltc12 = eur4 / ltcEurTop.m_ask;
                double usdOut8 = ltc12 * ltcUsdTop.m_bid;

                System.out.println(
                                   format(usdOut) + " " + format(usdOut2) + " " +
                                   format(usdOut3) + " " + format(usdOut4) + " | " +
                                   format(eurOut) + " " + format(eurOut2) + " " +
                                   format(eurOut3) + " " + format(eurOut4) + " | " +
                                   format(usdOut5) + " " + format(usdOut6) + " " +
                                   format(usdOut7) + " " + format(usdOut8) + " " +
                                   ((usdOut > 100.6) || (usdOut3 > 100.6) || (eurOut > 100.6) || (eurOut3 > 100.6) || (usdOut5 > 100.6) || (usdOut7 > 100.6) ? " *" : "") +
                                   ((usdOut2 > 100.6) || (usdOut4 > 100.6) || (eurOut2 > 100.6) || (eurOut4 > 100.6) || (usdOut6 > 100.6) || (usdOut8 > 100.6) ? "\t******************************" : "")
                );
//                System.out.println("=========================================================");

                Thread.sleep(6000);
            }
        } catch (Exception e) {
            System.out.println("error: " + e);
            e.printStackTrace();
        }
    }

    private static String format(double usdOut) {
        return Utils.padLeft(Fetcher.format(usdOut), 8);
    }
}
