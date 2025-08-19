---
aliases: ['/2018/02/24/Password-Safety.html']
title: "Password Safety"
date: 2018-02-24
description: What a password manager is and how to use one
---

# Introduction

On the web today, there many different services we use.
We use email, Facebook, GitHub, Google, Twitter, dozens of various services.
Each of these require their own username and password.

We're told, of course, to use a different password for each account.
[Only 70% of us][hunt passwords], however, actually do so.
How are we supposed to remember a [dozen random passwords][NIST], each different?
Traditionally, we use the same password for each,
perhaps with [slight differences][password changes].
This makes it very easy for hackers to [get into your accounts][xkcd resuse].

There are very few common remedies for this -
there's ways to [check if you've been hacked](https://haveibeenpwned.com/)
and [multi-factor auth](https://en.wikipedia.org/wiki/Multi-factor_authentication),
but most peoples' response is to either write sticky notes of passwords,
or ignore the problem altogether.

This does work if you don't [leave your passwords lying around](/assets/password.jpg).
It is inconvenient, however, and doesn't scale to a large password database.
What you should be using is a [password manager][manager].

## Password Managers
A password manager is an easy way to keep track of passwords.
It can be anything from an [encrypted Excel Spreadsheet][encrypt spreadsheet]
to [your browser's builtin manager][ddg browser passwords].
It takes one 'master password', and in return keeps track of everything else for you.
There's no need to remember multiple passwords,
it's easy to meet any complexity requirement,
and it doesn't matter how often you're required to change your passwords
(which is [bad practice](http://cs.unc.edu/~fabian/papers/PasswordExpire.pdf), by the way.

## What to use

Wikipedia has [an extensive list](https://en.wikipedia.org/wiki/List_of_password_managers)
of password managers.

When choosing a manager, you should consider several things:
- What features are you looking for? What would be a dealbreaker?
- How much security are you looking for? What is your
[threat model](https://en.wikipedia.org/wiki/Threat_model)?
- How much convenience are you willing to sacrifice?

I recommend [Keepass](https://keepass.info) because it
- encrypts your databases with [state of the art encryption][keepass aes]
- organizes your passwords [by groups](https://keepass.info/features.html#lnkgroups)
- stores usernames, URLs, and
[arbitrary data](https://keepass.info/features.html#lnktimes )
- randomly [generates passwords](https://keepass.info/help/base/pwgenerator.html)
- is [open source][source zip]
- is fully interoperable with many [other programs][wiki derivatives]
- has [many other features](https://keepass.info/features.html)

Once you start using a password manager, you can start using passwords like
`W(xv|u7N''fAs,{t|J` for all your accounts.

## Appendix
- If you're not on Windows, or just want more features, check out [Keepassxc][xc]
(Keepass Cross-Platform Community Edition). It supports global autotype and
[timed one-time passwords][totp], a.k.a. two-factor auth.
- I've written a [presentation on secure passwords][password presentation]
- [StackOverflow on password reuse](https://security.stackexchange.com/q/6682)
- The figure for reuse [may be lower][reuse study] than I've claimed,
but it's still a minimum of 30%

[reuse study]: https://www.lightbluetouchpaper.org/2011/02/09/measuring-password-re-use-empirically/
[password changes]: https://reusablesec.blogspot.com/2010/10/new-paper-on-password-security-metrics.html
[hunt passwords]: https://www.troyhunt.com/science-of-password-selection/
[xkcd resuse]: https://xkcd.com/792/
[NIST]: https://www.nist.gov/publications/character-strings-memory-and-passwords-what-recall-study-can-tell-us
[manager]: https://en.wikipedia.org/wiki/Password_manager
[encrypt spreadsheet]: https://support.office.com/en-us/article/Protect-an-Excel-file-7359d4ae-7213-4ac2-b058-f75e9311b599
[ddg browser passwords]: https://duckduckgo.com/?q=browser+manage+password+saving
[keepass aes]: https://keepass.info/help/base/security.html
[source zip]: https://keepass.info/download.html
[wiki derivatives]: https://en.wikipedia.org/wiki/KeePass#Unofficial_KeePass_derivatives
[xc]: https://keepassxc.org/
[totp]: https://en.wikipedia.org/wiki/Time-based_One-time_Password_Algorithm
[password presentation]: https://drive.google.com/open?id=15u4uXxC5K7v2Llsu8L4JVDcFNjyavW4pKBJ2GW5YV5M
