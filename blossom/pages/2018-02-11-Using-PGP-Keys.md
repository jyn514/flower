---
aliases: ['/2018/02/11/Using-PGP-Keys.html']
title: "Using PGP Keys"
date: 2018-02-11
description: Why and How to use encryption
taxonomies:
  tags:
    - walkthroughs
---

# What is GPG?

[GPG][gpg homepage] is a program for encrypting your files, including
- Documents
- Music
- Programs
- Anything else you can store digitally

[Technically][xkcd technically], GPG implements the [PGP][pgp homepage] standard,
which is [mandated][standard] by the [IETF®][ietf].

## What is encryption?

[Encryption][encryption] is a way of ensuring that *only* people you choose
are able to read your information. Encryption goes back thousands of years
to the [Roman empire][caesar cipher]. It is [used every day](https://doesmysiteneedhttps.com/)
in the [`HTTPS`][https] protocol, which ensures that the websites you visit
are only visible to you, and that the site has not [been changed in transit][comcast inject].

Cryptography therefore has two major functions:
- To encrypt data: to make sure only people you choose can read it, and
- To sign data: to ensure that information you read has not been altered by third parties.


## How do I get GPG?

GPG is only fully-featured when used from the [command line][cli].
If that sounds foreign to you, I have a quick-and-dirty guide on [GitHub][shell intro].

Some binary releases:
- Debian/Ubuntu: `sudo apt-get install gnupg`
- Windows: [GPG4Win](https://gpg4win.org/download.html)
- macOS:
    - without brew: [GPGtools.org](https://gpgtools.org/)
    - with `brew` installed: `brew install gnupg `

## How do I use GPG?
Now we get to the fun stuff! GPG uses what's called [asymmetric encryption][public-key crypto],
which allows *anyone* to send secure messages to you, but only you to read them.
In order to take full advantage, you'll need to
1. Make a key
2. Publish your key
3. Start signing things

### Make a key
You need a [passphrase](https://whatisapassphrase.com/) to use a GPG key.
This prevents anyone from using your key.

```sh
gpg --quick-gen-key "Joe Shmoe <joe@email.example.com>"
```
Enter your passphrase into the prompt that pops up.

### Publish your key
```sh
gpg --send-keys
```

### Start signing things

- Sign things with git:
`gpg config --global commit.gpgsign true`
- Sign your emails with supporting clients:
    * [Thunderbird][thunderbird] and [Enigmail][enigmail] for desktop
    * [Kmail](https://www.kde.org/applications/internet/kmail/) for KDE
    * [K9mail](https://k9mail.github.io/) and [OpenKeychain](https://openkeychain.org/)
    for Android
    * [Mutt](https://gnupg.org/software/swlist.html#mutt) for the terminal
- Join [Keybase](https://keybase.io/)

## More information
- [GPG handbook](https://www.gnupg.org/gph/en/manual/book1.html)
- [More frontends](https://gnupg.org/software/frontends.html)
- [A much more thorough explanation](https://www.glump.net/howto/cryptography/practical-introduction-to-gnu-privacy-guard-in-windows)
- [Homepage](https://www.openpgp.org/)
- [GPG homepage](https://gnupg.org/)
- [FSF](https://emailselfdefense.fsf.org/en/) on email self-defense

## Appendix
### Prompts are repeated
Note that the following prompt appears *twice* when you generate a key:

```
We need to generate a lot of random bytes.
It is a good idea to perform some other action (type on the keyboard,
move the mouse, utilize the disks) during the prime generation;
this gives the random number generator a better chance to gain enough entropy.
```

This is because there are actually two keys being generated:
1 private key and 1 public. The private you will store on your computer
securely. The public you will upload to a keyserver for anyone to see.

### What if I'm not comfortable with shell quoting?
Use `gpg --gen-key` or `gpg --full-gen-key`.

[thunderbird]: https://www.mozilla.org/en-US/thunderbird/
[enigmail]: https://www.enigmail.net/index.php/en/
[shell intro]: https://github.com/jyn514/215-resources/blob/master/tutorials/ShellIntro.pdf
[gpg homepage]: https://gnupg.org/
[pgp homepage]: https://www.openpgp.org/
[standard]: https://www.ietf.org/rfc/rfc4880.txt
[ietf]: https://www.ietf.org/
[caesar cipher]: https://en.wikipedia.org/wiki/Caesar_cipher
[xkcd technically]: https://www.xkcd.com/1475/
[encryption]: https://en.wikipedia.org/wiki/Encryption
[https]: https://en.wikipedia.org/wiki/HTTPS
[comcast inject]: https://gist.github.com/ryankearney/4146814
[cli]: https://en.wikipedia.org/wiki/Command-line_interface
[public-key crypto]: https://en.wikipedia.org/wiki/Public-key_cryptography
