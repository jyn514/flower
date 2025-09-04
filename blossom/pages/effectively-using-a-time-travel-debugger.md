---
title: effectively using a time travel debugger
date: 2025-06-17
draft: true
taxonomies:
  tags:
    - workflows
---
◊(use 'expressions.shortcodes)

i keep forgetting how to do this, and it keeps coming in handy. so, i am writing it down for future me. tl;dr: [be systematic](https://danluu.com/teach-debugging/#:~:text=systematically).

---

say you have a program that behaves differently on two different inputs for a reason you don't understand. or, maybe it's the same input but you've changed the program. how can you figure out what is happening?

◊(note)«
i'm going to use a real world example, a debugging problem at work that took me nearly a week to figure out. using the steps here would have saved me about three days. i encourage you to follow along with your own nasty problem you want to figure out, just like in [theory building without a mentor](/theory-building-without-a-mentor).

i work on a codebase called YottaDB, which is [open source](https://gitlab.com/YottaDB/DB/YDB). i was implementing a compiler peephole optimization that transformed an internal IR to be more efficient at runtime. for this post, i will be using commit [c978ca1c](https://gitlab.com/jyn514/YDB/-/commit/c978ca1cc9dea5cf8ab255d2c0c6f531ee2ed0f6), which has a partial implementation of the new feature that crashes on the input `	for  write ^x($job)`. to replicate, put that in a file, then run `build/yottadb -machine file.m`. run with `env ydb_dbglvl=16385` to replicate the "tree view" below.

for the "two column side-by-side comparison" section below, i am comparing to [31205c980](https://gitlab.com/YottaDB/DB/YDB/-/commit/31205c980c04a21f63b201a740e6fd5065b5c987).

◊expand-button
»

you can always use printf debugging, of course. but say your data is too large for you to notice the difference between the inputs by eye[^1], or the print function itself is not showing you differences, even though the program is behaving differently (real thing i have run into!). what can you try next?

◊(note "YDB")«
in my case, my print function was showing *one* view of the data correctly. in particular, i was seeing this tree view:
```
# condensed from original for clarity
# (opcode, [operand, operand], memory address)
(OC_GVNAME, [e9f8, e970], 0xea80)
  (OC_ILIT, [   3,    0], 0xe9f8)
  (OC_PARAMETER, [e8e8, e860], 0xe970)
    (OC_ILIT, [fee7,    0], 0xe8e8)
    (OC_PARAMETER, [e7d8, e750], 0xe860)
      (OC_LIT, [f268,    0], 0xe7d8)
      (OC_PARAMETER, [e6c8,    0], 0xe750)
        (OC_SVGET, [e640,    0], 0xe6c8)
          (OC_ILIT, [   3,    0], 0xe640)
```
but there are at least 3 other views, and i needed one of the others.
»

if you have a debugger, you can run the process and stop at the first thing that goes wrong [^2].

◊(note "YDB")«
i had a very obvious thing that went wrong: i got an assertion failure.
```
%YDB-F-ASSERT, Assert failed in /home/jyn/work/YDB2/sr_port/emit_code.c line 1221 for expression (FALSE && opr->oprclass)
```
unfortunately, that code doesn't have a lot of info.
```c,linenos,linenostart=1032
// abridged for clarity
/* Emit the code for a given triple */
void emit_trip(oprtype *opr) {
	triple		*ct = NULL;
	if (TRIP_REF == opr->oprclass) {
		ct = opr->oprval.tref;
		if (ct->destination.oprclass)
			opr = &ct->destination;
		/* else lit or error */
	}
	// editor's note: this is a global, i did not omit any local variables used
	switch (cg_phase) {
		case CGP_ADDR_OPT:
		case CGP_APPROX_ADDR:
			switch (opr->oprclass) {
				case TRIP_REF:
				/* ... */
				case TINT_REF:
				/* ... */
				default:
					assert(FALSE && opr->oprclass);
			}
	}
}
```
note that this is not code i modified. *something* went wrong with `opr` before we got to this point, but it's entirely unclear what.
»

sometimes the bug is obvious, like "oops we needed to check for a null pointer". but sometimes it's not. you don't know how you got to this error case, and you're confused that it's possible at all. in that case you can use a time-travel debugger, like [rr](https://rr-project.org/), to record the process execution and work backwards from there. in fact, because we know this bug is based on the input we got, we can run *two* processes side-by-side and compare the two, as long as we know what to compare.

◊(note "YDB")«
let's run these backwards from the point that went wrong. first, let's compare `opr` in both cases. to do that, we have to know when to stop the second program to have a 1-1 comparison, so let's figure that out first.

```
$ (assertfail) rr record build/yottadb -machine test/_RSEL.m
%YDB-F-ASSERT, Assert failed in /home/jyn/work/YDB2/sr_port/emit_code.c line
1221 for expression (FALSE && opr->oprclass)
$ rr replay
(rr) set breakpoint pending on
(rr) break assertfail
(rr) continue
Breakpoint 1, assertfail (testlen=22, teststr=0x7d7439bdc45f "FALSE && opr->oprclass", flen=39, fstr=0x7d7439bdc11c "/home/jyn/work/YDB2/sr_port/emit_code.c", line=1221) at /home/jyn/work/YDB2/sr_port/mdef.h:828
828		rts_error_csa(NULL, VARLSTCNT(7) ERR_ASSERT, 5, flen, fstr, line, testlen, teststr);
(rr) up
#1  0x00007d7439a9ae7d in emit_trip (opr=0x6137f0fb47a0, val_output=1, generic_inst=11, trg_reg=7) at /home/jyn/work/YDB2/sr_port/emit_code.c:1221
1221						assert(FALSE && opr->oprclass);
(rr) print opr.oprclass
$3 = NO_REF
```
ok, now let's replicate that in our other process, and—oh.
```
$ (baseline) rr record build/yottadb -machine test/_RSEL.m
$ rr replay
(rr) break emit_trip if opr.oprclass == NO_REF
(rr) continue
Continuing.
Program received signal SIGKILL, Killed.
```
how did we even get here?
<!-- maybe that reassignment we saw earlier was important after all? -->
```
(rr) (assertfail) backtrace
#0  assertfail (testlen=22, teststr=0x7d7439bdc45f "FALSE && opr->oprclass", flen=39, fstr=0x7d7439bdc11c "/home/jyn/work/YDB2/sr_port/emit_code.c", line=1221) at /home/jyn/work/YDB2/sr_port/mdef.h:828
#1  0x00007d7439a9ae7d in emit_trip (opr=0x6137f0fb47a0, val_output=1, generic_inst=11, trg_reg=7) at /home/jyn/work/YDB2/sr_port/emit_code.c:1221
#2  0x00007d7439a97173 in emit_vax_inst (inst=0x7d7439be4050 <ttt+7312>, fst_opr=0x7ffdbb554800, lst_opr=0x7ffdbb554810) at /home/jyn/work/YDB2/sr_port/emit_code.c:765
#3  0x00007d7439a93c67 in trip_gen (ct=0x6137f0fb4728) at /home/jyn/work/YDB2/sr_port/emit_code.c:318
#4  0x00007d7439a83ebf in code_gen () at /home/jyn/work/YDB2/sr_port/code_gen.c:85
```
`trip_gen` looks promising, that's probably starting from the top of our tree?
```
(rr) (assertfail) up
#2  0x00007d7439a97173 in emit_vax_inst (inst=0x7d7439be4050 <ttt+7312>, fst_opr=0x7ffdbb554800, lst_opr=0x7ffdbb554810) at /home/jyn/work/YDB2/sr_port/emit_code.c:765
(rr) up
#3  0x00007d7439a93c67 in trip_gen (ct=0x6137f0fb4728) at /home/jyn/work/YDB2/sr_port/emit_code.c:318
(rr) print ct.opcode
OC_SVGET
```
yay we saw that in our tree earlier! so this at least should be the same between the processes. let's compare what happens.

<div class="column-container">
    <div class=column>
        main branch

<!-- <table> -->
<!--     <thead><tr><td>baseline</td><td>assertfail</td></thead> -->
<!--     <tbody><tr> -->
<!---->
<!-- <td> -->

```
(rr) break trip_gen if ct.opcode == OC_SVGET
Breakpoint 5 at 0x77b986e6b86f: file /home/jyn/work/YDB/sr_port/emit_code.c, line 139.
($$)
```

<!-- </td> -->
<!-- <td> -->

</div>
<hr class=vr></hr>
<div class=column>
    assertion failure

```
(rr) break trip_gen if ct.opcode == OC_SVGET
Breakpoint 5 at 0x77b986e6b86f: file /home/jyn/work/YDB/sr_port/emit_code.c, line 139.
($$)
```

</td>
</tr></tbody></table>

»

[^1]: if your input is a flat file you can always run `diff`, of course (actually i like `git diff --no-index`, it's very pretty, especially if you use [delta](https://dandavison.github.io/delta/)). or if it comes over a network socket you can use wireshark to save it to a file. but sometimes your input comes over something weirder, like a pipe or unix domain socket or shared memory, and now inspecting it without being in the same address space as your process is a pain. or sometimes it's "derived data" that's based on the input but does not have a 1-1 correspondence (or the code that builds that correspondence is broken). in that case looking at the original input doesn't help very much.

[^2]: you might ask "how can i find the first thing?" it doesn't have to be strictly the first. it's enough to find *a* thing that goes wrong, and then you can apply this process recursively from there, working backwards.
