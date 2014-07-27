package plock.math;

import static java.lang.Math.*;
import java.util.function.*;

public class Math {
   /** @param f function to take parameter x and produce f(x)
        @param t desired f(x)
        @param s current x
        @param inc how much to increment / decrement
        @param e amount of error from t allowed to say we found a solution
        @return x within e of the exact answer
    */
    public static double iterateSolve(final Function<Double,Double> f, final double t, 
            final double s, final double inc, final double e) {
        double right = f.apply(s+inc);
        double fs = f.apply(s);
        //System.out.println("binary expand target "+t+" and err: "+e+" with x: "+s+" inc: "+inc+" f(x):"+fs+" f(x+inc):"+right);

        if (Double.isNaN(fs) || Double.isNaN(right)) {
            String message ="cannot find a valid value for function, target: "+t+" checking against "+right;
            message += " and "+fs+" currently: "+s+" and inc: "+inc;
            throw new IllegalArgumentException(message);
        }
        if (abs(fs-t) < e) {return s;}
        if (abs(right-t) < e) {return s+inc;}
        // so first two here check for t not inbetween f(s) and f(s+inc)
        if (fs < t && right < t) { // is t more to the right?
            //System.out.println("t is larger, move to the right");
            // if moving x to the right moves f(x) right, keep going more right, else left
            return iterateSolve(f, t, right>fs ? (s+inc) : (s-2*inc), inc*2, e);
        }
        if (fs > t && right > t) { // is t more to the left?
            //System.out.println("t is smaller, move to the left");
            // if moving x to the right moves f(x) left, keep going more right, else left
            return iterateSolve(f, t, right<fs ? (s+inc) : (s-2*inc), inc*2, e);
        }

        return iterateSolveSmaller(f, t, s, inc, e);       
    } 
    private static double iterateSolveSmaller(final Function<Double,Double> f, final double t, 
            final double s, final double inc, final double e) {
        double right = f.apply(s+inc);
        double fs = f.apply(s);
        //System.out.println("binary search target "+t+" and err: "+e+" with x: "+s+" inc: "+inc+" f(x):"+fs+" f(x+inc):"+right);
        if (inc == 0.0 || fs == t) {return s;} 
        // first we check if we already have and answer with f(s)
        // opposite signs, subtracting will give total distance that we need absolute value of
        if (copySign(fs,t) != fs && abs(fs-t) <= e) {return s;}
        // same sign, get abs values of each, the diff of that, then abs value of result
        if (copySign(fs,t) == fs && abs(abs(fs)-abs(t)) <=e) {return s;}

        // either we're working with infinity, or the increment is not creating a change in function, won't be able
        // to solve
        if (right == fs || s == Double.NEGATIVE_INFINITY || s == Double.POSITIVE_INFINITY) {return Double.NaN;}

        // so t is in between f(s) and f(s+inc), recurse checking if falls in left or right
        double half = f.apply(s+inc/2);
        if (fs<t && half<=t || fs>t && half>=t) { // no longer inbetween, must be other side
            //System.out.println("split in half but it is in the other half");
            return iterateSolveSmaller(f, t, s+inc/2, inc/2, e);
        }
        //System.out.println("split in half and is in the first half"); 
        return iterateSolveSmaller(f, t, s, inc/2, e);
    }

    public static void main(String[] args) {
        Function<Double,Double> f = x -> {return 2*x+23;};
        System.out.println("answer: "+iterateSolve(f, 50, 0, .00000000001, .000000001));
        System.out.println("answer: "+iterateSolve(f, 0, 0, .00000000001, .000000001));
        System.out.println("answer: "+iterateSolve(f, -33.33, 0, .00000000001, .000000001));
        f = x -> {return -20 + pow(2.001,x);};
        System.out.println("answer: "+iterateSolve(f, 50, 0, .00000000001, .000000001));
        System.out.println("answer: "+iterateSolve(f, 0, 0, .00000000001, .000000001));
        // this one should fail
        System.out.println("answer: "+iterateSolve(f, -33.33, 0, .00000000001, .000000001));
    }
}
 
