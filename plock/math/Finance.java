// some basics: http://www.investopedia.com/articles/03/101503.asp
public class Finance {
    // 10000 @ 10% for 10 years = 25,937.42 total (PASS)
    public static double fvCompoundInterest(double rate, int numPeriods, double presentValue) {
        return fvGrowingAnnuity(rate, numPeriods, 0, 0, presentValue);
    }

    // 5,000 future value in 5 years requires 2967 now invested at 11% rate (PASS)
    public static double pvCompoundInterest(double rate, int numPeriods, double futureValue) {
        return pvAnnuity(rate, numPeriods, 0, futureValue);
    }
        
    /** This is the most basic annuity, useful for determining your total savings in the future
     * given steady deposits and interest rate or for the total paid in a car loan, where the
     * deposits or withdrawls are done at the end of the period
     * 5% and 1000 per period for 5 periods with 0 dollars presently = 5525.64 (PASS)
     * @param rate the interest rate
     * @param n number of time periods
     * @param adjustment positive are regular deposits, negative is withdrawls
     * @param presentValue the starting balance
     */
    public static double fvAnnuity(double rate, int n, double adjustment, double presentValue) {
        return fvGrowingAnnuity(rate, n, adjustment, 0, presentValue);
    }

    /** Just like fvAnnuity except the adjustment is made at the beginning of the period so the rate
     * has an effect on the adjustment value during the first period
     * 5% and 1000 per period for 5 periods with 0 initial balance = 5801.92 (PASS)
     * @param rate the interest rate
     * @param n number of time periods
     * @param adjustment positive are regular deposits, negative is withdrawls
     * @param presentValue the starting balance
     */
    public static double fvAnnuityDue(double rate, int n, double adjustment, double presentValue) {
        return (1+rate) * fvAnnuity(rate, n, adjustment, presentValue);
    }

    /** Gives present value required to provide a steady cash flow while interest is accruing, with a final balance.
     * This could be what money do i need to lump into an investment with fixed payments to 
     * reach the target future value.
     * @param rate the interest rate
     * @param n number of time periods
     * @param adjustment how much is taken out of the account each period
     * @param futureValue the final balance after all the periods
     * What is the PV of 1000 payments for the next 5 years with a 5% interset rate? 4329.48 (PASS)
     */
    public static double pvAnnuity(double rate, int n, double adjustment, double futureValue) {
        double present_value_from_compound_interest = fvCompoundInterest(rate, -n, futureValue);
        return present_value_from_compound_interest + adjustment * (1-Math.pow((1+rate),-n)) / rate;
    }

    /** Just like pvAnnuity except the adjustment is made at the beginning of the period so the rate
     *  has an effect on the adjustment value during the first period
     *  @param rate the interest rate
     *  @param n number of time periods
     *  @param adjustment positive are regular deposits, negative is withdrawls
     *  @param futureValue the future balance
     *
     */
    public static double pvAnnuityDue(double rate, int n, double adjustment, double futureValue) {
        return (1+rate) * pvAnnuity(rate, n, adjustment, futureValue);
    }

    /** This models an initial starting point, payments every period, interest every period, and payments increase
     * every period (for example to combat cost of living increase, or maybe in line with yearly raises into a 401k).
     * Might need to back into the rate if payments are monthly and the rate is yearly for example, or the increase
     * is yearly with monthly adjustments
     * 0 initially, 3% return over 5 years with 2000 the first year, each year afterwords increasing by 5% = 11,700.75 finally
     * (PASS)
     */
    public static double fvGrowingAnnuity(double rate, int n, double adjustment, double adjGrowthRate, double presentValue) {
        double fvFromPv = presentValue * Math.pow(1+rate, n);
        return fvFromPv + adjustment * ( (Math.pow(1+rate,n) - Math.pow(1+adjGrowthRate,n)) ) / (rate - adjGrowthRate);
    }

    /** This is useful to determine the amount of money needed (the presentValue) to put into a retirement account
     * so that you can receive payments every year that increase by the cost of living
     * @param initialAdjustment the amount of the first payout
     * @param rate the return on the investment every period
     * @param n number of periods
     * @param adjGrowthRate how much should the payment increase each period
     * What does it cost to initially get 10,000 year, growing at 5% / year, for 15 years with a return rate of 12%
     * 91,989.41 (PASS)
     */
    public static final double pvGrowingAnnuity(double rate, int n, double initialAdjustment, double adjGrowthRate) {
        double z = 1/(rate-adjGrowthRate);
        return initialAdjustment * ( z - z*( Math.pow( (1+adjGrowthRate)/(1+rate), n) ) );
    }
}
