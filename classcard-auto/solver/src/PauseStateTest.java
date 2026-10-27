package net.classcardauto.mobile;

public class PauseStateTest {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond) {
        if (cond) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name);
        }
    }

    public static void main(String[] a) {
        PauseState s = new PauseState();
        ok("starts running", !s.isPaused() && s.getTouches() == 0);

        // touches below the limit do not pause, the limit-th does
        ok("1st touch below limit 2", !s.addTouch(2) && s.getTouches() == 1);
        ok("2nd touch reaches limit 2", s.addTouch(2) && s.getTouches() == 2);

        // the caller pauses when told to; further touches are then ignored
        ok("pause changes state", s.pause(10_000));
        ok("second pause is a no-op", !s.pause(11_000));
        ok("touch while paused ignored", !s.addTouch(2) && s.getTouches() == 2);
        ok("pause counted once", s.getPauseCount() == 1);

        // resume reports the pause length and clears the touch count
        long lasted = s.resume(25_000);
        ok("pause length", lasted == 15_000);
        ok("touches cleared on resume", s.getTouches() == 0 && !s.isPaused());
        ok("resume when running returns -1", s.resume(30_000) == -1);

        // after resuming the count starts from zero again
        ok("count restarts", !s.addTouch(2) && s.getTouches() == 1 && s.addTouch(2));

        // limit 1 pauses on the first touch; nonsense limits behave like 1
        PauseState one = new PauseState();
        ok("limit 1", one.addTouch(1));
        PauseState zero = new PauseState();
        ok("limit 0 treated as 1", zero.addTouch(0));

        // remaining time: counts down, freezes while paused, and run clocks shifted by the pause stay consistent
        PauseState t = new PauseState();
        long started = 100_000, limit = 600_000;
        ok("remaining at start", t.remainingSeconds(started, limit, 100_000) == 600);
        ok("remaining later", t.remainingSeconds(started, limit, 160_000) == 540);
        t.pause(160_000);
        ok("frozen while paused", t.remainingSeconds(started, limit, 400_000) == 540);
        long pausedFor = t.resume(400_000);
        started += pausedFor;                    // what the engine does with its run clock
        ok("same remaining right after resume", t.remainingSeconds(started, limit, 400_000) == 540);
        ok("counts down again", t.remainingSeconds(started, limit, 430_000) == 510);
        ok("never negative", t.remainingSeconds(started, limit, 9_999_999) == 0);

        // reset for a new run
        t.addTouch(5);
        t.pause(1);
        t.reset();
        ok("reset clears everything", !t.isPaused() && t.getTouches() == 0 && t.getPauseCount() == 0);

        System.out.println(pass + " pass / " + fail + " fail");
        System.exit(fail == 0 ? 0 : 1);
    }
}
