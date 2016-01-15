package bthdg.tres.opt;

import bthdg.tres.Tres;
import bthdg.tres.ind.AroonIndicator;
import bthdg.tres.ind.OscIndicator;

public enum OptimizeField {
    OSC_BAR_SIZE("osc.bar_size") {
        @Override public double get(Tres tres) { return tres.m_barSizeMillis; }
        @Override public void set(Tres tres, double value) { tres.m_barSizeMillis = (int) value; }
    },
    OSC_LEN1("osc.len1") {
        @Override public double get(Tres tres) { return tres.m_len1; }
        @Override public void set(Tres tres, double value) { tres.m_len1 = (int) value; }
    },
    OSC_LEN2("osc.len2") {
        @Override public double get(Tres tres) { return tres.m_len2; }
        @Override public void set(Tres tres, double value) { tres.m_len2 = (int) value; }
    },
    OSC_PEAK("osc.peak") {
        @Override public double get(Tres tres) { return OscIndicator.PEAK_TOLERANCE; }
        @Override public void set(Tres tres, double value) { OscIndicator.PEAK_TOLERANCE = value; }
    },
    ARO_LEN("aro.len") {
        @Override public double get(Tres tres) { return AroonIndicator.PhasedAroonIndicator.LENGTH; }
        @Override public void set(Tres tres, double value) { AroonIndicator.PhasedAroonIndicator.LENGTH = (int) value; }
    },
    ARO_PEAK("aro.peak") {
        @Override public double get(Tres tres) { return AroonIndicator.PEAK_TOLERANCE; }
        @Override public void set(Tres tres, double value) { AroonIndicator.PEAK_TOLERANCE = value; }
    };

    public final String m_key;

    OptimizeField(String key) {
        m_key = key;
    }

    public double get(Tres tres) { throw new RuntimeException("not implemented"); }
    public void set(Tres tres, double value) { throw new RuntimeException("not implemented"); }
}
