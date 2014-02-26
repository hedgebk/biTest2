package bthdg;

public class Log {
    public static ILog s_impl = new StdLog();

    public static void log(String s) {
        s_impl.log(s);
    }

    public static void err(String s, Exception err) {
        s_impl.err(s, err);
    }

    public interface ILog {
        void log(String s);
        void err(String s, Exception err);
    }

    public static class StdLog implements ILog {
        @Override public void log(String s) {
            System.out.println(s);
        }

        @Override public void err(String s, Exception err) {
            System.out.println(s);
            err.printStackTrace();
        }
    }
}
