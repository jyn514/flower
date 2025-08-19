---
aliases: ['/2018/08/24/Keepass-on-Linux-and-Android.html']
title: "Keepass on Linux and Android"
date: 2018-08-24
description: File synchronization on Linux
taxonomies:
  tags:
    - walkthroughs
---

Previously, I wrote about
[using a password manager][password manager].
However, the disadvantage of using an audited, local manager like
[Keepass](https://keepass.info) is that it's hard to share passwords between devices.
You can put the encrypted database in a file-sharing service like Google Drive,
but that means you need a sync client on all of your devices, and Google
[doesn't have one for Linux](https://abevoelker.github.io/how-long-since-google-said-a-google-drive-linux-client-is-coming/).

Fortunately, it's Linux, so there are alternatives.

My current favorite sync client is [google-drive-ocamlfuse][gdrive-ocaml].
Although it's annoying to have to [build from source][install]
and is [command-line only][usage],
it lets you treat Drive as a user-land file system using [FUSE][fuse],
which is particularly nice for clients that aren't web-aware (like keepass).
It also has an excellent [walkthrough][automount] on how to automount on boot
(this is Linux, nothing is automatic).

## Appendix
- Why not just use Dropbox? It doesn't support NTFS drives
(or anything other than [ext4][requirements]).

- Why not use an encrypted service, like [MEGA](https://mega.nz/)
or [Spideroak](https://spideroak.com/)? They don't support WebDAV,
and I don't feel like going through a file system every time I update passwords
(most phone clients only allow file access through a specific app).

- Why not use \<some other service> that (supports WebDAV or syncs automatically on phones) and has a Linux client? I haven't heard of \<some other service>.

[password manager]: /2018-02-24-Password-Safety.html
[gdrive-ocaml]: https://github.com/astrada/google-drive-ocamlfuse
[install]: https://github.com/astrada/google-drive-ocamlfuse/wiki/Installation
[usage]: https://github.com/astrada/google-drive-ocamlfuse#usage
[fuse]: https://github.com/libfuse/libfuse
[automount]: https://github.com/astrada/google-drive-ocamlfuse/wiki/Automounting
[requirements]: https://www.dropbox.com/help/desktop-web/system-requirements#desktop
