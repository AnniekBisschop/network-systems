# Network hacking

To test the reliability of your file transfer protocol we are going to need an unreliable network.
Unfortunately, many network interfaces (including WiFi) will try very hard to provide stable network communication.
Therefore, we will be using tools to emulate an unreliable network.

## Netem

netem provides Network Emulation functionality for testing protocols by emulating the properties of wide area networks. 
The current version emulates variable delay, loss, duplication and re-ordering.

It should be installed by default on your Raspberry Pi.
If not, you can install it by executing `sudo apt install iproute2`.

All commands must be executed as root, which is handled by `sudo`.
During the tournament we may experiment with packet loss and delays.

### Add 1s delay to packets

```
sudo tc qdisc add dev eth0 root netem delay 1s
```


### Add 10% packet loss

```
sudo tc qdisc change dev eth0 root netem loss 10%
```

## Testing with netcat

To verify the behavior above you can use netcat (`nc`).

On the Raspberry Pi start a netcat server on port 1234. We will be using `-u` for UDP.

```
nc -u -l 1234
```

On your laptop start a netcat client

```
nc -u raspberrypi.local 1234
```

This opens an interactive console on both machines.
You can type text in either console and press [enter] to transmit the data to the other side.
Depending on the configuration with netem, you can observe delays or data loss.
