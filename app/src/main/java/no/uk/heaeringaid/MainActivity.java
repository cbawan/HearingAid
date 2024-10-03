package no.uk.heaeringaid;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private Button btnStartStop;
    private TextView tvAmplification;
    private SeekBar seekBarAmplification;
    private Switch switchNoiseReduction;

    private TextView tvBand1, tvBand2, tvBand3;
    private SeekBar seekBarBand1, seekBarBand2, seekBarBand3;

    private boolean isRecording = false;
    private Thread recordingThread;

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private int bufferSize;

    private float amplification = 1.0f;
    private boolean noiseReductionEnabled = false;

    // Equalizer settings
    private float band1Gain = 0.0f; // Low frequencies
    private float band2Gain = 0.0f; // Mid frequencies
    private float band3Gain = 0.0f; // High frequencies

    // Audio Effects
    private NoiseSuppressor noiseSuppressor;
    private AcousticEchoCanceler echoCanceler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        btnStartStop = findViewById(R.id.btnStartStop);
        tvAmplification = findViewById(R.id.tvAmplification);
        seekBarAmplification = findViewById(R.id.seekBarAmplification);
        switchNoiseReduction = findViewById(R.id.switchNoiseReduction);

        tvBand1 = findViewById(R.id.tvBand1);
        tvBand2 = findViewById(R.id.tvBand2);
        tvBand3 = findViewById(R.id.tvBand3);

        seekBarBand1 = findViewById(R.id.seekBarBand1);
        seekBarBand2 = findViewById(R.id.seekBarBand2);
        seekBarBand3 = findViewById(R.id.seekBarBand3);

        // Request microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION);
        }

        // Set up event listeners
        btnStartStop.setOnClickListener(v -> {
            if (!isRecording) {
                startAudioProcessing();
                btnStartStop.setText("Stop");
            } else {
                stopAudioProcessing();
                btnStartStop.setText("Start");
            }
        });

        seekBarAmplification.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                amplification = progress / 100.0f; // Convert to 0.0 - 3.0
                tvAmplification.setText(String.format("Amplification: %.1fx", amplification));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        switchNoiseReduction.setOnCheckedChangeListener((buttonView, isChecked) -> {
            noiseReductionEnabled = isChecked;
        });

        // Equalizer SeekBars
        seekBarBand1.setOnSeekBarChangeListener(equalizerChangeListener);
        seekBarBand2.setOnSeekBarChangeListener(equalizerChangeListener);
        seekBarBand3.setOnSeekBarChangeListener(equalizerChangeListener);
    }

    private SeekBar.OnSeekBarChangeListener equalizerChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            float gain = (progress - 10); // Range -10dB to +10dB

            int id = seekBar.getId();

            if (id == R.id.seekBarBand1) {
                band1Gain = gain;
                tvBand1.setText(String.format("Low Frequency: %.1fdB", band1Gain));
            } else if (id == R.id.seekBarBand2) {
                band2Gain = gain;
                tvBand2.setText(String.format("Mid Frequency: %.1fdB", band2Gain));
            } else if (id == R.id.seekBarBand3) {
                band3Gain = gain;
                tvBand3.setText(String.format("High Frequency: %.1fdB", band3Gain));
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private void startAudioProcessing() {
        isRecording = true;

        int sampleRate = 44100;
        int channelConfigIn = AudioFormat.CHANNEL_IN_MONO;
        int channelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Handle permission request
            return;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // Use AudioRecord.Builder and setPreferredDevice()
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
            AudioDeviceInfo selectedDevice = null;

            for (AudioDeviceInfo device : devices) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                    selectedDevice = device;
                    break;
                }
            }

            if (selectedDevice != null) {
                try {
                    audioRecord = new AudioRecord.Builder()
                            .setAudioSource(MediaRecorder.AudioSource.MIC)
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(audioFormat)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(channelConfigIn)
                                    .build())
                            .setBufferSizeInBytes(bufferSize)
                            .build();
                } catch (Exception e) {
                    e.printStackTrace();
                    // Fallback to default AudioRecord initialization
                    audioRecord = new AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfigIn,
                            audioFormat,
                            bufferSize);
                }
            } else {
                // Fallback if no built-in mic is found
                audioRecord = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfigIn,
                        audioFormat,
                        bufferSize);
            }
        } else {
            // For devices below API level 23
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfigIn,
                    audioFormat,
                    bufferSize);
        }

        // Rest of your existing code
        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelConfigOut,
                audioFormat,
                bufferSize,
                AudioTrack.MODE_STREAM);

        // Initialize Noise Suppressor if available
        if (NoiseSuppressor.isAvailable() && noiseReductionEnabled) {
            noiseSuppressor = NoiseSuppressor.create(audioRecord.getAudioSessionId());
            noiseSuppressor.setEnabled(true);
        }

        // Initialize Echo Canceler if available
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(audioRecord.getAudioSessionId());
            echoCanceler.setEnabled(true);
        }

        audioRecord.startRecording();
        audioTrack.play();

        recordingThread = new Thread(this::processAudioStream);
        recordingThread.start();
    }


    private void stopAudioProcessing() {
        isRecording = false;

        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }

        if (audioTrack != null) {
            audioTrack.stop();
            audioTrack.release();
            audioTrack = null;
        }

        if (noiseSuppressor != null) {
            noiseSuppressor.release();
            noiseSuppressor = null;
        }

        if (echoCanceler != null) {
            echoCanceler.release();
            echoCanceler = null;
        }
    }

    private void processAudioStream() {
        short[] buffer = new short[bufferSize / 2]; // Because we're using PCM 16-bit

        while (isRecording) {
            int read = audioRecord.read(buffer, 0, buffer.length);

            if (read > 0) {
                // Amplification
                for (int i = 0; i < read; i++) {
                    buffer[i] = (short) Math.min((buffer[i] * amplification), Short.MAX_VALUE);
                }

                // Equalization
                buffer = equalize(buffer);

                // Feedback Cancellation would be implemented here (advanced)

                audioTrack.write(buffer, 0, read);
            }
        }
    }

    private short[] equalize(short[] input) {
        // Placeholder for actual equalization processing
        // Simplified example

        return input;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRecording) {
            stopAudioProcessing();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
            } else {
                // Permission denied
                // Inform the user and close the app or disable functionality
            }
        }
    }
}
