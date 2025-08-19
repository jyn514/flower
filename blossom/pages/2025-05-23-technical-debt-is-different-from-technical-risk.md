---
title:  technical debt is different from technical risk
date:   2025-05-23
description: "\"technical debt\" is about updating your understanding of the program over time. \"technical risk\" is about sacrificing your ability to make changes for speed of development in the short term."
taxonomies:
  tags:
    - ideas
---

## technical debt is commonly misunderstood
the phrase "technical debt" at this point is very common in programming circles. however, i think the way this phrase is commonly used is misleading and in some cases actively harmful. here is a statement of the common usage by [a random commenter on my fediverse posts][mezza]:
> tech debt is [...] debt in the literal sense that you took a shortcut to get to your product state. You've saved time by moving quick, and are now paying interest by development slowing down.

[mezza]: https://mastodon.pub.solar/@mezza/114219114805175449

contrast this to the original statement of technical debt in [Ward Cunningham's paper from 1992][ward-original]:
> Shipping first time code is like going into debt. A little debt speeds development so long as it is paid back promptly with a rewrite. [...] The danger occurs when the debt is not repaid. Every minute spent on not-quite-right code counts as interest on that debt. Entire engineering organizations can be brought to a stand-still under the debt load of an unconsolidated implementation.

Ward isn't comparing "shortcuts" or "move fast and break things" to "good code"—he's comparing iterative design (often called "agile") to the waterfall method [^smalltalk]:
> The traditional waterfall development cycle has endeavored to avoid programming catastrophy by working out a program in detail before programming begins. [...] However, using our debt analogy, we recognize this amounts to preserving the concept of payment up-front and in-full.

[^smalltalk]: Actually, if you read the post more closely, he is saying something even more interesting: iterative development is only possible *because* his company is using a language (smalltalk) that has privacy boundaries.

[ward-original]: https://c2.com/doc/oopsla92.html
[Programming As Theory Building]: https://gist.github.com/onlurking/fc5c81d18cfce9ff81bc968a7f342fb1

Finally, I want to quote a [follow-up statement from Ward][follow-up ward] in 2006, which is closely related to [Programming As Theory Building] by Peter Naur:
> A lot of bloggers at least have explained the debt metaphor and confused it, I think, with the idea that you could write code poorly with the intention of doing a good job later and thinking that that was the primary source of debt. I'm never in favor of writing code poorly, but I am in favor of writing code to reflect your current understanding of a problem even if that understanding is partial.

[follow-up ward]: https://wiki.c2.com/?WardExplainsDebtMetaphor

It seems pretty clear at this point that Ward is describing something different from the common usage (I think "technical debt" is a good term for Ward's original idea). What then can we call the common usage? I like *technical risk*.
## technical risk means a program is hard to modify
Whenever you modify a program's behavior, you incur a risk that you introduce a bug. Over time, as the code is used more, the number of bugs tends to decrease as you fix them. [Two studies in 2021 and 2022][android vulnerabilities] (one by the Android security team, one by Usenix security) found empirically that memory vulnerabilities decay *exponentially* over time. So you have an inherent tension between minimizing your changes so that your code gets less buggy over time and modifying your code so that your program becomes more useful.

[android vulnerabilities]: https://security.googleblog.com/2024/09/eliminating-memory-safety-vulnerabilities-Android.html#:~:text=the%20math

When people talk about "technical debt", what I am calling "technical risk", they mean "modifying the code has a very high risk"—any kind of modification has a high chance of introducing bugs, not only when adding new features but also when doing refactors and bug fixes. Even the most trivial changes become painful and time-consuming, and the right tail of your time distribution increases dramatically.

Furthermore, when we say "this program has a lot of tech debt", we are implicitly arguing "the risk of a refactor is lower than the risk of it eventually breaking when we make _some other change_". We are *gambling* that the risk of a refactor (either in time or breakage) is worth the decreased risk going forward.

Note that you cannot overcome technical risk simply by spending more time; in this way it is unlike technical debt. With sufficient technical risk, simply predicting how long a change will take becomes hard. Due to the risk of regressions you must spend more time testing; but because of the complexity of the program, creating tests is also time-consuming, and it is less likely that you can test exhaustively, so there is a higher risk that your tests don't catch all regressions. Eventually changing the program without regressions becomes nearly impossible, and people fork or reimplement the program from scratch (what Ward describes as "the interest is total").
## all programs have risk
The common understanding of "tech debt" is that it only applies to programs that were built hastily or without planning. "tech risk" is much more broad than that, though.
It also applies to old programs which no longer have anyone that [understands their theory][programming as theory building]; new code if it's sufficiently complicated (stateful, non-local, "just a complicated algorithm", etc); and large programs that are too big for any one person to understand in full.
In fact, most code has some amount of risk, simply because it isn't usually worth making readability the number 1 priority (and readability differs from programmer to programmer).
## "bad code" misses the point
Hillel Wayne recently wrote a post titled [Write the most clever code you possibly can][hillel]. At one point he says this:
> I've also talked to people who think that datatypes besides lists and hashmaps are too clever to use, that most optimizations are too clever to bother with, and even that functions and classes are too clever and code should be a linear script.

This is an extreme example, but it reflects a pattern I often see: people think any code that uses complicated features is "too clever" and therefore bad.
This comes up a lot for "weird" syscalls or libc functions, like `setjmp`/`longjmp` and `fork`.
I think this misses the point. What makes something technical risk is the *risk*, the inertia when you try to modify it, the likelihood of bugs.
Having a steep learning curve is not the same as being hard to modify, because once you learn it once, future changes become easier.

[hillel]: https://buttondown.com/hillelwayne/archive/write-the-most-clever-code-you-possibly-can/

## feature flags are the taste of the lotus
Once your risk is high enough, and if you don't have the option of reducing complexity, people tend to work around the risk with feature flags or configuration options. These flags avoid the new behavior altogether in the default case, such that "changing the program" is decoupled from "changing the behavior".

In my experience this can be good in moderation—but if every new change requires a feature flag, and you never go back and remove old flags, then you're in trouble, because the flags themselves are adding complexity and risk. Each new change has to consider not just the default case, but all possible combinations of flags in a combinatorial explosion. You see this with things like tmux, OracleDB, and vim, all of which tend to accumulate options without removing them. Consider [this quote][hacker news oracle] from someone who claimed to work at Oracle:
> Sometimes one needs to understand the values and the effects of 20 different flag to predict how the code would behave in different situations. Sometimes 100s too! I am not exaggerating. The only reason why this product is still surviving and still works is due to literally millions of tests!

This is an extreme case, but in my experience it is absolutely representative of what happens to sufficiently large codebases over time. Once things are this bad you are "locked in" to the feature flag model—there's too many to remove (and your users may be depending on them!), but you cannot understand the interactions of all their combinations, so you gate new changes behind more new flags just to be sure.

[hacker news oracle]: https://news.ycombinator.com/item?id=18442941

## what to do about risk?
this post is kinda scary! it tells a story of codebases that grow more and more bogged down over time, despite people's best efforts, until they eventually die because they can't be modified.

i think things are not actually so dire as they seem. firstly, you always have the option to do ongoing refactors, reducing the risk of changes. with ongoing maintenance like this, even extremely complex programs can be maintained for years or decades; i think [the rust compiler][rustc-constant-change] is a good example of such a program.

secondly, rebuilding systems is good, actually, because it lets us learn from the lessons of the past. oracledb, tmux, and vim all have younger competitors (e.g. sqlite, zellij, and helix) that are more nimble. even more than that, new systems have the opportunity to be built on a different paradigm (e.g. sqlite runs in-process instead of as a daemon) with different tradeoffs. this is the classic case of [disruptive innovation].

[disruptive innovation]: https://en.wikipedia.org/wiki/Disruptive_innovation

to some extent, people or teams can get "locked in" to existing systems, especially if they are highly configurable or changing to a new system would be high risk for the organization (e.g. migrating to a new database is extremely high risk for almost anyone), but this can be mitigated by open file formats (such as [sqlite's database file]) and backwards compatibility for the old options (such as in neovim).

[Platform Envelopment]: https://www.hbs.edu/ris/Publication%20Files/07-104.pdf

## in conclusion

- "technical debt" as commonly understood is different from its origins.
- the original "technical debt" referred to iterative design.
- the common meaning is about programs that are hard to change, and i refer to it as "technical risk".
- all programs have technical risk to greater or lesser degree; you can decrease it but never eliminate it altogether.
- once risk grows sufficiently high, changes become hard enough that they have to be gated behind feature flags. the program eventually stagnates and is rewritten.

[rustc-constant-change]: https://rustc-dev-guide.rust-lang.org/about-this-guide.html#constant-change
[sqlite's database file]: https://www.sqlite.org/fileformat2.html
