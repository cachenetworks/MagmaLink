---
description: How to run MagmaLink as a Systemd service
---

# Systemd Service

If you're using a Systemd-based Linux distribution you may want to install MagmaLink as a background service. You will need to create a `magmalink.service` file inside `/usr/lib/systemd/system`. Create the file with the following template (replacing the values inside the `<>` brackets):

```ini title="lavalink.service"
[Unit]
# Describe the service
Description=MagmaLink Service

# Configure service order
After=syslog.target network.target

[Service]
# The user which will run MagmaLink
User=<usr>

# The group which will run Lavalink
Group=<usr>

# Where the program should start
WorkingDirectory=</home/usr/lavalink>

# The command to start MagmaLink
ExecStart=java -Xmx4G -jar </home/usr/magmalink>/MagmaLink.jar

# Restart the service if it crashes
Restart=on-failure

# Delay each restart by 5s
RestartSec=5s

[Install]
# Start this service as part of normal system start-up
WantedBy=multi-user.target
```

To initiate the service, run

```shell
$ sudo systemctl daemon-reload
$ sudo systemctl enable magmalink
$ sudo systemctl start magmalink
```

In addition to the usual log files, you can also view the log with
```shell 
$ sudo journalctl -u magmalink
```
