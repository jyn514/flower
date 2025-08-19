---
aliases: ['/2023/12/04/How-to-maintain-an-open-source-project.html']
title: "How to maintain an Open Source project"
date: 2023-12-04
description: How to maintain a project without burning yourself out
taxonomies:
  tags:
    - ideas
---

Open source is unique in that *energy*, not time or money, is the limiting factor. The existential threats are maintainer burnout and an imploding community, not failing to make payroll. As a result, it’s very important to do three things:

1. Recruit new maintainers as frequently as possible.
2. Ruthlessly prioritize the energy of existing maintainers.
3. Be kind to your users.

All other concerns are in some sense secondary; it doesn’t matter if a project has lots of useful features if it’s been abandoned for 4 years and no longer compiles, or if it has no users.

Note that "be kind to your users" is not the same as doing everything they ask for. You can and should say no to features. Being kind is really more simple stuff – don’t be rude in discussions, don’t make breaking changes unless there’s a good reason (there’s tools for this, like [cargo-semver-checks]!), make it easy for people to submit bug reports and ask questions. If you close an issue as WONTFIX, tell them why; if it's not too much effort, perhaps say the circumstances under which you might change your mind, or give an example of another way to solve their problem.

## Balancing priorities

One might notice that all three of those bullets are contradictory: recruiting new maintainers takes energy from existing maintainers; a feature that’s fun for a developer to write may not be useful for users, or vice versa; the more features a project has, the more complicated it gets and the harder it is to onboard new maintainers. Balancing these concerns is the cornerstone of an open source project.

Here are a few concrete examples of how to meet those goals:
- Automate everything you find boring. CI, releases, updating dependencies, formatting, documentation are all good candidates here. Remember that it’s better to e.g. have a release that’s missing pre-compiled binaries than no release at all. There are many tools that can do this for you; a few examples in the rust ecosystem are Github Actions, [cargo-dist], dependabot, rustfmt, and rustdoc.
- Post about the project frequently on social media or other platforms. This is both a good motivator for you to see what you’ve accomplished and a great way to get other people’s interest, either as users or maintainers. Don’t wait until you have something polished, a half-baked post is better than no post at all. Start a platform dedicated to your project where you can talk with users and contributors; this can be as simple as a Discord server or as complicated as a self-hosted Zulip instance. The important thing is, again, that it should be fun for you to set up.
- Document how to develop the project, and how to write documentation. The latter pays dividends – not just for recruiting new maintainers, but also for reminding yourself how the project works when you come back from vacation in two weeks. The latter reduces your burden for writing documentation by making it easy for other people to help you write it.
- Take vacations, even extended vacations. If you know you'll be on vacation ahead of time, be kind to your users or fellow maintainers by telling them you're gone. If you have automated review assignments, set it up to allow people to take breaks.

## Culture

Past those concrete things, though, it’s important to set a culture for the project that makes you eager to work on it.

- Develop a taste for when to say no to features. This is especially hard because it changes as a project grows. Early on, features are easy and fun to add, and open up lots of possibilities for your users. Later, they get harder to add, and new features make the tool more complicated for your existing users (who after all, started using your tool when it was more simpler) and new users (who have to read through all your documentation); and you have enough users it becomes less important to grow your user base.
- Find people who are as excited about the project as you are. Invite constructive criticism – it’s a great way for you to learn and improve the project at the same time! – but at the same time make sure you balance it with positive feedback so you don’t get overwhelmed.
- Don’t let yourself get burned out. This is in some ways the hardest part. There is only so much work you can do every week before you get tired. Pushing yourself past that will work, temporarily, but at the cost of draining your energy and making you less willing to work on the project in the future. You are not a company and do not have an SLA to your users; if they are really harmed they can use an older release or submit a patch themselves.

## Recruiting maintainers

Most people will never ask to be maintainers. Either they don't want to be presumptuous, or they don't feel experienced enough, or it just won't occur to them. Instead, you should reach out to them.

As a general rule, I suggest leaning towards trusting people with merge privileges early. Anyone who's active in the project is likely going to stick around for a while; you can always revert code they merge, and you don't have to give them publish access. This is a great way to make people feel like part of the project, which in turns makes them more likely to keep contributing.

I have a lot more to say on this, but most of it has already been said by my friend Alice Cecile in [this wonderful talk][alice-rustconf]. Go watch it. Seriously, go watch it, it's only 20 minutes and none of them are wasted.

Projects can become self-sustaining rather quickly – you really only need to recruit one or two other people, get five or ten users, and have a decent codebase to expand on. Once you’ve done that, you can mentor your other contributors into taking a leading role, and, eventually, step back from your role as the founder.

[alice-rustconf]: https://www.youtube.com/watch?v=xM7bI2OPPLQ
[cargo-semver-checks]: https://github.com/obi1kenobi/cargo-semver-checks#cargo-semver-checks
[cargo-dist]: https://opensource.axo.dev/cargo-dist/
