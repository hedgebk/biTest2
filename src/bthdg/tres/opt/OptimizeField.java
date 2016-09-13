package bthdg.tres.opt;

import bthdg.calc.SarCalculator;
import bthdg.tres.Tres;
import bthdg.tres.alg.*;
import bthdg.tres.ind.*;

public enum OptimizeField {
    BAR_SIZE("bar_size") {
        @Override public double get(Tres tres) { return tres.m_barSizeMillis; }
        @Override public void set(Tres tres, double value) {
            tres.m_barSizeMillis = (long) value;
            tres.m_phases = (int) (value * BAR_SIZE_TO_PHASES_RATE);
            if (tres.m_phases < 1) {
                System.out.println("ERROR: tres.m_phases = " + tres.m_phases + " for barSize" + value);
            }
        }
        @Override public String getFormat() { return "%,.0f"; }
    },
    OSC_BAR_SIZE("osc.bar_size") {
        @Override public double get(Tres tres) { return tres.m_barSizeMillis; }
        @Override public void set(Tres tres, double value) { tres.m_barSizeMillis = (int) value; }
        @Override public String getFormat() { return "%,.0f"; }
    },
    HALF_BID_ASK_DIFF("avg_half_bid_ask_diff") {
        @Override public double get(Tres tres) { return BaseAlgoWatcher.AVG_HALF_BID_ASK_DIF; }
        @Override public void set(Tres tres, double value) { BaseAlgoWatcher.AVG_HALF_BID_ASK_DIF = value; }
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
    //------------------------------------------------------------------------------------------
    CCI_PEAK("cci.peak") {
        @Override public double get(Tres tres) { return CciIndicator.PEAK_TOLERANCE; }
        @Override public void set(Tres tres, double value) { CciIndicator.PEAK_TOLERANCE = value; }
    },
    //------------------------------------------------------------------------------------------
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
    //------------------------------------------------------------------------------------------
    CNO3_PEAK("cno3.peak") {
        @Override public double get(Tres tres) { return Cno3Algo.SMOOTH_PEAK_TOLERANCE; }
        @Override public void set(Tres tres, double value) { Cno3Algo.SMOOTH_PEAK_TOLERANCE = value; }
    },
    CNO3_SMOOTH("cno3.smooch") {
        @Override public double get(Tres tres) { return Cno3Algo.SMOOTH_RATE; }
        @Override public void set(Tres tres, double value) { Cno3Algo.SMOOTH_RATE = value; }
    },
    //------------------------------------------------------------------------------------------
    EMAS_SIZE("emas.size") {
        @Override public double get(Tres tres) { return EmasAlgo.EMA_SIZE; }
        @Override public void set(Tres tres, double value) { EmasAlgo.EMA_SIZE = value; }
    },
    EMAS_LEVEL("emas.level") {
        @Override public double get(Tres tres) { return EmasAlgo.BOUND_LEVEL; }
        @Override public void set(Tres tres, double value) { EmasAlgo.BOUND_LEVEL = value; }
    },
    EMAS_FAST_SIZE("emas.fast") {
        @Override public double get(Tres tres) { return EmasAlgo.TEMA_FAST_SIZE; }
        @Override public void set(Tres tres, double value) { EmasAlgo.TEMA_FAST_SIZE = value; }
    },
    EMAS_START("emas.start") {
        @Override public double get(Tres tres) { return EmasAlgo.TEMA_START; }
        @Override public void set(Tres tres, double value) { EmasAlgo.TEMA_START = value; }
    },
    EMAS_STEP("emas.step") {
        @Override public double get(Tres tres) { return EmasAlgo.TEMA_STEP; }
        @Override public void set(Tres tres, double value) { EmasAlgo.TEMA_STEP = value; }
    },
    EMAS_SUM_PEAK("emas.sum_peak") {
        @Override public double get(Tres tres) { return EmasAlgo.SUM_PEAK_TOLERANCE; }
        @Override public void set(Tres tres, double value) { EmasAlgo.SUM_PEAK_TOLERANCE = value; }
    },
    //------------------------------------------------------------------------------------------
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
        @Override public String getFormat() { return "%.8f"; }
    },
    //------------------------------------------------------------------------------------------
    EWO_VELOCITY("ewo.velocity") {
        @Override public double get(Tres tres) { return EwoAlgo.Old.VELOCITY_SIZE; }
        @Override public void set(Tres tres, double value) { EwoAlgo.Old.VELOCITY_SIZE = value; }
    },
    EWO_SLOW_EMA("ewo.slow_ema") {
        @Override public double get(Tres tres) { return EwoAlgo.SLOW_EMA_SIZE; }
        @Override public void set(Tres tres, double value) { EwoAlgo.SLOW_EMA_SIZE = value; }
    },
    EWO_FAST_EMA("ewo.fast_ema") {
        @Override public double get(Tres tres) { return EwoAlgo.FAST_EMA_SIZE; }
        @Override public void set(Tres tres, double value) { EwoAlgo.FAST_EMA_SIZE = value; }
    },
    EWO_ZERO("ewo.zero") {
        @Override public double get(Tres tres) { return EwoAlgo.Old.START_ZERO_LEVEL; }
        @Override public void set(Tres tres, double value) { EwoAlgo.Old.START_ZERO_LEVEL = value; }
        @Override public String getFormat() { return "%.10f"; }
    },
    EWO_N_ZERO("ewoN.zero") {
        @Override public double get(Tres tres) { return EwoAlgo.New.START_ZERO_LEVEL; }
        @Override public void set(Tres tres, double value) { EwoAlgo.New.START_ZERO_LEVEL = value; }
        @Override public String getFormat() { return "%.10f"; }
    },
    EWO_SMOOTH("ewo.smooth") {
        @Override public double get(Tres tres) { return EwoAlgo.Old.SMOOTH_RATE; }
        @Override public void set(Tres tres, double value) { EwoAlgo.Old.SMOOTH_RATE = value; }
    },
    //------------------------------------------------------------------------------------------
    CMF_LEN("cmf.len") {
        @Override public double get(Tres tres) { return CmfIndicator.LENGTH; }
        @Override public void set(Tres tres, double value) { CmfIndicator.LENGTH = value; }
    },
    CMF_LEN2("cmf.len2") {
        @Override public double get(Tres tres) { return CmfIndicator.LENGTH2; }
        @Override public void set(Tres tres, double value) { CmfIndicator.LENGTH2 = value; }
    },
    CMF_LEVEL("cmf.level") {
        @Override public double get(Tres tres) { return CmfIndicator.LEVEL; }
        @Override public void set(Tres tres, double value) { CmfIndicator.LEVEL = value; }
    },
    //------------------------------------------------------------------------------------------
    SAR_INIT("sar.init") {
        @Override public double get(Tres tres) { return SarCalculator.INIT_AF; }
        @Override public void set(Tres tres, double value) { SarCalculator.INIT_AF = value; }
    },
    SAR_DELTA("sar.delta") {
        @Override public double get(Tres tres) { return SarCalculator.DELTA_AF; }
        @Override public void set(Tres tres, double value) { SarCalculator.DELTA_AF = value; }
    },
    SAR_MAX("sar.max") {
        @Override public double get(Tres tres) { return SarCalculator.MAX_AF; }
        @Override public void set(Tres tres, double value) { SarCalculator.MAX_AF = value; }
    },
    //------------------------------------------------------------------------------------------
    EWO_CMF_SLOW_EMA("ewo_cmf.slow_ema") {
        @Override public double get(Tres tres) { return EwoCmfAlgo.SLOW_EMA_SIZE; }
        @Override public void set(Tres tres, double value) { EwoCmfAlgo.SLOW_EMA_SIZE = value; }
    },
    EWO_CMF_FAST_EMA("ewo_cmf.fast_ema") {
        @Override public double get(Tres tres) { return EwoCmfAlgo.FAST_EMA_SIZE; }
        @Override public void set(Tres tres, double value) { EwoCmfAlgo.FAST_EMA_SIZE = value; }
    },
    EWO_CMF_EWO_VELOCITY("ewo_cmf.ewo_velocity") {
        @Override public double get(Tres tres) { return EwoCmfAlgo.EWO_VELOCITY_SIZE; }
        @Override public void set(Tres tres, double value) { EwoCmfAlgo.EWO_VELOCITY_SIZE = value; }
    },
    EWO_CMF_LEN("ewo_cmf.len") {
        @Override public double get(Tres tres) { return EwoCmfAlgo.LENGTH; }
        @Override public void set(Tres tres, double value) { EwoCmfAlgo.LENGTH = value; }
    },
    EWO_CMF_LEN2("ewo_cmf.len2") {
        @Override public double get(Tres tres) { return EwoCmfAlgo.LENGTH2; }
        @Override public void set(Tres tres, double value) { EwoCmfAlgo.LENGTH2 = value; }
    },
    EWO_CMF_CMF_VELOCITY("ewo_cmf.cmf_velocity") {
        @Override public double get(Tres tres) { return EwoCmfAlgo.CMF_VELOCITY_SIZE; }
        @Override public void set(Tres tres, double value) { EwoCmfAlgo.CMF_VELOCITY_SIZE = value; }
    },
    EWO_CMF_CORRECT_RATE("ewo_cmf.cmf_correct") {
        @Override public double get(Tres tres) { return EwoCmfAlgo.CMF_CORRECT_RATE; }
        @Override public void set(Tres tres, double value) { EwoCmfAlgo.CMF_CORRECT_RATE = value; }
    },
    EWO_CMF_ZERO("ewo_cmf.zero") {
        @Override public double get(Tres tres) { return EwoCmfAlgo.START_ZERO_LEVEL; }
        @Override public void set(Tres tres, double value) { EwoCmfAlgo.START_ZERO_LEVEL = value; }
        @Override public String getFormat() { return "%.10f"; }
    },
    //------------------------------------------------------------------------------------------
    LRP_LEN("lrp.len") {
        @Override public double get(Tres tres) { return LinearRegressionPowerIndicator.LENGTH; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowerIndicator.LENGTH = (int) value; }
    },
    LRP_POW("lrp.pow") {
        @Override public double get(Tres tres) { return LinearRegressionPowerIndicator.POW; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowerIndicator.POW = value; }
    },
    LRPS_SMOOTH("lrps.smooth") {
        @Override public double get(Tres tres) { return LinearRegressionPowerAlgo.Smoothed.SMOOCH_RATE; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowerAlgo.Smoothed.SMOOCH_RATE = value; }
    },
    LRPSS_STEP_START("lrpss.step_start") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.LEN_STEP_START; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.LEN_STEP_START = (int) value; }
    },
    LRPSS_STEPS_NUM("lrpss.steps_num") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.LEN_STEPS_NUM; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.LEN_STEPS_NUM = (int) value; }
    },
    LRPSS_STEP_SIZE("lrpss.step_size") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.LEN_STEP_SIZE; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.LEN_STEP_SIZE = (int) value; }
    },
    LRPSS_SMOOTH("lrpss.smooth") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.SMOOTH_RATE; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.SMOOTH_RATE = value; }
    },
    LRPSS_GAIN_FROM("lrpss.gain_from") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.SlidingGainer.FROM; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.SlidingGainer.FROM = value; }
    },
    LRPSS_GAIN_TO("lrpss.gain_to") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.SlidingGainer.TO; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.SlidingGainer.TO = value; }
    },
    LRPSS_SMOOTH_GAIN("lrpss.smooth_gain") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.SMOOTH_GAIN_RATE; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.SMOOTH_GAIN_RATE = value; }
    },
    LRPSS_GAIN_POW("lrpss.gain_pow") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.GAIN_POW; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.GAIN_POW = value; }
    },
    LRPSS_SMOOTH_PRICE("lrpss.smooth_price") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.SMOOTH_PRICE_RATE; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.SMOOTH_PRICE_RATE = value; }
    },
    LRPSS_SPREAD("lrpss.spread") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.SPREAD; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.SPREAD = value; }
    },
    LRPSS_SPREAD_OFFSET("lrpss.spread_offset") {
        @Override public double get(Tres tres) { return LinearRegressionPowersAlgo.OFFSET; }
        @Override public void set(Tres tres, double value) { LinearRegressionPowersAlgo.OFFSET = value; }
    },
        ;

    public static double BAR_SIZE_TO_PHASES_RATE = 0.001;

    public final String m_key;

    OptimizeField(String key) {
        m_key = key;
    }

    public double get(Tres tres) { throw new RuntimeException("not implemented"); }
    public void set(Tres tres, double value) { throw new RuntimeException("not implemented"); }
    public String getFormat() { return "%.6f"; }

}
