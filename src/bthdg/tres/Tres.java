package bthdg.tres;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.BaseExch;
import bthdg.exch.Pair;
import bthdg.exch.TradeData;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;
import bthdg.ws.ITradesListener;
import bthdg.ws.IWs;
import bthdg.ws.WsFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

public class Tres {
    public static final Pair PAIR = Pair.BTC_CNH;
    private static Tres s_inst;

    private Properties m_keys;
    private int m_len1;
    private int m_len2;
    private int m_k;
    private int m_d;
    private int m_phases;
    private int m_ma;
    private ArrayList<ExchData> m_exchDatas;

    private static void log(String s) { Log.log(s); }
    private static void err(String s, Exception e) { Log.err(s, e); }

    public Tres(String[] args) {

    }

    public static void main(String[] args) {
        try {
            s_inst = new Tres(args);
            s_inst.start();

            new IntConsoleReader().run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean onConsoleLine(String line) {
        log("onConsoleLine: " + line);
        if (line.equals("stop")) {
            s_inst.onStop();
            return true;
        }
        return false;
    }

    private void onStop() {
        log("stop()-----------------------------------------------------");
        for (ExchData exchData : m_exchDatas) {
            exchData.stop();
        }
    }

    private void start() throws IOException {
        m_keys = BaseExch.loadKeys();
        init();

        for (ExchData exchData : m_exchDatas) {
            exchData.start();
        }
    }

    private void init() {
        String exchangesStr = getProperty("tre.exchanges");
        log("EXCHANGES=" + exchangesStr);
        String[] exchangesArr = exchangesStr.split(",");
        int exchangesLen = exchangesArr.length;
        log(" .len=" + exchangesLen);

        m_exchDatas = new ArrayList<ExchData>(exchangesLen);
        for (int i = 0; i < exchangesLen; i++) {
            IWs ws = WsFactory.get(exchangesArr[i], m_keys);
            m_exchDatas.add(new ExchData(ws));
        }

        String barSizeStr = getProperty("tre.bar_size");
        log("barSize=" + barSizeStr);
        long barSizemMillis = Utils.toMillis(barSizeStr);
        log(" .millis=" + barSizemMillis);

        m_len1 = Integer.parseInt(getProperty("tre.len1"));
        log("len1=" + m_len1);
        m_len2 = Integer.parseInt(getProperty("tre.len2"));
        log("len2=" + m_len1);
        m_k = Integer.parseInt(getProperty("tre.k"));
        log("k=" + m_k);
        m_d = Integer.parseInt(getProperty("tre.d"));
        log("d=" + m_d);
        m_phases = Integer.parseInt(getProperty("tre.phases"));
        log("phases=" + m_phases);
        m_ma = Integer.parseInt(getProperty("tre.ma"));
        log("ma=" + m_ma);

        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
    }

    private String getProperty(String key) {
        String ret = m_keys.getProperty(key);
        if (ret == null) {
            throw new RuntimeException("no property found for key '" + key + "'");
        }
        return ret;
    }

    private static class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() { System.out.print(">"); }
        @Override protected boolean processLine(String line) throws Exception { return onConsoleLine(line); }
    }

    private class ExchData implements ITradesListener {
        private final IWs m_ws;

        public ExchData(IWs ws) {
            m_ws = ws;
        }

        public void start() {
            try {
                m_ws.subscribeTrades(PAIR, this);
            } catch (Exception e) {
                err("error subscribeTrades: " + e, e);
            }
        }

        @Override public void onTrade(TradeData tdata) {
            log("onTrade["+m_ws.exchange()+"]: " + tdata);
        }

        public void stop() {
            m_ws.stop();
        }
    }
}
