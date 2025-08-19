---
aliases: ['/2024/01/16/the-rust-project-has-a-burnout-problem.html']
title: "the rust project has a burnout problem"
date: 2024-01-16
extra: {audience: the rust community}
description:
preview_image: /assets/burned%20out%20rust%20club.png
taxonomies:
  tags:
    - rust
---

![a melting, smiling, ferris. it's surrounded by the cursive text "burned out rust kid club".](/assets/burned%20out%20rust%20club.png)

the number of people who have left the rust project due to burnout is shockingly high. the number of people in the project who are close to burnout is also shockingly high.

this post is about myself, but it's not just about myself. i'm not going to name names because either you know what i'm talking about, in which case you know *at least* five people matching this description, or you don't, in which case sorry but you're not the target audience. consider, though, that the project has been around for 15 years, and compare that to the average time a maintainer has been active ...

## what does this look like

(i apologize in advance if this story does not match your experience; hopefully the suggestions on what to do about burnout will still be helpful to you.)

the pattern usually goes something like this:
- you want to work on rust. you go to look at the issue tracker. you find something *you* care about, since the easy/mentored issues are taken. it's hard to find a mentor because all the experienced people are overworked and burned out, so you end up doing a lot of the work independently.

guess what you've already learned at this point: work in this project doesn't happen unless *you personally* drive it forward. that issue you fixed was opened for years; the majority of issues you will work on as you start will have been open for months.

- you become a more active contributor. the existing maintainer is too burned out to do regular triage, so you end up going through the issue backlog (usually, you're the first person to have done so in years). this reinforces the belief work doesn't happen unless *you* do it *personally*.
- the existing maintainer recognizes your work and turns over a lot of the responsibilities to you, especially reviews. new contributors make PRs. they make silly simple mistakes due to lack of experience; you point them out and they get fixed. this can be fun, for a time. what it's teaching you is that *you personally* are responsible for catching mistakes.
- you get tired. you've been doing this for a while. people keep making the same mistakes, and you're afraid to trust other reviewers; perhaps you're the *only* reviewer, or other reviewers have let things slip before and you don't trust their judgement as much as you used to. perhaps you're assigned too many PRs and you can't keep up. you haven't worked on the things you *want* to work on in weeks, and no one else is working on them because you said you were going to ("they won't happen unless *you do them personally*", a voice says). you want a break, but you have a voice in the back of your head: "the project would be worse without you".

i'm going to stop here; i think everyone gets the idea.

## what can i do about it

"it won't get done if i don't do it" and "i need to review everything or stuff will slip through" is exactly the mindset of my own burnout from rust. it doesn't matter if it's true, it will cause you pain. if the project cannot survive without *you personally* putting in unpaid overtime, perhaps it does not deserve to survive.

if you are paid to work on rust, you likely started as an unpaid contributor and got the job later. *treat it like a job now*. do not work overtime; do not volunteer at every turn; do not work on things far outside your job description.

the best way to help the project is to keep contributing for it for years. to do that, you have to avoid burning out, which means you have to *treat yourself well*.

## what can team leads do about it

have documentation for "what to do about burnout"; give it just as much priority as technical issues or moderation conflicts.

rotate responsibilities. don't have the same person assigned to the majority of PRs. if they review other people's PRs unsolicited, talk to them 1-1 about why they feel the need to do so. if someone is assigned to the review queue and never reviews PRs, talk to them; take them off the queue; give them a vacation or different responsibilities as appropriate.

ask people why they leave. i know at least one person whose burnout story does not match the one in this post. i am sure there are others. you cannot solve a problem if you don't understand what causes it.

*take these problems seriously*. [prioritize growing the team and creating a healthy environment over solving technical issues][open source post]. **the issues will still be there in a few months; your people may not be**.

[open source post]: https://jyn.dev/2023/12/04/How-to-maintain-an-open-source-project.html

## what can the rust project do about it

one thing bothering me as i wrote this post is how much of this still falls on individuals within the project. i don't think this is an individual problem; i think it is a cultural, organizational, and resource problem. i may write more about this once i have concrete ideas about what the project could do.

## be well. be kind to each other. i love you.

remember:

<blockquote class="twitter-tweet"><p lang="en" dir="ltr">EMPATHY WITHOUT BOUNDARIES IS SELF DESTRUCTION <a href="https://t.co/HbBwEj4hc3">pic.twitter.com/HbBwEj4hc3</a></p>&mdash; 𖤐ARCH BUDZAR𖤐 (@ArchBudzar) <a href="https://twitter.com/ArchBudzar/status/1313572660048269315?ref_src=twsrc%5Etfw">October 6, 2020</a></blockquote> <script async src="https://platform.twitter.com/widgets.js" charset="utf-8"></script> 

### acknowledgements

thank you **@QuietMisdreavus** for the *burned out rust kid club* art.

thank you **@Gankra**, **@QuietMisdreavus**, **@alercah**, **@ManishEarth**, **@estebank**, **@workingjubilee** and **@yaahc** for discussion and feedback on early drafts of this post. any errors are my own.
