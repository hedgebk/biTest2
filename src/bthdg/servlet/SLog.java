package bthdg.servlet;

import bthdg.Log;

import java.util.logging.Level;
import java.util.logging.Logger;

public class SLog implements Log.ILog {
    private static final Logger log = Logger.getLogger("bthdg");

    @Override public void log(String s) {
        log.info(s);
        log.warning(s);
    }

    @Override public void err(String s, Exception err) {
        log.log(Level.SEVERE, s, err);
    }
}
