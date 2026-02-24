package com.motioncues;

/** Runnable to trigger download on main thread. */
public class DownloadStarter implements Runnable {
    private final UpdateChecker checker;

    public DownloadStarter(UpdateChecker checker) {
        this.checker = checker;
    }

    @Override
    public void run() {
        checker.doDownload();
    }
}
