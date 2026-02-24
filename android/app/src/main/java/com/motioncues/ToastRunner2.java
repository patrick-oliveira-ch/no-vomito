package com.motioncues;

import android.content.Context;
import android.widget.Toast;

/** Simple toast runner with direct context reference. */
public class ToastRunner2 implements Runnable {
    private final Context context;
    private final String message;

    public ToastRunner2(Context ctx, String message) {
        this.context = ctx;
        this.message = message;
    }

    @Override
    public void run() {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}
