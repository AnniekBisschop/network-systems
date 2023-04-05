#!/bin/bash

#
# Usage: sudo ./setup.sh <name>
#

apt-get update
apt-get -y upgrade

apt-get -y install openjdk-17-jdk

cat >>/etc/dhcpcd.conf <<EOL
interface wlan0
    static ip_address=172.16.1.1/24
    nohook wpa_supplicant
EOL

apt-get install -y hostapd dnsmasq
systemctl stop hostapd
systemctl stop dnsmasq
mv /etc/dnsmasq.conf /etc/dnsmasq.conf.bak
cat >/etc/dnsmasq.conf <<EOL
interface=wlan0      # Use the require wireless interface - usually wlan0
  dhcp-range=172.16.1.2,172.16.1.100,255.255.255.0,24h
EOL

cat >/etc/hostapd/hostapd.conf <<EOL
interface=wlan0
driver=nl80211
ssid=nu-pi-${1}
hw_mode=g
channel=6
wmm_enabled=0
macaddr_acl=0
auth_algs=1
ignore_broadcast_ssid=0
wpa=2
wpa_passphrase=nedap1234
wpa_key_mgmt=WPA-PSK
wpa_pairwise=TKIP
rsn_pairwise=CCMP
EOL

/bin/sed -i 's/#DAEMON_CONF=""/DAEMON_CONF="\/etc\/hostapd\/hostapd.conf"/g' /etc/default/hostapd

systemctl unmask hostapd
systemctl enable hostapd
systemctl start hostapd

cat >/lib/systemd/system/num2.service <<EOL
[Unit]
Description=Nedap U Service
After=multi-user.agent

[Service]
Type=simple
ExecStart=/usr/bin/java -jar /home/pi/NUM2.jar
Restart=on-abort
TimeoutStopSec=30

[Install]
WantedBy=multi-user.target
EOL

systemctl daemon-reload
systemctl enable num2.service

