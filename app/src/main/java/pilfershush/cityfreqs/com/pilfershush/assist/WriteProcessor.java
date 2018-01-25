package pilfershush.cityfreqs.com.pilfershush.assist;


import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import pilfershush.cityfreqs.com.pilfershush.MainActivity;

public class WriteProcessor {
    // until we bother with wav headers, this is raw format buffer writes
    // pcm savefile for raw import into Audacity as 48 kHz, signed 16 bit, big-endian, mono

    private AudioSettings audioSettings;
    private File extDirectory;

    private String audioFilename;
    private String logFilename;
    private String sessionFilename;

    public static File AUDIO_OUTPUT_FILE;
    public static DataOutputStream AUDIO_DATA_STREAM;
    public static BufferedOutputStream AUDIO_OUTPUT_STREAM;

    public static File LOG_OUTPUT_FILE;
    public static FileOutputStream LOG_OUTPUT_STREAM;
    public static OutputStreamWriter LOG_OUTPUT_WRITER;

    private static final String APP_DIRECTORY_NAME = "PilferShush";
    private static final String AUDIO_FILE_EXTENSION = ".pcm";
    private static final String LOG_FILE_EXTENSION = ".txt";
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd-HH:mm:ss", Locale.ENGLISH);

    public WriteProcessor(String sessionName, AudioSettings audioSettings) {
        setSessionName(sessionName);
        this.audioSettings = audioSettings;

        log("Check: Download(s)/PilferShush/");
        // checks for read/write state
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            createDirectory();
            log("ext dir: " + extDirectory.toString());
        }
    }

    public void setSessionName(String sessionName) {
        if (sessionName == null || sessionName == "") {
            sessionFilename = "capture";
        }
        else {
            sessionFilename = sessionName;
        }
    }

    /**************************************************************/
    /*
        text logging
     */
    public void prepareLogToFile() {
        // need to build the filename AND path
        log("prepare log file...");
        File location = extDirectory;
        if (location == null) {
            log("Error getting storage directory");
            return;
        }
        // add the extension and timestamp
        // eg: 20151218-10:14:32-capture.txt
        logFilename = getTimestamp() + "-" + sessionFilename + LOG_FILE_EXTENSION;
        try {
            LOG_OUTPUT_FILE = new File(location, logFilename);
            LOG_OUTPUT_FILE.createNewFile();
            LOG_OUTPUT_STREAM = new FileOutputStream(LOG_OUTPUT_FILE, true);
            LOG_OUTPUT_WRITER = new OutputStreamWriter(LOG_OUTPUT_STREAM);
        }
        catch (FileNotFoundException ex) {
            ex.printStackTrace();
            log("File not found error.");
        }
        catch (IOException e) {
            e.printStackTrace();
            log("Log file write error.");
        }
    }

    public static void writeLogFile(String textline) {
        if (LOG_OUTPUT_WRITER != null) {
            try {
                LOG_OUTPUT_WRITER.append((new StringBuilder()).append(textline).append("\n"));
            }
            catch (IOException e) {
                //
            }
        }
    }

    public void closeLogFile() {
        // final act, no more writes possible.
        log("close log file.");
        try {
            if (LOG_OUTPUT_WRITER != null) {
                LOG_OUTPUT_WRITER.flush();
                LOG_OUTPUT_WRITER.close();
            }
            if (LOG_OUTPUT_STREAM != null) {
                LOG_OUTPUT_STREAM.flush();
                LOG_OUTPUT_STREAM.close();
            }
        }
        catch (IOException e) {
            log("Error closing log output stream.");
        }
    }

    /**************************************************************/
    /*
        audio logging
     */

    public void prepareWriteToFile() {
        // need to build the filename AND path
        File location = extDirectory;
        if (location == null) {
            log("Error getting storage directory");
            return;
        }
        // add the extension and timestamp
        // eg: 20151218-10:14:32-capture.pcm
        audioFilename = getTimestamp() + "-" + sessionFilename + AUDIO_FILE_EXTENSION;
        // file save will overwrite unless new name is used...

        try {
            AUDIO_OUTPUT_FILE = new File(location, audioFilename);
            if (!AUDIO_OUTPUT_FILE.exists()) {
                AUDIO_OUTPUT_FILE.createNewFile();
            }
            AUDIO_OUTPUT_STREAM = null;
            AUDIO_OUTPUT_STREAM = new BufferedOutputStream(new FileOutputStream(AUDIO_OUTPUT_FILE, false));
            AUDIO_DATA_STREAM = new DataOutputStream(AUDIO_OUTPUT_STREAM);
        }
        catch (FileNotFoundException ex) {
            ex.printStackTrace();
            log("File not found error.");
        }
        catch (IOException e) {
            e.printStackTrace();
            log("Audio write file error.");
        }
    }

    public static void writeBufferToLog(short[] shortBuffer, int bufferRead) {
        if (shortBuffer != null) {
            try {
                for (int i = 0; i < bufferRead; i++) {
                    AUDIO_DATA_STREAM.writeShort(shortBuffer[i]);
                    /*
                    ByteBuffer bb = ByteBuffer.allocate(Short.SIZE / Byte.SIZE);
                    bb.order(ByteOrder.BIG_ENDIAN);
                    bb.putShort(shortBuffer[i]);
                    AUDIO_OUTPUT_STREAM.write(bb.array(), 0, bb.limit());
                    */
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void closeAllFiles() {
        try {
            if (AUDIO_OUTPUT_STREAM != null) {
                AUDIO_OUTPUT_STREAM.flush();
                AUDIO_OUTPUT_STREAM.close();
                AUDIO_DATA_STREAM.close();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            log("onCancelled write stream close error.");
        }
        // then close text logging
        closeLogFile();
    }

    /**************************************************************/
    /*
        wav + header logging
     */

    public static void initAudioBuffer(short channels, int sampleRate, short bitDepth) {
        try {
            writeWavHeader(AUDIO_OUTPUT_STREAM, channels, sampleRate, bitDepth);
        }
        catch (IOException ex) {
            //
        }
    }

    public static void writeAudioBuffer(short[] bufferIn, int length) {
        // too much overhead?
        int short_index, byte_index;
        int iterations = length;
        byte [] buffer = new byte[length * 2];
        short_index = byte_index = 0;

        for( ; short_index != iterations ; ) {
            buffer[byte_index]     = (byte) (bufferIn[short_index] & 0x00FF);
            buffer[byte_index + 1] = (byte) ((bufferIn[short_index] & 0xFF00) >> 8);
            ++short_index; byte_index += 2;
        }

        try {
            AUDIO_OUTPUT_STREAM.write(buffer, 0, length);
        }
        catch (IOException ex) {
            //
        }
    }

    public static void closeWriteBuffer() {
        try {
            updateWavHeader();
        }
        catch (IOException ex) {
            //
        }
    }

    private static void writeWavHeader(OutputStream out, short channels, int sampleRate, short bitDepth) throws IOException {
        // Convert the multi-byte integers to raw bytes in little endian format as required by the spec
        byte[] littleBytes = ByteBuffer
                .allocate(14)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort(channels)
                .putInt(sampleRate)
                .putInt(sampleRate * channels * (bitDepth / 8))
                .putShort((short) (channels * (bitDepth / 8)))
                .putShort(bitDepth)
                .array();

        out.write(new byte[]{
                // RIFF header
                'R', 'I', 'F', 'F', // ChunkID
                0, 0, 0, 0, // ChunkSize (must be updated later)
                'W', 'A', 'V', 'E', // Format
                // fmt subchunk
                'f', 'm', 't', ' ', // Subchunk1ID
                16, 0, 0, 0, // Subchunk1Size
                1, 0, // AudioFormat
                littleBytes[0], littleBytes[1], // NumChannels
                littleBytes[2], littleBytes[3], littleBytes[4], littleBytes[5], // SampleRate
                littleBytes[6], littleBytes[7], littleBytes[8], littleBytes[9], // ByteRate
                littleBytes[10], littleBytes[11], // BlockAlign
                littleBytes[12], littleBytes[13], // BitsPerSample
                // data subchunk
                'd', 'a', 't', 'a', // Subchunk2ID
                0, 0, 0, 0, // Subchunk2Size (must be updated later)
        });
    }

    private static void updateWavHeader() throws IOException {
        //AUDIO_OUTPUT_FILE

        byte[] sizes = ByteBuffer
                .allocate(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                // There are probably a bunch of different/better ways to calculate
                // these two given your circumstances. Cast should be safe since if the WAV is
                // > 4 GB we've already made a terrible mistake.
                .putInt((int) (AUDIO_OUTPUT_FILE.length() - 8)) // ChunkSize
                .putInt((int) (AUDIO_OUTPUT_FILE.length() - 44)) // Subchunk2Size
                .array();

        RandomAccessFile accessWave = null;
        //noinspection CaughtExceptionImmediatelyRethrown
        try {
            accessWave = new RandomAccessFile(AUDIO_OUTPUT_FILE, "rw");
            // ChunkSize
            accessWave.seek(4);
            accessWave.write(sizes, 0, 4);

            // Subchunk2Size
            accessWave.seek(40);
            accessWave.write(sizes, 4, 4);
        }
        catch (IOException ex) {
            // Rethrow but we still close accessWave in our finally
            throw ex;
        }
        finally {
            if (accessWave != null) {
                try {
                    accessWave.close();
                }
                catch (IOException ex) {
                    //
                }
            }
        }
    }

    /**************************************************************/

    private void createDirectory() {
        // may not be writable if no permissions granted
        extDirectory = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), APP_DIRECTORY_NAME);
        if (!extDirectory.exists()) {
            extDirectory.mkdirs();
        }
    }

    private String getTimestamp() {
        // for adding to default file save name
        // eg: 20151218-10:14:32-capture
        return TIMESTAMP_FORMAT.format(new Date());
    }

    private void log(String message) {
        MainActivity.logger(message);
    }
}

