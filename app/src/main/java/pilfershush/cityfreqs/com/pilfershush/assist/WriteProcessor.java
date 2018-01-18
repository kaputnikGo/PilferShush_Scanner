package pilfershush.cityfreqs.com.pilfershush.assist;


import android.content.Context;
import android.os.Environment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import pilfershush.cityfreqs.com.pilfershush.MainActivity;

public class WriteProcessor {
    //
    //private int audioLen = 0;
    //private  static final int HEADER_LENGTH = 44; //byte
    // until we bother with wav headers, this is raw format bytebuffer saves

    private File intDirectory;
    private boolean ext_capable = false;
    private File extDirectory;

    private String filename;
    private String sessionFilename; // base filename
    private File outputFile;

    private static final String LOCAL_DIRECTORY = "PilferShush";
    private static final String FILE_EXTENSION = ".raw";
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMdd-HH:mm:ss", Locale.ENGLISH);

    public WriteProcessor(Context context, String filename) {
        if (filename == null || filename == "") {
            sessionFilename = "capture";
        }
        else {
            sessionFilename = filename;
        }

        // grab app local internal storage directory
        intDirectory = context.getFilesDir();

        // prepare the default file save location here
        if (isExternalStorageWritable()) {
            if (createDirectory()) {
                log("Ext storage ready.");
            }
            else {
                log("Ext storage directory not created.");
                ext_capable = false;
                // permissions fail?, fallback to internal
            }
        }
        else {
            // prepare the internal
            log("Int storage only.");
        }
    }

    public void writeToFile(byte[] audioArray) {
        // need to build the filename AND path
        File location = getStorageFile();
        if (location == null) {
            log("Error getting storage directory");
            return;
        }
        // add the extension and timestamp
        // eg: 20151218-10:14:32-capture.raw
        filename = getTimestamp() + "-" + sessionFilename + FILE_EXTENSION;

        // file save will overwrite unless new name is used...
        try {
            outputFile = new File(location, filename);
            if (!outputFile.exists()) {
                outputFile.createNewFile();
            }
            OutputStream out = null;
            out = new BufferedOutputStream(new FileOutputStream(outputFile, false)); // append == false
            out.write(audioArray);
            if (out != null) {
                out.close();
            }
        }
        catch (FileNotFoundException ex) {
            ex.printStackTrace();
            log("File not found error.");
        }
        catch (IOException e) {
            e.printStackTrace();
            log("File save IO error.");
        }
    }

    /**************************************************************/
    private boolean isExternalStorageWritable() {
        // is available for read and write
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            log("Ext storage is readable.");
            ext_capable = true;
            return ext_capable;
        }
        else {
            log("Ext storage not readable.");
            return false;
        }
    }

    private boolean createDirectory() {
        // may not be writable if no permissions granted
        if (ext_capable) {
            extDirectory = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MUSIC), LOCAL_DIRECTORY);

            if (extDirectory != null) {
                if (extDirectory.mkdir()) {
                    log("Make ext dir.");
                    return true;
                }
                else {
                    log("mkdir failed");
                }
            }
            else {
                log("Ext directory is null.");
                return false;
            }
        }
        else {
            log("Ext storage not available.");
        }
        return false;
    }

    private File getStorageFile() {
        // get ext if possible
        if (extDirectory != null) {
            return extDirectory;
        }
        else if (intDirectory != null) {
            return intDirectory;
        }
        else {
            log("no storage directories found.");
            return null;
        }
    }

    /**************************************************************/

    private String getTimestamp() {
        // for adding to default file save name
        // eg: 20151218-10:14:32-capture
        return TIMESTAMP_FORMAT.format(new Date());
    }

    private void log(String message) {
        MainActivity.logger(message);
    }
}

