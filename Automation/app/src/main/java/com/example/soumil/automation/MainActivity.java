package com.example.soumil.automation;

import android.app.Activity;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.daimajia.numberprogressbar.NumberProgressBar;
import com.daimajia.numberprogressbar.OnProgressBarListener;

import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity implements OnProgressBarListener,View.OnClickListener {
    private Timer timer;

    private NumberProgressBar bnp;
    private Button StartTestButton;
    private Button CancelButton;
    private Button RestartButton;
    private Button EndTestButton;
    private Button BLEConnectButton;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        StartTestButton = (Button) findViewById(R.id.StartTestButton);
        StartTestButton.setOnClickListener(this);
        CancelButton = (Button) findViewById(R.id.cancelTestButton);
        CancelButton.setOnClickListener(this);
        RestartButton = (Button) findViewById(R.id.RestartTestButton);
        RestartButton.setOnClickListener(this);
        EndTestButton = (Button) findViewById(R.id.EndTestButton);
        EndTestButton.setOnClickListener(this);
        BLEConnectButton = (Button) findViewById(R.id.ConnectButton);
        BLEConnectButton.setOnClickListener(this);
        textView = (TextView) findViewById(R.id.Progressbartxt);
        bnp = (NumberProgressBar) findViewById(R.id.numberbar1);
        bnp.setOnProgressBarListener(this);
        bnp.incrementProgressBy(1);
    }

    @Override
    public void onResume()
    {
        super.onResume();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.cancel();
    }

    @Override
    public void onProgressChange(int current, int max) {
        if(current == max) {
            Toast.makeText(getApplicationContext(), "finish", Toast.LENGTH_SHORT).show();
            bnp.setProgress(0);
        }
    }

    @Override
    public void onClick(View view) {
        switch(view.getId())
        {
            case R.id.cancelTestButton:

                break;
            case R.id.ConnectButton:
                break;
            case R.id.EndTestButton:
                break;
            case R.id.StartTestButton:
                break;
            case R.id.RestartTestButton:
                break;
        }
    }
}
