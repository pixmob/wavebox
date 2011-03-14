/*
 * Copyright (C) 2011 Alexandre Roman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pixmob.wavebox.demo;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.View;

import com.pixmob.wavebox.WaveException;
import com.pixmob.wavebox.WaveListener;
import com.pixmob.wavebox.WavePlayer;
import com.pixmob.wavebox.WaveRecorder;

/**
 * Activity showing how Wavebox could be used.
 * @author Pixmob
 */
public class DemoActivity extends Activity implements WaveListener {
    private static final int RECORDING_PROGRESS_DIALOG = 1;
    private static final int PLAYBACK_PROGRESS_DIALOG = 2;
    private static final int SELECT_SAMPLE_RATE_DIALOG = 3;
    private static final int RECORD_NOT_FOUND_DIALOG = 4;
    private static final int EXTERNAL_STORAGE_NOT_AVAILABLE_DIALOG = 5;
    private static final int RECORDING_FAILED_DIALOG = 6;
    private static final int PLAYBACK_FAILED_DIALOG = 7;
    private final Handler waveEventHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            final int event = msg.what;
            if (event == PLAYBACK_STOPPED) {
                dismissDialog(PLAYBACK_PROGRESS_DIALOG);
            } else if (event == RECORDING_STOPPED) {
                dismissDialog(RECORDING_PROGRESS_DIALOG);
            }
        }
    };
    private int[] sampleRates;
    private String[] sampleRateStrings;
    private int sampleRate;
    private int selectedSampleRate;
    private State state;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.demo);
        
        // the recorder and the player will survive screen rotation,
        // thanks to this State class
        state = (State) getLastNonConfigurationInstance();
        if (state == null) {
            state = new State();
        }
        state.attach(this);
        
        // get available sample rates for recording on this device
        sampleRates = WaveRecorder.getAvailableSampleRates();
        if (sampleRates.length == 1) {
            sampleRate = sampleRates[0];
        }
        
        // translate these sample rates in Strings
        final String sampleRateFormat = getString(R.string.sample_rate);
        sampleRateStrings = new String[sampleRates.length];
        for (int i = 0; i < sampleRateStrings.length; ++i) {
            sampleRateStrings[i] = String.format(sampleRateFormat,
                sampleRates[i]);
        }
    }
    
    @Override
    public Object onRetainNonConfigurationInstance() {
        return state;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // check if the external storage is available:
        // the audio record in stored in this area
        final String externalStorageState = Environment
                .getExternalStorageState();
        if (!Environment.MEDIA_MOUNTED.equals(externalStorageState)
                && !Environment.MEDIA_MOUNTED_READ_ONLY
                        .equals(externalStorageState)) {
            showDialog(EXTERNAL_STORAGE_NOT_AVAILABLE_DIALOG);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            if (state.recorder != null) {
                state.recorder.stop();
            }
            if (state.player != null) {
                state.player.stop();
            }
        }
    }
    
    @Override
    protected Dialog onCreateDialog(int id) {
        if (EXTERNAL_STORAGE_NOT_AVAILABLE_DIALOG == id) {
            return new AlertDialog.Builder(this).setTitle(R.string.sorry)
                    .setMessage(R.string.external_storage_not_available)
                    .setOnCancelListener(new OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            finish();
                        }
                    }).create();
        }
        if (RECORD_NOT_FOUND_DIALOG == id) {
            return new AlertDialog.Builder(this).setTitle(R.string.sorry)
                    .setMessage(R.string.record_not_found).create();
        }
        if (RECORDING_FAILED_DIALOG == id) {
            return new AlertDialog.Builder(this).setTitle(R.string.sorry)
                    .setMessage(R.string.recording_failed).create();
        }
        if (PLAYBACK_FAILED_DIALOG == id) {
            return new AlertDialog.Builder(this).setTitle(R.string.sorry)
                    .setMessage(R.string.playback_failed).create();
        }
        if (RECORDING_PROGRESS_DIALOG == id) {
            final ProgressDialog d = new ProgressDialog(this);
            d.setMessage(getString(R.string.recording));
            d.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    stopRecord();
                }
            });
            return d;
        }
        if (PLAYBACK_PROGRESS_DIALOG == id) {
            final ProgressDialog d = new ProgressDialog(this);
            d.setMessage(getString(R.string.playing));
            d.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    stopPlay();
                }
            });
            return d;
        }
        if (SELECT_SAMPLE_RATE_DIALOG == id) {
            return new AlertDialog.Builder(this).setTitle(
                R.string.select_sample_rate).setSingleChoiceItems(
                sampleRateStrings, selectedSampleRate, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dismissDialog(SELECT_SAMPLE_RATE_DIALOG);
                        
                        selectedSampleRate = which;
                        sampleRate = sampleRates[which];
                        doRecord();
                    }
                }).create();
        }
        
        return super.onCreateDialog(id);
    }
    
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        selectedSampleRate = savedInstanceState
                .getInt("selectedSampleRate", -1);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("selectedSampleRate", selectedSampleRate);
    }
    
    public void onWaveEvent(String filePath, int event) {
        if (PLAYBACK_STOPPED == event) {
            waveEventHandler.sendEmptyMessage(event);
        } else if (RECORDING_STOPPED == event) {
            waveEventHandler.sendEmptyMessage(event);
        }
    }
    
    public void onRecord(View view) {
        if (sampleRates.length == 1) {
            doRecord();
        } else {
            showDialog(SELECT_SAMPLE_RATE_DIALOG);
        }
    }
    
    private void doRecord() {
        try {
            state.recorder.record(sampleRate);
            showDialog(RECORDING_PROGRESS_DIALOG);
        } catch (WaveException e) {
            showDialog(RECORDING_FAILED_DIALOG);
        }
    }
    
    public void onPlay(View view) {
        if (!state.recordFile.exists()) {
            showDialog(RECORD_NOT_FOUND_DIALOG);
        } else {
            try {
                state.player.play();
                showDialog(PLAYBACK_PROGRESS_DIALOG);
            } catch (WaveException e) {
                showDialog(PLAYBACK_FAILED_DIALOG);
            }
        }
    }
    
    private void stopRecord() {
        state.recorder.stop();
    }
    
    private void stopPlay() {
        state.player.stop();
    }
    
    /**
     * Internal state. This class is used for storing references which are not
     * affected by a screen rotation.
     * @author Pixmob
     */
    private static class State {
        public final File recordFile;
        public final WavePlayer player;
        public final WaveRecorder recorder;
        
        public State() {
            recordFile = new File(Environment.getExternalStorageDirectory(),
                    "waveboxdemo.wav");
            recorder = new WaveRecorder(recordFile.getAbsolutePath());
            player = new WavePlayer(recordFile.getAbsolutePath());
        }
        
        public void attach(WaveListener listener) {
            recorder.setListener(listener);
            player.setListener(listener);
        }
    }
}
