package pilfershush.cityfreqs.com.pilfershush;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pilfershush.cityfreqs.com.pilfershush.assist.AppEntry;

public class BackgroundChecker {
    private PackageManager packageManager;
    private List<ApplicationInfo> packages;
    private PackageInfo packageInfo;
    private ArrayList<AppEntry> appEntries;
    private ArrayList<AppEntry> audioAppEntries;
    private ArrayList<AppEntry> audioBeaconAppEntries;

    // needs to check for :
    // " android.Manifest.permission.* "
    // " android.permission.* "
    //
    // Manifest.permission can be requested at runtime
    //

    private static final String RECORD_PERMISSION = "RECORD_AUDIO";
    private static final String BOOT_PERMISSION = "RECEIVE_BOOT_COMPLETED";

    // alphabetical list of package names
    private static final String[] SDK_NAMES =
            {
                    "fidzup",
                    "lisnr",
                    "shopkick",
                    "signal360",
                    "silverpush",
                    "sonicnotify"
            };

    protected boolean initChecker(PackageManager packageManager) {
        // need a user updatable SDK_NAMES list insert...
        this.packageManager = packageManager;
        appEntries = new ArrayList<>();
        audioAppEntries = new ArrayList<>();
        audioBeaconAppEntries = new ArrayList<>();
        return true;
    }

    protected void destroy() {
        if (packageManager != null) packageManager = null;
        if (packages != null) packages = null;
        if (packageInfo != null) packageInfo = null;
        if (appEntries != null) appEntries = null;
        if (audioAppEntries != null) audioAppEntries = null;
        if (audioBeaconAppEntries != null) audioBeaconAppEntries = null;
    }

    /********************************************************************/
/*
 *
 */
    public static boolean isSdkName(String nameQuery) {
        for (String name : SDK_NAMES) {
            if (nameQuery.contains(name))
                return true;
        }
        return false;
    }

    /********************************************************************/

    protected int getUserRecordNumApps() {
        if (audioAppEntries != null) {
            return audioAppEntries.size();
        }
        else
            return 0;
    }

    protected void audioAppEntryLog() {
        if (appEntries.size() > 0) {
            for (AppEntry appEntry : appEntries) {
                MainActivity.entryLogger(appEntry.toString(), appEntry.checkForCaution());
            }
        }
    }

    protected boolean checkCautionedApps() {
        int counter = 0;
        if (appEntries.size() > 0) {
            for (AppEntry appEntry : appEntries) {
                if (appEntry.getCaution() == true) {
                    counter++;
                }
            }
        }
        return (counter > 0);
    }

    protected String[] getCautionedAppNames() {
        String[] appNames = new String[appEntries.size()];
        int i = 0;
        if (appEntries.size() > 0) {
            for (AppEntry appEntry : appEntries) {
                if (appEntry.getCaution() == true) {
                    appNames[i] = appEntry.getActivityName();
                    i++;
                }
            }
        }
        return appNames;
    }

    protected AppEntry getCautionedAppEntry(int appEntryIndex) {
        //TODO
        // base on name..
        return appEntries.get(appEntryIndex);
    }

    protected boolean checkAudioBeaconApps() {
        // while we check, populate audioBeaconAppEntries list for later use
        audioBeaconAppEntries = new ArrayList<>();
        if (appEntries.size() > 0) {
            for (AppEntry appEntry : appEntries) {
                if (appEntry.getServices() == true) {
                    // have services, check for audioBeacon names
                    if (checkForAudioBeaconService(appEntry.getServiceNames()) == true) {
                        // have a substring match
                        audioBeaconAppEntries.add(appEntry);
                    }
                }
            }
        }
        if (audioBeaconAppEntries.size() > 0) {
            return true;
        }
        else {
            return false;
        }
    }

    protected String[] getAudioBeaconAppNames() {
        String[] appNames = new String[audioBeaconAppEntries.size()];
        int i = 0;
        for (AppEntry appEntry : audioBeaconAppEntries) {
            appNames[i] = appEntry.getActivityName();
            i++;
        }
        return appNames;
    }

    protected AppEntry getAudioBeaconAppEntry(int appEntryIndex) {
        return audioBeaconAppEntries.get(appEntryIndex);
    }


// loop though services/receivers lists and look for substrings of interest,
// hardcoded for now, user added later?
// if find one instance return true

    private boolean checkForAudioBeaconService(String[] serviceNames) {
        for (String name: serviceNames) {
            for (int i = 0; i < SDK_NAMES.length; i++) {
                if (name.contains(SDK_NAMES[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    protected String[] getOverrideScanAppNames() {
        //
        String[] appNames = new String[appEntries.size()];
        int i = 0;
        for (AppEntry appEntry : appEntries) {
            appNames[i] = appEntry.getActivityName();
            i++;
        }
        return appNames;
    }

    protected AppEntry getOverrideScanAppEntry(int appEntryIndex) {
        return appEntries.get(appEntryIndex);
    }

    /********************************************************************/
/*
 *
 */
    private boolean isUserApp(ApplicationInfo applicationInfo) {
        int mask = ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        return (applicationInfo.flags & mask) == 0;
    }

    protected void runChecker() {
        // get list of apps
        // only lists apps with declared AudioRecord/Mic permission...
        // need to check for runonce...
        //TODO
        packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
        int idCounter = 0;
        for (ApplicationInfo applicationInfo : packages) {
            try {
                packageInfo = packageManager.getPackageInfo(applicationInfo.packageName,
                        PackageManager.GET_PERMISSIONS |
                                PackageManager.GET_SERVICES |
                                PackageManager.GET_RECEIVERS);

                // check permissions and services

                if (packageInfo.requestedPermissions != null && isUserApp(applicationInfo)) {
                    //TODO
                    // have user switch, or collect theses elsewhere in UI
                    // do not include system apps
                    AppEntry appEntry = new AppEntry(packageInfo.packageName,
                            (String) packageInfo.applicationInfo.loadLabel(packageManager));
                    // check for specific permissions
                    for (String permsString: packageInfo.requestedPermissions) {
                        if (permsString.contains(BOOT_PERMISSION)) {
                            appEntry.setBootCheck(true);
                        }
                        if (permsString.contains(RECORD_PERMISSION)) {
                            appEntry.setRecordable(true);
                        }
                    }

                    // check for services and receivers
                    if (packageInfo.services != null) {
                        appEntry.setServiceInfo(packageInfo.services);
                    }
                    else {
                        appEntry.setServices(false);
                    }

                    if (packageInfo.receivers != null) {
                        appEntry.setActivityInfo(packageInfo.receivers);
                    }
                    else {
                        appEntry.setReceivers(false);
                    }
                    //add to list
                    appEntry.setIdNum(idCounter);
                    appEntries.add(appEntry);
                    // also add to this list
                    if (appEntry.getRecordable()) {
                        audioAppEntries.add(appEntry);
                    }
                    idCounter++;
                }
            }
            catch (NameNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    protected void auditLogAsync() {
        //TODO
        // this may not work for logic reasons, and
        // cos in JB (API 16) up can only access this activity's log entries... ??
        // therefore, it can only find when we provoke the exception
        // and even then its up to specific device implementations to allow logcat

        new AsyncTask<Void, String, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                try {
                    String seekerEx = "java.lang.IllegalStateException";
                    String seekerType = "AudioRecord";


                    Process process = Runtime.getRuntime().exec("logcat -v threadtime");
                    BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));

                    String line = "";
                    while ((line = bufferedReader.readLine()) != null) {
                        if (line.contains(seekerEx)) {
                            if (line.contains(seekerType)) {
                                publishProgress(line);
                            }
                        }
                    }
                }
                catch (IOException e) {
                    //
                }
                return null;
            }

            @Override
            protected void onProgressUpdate(String... values) {
                MainActivity.entryLogger("logcat match:\n\n " + Arrays.toString(values) + "\n", false);
            }
        }.execute();
    }
}

