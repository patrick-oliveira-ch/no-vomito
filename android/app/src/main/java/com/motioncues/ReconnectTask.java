package com.motioncues;

/**
 * Simple Runnable to reconnect the long-poll after delay.
 * Top-level class to avoid d8 inner class bug.
 */
public class ReconnectTask implements Runnable {
    private final UpdateChecker checker;

    public ReconnectTask(UpdateChecker checker) {
        this.checker = checker;
    }

    @Override
    public void run() {
        checker.startChecking();
    }
}
