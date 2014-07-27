package plock.math;

import java.util.*;
import static org.junit.Assert.*;
import org.junit.*;
import static plock.math.Finance.TmvParams.*;

@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)
public class FinanceTest {
    List<Map<String,Object>> fs = Arrays.asList(
        f(new Finance().n(3).comp(2).g(.02).r(.08).pmt(200), 1349.32),
        f(new Finance().n(15).comp(12).g(.02).r(.08).pmt(200), 75693.0),
        f(new Finance().n(15).g(.02).r(.08).pmt(200).comp(12), 75693.0),
        f(new Finance().n(5).g(.05).r(.03).pmt(2000), 11700.75),
        f(new Finance().n(5).r(.05).pmt(1000), 5525.63),
        f(new Finance().n(10).r(.05).pv(2000), 3257.789253554884),
        f(new Finance().n(3).comp(2).g(.02).r(.08).pmt(200).pv(2000), 3868.742027011306)); 
    private Map<String,Object> f(Finance f, Double val) {
        Map<String,Object> values = f.getValues();
        values.put("fv", val);
        return values;
    }
    @Test public void testChangeSolveFor() {
        Finance f = new Finance().n(10).solveFor(pv).r(.05).fv(3257.789253554884);
        assertEquals(2000, f.getSolution(), .001);
        f.solveFor(fv).n(5).r(.05).pv(0).pmt(1000);
        assertEquals(5525.63, f.getSolution(), .01);
        f.solveFor(pv);
        assertNotEquals(5525.63, f.getSolution(), .01); // make sure it recalculates solution right away
    }
    @Test public void testFV() {
        fs.stream().forEach(m->{
            Finance f = new Finance(m, fv);
            //System.out.println("test case finance is: "+f);
            assertEquals((Double)m.get("fv"), f.getSolution(), 1.0);
        });
    }
    @Test public void testPV() {
        fs.stream().forEach(f->assertEquals((Double)f.get("pv"), new Finance(f, pv).getSolution(), 1.0));
    }
    @Test public void testN() {
        fs.stream().forEach(f->assertEquals(f.toString(), (Double)f.get("n"), new Finance(f, n).getSolution(), .1));
    }
    @Test public void testPmt() {
        fs.stream().forEach(f->assertEquals((Double)f.get("pmt"), new Finance(f, pmt).getSolution(), 1.0));
    }
    @Test public void testR() {
        fs.stream().forEach(f->assertEquals((Double)f.get("r"), new Finance(f, r).getSolution(), .0001));
    }
    @Test public void testG() {
        fs.stream().filter(f->(Double)f.get("pmt")!=0.0 && (Double)f.get("n") != 0.0).forEach(f->{
            assertEquals((Double)f.get("g"), new Finance(f, g).getSolution(), .0001);
        });
    }
    @Test public void testChangeToR() {
        Finance f= new Finance().comp(12).pv(123).r(.02).n(3).pmt(123);
        System.out.println("pmt: "+f.get("pmt"));
        f.g(.02);
        System.out.println("pmt: "+f.get("pmt"));
        f.solveFor(r);
        System.out.println("OK");
        System.out.println("pmt: "+f.get("pmt"));
        assertEquals(123, new Finance().comp(12).pv(123).r(.02).n(3).pmt(123).g(.02).solveFor(r).getSolution());
    }
}


