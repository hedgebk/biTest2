package bthdg.tres.opt;

import bthdg.tres.Tres;
import bthdg.tres.alg.*;
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
        @Override public double get(Tres tres) { return Aroon2Algo.PEAK_TOLERANCE4; }
        @Override public void set(Tres tres, double value) { Aroon2Algo.PEAK_TOLERANCE4 = value; }
    },
    ARO_BAR_RATIO_STEP("aro.bar_ratio_step") {
        @Override public double get(Tres tres) { return AroonAlgo.BAR_RATIOS_STEP; }
        @Override public void set(Tres tres, double value) { AroonAlgo.BAR_RATIOS_STEP = value; }
    },
    ARO_BAR_RATIO_STEP_NUM("aro.bar_ratio_step_num") {
        @Override public double get(Tres tres) { return AroonAlgo.BAR_RATIOS_STEP_NUM; }
        @Override public void set(Tres tres, double value) { AroonAlgo.BAR_RATIOS_STEP_NUM = (int) value; }
    },
    ARO_SMOOTH_RATE("aro.smooth_rate") {
        @Override public double get(Tres tres) { return AroonAlgo.SMOOTH_RATE; }
        @Override public void set(Tres tres, double value) { AroonAlgo.SMOOTH_RATE = value; }
    },
    CNO3_PEAK("cno3.peak") {
        @Override public double get(Tres tres) { return Cno3Algo.SMOOTH_PEAK_TOLERANCE; }
        @Override public void set(Tres tres, double value) { Cno3Algo.SMOOTH_PEAK_TOLERANCE = value; }
    },
    CNO3_SMOOTH("cno3.smooch") {
        @Override public double get(Tres tres) { return Cno3Algo.SMOOTH_RATE; }
        @Override public void set(Tres tres, double value) { Cno3Algo.SMOOTH_RATE = value; }
    },
    EMAS_SIZE("emas.size") {
        @Override public double get(Tres tres) { return EmasAlgo.EMA_SIZE; }
        @Override public void set(Tres tres, double value) { EmasAlgo.EMA_SIZE = value; }
    },
    EMAS_LEVEL("emas.level") {
        @Override public double get(Tres tres) { return EmasAlgo.BOUND_LEVEL; }
        @Override public void set(Tres tres, double value) { EmasAlgo.BOUND_LEVEL = value; }
    },
    FOUR_EMA_SIZE("4emas.size") {
        @Override public double get(Tres tres) { return FourEmaAlgo.EMA_SIZE; }
        @Override public void set(Tres tres, double value) { FourEmaAlgo.EMA_SIZE = value; }
    },
    FOUR_EMA_SMOOTH("4emas.smooch") {
        @Override public double get(Tres tres) { return FourEmaAlgo.MID_SMOOCH_RATE; }
        @Override public void set(Tres tres, double value) {
            FourEmaAlgo.MID_SMOOCH_RATE = value;
            FourEmaAlgo.SUM_SMOOCH_RATE = value;
        }
    },
    FOUR_EMA_VELOCITY("4emas.velocity") {
        @Override public double get(Tres tres) { return FourEmaAlgo.MID_VELOCITY_SIZE; }
        @Override public void set(Tres tres, double value) {
            FourEmaAlgo.MID_VELOCITY_SIZE = value;
            FourEmaAlgo.SUM_VELOCITY_SIZE = value;
        }
    },
    FOUR_EMA_ZERO("4emas.zero") {
        @Override public double get(Tres tres) { return FourEmaAlgo.START_ZERO_LEVEL; }
        @Override public void set(Tres tres, double value) { FourEmaAlgo.START_ZERO_LEVEL = value; }
    };

    public final String m_key;

    OptimizeField(String key) {
        m_key = key;
    }

    public double get(Tres tres) { throw new RuntimeException("not implemented"); }
    public void set(Tres tres, double value) { throw new RuntimeException("not implemented"); }
}
