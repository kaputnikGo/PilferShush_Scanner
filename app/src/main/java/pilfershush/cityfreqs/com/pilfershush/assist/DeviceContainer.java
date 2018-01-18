package pilfershush.cityfreqs.com.pilfershush.assist;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;

public class DeviceContainer {
    // container for usb device info and
    // possible functions
    // ie: vendor-id="1234" product-id="5678" class="255" subclass="66" protocol="1"
    private UsbDevice device;
    private UsbInterface usbInterface;
    //TODO
    // need to access UsbInterface to determine type audio and Playback/Capture
    // need to check for Endpoint

    public DeviceContainer() {
        // defaults of no values
        device = null;
    }

    public DeviceContainer(UsbDevice device) {
        this.device = device;
        usbInterface = device.getInterface(0);
    }

    public void setUsbDevice(UsbDevice device) {
        this.device = device;
        usbInterface = device.getInterface(0);
    }

    public UsbDevice getDevice() {
        return device;
    }

    public UsbInterface getInterface() {
        return usbInterface;
    }

    public boolean hasDevice() {
        return device != null;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("Device name: ").append(getDeviceName())
                .append(", Interface count: ").append(getInterfaceCount())
                .append(", VendorId: ").append(getVendorId())
                .append(", DeviceId: ").append(getDeviceId())
                .append(", ProductId: ").append(getProductId())
                .append(", USB class: ").append(getUsbClass())
                .append(", USB subclass: ").append(getUsbSubClass())
                .append(", USB protocol: ").append(getUsbProtocol()).toString();
    }

    /*
    * specific getters
    */
    public String getDeviceName() {
        return device.getDeviceName();
    }
    public int getInterfaceCount() {
        return device.getInterfaceCount();
    }
    public int getVendorId() {
        return device.getVendorId();
    }
    public int getDeviceId() {
        return device.getDeviceId();
    }
    public int getProductId() {
        return device.getProductId();
    }
    public int getUsbClass() {
        return device.getDeviceClass();
    }
    public int getUsbSubClass() {
        return device.getDeviceSubclass();
    }
    public int getUsbProtocol() {
        return device.getDeviceProtocol();
    }

	/*
	 * NOTES REGARDING USB AUDIO DONGLE ATTACHED TO TEST DEVICE
	 * Sammy 5 says:
	 *
	 * 	Advanced Linux Sound Architecture Driver Version 1.0.25.
	 *
	 *
	 * usb audio dongle reports:
	 *
	 * device-name="/dev/bus/usb/001/002"
	 * interface-count="6" <- some in, some out....
	 * vendor-id="6975" <- GeneralPlus Technology Inc
	 * device-id="1002" <- not reliable, many diff devices report this id
	 * product-id="8199"
	 *
	 * usb-class="0" <= this needs to be 1 (USB_CLASS_AUDIO)
	 * 0 = USB_CLASS_PER_INTERFACE (class is determined on per-interface basis)
	 * need to check via UsbInterface(s)
	 *
	 * usb-subclass="0"
	 * usb-protocol="0"
	 *
	 *
	 *
	 *
	 *
	 *
	 * 	proc/asound/card1/usbid  :
	 * 		1b3f:2007
	 *
	 *
	 *
	 *
	 *  proc/asound/card1/stream0  :
	 *  GeneralPlus USB Audio Device at usb-xhci-hcd-1, full speed : USB Audio
	 *
	 *  Playback:
	 *  	Status : Running
	 *  		Interface = 1
	 *  		Altset = 1
	 *  		URBs = 2 [5 6 ]
	 *  		Packet Size = 192
	 *  		Momentary freq = 44100 Hz (0x2c.199a)
	 *  	Interface 1
	 *  		Altset 1
	 *  		Format: S16_LE
	 *  		Channels: 2
	 *  		Endpoint: 5 OUT (NONE)
	 *  		Rates: 44100, 48000
	 *
	 *  Capture
	 *  	Status: Stop
	 *  		Interface 2
	 *  		Altset 1
	 *  		Format: S16_LE
	 *  		Channels: 1
	 *  		Endpoint: 6 IN (NONE)
	 *  		Rates: 44100, 48000
	 *
	 *
	 *
	 *
	 *
	 *  proc/asound/card1/usbmixer  :
	 *
	 *  USB Mixer: usb_id=0x1b3f2007, ctrlif=0, ctlerr=0
	 *  Card: GeneralPlus USB Audio Device at usb-xhci-hcd-1, full speed : USB Audio
	 *
	 *  	Unit 5
	 *  		Control: name="Auto Gain Control", index=0
	 *  		Info: id=5, control=7, cmask=0x0, channels=1, type="BOOLEAN"
	 *  		Volume: min=0, max=1, dBmin=0, dBmax=0
	 *  	Unit 5
	 *  		Control: name="Mic Capture Volume", index=0
	 *  		Info: id=5, control=2, cmask=0x0, channels=1, type="S16"
	 *  		Volume: min=0, max=1, dBmin=0, dBmax=0
	 *  	Unit 5
	 *  		Control: name="Mic Capture Switch", index=0
	 *  		Info: id=5, control=1, cmask=0x0, channels=1, type="INV_BOOLEAN"
	 *  		Volume: min=0, max=1, dBmin=0, dBmax=0
	 *  	Unit 6
	 *  		Control: name="Speaker Playback Volume", index=0
	 *  		Info: id=6, control=2, cmask=0x3, channels=2, type="S16"
	 *  		Volume: min=0, max=1, dBmin=0, dBmax=0
	 *  	Unit 6
	 *  		Control: name="Speaker Playback Switch", index=0
	 *  		Info: id=6, control=1, cmask=0x0, channels=1, type="INV_BOOLEAN"
	 *  		Volume: min=0, max=1, dBmin=0, dBmax=0
	 *	  	Unit 7
	 *  		Control: name="Mic Playback Volume", index=0
	 *  		Info: id=7, control=2, cmask=0x0, channels=2, type="S16"
	 *  		Volume: min=0, max=1, dBmin=0, dBmax=0
	 *  	Unit 7
	 *  		Control: name="Mic Playback Switch", index=0
	 *  		Info: id=7, control=1, cmask=0x0, channels=1, type="INV_BOOLEAN"
	 *  		Volume: min=0, max=1, dBmin=0, dBmax=0
	 *
	 *
	 *
	 *
	 *
	 *  proc/asound/card1/pcm0c/info  :
	 *  	card: 1
	 *  	device: 0
	 *  	subdevice: 0
	 *  	stream: CAPTURE
	 *  	id: USB Audio
	 *  	name: USB Audio
	 *  	subname: subdevice #0
	 *  	class: 0
	 *  	subclass: 0
	 *  	subdevices_count: 1
	 *  	subdevices_avail: 1
	 *
	 *  hw_params ::
	 *  	closed
	 *
	 *  sw_params ::
	 *  	closed
	 *
	 *
	 *   proc/asound/card1/pcm0p/info  :
	 *  	card: 1
	 *  	device: 0
	 *  	subdevice: 0
	 *  	stream: PLAYBACK
	 *  	id: USB Audio
	 *  	name: USB Audio
	 *  	subname: subdevice #0
	 *  	class: 0
	 *  	subclass: 0
	 *  	subdevices_count: 1
	 *  	subdevices_avail: 0
	 *
	 *  hw_params ::
	 *  	access: RW_INTERLEAVED
	 *  	format: S16_LE
	 *  	subformat: STD
	 *  	channels: 2
	 *  	rate: 44100 (44100/1)
	 *  	period_size: 448
	 *  	buffer_size: 896
	 *
	 *  sw_params ::
	 *  	tstamp_mode: ENABLE
	 *  	period_step: 1
	 *  	avail_min: 1
	 *  	start_threshold: 448
	 *  	stop_threshold: 896
	 *  	silence_threshold: 0
	 *  	silence_size: 0
	 *  	boundary: 939524096
	 *
	 */
}
