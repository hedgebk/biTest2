package bthdg.tres.opt;

import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.analysis.interpolation.MicrosphereInterpolator;
import org.apache.commons.math3.analysis.interpolation.MultivariateInterpolator;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.PowellOptimizer;
import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;

public class Optimize {

    public static void main(String [] args){
        double[] point = {10.5, 6.5};
        System.out.println("point: " + Arrays.toString(point));
        double[][] nearest = buildNearest(point);
        System.out.println("nearest");
        for (double[] doubles : nearest) {
            System.out.println(" : " + Arrays.toString(doubles));
        }
        double[] values = calcValues(nearest);
        System.out.println("values");
        for (double value : values) {
            System.out.println(" : " + value);
        }

        final MultivariateInterpolator interpolator = new MicrosphereInterpolator();
        MultivariateFunction function = interpolator.interpolate(nearest, values);
        double value = function.value(point);
        System.out.println("value="+ value);
    }

    private static double[] calcValues(double[][] nearest) {
        MultivariateFunction f = new MultivariateFunction() {
            @Override public double value(double[] x) {
                return x[0] + x[1];
            }
        };

        int pointsNum = nearest.length;
        double[] ret = new double[pointsNum];
        for (int i = 0; i < pointsNum; i++) {
            double[] point = nearest[i];
            double value = f.value(point);
            ret[i] = value;
        }
        return ret;
    }

    private static double[][] buildNearest(double[] point) {
        int dimension = point.length;
        int num = (int) Math.pow(2, dimension);
        double[][] ret = new double[num][];
        int index = 0;
        int offset = 0;
        double[] temp = new double[dimension];
        addNearest(point, index, temp, offset, ret);
        return ret;
    }

    private static int addNearest(double[] point, int index, double[] temp, int offsetIn, double[][] ret) {
        int offset = offsetIn;
        double val = point[index];
        double floor = Math.floor(val);
        offset = addValueAndNearest(point, index, temp, offset, ret, floor);
        double ceil = Math.ceil(val);
        offset = addValueAndNearest(point, index, temp, offset, ret, ceil);
        return offset;
    }

    private static int addValueAndNearest(double[] point, int index, double[] temp, int offset, double[][] ret, double value) {
        temp[index] = value;
        int dimension = point.length;
        if (index == dimension - 1) {
            offset = addPoint(temp, offset, ret);
        } else {
            offset = addNearest(point, index + 1, temp, offset, ret);
        }
        return offset;
    }

    private static int addPoint(double[] temp, int offset, double[][] ret) {
        int dimension = temp.length;
        ret[offset] = Arrays.copyOf(temp, dimension);
        return ++offset;
    }

    public static void _main(String []args){
        MultivariateFunction function = new MultivariateFunction() {
            @Override public double value(double[] point) {
                double x = point[0];
                double y = point[1];
                double z = point[2];
                System.out.println("function() x=" + x + "; y="+ y + "; z="+ z);
                return (100-Math.abs(x)) * (100-Math.abs(y)) * (100-Math.abs(z));
            }
        };

        double[] startPoint = new double[] {2,3,4};

        // Number of interpolation conditions. For a problem of dimension n, its value must be in the interval [n+2, (n+1)(n+2)/2].
        // Choices that exceed 2n+1 are not recommended.
//        int numberOfInterpolationPoints = startPoint.length * 2 + 1;
//        MultivariateOptimizer optimize = new BOBYQAOptimizer(numberOfInterpolationPoints);
//        PointValuePair pair = optimize.optimize(
//                new ObjectiveFunction(function),
//                new MaxEval(200),
//                GoalType.MAXIMIZE,
//                new InitialGuess(startPoint),
//                new SimpleBounds(new double[]{-99, -99, -99}, new double[]{99, 99, 99})
//        );

//        SimplexOptimizer optimize = new SimplexOptimizer(1e-3, 1e-6);
//        PointValuePair pair = optimize.optimize(
//                new ObjectiveFunction(function),
//                new MaxEval(200),
//                GoalType.MAXIMIZE,
//                new InitialGuess(startPoint),
//                new MultiDirectionalSimplex(3));

        MultivariateOptimizer optimize = new PowellOptimizer(1e-13, FastMath.ulp(1d));
        PointValuePair pair = optimize.optimize(
                new ObjectiveFunction(function),
                new MaxEval(100000),
                GoalType.MAXIMIZE,
                new InitialGuess(startPoint)
        );

        double[] point = pair.getPoint();
        String pointStr = Arrays.toString(point);

        System.out.println("point="+ pointStr + "; value=" + pair.getValue());
        System.out.println("optimize: Evaluations=" + optimize.getEvaluations()
                + "; Iterations=" + optimize.getIterations());
    }
}
