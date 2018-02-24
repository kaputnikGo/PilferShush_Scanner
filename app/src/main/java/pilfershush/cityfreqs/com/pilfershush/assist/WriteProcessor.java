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
    // pcm savefile for raw import into Audacity as 48 kHz, signed 16 bit, big-endian, mono

    // wav has a little-endian

    // laborious process to export files from Download(s) folder to PC, need Music folder or similar
    // with proper file read access for external - possibly just a samsung pita

    private AudioSettings audioSettings;

    private File extDirectory;

    private String audioFilename;
    private String logFilename;
    private String sessionFilename;
    private static boolean writeWav;

    public static File AUDIO_OUTPUT_FILE;
    public static FileOutputStream AUDIO_WAV_STREAM;

    public static BufferedOutputStream AUDIO_OUTPUT_STREAM;
    public static DataOutputStream AUDIO_RAW_STREAM;

    public static File LOG_OUTPUT_FILE;
    public static FileOutputStream LOG_OUTPUT_STREAM;
    public static OutputStreamWriter LOG_OUTPUT_WRITER;

    private static final String APP_DIRECTORY_NAME = "PilferShush";
    private static final String AUDIO_FILE_EXTENSION_RAW = ".pcm";
    private static final String AUDIO_FILE_EXTENSION_WAV = ".wav";
    private static final String LOG_FILE_EXTENSION = ".txt";

    private static final long MINIMUM_STORAGE_SIZE_BYTES = 2048; // approx 2 mins pcm audio

    // diff OS can pattern the date in the following -
    // underscore: 20180122-12_37_29-capture
    // nospace: 20180122-123729-capture
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd-HH:mm:ss", Locale.ENGLISH);

    public WriteProcessor(String sessionName, AudioSettings audioSettings, boolean writeWav) {
        setSessionName(sessionName);
        this.audioSettings = audioSettings;
        this.writeWav = writeWav;

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

    public long getStorageSize() {
        return calculateStorageSize();
    }

    public long getFreeStorageSpace() {
        return calculateFreeStorageSpace();
    }

    public void deleteEmptyLogFiles() {
        // storage could have only 0kb log files
        deleteZeroSizeLogFiles();
    }

    public void deleteStorageFiles() {
        if (calculateStorageSize() > 0) {
            deleteAllStorageFiles();
        }
    }

    public boolean cautionFreeSpace() {
        if (calculateFreeStorageSpace() <= MINIMUM_STORAGE_SIZE_BYTES) {
            return true;
        }
        return false;
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
        audio logging init
     */

    public void prepareWriteAudioFile() {
        // need to build the filename AND path
        File location = extDirectory;
        if (location == null) {
            log("Error getting storage directory");
            return;
        }
        // add the extension and timestamp
        // eg: 20151218-10:14:32-capture.pcm(.wav)
        if (writeWav) {
            audioFilename = getTimestamp() + "-" + sessionFilename + AUDIO_FILE_EXTENSION_WAV;
        }
        else {
            audioFilename = getTimestamp() + "-" + sessionFilename + AUDIO_FILE_EXTENSION_RAW;
        }

        // file save will overwrite unless new name is used...
        try {
            AUDIO_OUTPUT_FILE = new File(location, audioFilename);
            if (!AUDIO_OUTPUT_FILE.exists()) {
                AUDIO_OUTPUT_FILE.createNewFile();
            }

            if (writeWav) {
                AUDIO_WAV_STREAM = new FileOutputStream(AUDIO_OUTPUT_FILE);
                writeWavHeader(AUDIO_WAV_STREAM,
                        (short) audioSettings.getChannelCount(),
                        audioSettings.getSampleRate(),
                        (short) audioSettings.getBitDepth());
            }
            else {
                AUDIO_OUTPUT_STREAM = null;
                AUDIO_OUTPUT_STREAM = new BufferedOutputStream(new FileOutputStream(AUDIO_OUTPUT_FILE, false));
                AUDIO_RAW_STREAM = new DataOutputStream(AUDIO_OUTPUT_STREAM);
            }
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


    /**************************************************************/
    /*
        audio logging writes
     */
    /********************************************************************/

    public static void writeAudioFile(final short[] shortBuffer, final int bufferRead) {

            if (shortBuffer != null) {
                try {
                    if (writeWav) {
                        writeWavBuffer(shortBuffer, bufferRead);
                    }
                    else {
                        for (int i = 0; i < bufferRead; i++) {
                            AUDIO_RAW_STREAM.writeShort(shortBuffer[i]);
                        }
                    }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

    }

    public void closeWriteFile() {
        try {
            if (writeWav) {
                if (updateWavHeader()) {
                    // wait for it...
                    if (AUDIO_WAV_STREAM != null) {
                        AUDIO_WAV_STREAM.flush();
                        AUDIO_WAV_STREAM.close();
                    }
                }
            }
            if (AUDIO_OUTPUT_STREAM != null) {
                AUDIO_OUTPUT_STREAM.flush();
                AUDIO_OUTPUT_STREAM.close();
                AUDIO_RAW_STREAM.close();

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
    //TODO
    /*
        audio logging wav header, buffer and close
        glitchy...

        from: https://gist.github.com/kmark/d8b1b01fb0d2febf5770
    */
    private static void writeWavHeader(OutputStream out, short channels, int sampleRate, short bitDepth) {
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

        try {
            out.write(new byte[]{
                    // RIFF header
                    'R', 'I', 'F', 'F', // ChunkID
                    0, 0, 0, 0, // ChunkSize (must be updated later)
                    'W', 'A', 'V', 'E', // Format
                    // fmt subchunk
                    'f', 'm', 't', ' ', // Subchunk1ID
                    16, 0, 0, 0, // Subchunk1Size - PCM
                    1, 0, // AudioFormat - PCM
                    littleBytes[0], littleBytes[1], // NumChannels - MONO
                    littleBytes[2], littleBytes[3], littleBytes[4], littleBytes[5], // SampleRate
                    littleBytes[6], littleBytes[7], littleBytes[8], littleBytes[9], // ByteRate
                    littleBytes[10], littleBytes[11], // BlockAlign
                    littleBytes[12], littleBytes[13], // BitsPerSample
                    // data subchunk
                    'd', 'a', 't', 'a', // Subchunk2ID
                    0, 0, 0, 0, // Subchunk2Size (must be updated later)
            });
        }
        catch (IOException ex) {
            //
            log("Error setting up wav file header write.");
        }
    }

    private static void writeWavBuffer(short[] bufferIn, int length) {
        // too much overhead?
        int short_index, byte_index;
        int iterations = length;
        byte [] buffer = new byte[length * 2];
        short_index = byte_index = 0;

        for( ; short_index != iterations ; ) {
            buffer[byte_index] = (byte) (bufferIn[short_index] & 0x00FF);
            buffer[byte_index + 1] = (byte) ((bufferIn[short_index] & 0xFF00) >> 8);
            ++short_index;
            byte_index += 2;
        }

        try {
            AUDIO_WAV_STREAM.write(buffer, 0, length);
        }
        catch (IOException ex) {
            //
            log("Error writing wav audio buffer.");
        }
    }

    private static boolean updateWavHeader() throws IOException {
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
            log("Error writing updated wav header.");
            throw ex;
        }
        finally {
            if (accessWave != null) {
                try {
                    accessWave.close();
                    return true;
                }
                catch (IOException ex) {
                    //
                    log("Error closing wav file.");
                    return false;
                }
            }
        }
        return false;
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

    private void deleteZeroSizeLogFiles() {
        // assume MainActivity has cautioned first.
        if (!extDirectory.exists()) {
            log("No storage folder found.");
            return;
        }

        log("Deleting empty files...");
        int counter = 0;
        for (File file : extDirectory.listFiles()) {
            if (file.isFile()) {
                if (file.length() == 0) {
                    file.delete();
                    counter++;
                }
            }
        }
        log("Deleted " + counter + " empty file(s).");

    }

    private void deleteAllStorageFiles() {
        // assume MainActivity has cautioned first.
        if (!extDirectory.exists()) {
            log("No storage folder found.");
            return;
        }
        String[] filesDelete = extDirectory.list();
        log("Deleting " + filesDelete.length + " files from storage...");
        for (int i = 0; i < filesDelete.length; i++) {
            new File(extDirectory, filesDelete[i]).delete();
        }
        log("Storage folder now empty.");
    }

    private long calculateStorageSize() {
        // returns long size in bytes
        if (!extDirectory.exists()) {
            log("No storage folder found.");
            return 0;
        }
        long length = 0;
        for (File file : extDirectory.listFiles()) {
            if (file.isFile()) {
                length += file.length();
            }
        }
        log("Storage size: " + (int)length);
        return length;
    }

    private long calculateFreeStorageSpace() {
        // returns long size in bytes
        if (!extDirectory.exists()) {
            log("No storage folder found.");
            return 0;
        }
        // getFreeSpace == unallocated
        return extDirectory.getUsableSpace();
    }

    private String getTimestamp() {
        // for adding to default file save name
        // eg: 20151218-10:14:32-capture
        return TIMESTAMP_FORMAT.format(new Date());
    }

    private static void log(String message) {
        MainActivity.logger(message);
    }
}

