## Initial configuration

### On your laptop

Clone the [nu-module-2](https://github.com/nedap/nu-module-2) repository:

`git clone git@github.com:nedap/nu-module-2.git`

`cd nu-module-2`

(This guide assumes your current working directory is the root of that Git workspace.)

Put the SD-card into the Pi, then connect the power supply, keyboard, **network cable**, and monitor.

### On the Pi

If you boot your Raspberry Pi for the first time you can choose an operating system to install. This guide assumes
you install "Raspberry Pi OS Lite", but it should also work with other variants of Raspberry Pi OS. If you don't have 
the "Raspberry Pi OS Lite" option, your wired network is probably not connected properly.

Log in with the default username/password:

* username: `pi`
* password: `raspberry`

Or open a terminal if you are using the desktop version.

(This guide assumes your working directory is `~`, a.k.a. `/home/pi`.)

Use `sudo raspi-config` to change the following settings:

* 1 System Options > S4 Hostname > `nu-pi-{name}`<br>(For `{name}`, insert your name, e.g. `robin`, to personalize the Pi's hostname.)
* 3 Interfacing Options > P2 SSH > `Yes`
* 5 Localization Options >
    * I2 Change Timezone > `Europe` > `Amsterdam`
    * I3 Change Keyboard Layout > `Generic 104-key PC` > (`Other` > `English (US)`) >) `English (US)` > `The default for the keyboard layout` > `No compose key`
    * I4 Change WLAN Country > `Netherlands`

Close with `<Finish>`. Choose `<Yes>` to reboot.

Restart the Pi:

`sudo reboot`

### On your laptop

Copy the setup script to the Pi:

`scp pi_setup/setup.sh pi@10.10.10.10:/home/pi`

(When asked, use the password `raspberry` again. The IP-address `10.10.10.10` should be replaced by the Pi's actual 
wired IP-address, which you can find with the command `ifconfig` on the Pi.)

If you get the error `pi@10.10.10.10: Permission denied (publickey,password).` without the option to enter your 
password, you should add the following two lines to the top of the `.ssh/config` file on your laptop. Replace the 
`10.10.10.10` IP-address, but keep `172.16.1.1` in there. 

```
Host 10.10.10.10 172.16.1.1
  PasswordAuthentication yes
```

### On the Pi

If you copied the setup.sh file using a Windows system, you may need to execute the following two steps:

* Change the line endings from Windows style (CRLF) to Unix style (LF). This can be done by a text editor or with the 
  command `sed -i 's/\r$//' setup.sh`.
* Mark the file as executable. This can be done with the command `chmod +x setup.sh`.

Check that the network connection is working, then execute the setup script:

`sudo ./setup.sh {name}`

(For `{name}`, insert your name, e.g. `robin`, to personalize the Pi's Wi-Fi SSID.)

This script will install all the required software and will configure the Pi to act as a Wi-Fi accesspoint.

Once again, restart the Pi:

`sudo reboot`

### On your laptop

Connect to the Wi-Fi network `nu-pi-{name}`. The default password is `nedap1234`. If you don't see the accesspoint you
may need to disable and re-enable the Wi-Fi on your laptop.

Build and deploy the sample project `NUM2.jar`:

`./gradlew deploy`

### On the Pi

Check that the `NUM2.jar` was copied:

`ls -l`

Check that the example project is running as service `num2`:

`systemctl status num2`



## Linux service

To start and stop our service when the Pi starts, we need a service wrapper. We've created a service wrapper that start your Java application: `num2.service`. The service configuration can be found in:

`/lib/systemd/system/num2.service`

The service is automatically started at boot. You can manually start/stop the service using:

`sudo systemctl start num2.service`

`sudo systemctl stop num2.service`

If you modify the service configuration you have to reload the configuration:

`sudo systemctl daemon-reload`
