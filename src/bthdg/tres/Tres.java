package bthdg.tres;

import bthdg.Fetcher;
import bthdg.Log;
import bthdg.exch.BaseExch;
import bthdg.exch.Pair;
import bthdg.util.ConsoleReader;
import bthdg.util.Utils;
import bthdg.ws.IWs;
import bthdg.ws.WsFactory;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

public class Tres {
    public static final Pair PAIR = Pair.BTC_CNH;
    private static Tres s_inst;

    private Properties m_keys;
    long m_barSizemMillis;
    int m_len1;
    int m_len2;
    int m_k;
    int m_d;
    int m_phases;
    public int m_preheatBarsNum;
    private int m_ma;
    private ArrayList<TresExchData> m_exchDatas;
    private TresFrame m_frame;

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
        if (line.equals("ui")) {
            showUI();
        }
        return false;
    }

    private void onStop() {
        log("stop()-----------------------------------------------------");
        for (TresExchData exchData : m_exchDatas) {
            exchData.stop();
        }
    }

    private void start() throws IOException {
        m_keys = BaseExch.loadKeys();
        init();

        for (TresExchData exchData : m_exchDatas) {
            exchData.start();
        }
    }

    private void init() {
        String exchangesStr = getProperty("tre.exchanges");
        log("EXCHANGES=" + exchangesStr);
        String[] exchangesArr = exchangesStr.split(",");
        int exchangesLen = exchangesArr.length;
        log(" .len=" + exchangesLen);

        String barSizeStr = getProperty("tre.bar_size");
        log("barSize=" + barSizeStr);
        m_barSizemMillis = Utils.toMillis(barSizeStr);
        log(" .millis=" + m_barSizemMillis);

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

        m_preheatBarsNum = m_len1 + m_len2 + (m_k - 1) + (m_d - 1);

        m_exchDatas = new ArrayList<TresExchData>(exchangesLen);
        for (int i = 0; i < exchangesLen; i++) {
            IWs ws = WsFactory.get(exchangesArr[i], m_keys);
            m_exchDatas.add(new TresExchData(this, ws));
        }

        Fetcher.MUTE_SOCKET_TIMEOUTS = true;
    }

    private String getProperty(String key) {
        String ret = m_keys.getProperty(key);
        if (ret == null) {
            throw new RuntimeException("no property found for key '" + key + "'");
        }
        return ret;
    }

    private static void showUI() {
        s_inst.showFrame();
    }

    private void showFrame() {
        if (m_frame != null) {
            m_frame.dispose();
        }
        m_frame = new TresFrame(s_inst);
        m_frame.setVisible(true);
    }

    public void fireUpdated() {
        if (m_frame != null) {
            m_frame.fireUpdated();
        }
    }

    private static class IntConsoleReader extends ConsoleReader {
        @Override protected void beforeLine() { System.out.print(">"); }
        @Override protected boolean processLine(String line) throws Exception { return onConsoleLine(line); }
    }

    public static class TresFrame extends JFrame {
        private final Tres m_tres;

        public TresFrame(Tres tres) throws java.awt.HeadlessException {
            m_tres = tres;
            setTitle("Tres");
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            add(new JButton("BTN"));
            pack();
            toFront();
        }

        public void fireUpdated() {
            // need snoozer
        }
    }
}
