package cityfreqs.com.pilfershush;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import java.util.ArrayList;
import java.util.List;

import cityfreqs.com.pilfershush.assist.AppEntry;
import cityfreqs.com.pilfershush.assist.AudioSettings;

class BackgroundChecker {
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

    boolean initChecker(PackageManager packageManager) {
        // need a user updatable SDK_NAMES list insert...
        this.packageManager = packageManager;
        appEntries = new ArrayList<>();
        audioAppEntries = new ArrayList<>();
        audioBeaconAppEntries = new ArrayList<>();
        return true;
    }

    void destroy() {
        if (packageManager != null) packageManager = null;
        if (packages != null) packages = null;
        if (packageInfo != null) packageInfo = null;
        if (appEntries != null) appEntries = null;
        if (audioAppEntries != null) audioAppEntries = null;
        if (audioBeaconAppEntries != null) audioBeaconAppEntries = null;
    }

    /********************************************************************/

    int getUserRecordNumApps() {
        if (audioAppEntries != null) {
            return audioAppEntries.size();
        }
        else
            return 0;
    }

    void audioAppEntryLog() {
        if (appEntries.size() > 0) {
            for (AppEntry appEntry : appEntries) {
                MainActivity.entryLogger(appEntry.entryPrint(), appEntry.checkForCaution());
            }
        }
    }

    boolean checkAudioBeaconApps() {
        // while we check, populate audioBeaconAppEntries list for later use
        audioBeaconAppEntries = new ArrayList<>();
        if (appEntries.size() > 0) {
            for (AppEntry appEntry : appEntries) {
                if (appEntry.getServices()) {
                    // have services, check for audioBeacon names
                    if (checkForAudioBeaconService(appEntry.getServiceNames())) {
                        // have a substring match
                        appEntry.setAudioBeacon(true);
                        audioBeaconAppEntries.add(appEntry);
                    }
                    else {
                        appEntry.setAudioBeacon(false);
                    }
                }
            }
        }
        return (audioBeaconAppEntries.size() > 0);
    }

    String[] getAudioBeaconAppNames() {
        String[] appNames = new String[audioBeaconAppEntries.size()];
        int i = 0;
        for (AppEntry appEntry : audioBeaconAppEntries) {
            appNames[i] = appEntry.getActivityName();
            i++;
        }
        return appNames;
    }

    AppEntry getAudioBeaconAppEntry(int appEntryIndex) {
        return audioBeaconAppEntries.get(appEntryIndex);
    }

    String displayAudioSdkNames() {
        // return a string of names + \n
        if (AudioSettings.SDK_NAMES != null) {
            StringBuilder sb = new StringBuilder();
            for (String name : AudioSettings.SDK_NAMES) {
                sb.append(name).append("\n");
            }
            return sb.toString();
        }
        return "error: none found \n";
    }


// loop though services/receivers lists and look for substrings of interest,
// hardcoded for now, user added later?
// if find one instance return true
    private boolean checkForAudioBeaconService(String[] serviceNames) {
        for (String name: serviceNames) {
            for (String sdkName : AudioSettings.SDK_NAMES) {
                if (name.contains(sdkName)){
                    return true;
                }
            }
        }
        return false;
    }

    String[] getOverrideScanAppNames() {
        //
        String[] appNames = new String[appEntries.size()];
        int i = 0;
        for (AppEntry appEntry : appEntries) {
            appNames[i] = appEntry.getActivityName();
            i++;
        }
        return appNames;
    }

    AppEntry getOverrideScanAppEntry(int appEntryIndex) {
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

    void runChecker() {
        // get list of apps
        // only lists apps with declared AudioRecord/Mic permission...
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
}

