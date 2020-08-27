# PilferShush_Scanner
Android near-ultra high frequency (NUHF) scanner  

Test application for researching methods of discovering use of NUHF audio beacons.
- compile API 29 (Q, 10.0), Android Studio 4.0.1 stable, AndroidX libs

   vers. 4.0.0
   - min API 18 (4.3)
   - target API 29 (10.x)
   - compiled API 29 (10.x)

   testing devices
    - EMU : Galaxy Nexus 4.3 (18) (Android Studio AVD, no GApps)
    - EMU : Nexus 4 5.1 (22) (Android Studio AVD, no GApps)
    - EMU : Nexus 5X 7.0 (24) (Android Studio AVD, GApps)
    - EMU : Galaxy Nexus Oreo (27) (Android Studio AVD, GApps)
    - EMU : Pixel 3a 10.0 (29) (Android Studio AVD, GApps)
    - LOW : s4 I9195 (deprecated) 4.3.1 (18)(CyanogenMod 10.2, F-Droid)
    - SLO : Mts 5045D (tainted) 6.0.1 (23) (CyanogenMod 13.0, GApps)
    - DEV : s5 G900I (tainted) 10.0 (29)(LineageOS 17.1, GApps)
    - PROD: s5 G900P 7.1.2 (25) (LineageOS 14.1, F-Droid)
 
 **Changes:**
 - upgrade to scanner only
 - remove jammer components, refer to PilferShush Jammer app
 - name changed to PilferShush_Scanner (not root module)
 
 **TODO**
 - update images
 - simplify activity.java and scanner.java
 - fix WriteProcessor (ext/emulated storage etc)
 - check AudioSettings and deps for use with AudioBundle
 - check AudioBundle fields, rem jammer types
 - make for FDroid
 - scanner as a foreground service
 - analysis view for spectrogram wav file?
 - rebuild scanner to allow different methods of scanning (more than goertzel)

# 2020 Kaputnik Go


Screenshots

- Update UI
<img src="https://github.com/kaputnikGo/PilferShush_prod/blob/master/images/updateUI-jammers.jpg" height="612px" />

- App open
<img src="https://github.com/kaputnikGo/PilferShush_prod/blob/master/images/app-open.jpg" height="612px" />

- Detailed View
<img src="https://github.com/kaputnikGo/PilferShush_prod/blob/master/images/detailed-view.jpg" height="612px" />

- Capture live
<img src="https://github.com/kaputnikGo/PilferShush_prod/blob/master/images/capture-live.jpg" height="612px" />

- Post capture
<img src="https://github.com/kaputnikGo/PilferShush_prod/blob/master/images/post_capture.jpg" height="612px" />

- Option menu
<img src="https://github.com/kaputnikGo/PilferShush_prod/blob/master/images/option-menu.jpg" height="612px" />
