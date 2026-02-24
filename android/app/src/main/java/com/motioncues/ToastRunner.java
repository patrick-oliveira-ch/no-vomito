package com.motioncues;

import android.widget.Toast;

/** Simple Runnable to show a Toast on the main thread. */
public class ToastRunner implements Runnable {
    private final UpdateChecker checker;
    private final String message;

    public ToastRunner(UpdateChecker checker, String message) {
        this.checker = checker;
        this.message = message;
    }

    @Override
    public void run() {
        Toast.makeText(checker.getContext(), message, Toast.LENGTH_SHORT).show();
    }
}
