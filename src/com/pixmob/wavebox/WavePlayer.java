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
package com.pixmob.wavebox;

import static com.pixmob.wavebox.Constants.HEADER_LEN;
import static com.pixmob.wavebox.Constants.RIFF;
import static com.pixmob.wavebox.Constants.TAG;
import static com.pixmob.wavebox.Constants.WAVE;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * The <code>WavePlayer</code> class is a helper class for playing audio files
 * in the <a
 * href="https://ccrma.stanford.edu/courses/422/projects/WaveFormat">WAVE
 * format</a>. Unlike the <code>MediaPlayer</code> class, this class supports
 * setting the gain while a file is playing.
 * @author aro
 */
public class WavePlayer {
    private final String filePath;
    private WeakReference<WaveListener> listenerRef = null;
    private FileReader fileReader;
    
    public WavePlayer(final String filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("File path is required");
        }
        this.filePath = filePath;
    }
    
    public void setListener(WaveListener listener) {
        // prevent memory leaks by using a WeakReference
        listenerRef = new WeakReference<WaveListener>(listener);
    }
    
    private void fireEvent(int event) {
        if (listenerRef != null) {
            final WaveListener listener = listenerRef.get();
            if (listener != null) {
                try {
                    listener.onWaveEvent(event, filePath, 0);
                } catch (Exception e) {
                    Log.w(TAG, "Error when invoking listener: " + listener, e);
                }
            }
        }
    }
    
    /**
     * Set the playback head to a relative position in the file.
     * @param position relative position in the file, in percentage (0.0 to 1.0)
     */
    public void seek(float position) {
        if (position < 0 || position > 1) {
            throw new IllegalArgumentException("Invalid seek position: "
                    + position);
        }
        Log.w(TAG, "Seek not implemented!");
        // TODO implement method seek
    }
    
    /**
     * Pause playback.
     */
    public void pause() {
        Log.w(TAG, "Pause not implemented!");
        // TODO implement method pause
    }
    
    /**
     * Start playback in a worker thread.
     * @throws WaveException if playback initialization failed
     */
    public void play() throws WaveException {
        // read the sample rate from the WAVE file
        final int sampleRate;
        try {
            sampleRate = getSampleRate(filePath);
        } catch (IOException e) {
            throw new WaveException("Failed to read audio file: " + filePath, e);
        }
        if (sampleRate == -1) {
            throw new WaveException("Invalid audio file: " + filePath);
        }
        
        // guess which buffer size we should use for playback
        final int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize == AudioTrack.ERROR_BAD_VALUE
                || minBufferSize == AudioTrack.ERROR) {
            throw new WaveException("Failed to initialize audio player");
        }
        
        // we use a longer buffer to prevent audio gaps
        final int bufferSize = 2 * minBufferSize;
        final AudioTrack audioPlayer = new AudioTrack(
                AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                bufferSize, AudioTrack.MODE_STREAM);
        
        final byte[] buffer = new byte[bufferSize];
        fileReader = new FileReader(audioPlayer, buffer);
        fileReader.start();
    }
    
    /**
     * Stop playback.
     */
    public void stop() {
        if (fileReader != null) {
            fileReader.playing.set(true);
            
            // let the worker thread stop by itself
            if (fileReader.isAlive()) {
                try {
                    fileReader.join(1000 * 5);
                    if (fileReader.isAlive()) {
                        // kill the worker thread
                        fileReader.interrupt();
                    }
                } catch (InterruptedException e) {
                    fileReader.interrupt();
                }
            }
            fileReader = null;
        }
    }
    
    private static int getSampleRate(String filePath) throws IOException {
        final ByteBuffer header = ByteBuffer.allocate(HEADER_LEN);
        final FileChannel fc = new FileInputStream(filePath).getChannel();
        try {
            while (header.hasRemaining() && fc.isOpen()) {
                fc.read(header);
            }
        } finally {
            try {
                fc.close();
            } catch (IOException ignore) {
            }
        }
        
        final byte[] data = new byte[4];
        
        // check if there is a RIFF block
        header.clear();
        header.position(0);
        header.order(ByteOrder.BIG_ENDIAN);
        header.get(data, 0, data.length);
        if (!Arrays.equals(RIFF, data)) {
            return -1;
        }
        
        // check if there is a WAVE block
        header.clear();
        header.position(8);
        header.get(data, 0, data.length);
        if (!Arrays.equals(WAVE, data)) {
            return -1;
        }
        
        // get the sample rate
        header.clear();
        header.order(ByteOrder.LITTLE_ENDIAN);
        header.position(24);
        
        // we may need to check other fields, such as number of channels or
        // sample length...
        
        return header.getInt();
    }
    
    /**
     * Internal worker thread for playback.
     * @author Pixmob
     */
    private class FileReader extends Thread {
        private final AudioTrack audioPlayer;
        private final byte[] buffer;
        public final AtomicBoolean playing = new AtomicBoolean();
        
        public FileReader(final AudioTrack audioPlayer, final byte[] buffer) {
            super("WavePlayer");
            this.audioPlayer = audioPlayer;
            this.buffer = buffer;
        }
        
        @Override
        public void run() {
            try {
                doRun();
            } catch (IOException e) {
                Log.w(TAG, "I/O error when reading audio data from file "
                        + filePath, e);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
            }
        }
        
        private void doRun() throws IOException {
            Log.i(TAG, "Start playing audio data from file " + filePath);
            
            final RandomAccessFile file = new RandomAccessFile(filePath, "r");
            // skip header
            file.skipBytes(HEADER_LEN);
            
            final int bufferCapacity = buffer.length;
            int bytesRead = 0;
            int bytesWritten = 0;
            
            try {
                audioPlayer.play();
                playing.set(true);
                fireEvent(WaveListener.PLAYBACK_STARTED);
                
                while (playing.get()) {
                    // read audio data from the file
                    bytesRead = file.read(buffer, 0, bufferCapacity);
                    if (bytesRead < 1) {
                        break;
                    }
                    
                    bytesWritten = 0;
                    while (bytesWritten != bytesRead) {
                        // send audio data to speaker
                        bytesWritten += audioPlayer.write(buffer, bytesWritten,
                            bytesRead - bytesWritten);
                        
                        if (bytesWritten == AudioTrack.ERROR_INVALID_OPERATION) {
                            Log.e(TAG, "Failed to initialize AudioTrack");
                            break;
                        }
                        if (bytesWritten == AudioTrack.ERROR_BAD_VALUE
                                || bytesWritten == AudioTrack.ERROR) {
                            Log.e(TAG, "Faileed to play audio data");
                            break;
                        }
                    }
                }
            } finally {
                audioPlayer.stop();
                audioPlayer.release();
                
                try {
                    file.close();
                } catch (IOException ignore) {
                }
                
                playing.set(false);
                fireEvent(WaveListener.PLAYBACK_STOPPED);
                
                Log.i(TAG, "Stop playing audio data from file " + filePath);
            }
        }
    }
}
