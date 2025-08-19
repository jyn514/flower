---
aliases: ['/2018/01/06/Using-DNSSEC.html']
title: "Using DNSSEC"
date: 2018-01-06
description: How and why to enable DNSSEC
taxonomies:
  tags:
    - walkthroughs
---

I was on StackOverflow the other day when I saw
[a nifty post][SO post] on using custom DNS servers with
[NetworkManager](https://wiki.gnome.org/Projects/NetworkManager).
As you can see, in the middle of the post there's a link to
[https://dnssec.vs.uni-due.de](https://dnssec.vs.uni-due.de).
Being a curious sort, I opened it up
and saw that it's a DNSSEC tester!

[DNSSEC][wiki] is a protocol for ensuring that [DNS][dns] results are accurate,
using [public-key cryptographpy](https://en.wikipedia.org/wiki/Public-key_cryptography).
It prevents [cache poisoning][spoofing] and [dns hijacking][hijacking]. 
It also prevents [court-ordered DNS bans][dutch DNS] and
[hidden redirects][dns ads], both of which violate [RFC][rfc] standards.

Unfortunately, most computers in the world do *not* use DNSSEC by default.
Most end-users use DNS servers provided by their [ISP][isp], which often
don't support DNSSEC at all. Fortunately, configuring it is a simple process for which
Google has an [extensive walkthrough](https://developers.google.com/speed/public-dns/docs/using),
as well as [an overview](https://developers.google.com/speed/public-dns/) of DNS in general.

# Appendix

- You may also want to [see if you can access IPv6 sites](https://test-ipv6.com)
- Google has a [web interface](https://dns.google.com) for DNS lookups
- [Run your own](https://jpmens.net/2011/10/21/automating-unbound-for-dnssec-on-your-workstation/) DNS server!
- I've included DNSSEC testers in my own site.
Open up the [browser console][open console] to take a look!

[SO post]: https://unix.stackexchange.com/questions/90035/how-to-set-dns-resolver-in-fedora-using-network-manager
[isp]: https://en.wikipedia.org/wiki/Internet_service_provider
[dns]: https://en.wikipedia.org/wiki/Domain_Name_System
[wiki]: https://en.wikipedia.org/wiki/Domain_Name_System_Security_Extensions
[hijacking]: https://en.wikipedia.org/wiki/DNS_hijacking
[spoofing]: https://en.wikipedia.org/wiki/DNS_spoofing
[dutch DNS]: https://blog.xs4all.nl/xs4all-moet-adressen-pirate-bay-voorlopig-blokkeren/
[dns ads]: https://blog.wired.com/27bstroke6/2008/04/isps-error-page.html
[rfc]: https://www.ietf.org/rfc.html
[open console]: https://webmasters.stackexchange.com/questions/8525/
