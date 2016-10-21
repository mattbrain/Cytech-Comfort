# Cytech-Comfort

UPNP / SmartThings  support for the Cytech Comfort system

## Introduction

### Instructions and software below are in early stages of development. YMMV.


This repository contains the necessary files and installation notes to expose elements of the Cytech Comfort alarm system via UPNP and onward to Samsung SmartThings

**Little testing has been performed so far.**

#### If you proceed with using this software, keep in mind the following:

1.	It may not work, or it may work but be unstable, just because it works for me doesn't mean it will work for you. I will endeavour to support anyone who is willing to try it, but remember I have a day job and this is something I have built because it's kinda fun.
2.	The current implementation requires your Comfort sign-in code to be in clear text in the configuration file. I will encrypt this at some point and resolve this. DO NOT send your configuration file to anyone without first removing your sign in code from this file.
3.	Functionality is currently limited to Zones and Outputs and UPNP is tailored to support Samsung SmartThings - I am planning on adding WeMo emulation for native support by many other client devices (such as amazon echo etc) but unless you have Samsung SmartThings you will either not get a fat lot from this or will need to develop your own client handlers.
4.	Do not reply on this interface for critical applications - the Comfort alarm is an extremely robust system. It has taken many years of development to get to this point. Do not think that this solution can replace critical functions you have or would like to script in your Comfort system. I would say it's fine for automating those things that are nice to have, but don't rely on it to turn on your sprinklers in the event of a fire!
5.	It is under constant development. Configuration files and other elements may change at any point, including the removal or replacement of services it currently exposes. Do not expect any current configuration you have setup to work in future versions and please read any release notes before updating to the current version.

#### Requirements:

1.	Cytech Comfort system
2.	UCM with Ethernet (any variant should work, I have tested with UCM/Eth and UCM/Eth3).
3.	Raspberry Pi Model B (or similar - instructions below are for the original Model B. I have tested on Model B and B2)
4.	Static IP addresses on both UCM and Raspberry Pi
5.	Samsung SmartThings (I have tested on V2 hub, it *may* work on v1)

### Installation

#### Setup a clean RPi image

Download Raspbian Jessie Lite image from https://www.raspberrypi.org/downloads/

Flash SD card following instructions  https://www.raspberrypi.org/documentation/installation/installing-images/README.md

Insert SD Card into RPi and connect to it using SSH

*	On Windows download PuTTY
	username: 	pi
	host:		raspberry
	password:	raspberry

*	On Mac / Linux use
	SSH pi@raspberry
	
	when prompted, enter the password raspberry
	

Configure RPi
```bash
$ sudo raspi-config
	-> Expand filesystem
	-> Change User Password
		* Set a new password
```
Finish and reboot when prompted

Reconnect and do the following
```bash
$ sudo apt-get update
$ sudo apt-get upgrade
$ sudo raspi-config
	-> Boot Options
		-> B1 - Console
			* set to console, not desktop or scratch
-> Advanced-Options
	-> Hostname
		* Set hostname to something appropriate
	-> SSH
		* Enable remote access
	-> Memory Split
		* Set GPU memory to 16
```
Finish and reboot when prompted		


Reconnect and do the following

#### Install git
`$ sudo apt-get install git`

#### Install Node and dependancies

** Check for latest version on nodejs.org website, instructions below are for v6.9.1 **
```bash
$ wget https://nodejs.org/dist/v6.9.1/node-v6.9.1-linux-armv6l.tar.xz
$ tar -xvf node-v6.9.1-linux-armv6l.tar.xz 
$ cd node-v6.9.1-linux-armv6l/
$ sudo cp -R * /usr/local
```

Check it is installed correctly
```bash
$ Node -v

v4.6.1
```


Install Dependancies

This installation will add node modules locally (i.e. in a subdirectory of the current directory). At a minimum create a directory to run the code from within the home folder of the user you are logged in as:

```bash
mkdir ~\alarm
cd ~\alarm	
```

```bash
$ npm install net
$ npm install peer-upnp
$ npm install fs
$ npm install path
$ npm install util
$ npm install properties-parser
$ npm install http
$ npm install express
$ npm install pm2 -g
```


#### Update Node Modules

** I have had to make some changes to the UPNP modules in order to support SmartThings, these need to be applied over the existing modules **

Navigate to the ~/alarm/node\_modules/peer-ssdp/lib directory, rename peer-ssdp.js to peer-ssdp.js.old and upload the peer-ssdp.js found in this repository in its place

Navigate to the ~/alarm/node\_modules/peer-upnp/lib directory, rename peer-upnp.js to peer-upnp.js.old and upload the peer-upnp.js found in this repository in its place

 

#### Install comfort UPNP bridge 

Copy alarm.js, alarm-client.js and alarm.config from this repository into the directory created earlier `(~/alarm)`

Copy your Comfigurator file used to configure the Comfort system into the same directory.  * Please note, it is essential that this file is the xml variant (cclx), use a recent version of Comfigurator to convert it if necessary *

#### Configuration

Using a text editor (such as nano), edit the configuration file and update the settings to match your configuration:

`nano alarm.config`

#### Test the installation

You can start the UPNP interface with the following command line

`$ node alarm`

It may take a few moments for things to happen, it has to read the config file, the Comfigurator config file and then instantiate UPNP devices for each of the comfort devices. It should then attempt to login to the alarm using the user specified in the configuration. You may see warnings related to emitters, you can safely ignore those at this time.

Open another connection to the Raspberry Pi, navigate to the directory in which you installed the code (`cd ~/alarm`) and start the test client (`node alarm-client`). If all is well it will discover the alarm UPNP devices and subscribe to events. Zone events will then be displayed as part of the output.

*well done, you have managed to make sense of my notes and now have your alarm inputs and outputs available as UPNP devices*

#### Start the interface as a deamon

If you are happy all is working as it should, you can exit the test app and the UPNP app by using CTRL-C in their respective windows and restart the alarm UPNP app with the following to allow it to run in the background and persist when you disconnect from the Pi

`$ pm2 start alarm.js`

You can monitor it by using

`$ pm2 show alarm` or 
`$ pm2 monit alarm`

You can stop it by using

`$ pm2 stop alarm`


#### Install the SmartThings elements

There is a SmartApp and 3 DeviceHandlers required for SmartThings to make sense of the alarm.

The process for installing the SmartApp and DeviceHandlers is basically the same, you will need to login to the online portal using your SmartThings credentials (for the UK the URL is :https://graph-eu01-euwest1.api.smartthings.com/)

##### Installation of the SmartApp

From this repository, view and copy into your clipboard all the code for the ComfortSmartApp.groovy file

From the online portal, do the following

	1.	Select 'My SmartApps' from the menu
	2.	Click '+ New SmartApp'
	3.	Click 'From Code'
	4.	Paste the code from the clipboard into the window
	5.	Click 'Create'
	6.	Click  'Save'
	7.	Click 'Publish' -> 'For Me'
	
##### Installation of the DeviceHandlers

From each of the files ComfortAlarmBridge.groovy, ComfortAlarmZone.groovy and ComfortAlarmOutput.groovy (one at a time), do the following:

	1.	Copy the code to the clipboard by viewing it
	2.	Select 'My Device Handlers'
	3.	Click '+ Create New Device Handler'
	4.	Click 'From Code'
	5.	Paste the code from the clipboard into the window
	6.	Click 'Create'
	7.	Click 'Save'
	8.	Click 'Publish' -> 'For Me'
	
##### Discovery of the devices

From your SmartThings app on your mobile phone, click 'MarketPlace' -> 'SmartApps', scroll to the bottom and select 'My Apps' then select 'Cytech Zone UPNP Manager v1'. Ensure the Search Target is 'urn:www.cytech.com:service' and click 'next'. It will then start the discovery process (please allow a few minutes for all the devices to be found), clicking on the 'Select Devices' will allow you to select the devices you wish to instantiate, once selected 'Done' will start to create them.

You should now find all the zones and outputs you selected are represented in 'Things' in 'My Home' and can be interacted with and included in logic as any other motion sensor or switch can in SmartThings.

You will also find a device called 'Comfort Bridge', this element is the receiver of asynchronous events from the Comfort system. It will show the average events per minute from the alarm to SmartThings.


#### Notes

Newly discovered devices will take a few minutes to receive an update from the alarm, once they have properly subscribed, events should be received in near real time (within <1 sec)


	




