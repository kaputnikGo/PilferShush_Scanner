# PilferShush_Scanner
Android near-ultra high frequency (NUHF) scanner  

Test application for researching methods of discovering use of NUHF audio beacons.
- compile API 29 (Q, 10.0), Android Studio 4.0.1 stable, AndroidX libs

**Notes**
- getExternalStorageDirectory method is deprecated in API 29 use getExternalFilesDir(String type)
- adding settings for use with testing HTML5 NUHF synth transmitter emulator
- problem of detecting freqs close to actual transmitted freq ( ie 18500 -> 18550 )

   vers. 4.0.1
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
    
 **TODO**
 - fix permissions check functions
 - make for FDroid
 - scanner as a foreground service
 - analysis view for spectrogram wav file?
 - rebuild scanner to allow different methods of scanning (more than goertzel)
 
 **Changes:**
 - malformed wav header, bit depth required
 - cleanup audioBundle
 - fix write to storage for API 29
 - combine MAIN and DEBUG views
 - move scanner.java to mainactivity, prep for proper
 - update app images
 - add settings for testing
 - magnitude scan value as double



# 2020 Kaputnik Go

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Screenshots

- App open
<img src="https://github.com/kaputnikGo/PilferShush_prod/blob/master/images/app-open.jpg" height="612px" />

- Capture live
<img src="https://github.com/kaputnikGo/PilferShush_prod/blob/master/images/capture-live.jpg" height="612px" />

- Post capture
<img src="https://github.com/kaputnikGo/PilferShush_prod/blob/master/images/post_capture.jpg" height="612px" />

- Option menu
<img src="https://github.com/kaputnikGo/PilferShush_prod/blob/master/images/option-menu.jpg" height="612px" />
