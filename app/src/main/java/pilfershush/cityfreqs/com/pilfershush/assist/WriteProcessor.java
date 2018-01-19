package pilfershush.cityfreqs.com.pilfershush.assist;


import android.content.Context;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import pilfershush.cityfreqs.com.pilfershush.MainActivity;

public class WriteProcessor {
    //
    //private int audioLen = 0;
    //private  static final int HEADER_LENGTH = 44; //byte
    // until we bother with wav headers, this is raw format bytebuffer saves


    private File extDirectory;

    private String audioFilename;
    private String logFilename;
    private String sessionFilename; // base filename
    public static File AUDIO_OUTPUT_FILE;
    public static File LOG_OUTPUT_FILE;
    public static BufferedOutputStream AUDIO_OUTPUT_STREAM;
    public static FileOutputStream LOG_OUTPUT_STREAM;
    public static OutputStreamWriter LOG_OUTPUT_WRITER;

    private static final String APP_DIRECTORY_NAME = "PilferShush";
    private static final String AUDIO_FILE_EXTENSION = ".pcm";
    private static final String LOG_FILE_EXTENSION = ".txt";
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd-HH:mm:ss", Locale.ENGLISH);

    public WriteProcessor(Context context, String sessionName) {
        if (sessionName == null || sessionName == "") {
            sessionFilename = "capture";
        }
        else {
            sessionFilename = sessionName;
        }

        log("Setting up storage: Download(s)/PilferShush/");
        // checks for read/write state
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            createDirectory();
            log("ext dir: " + extDirectory.toString());
        }
    }

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
            AUDIO_OUTPUT_STREAM = new BufferedOutputStream(new FileOutputStream(AUDIO_OUTPUT_FILE, false)); // append == false
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
        //LOG_OUTPUT_FILE = null;
        try {
            LOG_OUTPUT_FILE = new File(location, logFilename);
            //if (!LOG_OUTPUT_FILE.exists()) {
                LOG_OUTPUT_FILE.createNewFile();
            //}
            //LOG_OUTPUT_STREAM = null;
            //LOG_OUTPUT_WRITER = null;
            LOG_OUTPUT_STREAM = new FileOutputStream(LOG_OUTPUT_FILE, true); // append...
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

