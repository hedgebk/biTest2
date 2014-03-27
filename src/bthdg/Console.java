package bthdg;

import bthdg.exch.BaseExch;
import bthdg.exch.Btce;
import bthdg.exch.TopData;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

public class Console {
    static final Pair[] PAIRS = {Pair.LTC_BTC, Pair.BTC_USD, Pair.LTC_USD, Pair.BTC_EUR, Pair.LTC_EUR, Pair.EUR_USD};

    public static void main(String[] args) {
        System.out.println("Started.");
        printHelp();
        Fetcher.LOG_LOADING = false;
        Fetcher.MUTE_SOCKET_TIMEOUTS = true;

        try {
            Properties keys = BaseExch.loadKeys();
            Btce.init(keys);

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while(true) {
                String line = reader.readLine();
                try {
                    boolean exit = process(line);
                    if(exit) {
                        break;
                    }
                } catch (Exception e) {
                    System.err.println("error for command '"+line+"': " + e);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.err.println("error: " + e);
            e.printStackTrace();
        }
    }

    private static boolean process(String line) throws Exception {
        if( line.equals("exit")) {
            return true;
        } else if( line.equals("account")) {
            doAccount();
        } else if( line.equals("tops")) {
            doTops();
        } else if( line.startsWith("order ")) {
            doOrder(line);
        } else if( line.equals("help")) {
            printHelp();
        } else {
            System.err.println("unknown command '" + line + "'");
            printHelp();
        }
        return false;
    }

    private static void doAccount() throws Exception {
        AccountData account = Fetcher.fetchAccount(Exchange.BTCE);

        Map<Pair, TopData> tops = Fetcher.fetchTops(Exchange.BTCE, PAIRS);
        double valuate = account.evaluate(tops);

        System.out.println("account=" + account + "; valuate=" + valuate + " eur");
    }

    private static void printHelp() {
        System.out.println("supported commands:\n help; exit; account; tops; order");
    }

    private static void doTops() throws Exception {
        Map<Pair,TopData> tops = Fetcher.fetchTops(Exchange.BTCE, PAIRS);
        for(Map.Entry<Pair, TopData> entry:tops.entrySet()) {
            Pair pair = entry.getKey();
            TopData top = entry.getValue();
            System.out.println(" " + pair + " : " + top);
        }
    }

    private static void doOrder(String line) {
        // order [buy|sell] 0.1 eur for btc @ [11.5|mkt|peg]
        StringTokenizer tok = new StringTokenizer(line.toLowerCase());
        int tokensNum = tok.countTokens();
        if(tokensNum == 8) {
            String t1 = tok.nextToken();
            String sideStr = tok.nextToken();
            OrderSide side = OrderSide.getByName(sideStr);
            String amountStr = tok.nextToken();
            double amount = Double.parseDouble(amountStr);
            String to = tok.nextToken();
            Currency toCurrency = Currency.getByName(to);
            String sep1 = tok.nextToken();
            if(sep1.equals("for")) {
                String from = tok.nextToken();
                Currency fromCurrency = Currency.getByName(from);
                PairDirection pd = PairDirection.get(fromCurrency, toCurrency);
                Pair pair = pd.m_pair;

//                if(side.isBuy()) {
//                    Currency tmp = toCurrency;
//                    toCurrency = fromCurrency;
//                    fromCurrency = tmp;
//                }

                boolean forward = pd.m_forward;
                if(!forward) {
                    side = side.opposite();
                }

                String sep2 = tok.nextToken();
                if(sep2.equals("@")) {
                    String priceStr = tok.nextToken();
                    System.out.println(" order: side=" + side + "; amount=" + amount + "; fromCurrency=" + fromCurrency +
                            "; toCurrency=" + toCurrency + "; priceStr=" + priceStr + "; pair=" + pd);
                    if (priceStr.equals("mkt")) {
                        // place mkt
                        System.out.println("not implemented - place mkt");
                    } else if (priceStr.equals("peg")) {
                        // place peg
                        System.out.println("not implemented - place peg");
                    } else {
                        double limitPrice = Double.parseDouble(priceStr);
                        if(!forward) {
                            limitPrice = 1/limitPrice;
                            amount = amount/limitPrice;
                        }
                        OrderData orderData = new OrderData(pair, side, limitPrice, amount);
                        System.out.println("orderData=" + orderData);
                    }
                    return;
                }
            }
        }
        System.err.println("invalid 'order' command: use followed format: order [buy|sell] 0.1 eur for btc @ [11.5|mkt|peg]");
    }
}
