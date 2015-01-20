package bthdg.osc;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.BaseExch;
import bthdg.exch.Exchange;
import bthdg.exch.Pair;
import bthdg.exch.TradeData;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;
import bthdg.ws.HuobiWs;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;
import bthdg.ws.OkCoinWs;

import java.io.IOException;
import java.util.Properties;

public class Osc {
    public static long BAR_SIZE = Utils.toMillis("1d");
    public static int LEN1 = 14;
    public static int LEN2 = 14;
    public static int K = 3;
    public static int D = 3;
    public static int PHASES = 1;
    public static double START_LEVEL = 0.005;
    public static double STOP_LEVEL = 0.005;
    public static int START_STOP_LEVEL_MULTIPLY = 3;
    static boolean STICK_TOP_BOTTOM = false;
    static boolean DELAY_REVERSE_START = false;

    public static int PREHEAT_BARS_NUM = calcPreheatBarsNum();

    public static final int INIT_BARS_BEFORE = 4;
    public static final Pair PAIR = Pair.BTC_CNH; // TODO: BTC is hardcoded below
    static final int MAX_PLACE_ORDER_REPEAT = 2;
    public static final double CLOSE_PRICE_DIFF = 1.25;
    static final double ORDER_SIZE_TOLERANCE = 0.3;
    public static final double USE_FUNDS_FROM_AVAILABLE = 0.95; // 95%
    static final boolean DO_CLOSE_ORDERS = false;

    private static Osc s_osc; // instance

    private final String m_e;
    private OscProcessor m_processor;

    public Osc(String[] args) {
        Log.s_impl = new Log.TimestampLog();

        if(args.length > 0) {
            m_e = args[0];
        } else {
            throw new RuntimeException("no arg params");
        }
    }

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }
    private static int calcPreheatBarsNum() { return LEN1 + LEN2 + (K - 1) + (D - 1); }

    public static void main(String[] args) {
        try {
            s_osc = new Osc(args);
            s_osc.run();

            new ConsoleReader() {
                @Override protected void beforeLine() {
                    System.out.print(">");
                }
                @Override protected boolean processLine(String line) throws Exception {
                    return onConsoleLine(line);
                }
            }.run();

            Thread thread = Thread.currentThread();
            synchronized (thread) {
                thread.wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean onConsoleLine(String line) throws Exception {
        log("onConsoleLine: " + line);
        if (line.equals("stop")) {
            s_osc.onStop();
            return true;
        }
        return false;
    }

    private void onStop() throws Exception {
        log("stop()-----------------------------------------------------");
        m_processor.stop();
    }

    private void run() throws IOException {
        Properties keys = BaseExch.loadKeys();
//        Btcn.init(keys);

        init(keys);

        IWs ws;
        Exchange exchange = Exchange.getExchange(m_e);
        switch (exchange) {
            case HUOBI:
                ws = HuobiWs.create(keys);
                break;
            case OKCOIN:
                ws = OkCoinWs.create(keys);
                break;
            default:
                throw new RuntimeException("not supported exchange: " + exchange);
        }

//        BitstampWs.main(args);

        m_processor = new OscProcessor(ws);

        try {
            ws.subscribeTrades(PAIR, new ITradesListener() {
                @Override public void onTrade(TradeData tdata) {
                    m_processor.onTrade(tdata);
                }
            });
        } catch (Exception e) {
            err("subscribeTrades error" + e, e);
        }
    }

    private void init(Properties properties) {
        BAR_SIZE = Utils.toMillis(properties.getProperty("osc.bar_size"));
        log("BAR_SIZE=" + BAR_SIZE);
        LEN1 = Integer.parseInt(properties.getProperty("osc.len1"));
        log("LEN1=" + LEN1);
        LEN2 = Integer.parseInt(properties.getProperty("osc.len2"));
        log("LEN2=" + LEN2);
        K = Integer.parseInt(properties.getProperty("osc.k"));
        log("K=" + K);
        D = Integer.parseInt(properties.getProperty("osc.d"));
        log("D=" + D);
        PHASES = Integer.parseInt(properties.getProperty("osc.phases"));
        log("PHASES=" + PHASES);
        START_LEVEL = Double.parseDouble(properties.getProperty("osc.start_level"));
        log("START_LEVEL=" + START_LEVEL);
        STOP_LEVEL = Double.parseDouble(properties.getProperty("osc.stop_level"));
        log("STOP_LEVEL=" + STOP_LEVEL);
        START_STOP_LEVEL_MULTIPLY = Integer.parseInt(properties.getProperty("osc.start_stop_level_multiply"));
        log("START_STOP_LEVEL_MULTIPLY=" + START_STOP_LEVEL_MULTIPLY);
        String str = properties.getProperty("osc.stickTopBottom");
        STICK_TOP_BOTTOM = Boolean.parseBoolean(str);
        log("STICK_TOP_BOTTOM=" + STICK_TOP_BOTTOM);
        String str2 = properties.getProperty("osc.delayReverseStart");
        DELAY_REVERSE_START = Boolean.parseBoolean(str2);
        log("DELAY_REVERSE_START=" + DELAY_REVERSE_START);

        PREHEAT_BARS_NUM = calcPreheatBarsNum();

        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
    }
}
