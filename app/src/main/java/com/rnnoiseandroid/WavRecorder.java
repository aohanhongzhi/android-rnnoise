package com.rnnoiseandroid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

// 48 kHz RAW 16-bit mono PCM
public class WavRecorder {
    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    private static final int RECORDER_SAMPLERATE = 48000;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    short[] audioData;

    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;
    int[] bufferData;
    int bytesRecorded;

    private String output;
    private Context context;
    private String tempFilePath; // 存储临时文件路径

    public WavRecorder(String path, Context context) {
        bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING) * 3;

        audioData = new short[bufferSize]; // short array that pcm data is put
        // into.
        output = path;
        this.context = context;
    }

    private String getFilename() {
        return (output);
    }

    private String getTempFilename() {
        if (tempFilePath != null) {
            return tempFilePath; // 如果已经设置了临时文件路径，直接返回
        }

        // 使用应用专用目录代替外部存储
        File file = new File(context.getExternalFilesDir(null), AUDIO_RECORDER_FOLDER);
        Log.d("WavRecorder", "Audio folder path: " + file.getAbsolutePath());

        // 确保目录存在
        if (!file.exists()) {
            boolean success = file.mkdirs();
            Log.d("WavRecorder", "Directory created: " + success);
            if (!success) {
                Log.e("WavRecorder", "Failed to create directory: " + file.getAbsolutePath());
            }
        }

        File tempFile = new File(file, AUDIO_RECORDER_TEMP_FILE);
        Log.d("WavRecorder", "Temp file path: " + tempFile.getAbsolutePath());

        // 如果临时文件存在，删除它
        if (tempFile.exists()) {
            boolean deleted = tempFile.delete();
            if (!deleted) {
                Log.e("WavRecorder", "Failed to delete existing temp file");
            }
        }
        
        tempFilePath = tempFile.getAbsolutePath();
        return tempFilePath;
    }

    public void startRecording() {
        try {
            // 创建 AudioRecord 实例（会触发 RECORD_AUDIO 权限检查）
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING, bufferSize);
                    
            int i = recorder.getState();
            if (i == AudioRecord.STATE_INITIALIZED) {
                recorder.startRecording();
                isRecording = true;

                recordingThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        writeAudioDataToFile();
                    }
                }, "AudioRecorder Thread");

                recordingThread.start();
            } else {
                throw new IllegalStateException("AudioRecord initialization failed");
            }
        } catch (SecurityException e) {
            // 权限被拒绝时的处理
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeAudioDataToFile() {
        byte data[] = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;
        
        try {
            // 确保父目录存在
            File outputFile = new File(filename);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            Log.d("WavRecorder", "Creating output stream for: " + filename);
            os = new FileOutputStream(filename);
            Log.d("WavRecorder", "Output stream created successfully");
        } catch (FileNotFoundException e) {
            Log.e("WavRecorder", "FileNotFoundException: " + e.getMessage());
            e.printStackTrace();
        }

        int read = 0;
        if (null != os) {
            while (isRecording) {
                read = recorder.read(data, 0, bufferSize);
                if (read > 0) {
                }

                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stopRecording() {
        if (null != recorder) {
            isRecording = false;

            int i = recorder.getState();
            if (i == 1)
                recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
        }

        copyWaveFile(getTempFilename(), getFilename());
        deleteTempFile();
    }

    private void deleteTempFile() {
        File file = new File(getTempFilename());
        file.delete();
    }

    private void copyWaveFile(String inFilename, String outFilename) {
        Log.d("WavRecorder", "Copying wave file from " + inFilename + " to " + outFilename);
        
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = ((RECORDER_CHANNELS == AudioFormat.CHANNEL_IN_MONO) ? 1
                : 2);
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8;

        byte[] data = new byte[bufferSize];

        try {
            // 检查输入文件是否存在
            File inputFile = new File(inFilename);
            if (!inputFile.exists()) {
                Log.e("WavRecorder", "Input file does not exist: " + inFilename);
                return;
            }
            
            // 确保输出文件的目录存在
            File outputFile = new File(outFilename);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while (in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
            Log.d("WavRecorder", "File copied successfully");
        } catch (FileNotFoundException e) {
            Log.e("WavRecorder", "FileNotFoundException: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            Log.e("WavRecorder", "IOException: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {
        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (((RECORDER_CHANNELS == AudioFormat.CHANNEL_IN_MONO) ? 1
                : 2) * 16 / 8); // block align
        header[33] = 0;
        header[34] = RECORDER_BPP; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }
}
