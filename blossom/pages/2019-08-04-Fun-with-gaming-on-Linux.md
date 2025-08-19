---
aliases: ['/2019/08/04/Fun-with-gaming-on-Linux.html']
title: "Fun with gaming on Linux"
date: 2019-08-04
description: "How I got Multiplayer Civilization 5 to work on Linux"
taxonomies:
  tags:
    - stories
---

## The Problem

As my family frequently reminds me, most of my tech problems are of my own making.

For instance, I wanted to play [Civilization 5](https://civilization.com/civilization-5/) with a friend of mine through Steam.
I have a Linux/Windows dual-boot with a shared
[NTFS](https://en.wikipedia.org/wiki/NTFS)
[partition](https://en.wikipedia.org/wiki/Disk_partitioning) so that I can share files between the operating systems.
I run Linux by default.
I also have Hibernate disabled on Windows because I can't use the shared partition
from Linux if Windows is hibernated -
Windows [doesn't see the new changes](https://www.tuxera.com/community/ntfs-3g-faq/#fullyshut).

This led to a perfect storm of interactions, since running Civ required
- Rebooting the machine
- Waiting about 10 minutes for Windows to cold-start from a HDD (remember that Hibernate is disabled)
- Waiting another 5-10 minutes for Steam and Civ to launch

Needless to say, this was frustrating for both my friend and I.

## Trying new things

I found recently that [Steam has a compatibility layer](https://fosspost.org/tutorials/enable-steam-play-on-linux-to-run-windows-games)
for playing Windows games from other OSs.
I downloaded it and gave it a try on a single-player game.
It works great, I have no complaints.

The first time I tried to play with my friend, though, I got repeated issues saying
'Could not connect to host'.
Related, I block outgoing network traffic by default at the firewall level.
Unfortunately, Civ uses random ports to connect, so I can't whitelist it like I do for HTTP, SSH, etc.
My normal solution here is to have a group called `internet` which is whitelisted by
IPTables (the native firewall software on Linux, similar to Windows Firewall).
If I want to, for example, chat via Discord, I run `sg internet discord`
which allows unrestricted network traffic for that single Discord instance.

When I tried this with Steam, however, I ran into all sorts of strange errors.
Sometimes Civ would launch but show network errors when I tried to connect.
Sometimes Steam wouldn't recognize my game library, mounted on the NTFS partition so I don't have to redownload 5 GB of assets when I reboot.
I tried re-adding the library but got the message 'The partition where the game library is mounted must allow programs to be executed'.
This was exceedingly odd because I execute programs on that partition all the time.

## Strange and Maddening Rules

I finally tracked the error down to Steam trying to create and run a shell script unsuccessfully.
Fun fact: `strings steamclient.so` shows, among other things, a complete shell script:

```
...
.steam_exec_test.sh
#!/bin/sh
exit 0
Couldn't write %s: %s
...
```

The issue was that my NTFS drive only recognized me, UID 1000 and GID 1000, as a user.
Any file written by any other user or group would have the ownership set to `root:root`.
Worse yet, the OS would then deny all permissions to you (since you're not root).

I spent about a half hour reading through lots of manuals about Linux <=> Windows permissions
(here's [the manual](https://www.tuxera.com/community/ntfs-3g-advanced/ownership-and-permissions/#usermapping) if you're looking for it).
The solution I came up with was to explicitly add the `internet` group as me in `<mountpoint>/.NTFS-3G/UserMapping`.

Now everything works!

## Even more info

- [My IPTables configuration file](https://github.com/jyn514/dotfiles/blob/master/lib/iptables)
- [My `UserMapping` file](/assets/UserMapping.txt)
