package bthdg.tres.opt;

import bthdg.tres.Tres;
import bthdg.tres.alg.AroonAlgo;
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
        @Override public double get(Tres tres) { return AroonIndicator.LENGTH; }
        @Override public void set(Tres tres, double value) { AroonIndicator.LENGTH = (int) value; }
    },
    ARO_PEAK("aro.peak") {
        @Override public double get(Tres tres) { return AroonIndicator.PEAK_TOLERANCE; }
        @Override public void set(Tres tres, double value) { AroonIndicator.PEAK_TOLERANCE = value; }
    },
    ARO_PEAK2("aro.peak2") {
        @Override public double get(Tres tres) { return AroonAlgo.PEAK_TOLERANCE2; }
        @Override public void set(Tres tres, double value) { AroonAlgo.PEAK_TOLERANCE2 = value; }
    },
    ARO_PEAK3("aro.peak3") {
        @Override public double get(Tres tres) { return AroonAlgo.PEAK_TOLERANCE3; }
        @Override public void set(Tres tres, double value) { AroonAlgo.PEAK_TOLERANCE3 = value; }
    },
    ARO_PEAK4("aro.peak4") {
        @Override public double get(Tres tres) { return AroonAlgo.PEAK_TOLERANCE4; }
        @Override public void set(Tres tres, double value) { AroonAlgo.PEAK_TOLERANCE4 = value; }
    },
    ARO_BAR_RATIO_STEP("aro.bar_ratio_step") {
        @Override public double get(Tres tres) { return AroonAlgo.BAR_RATIOS_STEP; }
        @Override public void set(Tres tres, double value) { AroonAlgo.BAR_RATIOS_STEP = value; }
    },
    ARO_BAR_RATIO_STEP_NUM("aro.bar_ratio_step_num") {
        @Override public double get(Tres tres) { return AroonAlgo.BAR_RATIOS_STEP_NUM; }
        @Override public void set(Tres tres, double value) { AroonAlgo.BAR_RATIOS_STEP_NUM = (int) value; }
    };

    public final String m_key;

    OptimizeField(String key) {
        m_key = key;
    }

    public double get(Tres tres) { throw new RuntimeException("not implemented"); }
    public void set(Tres tres, double value) { throw new RuntimeException("not implemented"); }
}
