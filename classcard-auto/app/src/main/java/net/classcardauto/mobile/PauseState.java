package net.classcardauto.mobile;

/**
 * Pause bookkeeping for a run: whether it is paused, how many times the user touched the screen during the test,
 * and how long a pause lasted so run clocks can be moved forward by that much. No Android types, so it is testable.
 */
public final class PauseState {
    private boolean paused;
    private long pausedAt;
    private int touches;
    private int pauseCount;

    public boolean isPaused() {
        return paused;
    }

    public int getTouches() {
        return touches;
    }

    public int getPauseCount() {
        return pauseCount;
    }

    /** New run: nothing paused, no touches. */
    public void reset() {
        paused = false;
        pausedAt = 0;
        touches = 0;
        pauseCount = 0;
    }

    /** @return true if the state changed (false when already paused). */
    public boolean pause(long now) {
        if (paused) {
            return false;
        }
        paused = true;
        pausedAt = now;
        pauseCount++;
        return true;
    }

    /**
     * Ends the pause and clears the touch count.
     *
     * @return how long the pause lasted, or -1 if it was not paused
     */
    public long resume(long now) {
        if (!paused) {
            return -1;
        }
        paused = false;
        touches = 0;
        return Math.max(0, now - pausedAt);
    }

    /**
     * Counts one touch by the user. Touches while already paused are ignored.
     *
     * @return true when this touch reached {@code limit}, i.e. the run should be paused now
     */
    public boolean addTouch(int limit) {
        if (paused) {
            return false;
        }
        touches++;
        return touches >= Math.max(1, limit);
    }

    /** Seconds left of {@code limitMs} for a run that started at {@code startedAt}; frozen while paused. */
    public long remainingSeconds(long startedAt, long limitMs, long now) {
        long clock = paused ? pausedAt : now;
        return Math.max(0, (startedAt + limitMs - clock) / 1000);
    }
}
