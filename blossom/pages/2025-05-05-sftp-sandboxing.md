---
title: sftp sandboxing
date: 2025-05-05
description: unix sure is a system
taxonomies:
  tags:
    - walkthroughs
---
consider the following problem:
- you want to share a 300 MB directory
- with a single other person, not publicly
- over a public network

any hosting service that lets you serve 300 MB costs money. also, for sufficient file sizes the wasted upload becomes noticeable. it would be much nicer if we could peer-to-peer this.

sftp to the rescue!

small problem: sftp shows you a *lot* of the system state. for one thing, you have read access to basically every file on the system, and write access to anything owned by your user. for another, the easiest way to set up sftp is through an ssh server, which is. well. it has "shell" in the name for a reason.

so! here is how to set up a read-only sftp server which doesn't allow any other kind of access.

First, add the following to `/etc/ssh/sshd_config`:
```sshd
Subsystem       sftp    internal-sftp
Match User myuser
	ChrootDirectory %h
	ForceCommand internal-sftp -R
	DisableForwarding yes
```
Then run the following commands in a root shell (e.g. with `sudo -i`):
```sh
mkdir /empty
chmod a-w /empty
useradd -s /usr/sbin/nologin -m --skel /empty myuser
chown root:root /home/myuser
chmod a+rx /home/myuser
```
Finally, create `/home/myuser/.ssh/authorized_keys` in any way you wish

This creates a sftp session that looks like this:
```
$ ssh myuser@localhost
This service allows sftp connections only.
Connection to localhost closed.
$ sftp myuser@localhost
Connected to localhost.
sftp> pwd
Remote working directory: /
sftp> ls
myfile
sftp> ls ..
../myfile
sftp> ls ../../..
../../../myfile
sftp> mkdir x
remote mkdir "/x": Permission denied
sftp> chmod 644	myfile
Changing mode on /myfile
remote setstat "/myfile": Permission denied
sftp> get myfile
Fetching /myfile to myfile
myfile       100%   46MB 899.5MB/s   00:00
```
---

Some explanations and references:

[`Subsystem`](https://man.openbsd.org/cgi-bin/man.cgi/OpenBSD-current/man5/sshd_config.5#Subsystem) is documented as follows:
> Configures an external subsystem (e.g. file transfer daemon). [...] the name `internal-sftp` implements an in-process SFTP server. [...] It accepts the same command line arguments as `sftp-server` [...]

what arguments can we pass to [`sftp-server`](https://man.openbsd.org/sftp-server#R)?
> `-R`	Places this instance of `sftp-server` into a read-only mode. Attempts to open files for writing, as well as other operations that change the state of the filesystem, will be denied.

just what we want!

[`ForceCommand internal-sftp`](https://man.openbsd.org/cgi-bin/man.cgi/OpenBSD-current/man5/sshd_config.5#ForceCommand) is a cute little feature of openssh:
> Specifying a command of `internal-sftp` will force the use of an in-process SFTP server that requires no support files when used with `ChrootDirectory`.

In particular, this denies shell and command access.

What is `ChrootDirectory`?
> Specifies the pathname of a directory to [chroot(2)](https://man.openbsd.org/chroot.2) to after authentication. At session startup [sshd(8)](https://man.openbsd.org/sshd.8) checks that all components of the pathname are root-owned directories which are not writable by group or others. After the chroot, [sshd(8)](https://man.openbsd.org/sshd.8) changes the working directory to the user's home directory. [...] For file transfer sessions using SFTP no additional configuration of the environment is necessary if the in-process sftp-server is used [...]

cute! an easy way to deny even reading other parts of the rest of the system.

why does it require the path to be owned by root? <https://lists.mindrot.org/pipermail/openssh-unix-dev/2009-May/027651.html>
> If you ever let the user chroot to this directory and execute his 
hard-linked /bin/su, he can become root within that directory and then 
escape the chroot. Even if you could prevent him from escaping chroot, 
he can create device nodes and operate directly on filesystems, mount 
/proc and operate on external processes, etc. It should be clear that 
this is Very Bad (tm).

cool cool cool. love unix. this sure is a tool we use to build software.

[`Match`](https://man.openbsd.org/cgi-bin/man.cgi/OpenBSD-current/man5/sshd_config.5#Match) is surprisingly complicated in the general case but simple enough here.
[`DisableForwarding`](https://man.openbsd.org/cgi-bin/man.cgi/OpenBSD-current/man5/sshd_config.5#DisableForwarding) is probably not *strictly* necessary but we don't want people using this file server as a jumpbox to whatever other networks it's connected to, nor as e.g. a tor exit node.

lastly i want to point out that `useradd` by default creates a user with no login password, which takes care of people guessing passwords for this without the proper ssh public key. i have `PasswordAuthentication no` set in `sshd_config`, but this allows disabling the password for just your sftp access without disabling it altogether. alternatively you could put it under the `Match User`.

---

**NOTE:** an earlier version of this post suggested `Subsystem sftp internal-sftp -R` and `ForceCommand internal-sftp`. that disables write access for *all* users, not just the selected user. the new version only disables it for the selected user.
