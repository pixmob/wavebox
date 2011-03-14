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

/**
 * Interface definition for a callback to be invoked when playing or recording a
 * WAVE audio file.
 * @author Pixmob
 */
public interface WaveListener {
    /**
     * The audio playback has started.
     */
    public static final int PLAYBACK_STARTED = 10;
    /**
     * The audio playback was stopped.
     */
    public static final int PLAYBACK_STOPPED = 11;
    /**
     * The audio playback was paused.
     */
    public static final int PLAYBACK_PAUSEd = 12;
    /**
     * The audio recording has started.
     */
    public static final int RECORDING_STARTED = 20;
    /**
     * The audio recording was stopped.
     */
    public static final int RECORDING_STOPPED = 21;
    /**
     * The audio recording was paused.
     */
    public static final int RECORDING_PAUSED = 22;
    
    /**
     * This method is invoked when an event occurred.
     * @param event what kind of event
     * @param filePath audio file path
     * @param position relative position where the event occured (in percentage)
     */
    void onWaveEvent(int event, String filePath, float position);
}
