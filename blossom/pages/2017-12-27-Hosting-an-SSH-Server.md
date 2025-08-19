---
aliases: ['/2017/12/27/Hosting-an-SSH-Server.html']
title: "Hosting an SSH Server"
date: 2017-12-27
description: I decided one day that I really wanted a remote server.
taxonomies:
  tags:
    - walkthroughs
---

I decided one day over break that I really wanted a remote server.
I had a spare laptop, a router at home, and far too much free time.
The obvious solution was to combine the three.

It ended up being much simpler than I expected to set up.
On my old laptop, I installed an SSH server:
```sh
sudo apt-get install openssh-server
```

I made a few changes to the default config:
```sh
sed -i '
s/Port: 22/Port: 666/
s/PermitRootLogin without-password/PermitRootLogin no/
s/#PasswordAuthentication yes/PasswordAuthentication no/
s/PrintMotd no/PrintMotd yes/
' /etc/ssh/sshd_config
```

Created and added an SSH key:
```sh
ssh-keygen
eval $(ssh-agent)
ssh-add
ssh-copy-id
sudo service ssh restart
```

And copied it to my new computer:
```sh
jyn@debian-acer:~$ cp ~/.ssh/id_rsa* /media/jyn/usb
jyn@debian-acer:~$ sudo umount $_
jyn@debian-thinkpad:~$ sudo mount /dev/sdb1 /mnt
cp /mnt/id_rsa* ~/.ssh
ssh-add
```

SSH now worked perfectly when I typed in the IP address.
I was moving back to USC in a few weeks though,
my server would be behind a firewall.
Not to worry though, with a mutter of approval from my dad,
I forwarded port 666 on the router to 666 on debian-acer.

Worked like a charm.

# Appendix
- If, like me, you have an outdated laptop
which refuses to start networking at boot, try this:
```sh
sudo sh -c 'echo "# Ethernet
auto eth0
iface eth0 inet dhcp
" >> /etc/network/interfaces'
```

- More elegant way to specify ssh ports:
```sh
sudo sh -c 'echo "192.168.1.13   home >> /etc/hosts"'
echo 'Host home
Port 666' >> ~/.ssh/config
```
