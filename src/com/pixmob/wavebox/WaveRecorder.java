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

import static com.pixmob.wavebox.Constants.DATA;
import static com.pixmob.wavebox.Constants.FMT;
import static com.pixmob.wavebox.Constants.HEADER_LEN;
import static com.pixmob.wavebox.Constants.RIFF;
import static com.pixmob.wavebox.Constants.TAG;
import static com.pixmob.wavebox.Constants.WAVE;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicBoolean;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.util.Log;

/**
 * The <code>WaveRecorder</code> is a helper class for recording audio to a file
 * using the <a
 * href="https://ccrma.stanford.edu/courses/422/projects/WaveFormat">WAVE
 * format</a>.
 * @author Pixmob
 */
public class WaveRecorder {
    private final String filePath;
    private WeakReference<WaveListener> listenerRef;
    private FileWriter fileWriter;
    
    public WaveRecorder(final String filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("File path is required");
        }
        this.filePath = filePath;
    }
    
    /**
     * Get available sample rates for recording on this device. Each of these
     * sample rates are safe to use with {@link #record(int)}.
     * @return sample rates in Hz
     */
    public static int[] getAvailableSampleRates() {
        final int[] candidates = { 8000, 11025, 22050, 44100, 48000 };
        int candidatesFound = 0;
        
        for (int i = 0; i < candidates.length; ++i) {
            final int minBufSize = AudioRecord.getMinBufferSize(candidates[i],
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minBufSize == AudioRecord.ERROR_BAD_VALUE
                    || minBufSize == AudioRecord.ERROR) {
                // not supported on this device
                candidates[i] = 0;
            } else {
                // we keep this sample rate
                candidatesFound++;
            }
        }
        
        // every non zero sample rates are returned
        final int[] sampleRates = new int[candidatesFound];
        int sampleRateIndex = 0;
        for (int i = 0; i < candidates.length; ++i) {
            if (candidates[i] != 0) {
                sampleRates[sampleRateIndex++] = candidates[i];
            }
        }
        
        return sampleRates;
    }
    
    public void setListener(WaveListener listener) {
        this.listenerRef = new WeakReference<WaveListener>(listener);
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
     * Pause recording.
     */
    public void pause() {
        Log.w(TAG, "Pause not implemented!");
        // TODO implement method pause
    }
    
    /**
     * Start recording in a worker thread. The file is recorded in 16 bits MONO
     * using the device microphone.
     * @param sampleRateInHz sample rate used for recording
     * @throws WaveException if recording initialization failed
     */
    public void record(int sampleRateInHz) throws WaveException {
        // guess which buffer size we should use for recording
        final int minBufferSize = AudioRecord.getMinBufferSize(sampleRateInHz,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE
                || minBufferSize == AudioRecord.ERROR) {
            throw new WaveException("Failed to initialize audio recorder");
        }
        
        // we use a longer buffer to prevent audio gaps
        final int bufferSize = 2 * minBufferSize;
        final AudioRecord audioRecorder = new AudioRecord(AudioSource.MIC,
                sampleRateInHz, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        
        final ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        fileWriter = new FileWriter(audioRecorder, buffer);
        fileWriter.start();
    }
    
    /**
     * Stop recording.
     */
    public void stop() {
        if (fileWriter != null) {
            fileWriter.recording.set(false);
            
            // let the worker thread stop by itself
            if (fileWriter.isAlive()) {
                try {
                    fileWriter.join(1000 * 5);
                    if (fileWriter.isAlive()) {
                        // kill the worker thread
                        fileWriter.interrupt();
                    }
                } catch (InterruptedException ignore) {
                    fileWriter.interrupt();
                }
            }
            fileWriter = null;
        }
    }
    
    /**
     * Internal worker thread for recording.
     * @author Pixmob
     */
    private class FileWriter extends Thread {
        private final AudioRecord audioRecorder;
        private final ByteBuffer buffer;
        public final AtomicBoolean recording = new AtomicBoolean();
        
        public FileWriter(final AudioRecord audioRecorder,
                final ByteBuffer buffer) {
            super("WaveRecorder");
            this.audioRecorder = audioRecorder;
            this.buffer = buffer;
        }
        
        @Override
        public void run() {
            try {
                doRun();
            } catch (IOException e) {
                Log.w(TAG, "I/O error when writing audio data to file "
                        + filePath, e);
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error", e);
            }
        }
        
        private void doRun() throws IOException {
            Log.i(TAG, "Start writing audio data to file " + filePath);
            
            final int sampleRateInHz = audioRecorder.getSampleRate();
            final short bitsPerSample = 16;
            final short numChannels = 1; // mono
            
            // prepare the WAVE file header:
            // the header is actually written when the recording is stopped
            final ByteBuffer header = ByteBuffer.allocate(HEADER_LEN);
            header.order(ByteOrder.BIG_ENDIAN);
            header.put(RIFF);
            header.order(ByteOrder.LITTLE_ENDIAN);
            // remember to write ChunkSize later
            header.putInt(0);
            header.order(ByteOrder.BIG_ENDIAN);
            header.put(WAVE);
            header.put(FMT);
            header.order(ByteOrder.LITTLE_ENDIAN);
            // Subchunk1Size
            header.putInt(16);
            // AudioFormat = PCM
            header.putShort((short) 1);
            // NumChannels = Mono
            header.putShort(numChannels);
            header.putInt(sampleRateInHz);
            // ByteRate = SampleRate * NumChannels * BitsPerSample/8
            header.putInt(sampleRateInHz * numChannels * bitsPerSample / 8);
            // BlockAlign = NumChannels * BitsPerSample/8
            header.putShort((short) (numChannels * bitsPerSample / 8));
            // BitsPerSample
            header.putShort(bitsPerSample);
            header.order(ByteOrder.BIG_ENDIAN);
            header.put(DATA);
            header.order(ByteOrder.LITTLE_ENDIAN);
            // remember to write Subchunk2Size later
            header.putInt(0);
            
            final RandomAccessFile file = new RandomAccessFile(filePath, "rw");
            file.setLength(HEADER_LEN);
            final FileChannel fc = file.getChannel();
            // skip header
            fc.position(HEADER_LEN);
            
            final int bufferCapacity = buffer.capacity();
            int samplesRead = 0;
            int bytesWritten = 0;
            
            try {
                audioRecorder.startRecording();
                recording.set(true);
                fireEvent(WaveListener.RECORDING_STARTED);
                
                while (recording.get()) {
                    buffer.clear();
                    // get audio data
                    samplesRead = audioRecorder.read(buffer, bufferCapacity);
                    if (samplesRead == 0) {
                        Log.d(TAG, "Got no audio samples");
                        break;
                    }
                    if (samplesRead == AudioRecord.ERROR_INVALID_OPERATION) {
                        Log.e(TAG, "Failed to initialize AudioRecord");
                        break;
                    } else if (samplesRead == AudioRecord.ERROR_BAD_VALUE
                            || samplesRead == AudioRecord.ERROR) {
                        Log.e(TAG, "Failed to get audio data");
                        break;
                    }
                    
                    // write audio data to the file
                    buffer.limit(samplesRead);
                    while (buffer.hasRemaining()) {
                        bytesWritten += fc.write(buffer);
                    }
                }
                
                // update Subchunk2Size
                header.position(40);
                header.putInt(bytesWritten);
                // update ChunkSize
                header.position(4);
                header.putInt(bytesWritten + 8 + 24 + 4);
                
                // write header
                header.clear();
                fc.position(0);
                while (header.hasRemaining() && fc.isOpen()) {
                    fc.write(header);
                }
            } finally {
                audioRecorder.stop();
                audioRecorder.release();
                
                try {
                    fc.close();
                } catch (IOException ignore) {
                }
                
                recording.set(false);
                fireEvent(WaveListener.RECORDING_STOPPED);
                
                Log.i(TAG, "Stop writing audio data to file " + filePath);
            }
        }
    }
}
