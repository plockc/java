package plock.math;

import java.util.*;
import static org.junit.Assert.*;
import org.junit.*;

@org.junit.runner.RunWith(org.junit.runners.JUnit4.class)
public class FinanceTest {
    List<Finance> fs = Arrays.asList(
        new Finance().fv(1349.32).n(3).comp(2).g(.02).r(.08).pmt(200),
        new Finance().fv(75693.0).n(15).comp(12).g(.02).r(.08).pmt(200),
        new Finance().fv(75693.0).n(15).g(.02).r(.08).pmt(200).comp(12),
        new Finance().fv(11700.75).n(5).g(.05).r(.03).pmt(2000),
        new Finance().fv(5525.63).n(5).r(.05).pmt(1000),
        new Finance().fv(3257.789253554884).n(10).r(.05).pv(2000),
        new Finance().fv(3868.742027011306).n(3).comp(2).g(.02).r(.08).pmt(200).pv(2000));
    @Test public void testFV() {
        fs.stream().forEach(f->assertEquals(f.getDouble("fv"), f.solve("fv"), 1.0));
    }
    @Test public void testPV() {
        fs.stream().forEach(f->assertEquals(f.getDouble("pv"), f.solve("pv"), 1.0));
    }
    @Test public void testN() {
        fs.stream().forEach(f->assertEquals(f.toString(), f.getDouble("n"), f.solve("n"), .001));
    }
    @Test public void testPmt() {
        fs.stream().forEach(f->assertEquals(f.getDouble("pmt"), f.solve("pmt"), 1.0));
    }
    @Test public void testR() {
        fs.stream().forEach(f->assertEquals(f.getDouble("r"), f.solve("r"), .0001));
    }
    @Test public void testG() {
        fs.stream().forEach(f->{
            if (f.getDouble("pmt")!=0.0) assertEquals(f.toString(), f.getDouble("g"), f.solve("g"), .0001);
        });
    }
}


