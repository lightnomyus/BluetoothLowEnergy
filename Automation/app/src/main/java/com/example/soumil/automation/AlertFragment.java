package com.example.soumil.automation;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AlertDialog;

/**
 * Created by soumi on 19-12-2017.
 */

public class AlertFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstance) {
        final AlertDialog dialog = new AlertDialog.Builder(getActivity())
                // Set Dialog Icon
                // Set Dialog Title
                .setTitle("Warning")
                // Set Dialog Message
                .setMessage("Timer ")

                // Positive button
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // yourCountDownTimer.cancel();
                        // Do something else
                    }
                })

                // Negative Button
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Do something else
                    }
                }).create();
        final CountDownTimer yourCountDownTimer = new CountDownTimer(20000, 1000) {

            public void onTick(long millisUntilFinished) {
                dialog.setMessage("Time Left " + millisUntilFinished / 1000);
            }

            public void onFinish() {
                dialog.setMessage("Discard the strip and restart the test");
                //dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

            }
        }.start();

        return dialog;

    }
}
