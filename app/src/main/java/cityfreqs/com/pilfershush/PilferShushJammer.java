package cityfreqs.com.pilfershush;

import android.content.Context;

import cityfreqs.com.pilfershush.assist.AudioSettings;
import cityfreqs.com.pilfershush.jammers.ActiveJammer;
import cityfreqs.com.pilfershush.jammers.PassiveJammer;

public class PilferShushJammer {
    private PassiveJammer passiveJammer;
    private ActiveJammer activeJammer;
    private Context context;
    private AudioSettings audioSettings;



    protected boolean initJammer(Context context, AudioSettings audioSettings) {
        // setup Jammer
        this.audioSettings = audioSettings;
        passiveJammer = new PassiveJammer(context, audioSettings);
        activeJammer = new ActiveJammer(context, audioSettings);

        return true;
    }

    /********************************************************************/

    protected boolean hasActiveJammer() {
        return activeJammer != null;
    }
    protected boolean hasPassiveJammer() {
        return passiveJammer != null;
    }

    protected void setEqOn(boolean eqOn) {
        activeJammer.setEqOn(eqOn);
    }

    protected void setJammerTypeSwitch(int jammerTypeSwitch) {
        activeJammer.setJammerTypeSwitch(jammerTypeSwitch);
    }

    protected int getJammerTypeSwitch() {
        return activeJammer.getJammerTypeSwitch();
    }

    protected void setUserCarrier(int userCarrier) {
        activeJammer.setUserCarrier(userCarrier);
    }

    protected void setDriftSpeed(int driftSpeed) {
        activeJammer.setDriftSpeed(driftSpeed);
    }

    protected void setUserLimit(int userLimit) {
        activeJammer.setUserLimit(userLimit);
    }


    protected void runActiveJammer(int activeTypeValue) {
        // background service runnable, emits white noise within n-uhf ranges
        if (activeJammer != null) {
            activeJammer.play(activeTypeValue);
        }
    }
    protected void stopActiveJammer() {
        if (activeJammer != null) {
            activeJammer.stop();
        }
    }

    protected boolean startPassiveJammer() {
        // holds mic and if needed records to zero values to dev/null
        if (passiveJammer != null) {
            return passiveJammer.startPassiveJammer();
        }
        return false;
    }
    protected boolean runPassiveJammer() {
        if (passiveJammer != null) {
            return passiveJammer.runPassiveJammer();
        }
        return false;
    }
    protected void stopPassiveJammer() {
        if (passiveJammer != null) {
            passiveJammer.stopPassiveJammer();
        }
    }

}
