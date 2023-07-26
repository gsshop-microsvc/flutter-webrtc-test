package com.cloudwebrtc.webrtc.audio;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.twilio.audioswitch.AudioDevice;
import com.twilio.audioswitch.AudioSwitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public class AudioSwitchManager {
    public static final String TAG = "AudioSwitchManager";
    @NonNull
    private final Context context;
    @NonNull
    private final AudioManager audioManager;

    public boolean loggingEnabled;
    @NonNull
    public Function2<
            ? super List<? extends AudioDevice>,
            ? super AudioDevice,
            Unit> audioDeviceChangeListener = (devices, currentDevice) -> null;

    @NonNull
    public AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = (i -> {});

    @NonNull
    public List<Class<? extends AudioDevice>> preferredDeviceList;

    // AudioSwitch is not threadsafe, so all calls should be done on the main thread.
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Nullable
    private AudioSwitch audioSwitch;

    /**
     * The audio focus mode to use while started.
     *
     * Defaults to [AudioManager.AUDIOFOCUS_GAIN].
     */
    private int focusMode = AudioManager.AUDIOFOCUS_GAIN;

    /**
     * The audio mode to use while started.
     *
     * Defaults to [AudioManager.MODE_NORMAL].
     */
    private int audioMode = AudioManager.MODE_NORMAL;

    public AudioSwitchManager(@NonNull Context context) {
        this.context = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        preferredDeviceList = new ArrayList<>();
        preferredDeviceList.add(AudioDevice.BluetoothHeadset.class);
        preferredDeviceList.add(AudioDevice.WiredHeadset.class);
        preferredDeviceList.add(AudioDevice.Speakerphone.class);
        preferredDeviceList.add(AudioDevice.Earpiece.class);
    }

    public void start() {
        if (audioSwitch == null) {
            handler.removeCallbacksAndMessages(null);
            handler.postAtFrontOfQueue(() -> {
                audioSwitch = new AudioSwitch(
                        context,
                        loggingEnabled,
                        audioFocusChangeListener,
                        preferredDeviceList
                );
                audioSwitch.setFocusMode(focusMode);
                audioSwitch.setAudioMode(audioMode);
                audioSwitch.start(audioDeviceChangeListener);
                audioSwitch.activate();
            });
        }
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        handler.postAtFrontOfQueue(() -> {
            if (audioSwitch != null) {
                audioSwitch.stop();
            }
            audioSwitch = null;
        });
    }

    public void setMicrophoneMute(boolean mute){
        audioManager.setMicrophoneMute(mute);
    }

    @Nullable
    public AudioDevice selectedAudioDevice() {
        AudioSwitch audioSwitchTemp = audioSwitch;
        if (audioSwitchTemp != null) {
            return audioSwitchTemp.getSelectedAudioDevice();
        } else {
            return null;
        }
    }

    @NonNull
    public List<AudioDevice> availableAudioDevices() {
        AudioSwitch audioSwitchTemp = audioSwitch;
        if (audioSwitchTemp != null) {
            return audioSwitchTemp.getAvailableAudioDevices();
        } else {
            return Collections.emptyList();
        }
    }

    public void selectAudioOutput(@NonNull Class<? extends AudioDevice> audioDeviceClass) {
        handler.post(() -> {
            if (audioSwitch != null) {
                List<AudioDevice> devices = availableAudioDevices();
                AudioDevice audioDevice = null;

                for (AudioDevice device : devices) {
                    if (device.getClass().equals(audioDeviceClass)) {
                        audioDevice = device;
                        break;
                    }
                }

                if (audioDevice != null) {
                    audioSwitch.selectDevice(audioDevice);
                }
            }
        });
    }

    public void enableSpeakerphone(boolean enable) {
        if(enable) {
            audioManager.setSpeakerphoneOn(true);
        } else {
            audioManager.setSpeakerphoneOn(false);
        }
    }
    
    public void selectAudioOutput(@Nullable AudioDeviceKind kind) {
        if (kind != null) {
            selectAudioOutput(kind.audioDeviceClass);
        }
    }


    public void setAudioConfiguration(Map<String, Object> configuration) {
        if(configuration == null) {
            return;
        }

        String audioMode = null;
        if (configuration.get("androidAudioMode") instanceof String) {
            audioMode = (String) configuration.get("androidAudioMode");
        }

        String focusMode = null;
        if (configuration.get("androidAudioFocusMode") instanceof String) {
            focusMode = (String) configuration.get("androidAudioFocusMode");
        }

        setAudioMode(audioMode);
        setFocusMode(focusMode);
    }

    public void setAudioMode(@Nullable String audioModeString) {
        if (audioModeString == null) {
            return;
        }

        int audioMode = -1;
        switch (audioModeString) {
            case "normal":
                audioMode = AudioManager.MODE_NORMAL;
                break;
            case "callScreening":
                audioMode = AudioManager.MODE_CALL_SCREENING;
                break;
            case "inCall":
                audioMode = AudioManager.MODE_IN_CALL;
                break;
            case "inCommunication":
                audioMode = AudioManager.MODE_IN_COMMUNICATION;
                break;
            case "ringtone":
                audioMode = AudioManager.MODE_RINGTONE;
                break;
            default:
                Log.w(TAG, "Unknown audio mode: " + audioModeString);
                break;
        }

        // Valid audio modes start from 0
        if (audioMode >= 0) {
            this.audioMode = audioMode;
            if (audioSwitch != null) {
                Objects.requireNonNull(audioSwitch).setAudioMode(audioMode);
            }
        }
    }

    public void setFocusMode(@Nullable String focusModeString) {
        if (focusModeString == null) {
            return;
        }

        int focusMode = -1;
        switch(focusModeString) {
            case "gain":
                focusMode = AudioManager.AUDIOFOCUS_GAIN;
                break;
            case "gainTransient":
                focusMode = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT;
                break;
            case "gainTransientExclusive":
                focusMode = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE;
                break;
            case "gainTransientMayDuck":
                focusMode = AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK;
                break;
            default:
                Log.w(TAG, "Unknown audio focus mode: " + focusModeString);
                break;
        }

        // Valid focus modes start from 1
        if (focusMode > 0) {
            this.focusMode = focusMode;
            if (audioSwitch != null) {
                Objects.requireNonNull(audioSwitch).setFocusMode(focusMode);
            }
        }
    }
}
