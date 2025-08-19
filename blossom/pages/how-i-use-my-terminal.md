---
title:  how i use my terminal
date:   2025-06-16
description: "i have gone a little above and beyond trying to get all the features of VSCode"
taxonomies:
  tags:
    - workflows
---

this is a whole blog post because it is "outside the overton window"; it usually takes at least a video before people even understand the thing i am trying to describe. so, here's the video:

<video controls><source src="/assets/terminal-recording.mp4"></video>

the steps here that tend to surprise people are {{ timestamp(when="0:11") }}, {{ timestamp(when="0:21") }}, and {{ timestamp(when="0:41") }}. when i say "surprise" i don't just mean that people are surprised that i've set this up, but they are surprised this is possible at all.

here's what happens in that video:
1. {{ timestamp(when="0:00") }} I start with Windows Terminal open on my laptop.
2. {{ timestamp(when="0:02") }} I hit {{kbd(key="ctrl+shift+5")}}, which opens a new terminal tab which `ssh`'s to my home desktop and immediately launches tmux.
3. {{ timestamp(when="0:03") }} tmux launches my default shell, `zsh`. zsh shows a prompt, while loading the full config asynchronously
4. {{ timestamp(when="0:08") }} i use `zoxide` to fuzzy find a recent directory
5. {{ timestamp(when="0:09") }} i start typing a ripgrep command. zsh autofills the command since i've typed it before and i accept it with {{kbd(key="ctrl+f")}}.
6. {{ timestamp(when="0:11") }} i hit {{kbd(key="ctrl+k f")}}, which tells tmux to search all output in the scrollback for filenames. the filenames are highlighted in blue.
7. {{ timestamp(when="0:12") }} i hold {{kbd(key="n")}} to navigate through the files. there are a lot of them, so it takes me a bit to find the one i'm looking for.
8. {{ timestamp(when="0:21") }} i press {{kbd(key="o")}} to open the selected file in my default application (`nvim`). tmux launches it in a new pane. note that this is still running *on the remote server*; it is opening a remote file in a remote tmux pane. i do not need to have this codebase cloned locally on my laptop.
9. {{ timestamp(when="0:26") }} i try to navigate to several references using rust-analyzer, which fails because RA doesn't understand the macros in this file. at {{ timestamp(when="0:32") }} i finally find one which works and navigate to it.
10. {{ timestamp(when="0:38") }} i hit {{kbd(key="ctrl+k h")}}, which tells tmux to switch focus back to the left pane.
11. {{ timestamp(when="0:39") }} i hit {{kbd(key="n")}} again. the pane is still in "copy-mode", so all the files from before are still the focus of the search. they are highlighted again and tmux selects the next file in search order.
12. {{ timestamp(when="0:41") }} i hit {{kbd(key="o")}}, which opens a different file than before, but in the *same* instance of `nvim`.
13. {{ timestamp(when="0:43") }} i hit {{kbd(key=" b")}}, which shows my open file buffers. in particular, this shows that the earlier file is still open. i switch back and forth between the two files a couple times before ending the stream.
## but why??
i got annoyed at VSCode a while back for being laggy, especially when the vim plugin was running, and at having lots of keybind conflicts between the editor, vim plugin, terminal, and window management. i tried zed but at the time it was quite immature (and still had the problem of lots of keybind conflicts).

i switched to using nvim in the terminal, but quickly got annoyed at how much time i spent copy-pasting filenames into the editor; in particular i would often copy-paste files with columns from ripgrep, get a syntax error, and then have to edit them before actually opening the file. this was quite annoying. what i wanted was an equivalent of ctrl-click in vscode, where i could take an arbitrary file path and have it open as smoothly as i could navigate to it. so, i started using tmux and built it myself.

people sometimes ask me why i use tmux. this is why! this is the whole reason! (well, this and session persistence.) terminals are stupidly powerful and most of them expose almost none of it to you as the user. i like tmux, despite its age, bugs, and antiquated syntax, because it's very extensible in this way.
## how it works
### search all scrollback for filenames
this is done purely with tmux config:
```tmux
# i am so sorry
# see `search-regex.sh` for wtf this means
# TODO: include shell variable names
bind-key f copy-mode \; send-keys -X search-backward \
  '(^|/|\<|[[:space:]"])((\.|\.\.)|[[:alnum:]~_"-]*)((/[][[:alnum:]_.#$%&+=@"-]+)+([/ "]|\.([][[:alnum:]_.#$%&+=@"-]+(:[0-9]+)?(:[0-9]+)?)|[][[:alnum:]_.#$%&+=@"-]+(:[0-9]+)(:[0-9]+)?)|(/[][[:alnum:]_.#$%&+=@"-]+){2,}([/ "]|\.([][[:alnum:]_.#$%&+=@"-]+(:[0-9]+)?(:[0-9]+)?)|[][[:alnum:]_.#$%&+=@"-]+(:[0-9]+)(:[0-9]+)?)?|(\.|\.\.)/([][[:alnum:]_.#$%&+=@"-]+(:[0-9]+)?(:[0-9]+)?))'
```
and this is the contents of `search-regex.sh`:
```sh
start_delim='(^|/|\<|[[:space:]"])'

relative_path='(\.|\.\.)'
start_path="($relative_path|[[:alnum:]~_\"-]*)"

component='[][[:alnum:]_.#$%&+=@"-]'
intermediate_paths="(/$component+)"

line_no='(:[0-9]+)'
file_end="($component+$line_no?$line_no?)"
end="([/ \"]|\.$file_end|$component+$line_no$line_no?)"

echo "$start_delim$start_path(${intermediate_paths}+$end|${intermediate_paths}{2,}$end?|$relative_path/$file_end)"

# test cases omitted for brevity
```
i will not go through the whole regex, but uh. there you go. i spent more time on this than i probably should have.
### open selected file in a new pane running nvim
this is actually a trick; there are many steps here.
#### open selected file in default application
this part is not so bad. tmux again.
```tmux
# `cd` is important in case this is a relative path. `echo | bash` is to perform tilde expansion.
bind-key -T copy-mode-vi o  send-keys -X copy-pipe \
    'cd #{pane_current_path}; xargs -I {} echo "echo {}" | bash | xargs open' \; \
  if -F "#{alternate_on}" { send-keys -X cancel }
```
i also have a version that always opens an editor in the current pane, instead of launching in the default application. for example i use [`fx`](https://fx.wtf/) by default to view json files, but `nvim` to edit them.
```tmux
# save the buffer, then open an editor in the current pane
bind-key -T copy-mode-vi O send-keys -X copy-pipe-and-cancel \
    'tmux send-keys "C-q"; xargs -I {} tmux send-keys "${EDITOR:-vi} {}"; tmux send-keys "C-m"'
```
#### open a new pane running nvim
here is the trick. i have created [a shell script](https://github.com/jyn514/dotfiles/blob/master/bin/hx-hax) (actually a perl script) that is the default application for all text files.

{% note() %}

setting up that many file associations by hand is a pain. i will write a separate blog post about the scripts that install my dotfiles onto a system. i don't use Nix partly because all my friends who use Nix have *even weirder* bugs than they already had, and partly because i don't like the philosophy of not being able to install things at runtime. i want to install things at runtime and *track* that i did so. that's a separate post too.

{% end %}

the relevant part is this:
```perl
# don't use `` so that args can have embedded pipes
my @split = ('tmux', 'split-window', '-h', '-P', '-F', '"#{pane_id}"', $editor, @args);
open(my $fd, '-|', @split) || die "can't open pipeline: $!";
```
this bounces *back* to tmux. in particular, this is being very dumb and assuming that tmux is running on the machine where the file is, which happens to be the case here. this is not too bad to ensure - i just use a separate terminal *emulator* tab for each instance of tmux i care about; for example i will often have open one Windows Terminal tab for WSL on my local laptop, one for my desktop, and one for a remote work machine via a VPN.

{% note() %}

there's actually even more going on here—for example i am translating the `file:line:column` syntax to something vim understands, and overriding `xdg-open` so that it doesn't error out on the `:line`—but for the most part it's straightforward and not that interesting.

{% end %}
### open a file in a running instance of nvim
this is a perl script that scripts tmux to send keys to a running instance of nvim (actually the same perl script as before, so that both of these can be bound to the same keybind regardless of whether nvim is already open or not):
```perl
my $current_window= trim `tmux display-message -p "#{window_id}"`;
my $pane = trim `tmux list-panes -a \\
  -f '#{&&:#{==:#{window_id},$current_window},#{==:#{pane_current_command},$editor}}' \\
  -F '#{pane_id}'`;

# ...

# exit copy mode so we don't send these commands directly to tmux
`tmux send-keys -t $pane -X cancel 2>/dev/null`;
# Escape for some reason doesn't get sent as the escape key if it shows up next to any other keys???
`tmux send-keys -t $pane Escape`;
my $args = join ' ', @args;
my $cmd = $editor eq 'nvim' ? 'drop' : 'open';
`tmux send-keys -t $pane ":$cmd $args" Enter`;
`tmux select-pane -t $pane -Z`;
```
## consequences of this setup
- i don't need a fancy terminal locally; something with nice fonts is enough. all the fancy things are done through tmux, which is good because it means they work on Windows too without needing to install a separate terminal.
- the editor thing works even if the editor doesn't support remote scripting. nvim *does* support RPC, but this setup also worked back when i used `helix` and `kakoune`.
- i *could* have written this such that the fancy terminal emulator scripts were in my editor, not in tmux (e.g. `:terminal` in nvim). but again this locks me into the editor; and the built-in terminals in editors are usually not very good.
## ok, but do you really want to use tmux
well. well. now that you mention it. the last thing keeping me on tmux was session persistence and [Ansuz has just released a standalone tool that does persistence and nothing else](https://ansuz.sooke.bc.ca/entry/389). so. i plan to switch to [kitty](https://sw.kovidgoyal.net/kitty/) in the near future, which lets me keep all these scripts and does not require shoving a whole second terminal emulator inside my terminal emulator, which hopefully will reduce the number of weird mysterious bugs i encounter on a regular basis.

the reason i picked kitty over [wezterm](https://wezterm.org/) is that ssh integration works by integrating with the shell, not by launching a server process, so it doesn't need to be installed on the remote. this mattered less for tmux because tmux is everywhere, but hardly anywhere has wezterm installed by default.
## ... was it worth it?
honestly, yeah. i spend quite a lot less time fighting my editor these days.
- it's *much* easier to debug when something goes wrong (vscode's debugging tools are mostly for plugin extension authors and running them is non-trivial). with vim plugins i can just add `print` statements to the lua source and see what's happening.
- all my keybinds make sense to me!
- my editor is less laggy.
- my terminal is much easier to script through tmux than through writing a VSCode plugin, which usually involves setting up a whole typescript toolchain and context-switching into a new project

that said, i cannot in good conscience recommend this to anyone else. all my scripts are fragile and will probably break if you look at them wrong, which is not ideal if you haven't written them yourself and don't know where to start debugging them.

## ok but this looks nice i want this

if you do want something similar without writing your own tools, i can recommend:
- [fish] + [zoxide] + [fzf]. that gets you steps 4, 5, and kinda sorta-ish 6.
- "builtin functionality in your editor" - fuzzy find, full text search, tabs and windows, and "open recent file" are all commonly supported.
- [qf], which gets you the "select files in terminal output" part of 6, kinda. you have to remember to pipe your output to it though, so it doesn't work after the fact and it doesn't work if your tool is interactive. note that it hard-codes a vi-like CLI (`vi +line file.ext`), so you may need to fork it or still add a script that takes the place of $EDITOR. see [julia evans' most recent post][jvns] for more info.
- [e], which gets you the "translate `file:line` into something your editor recognizes" part of 8, kinda. i had never heard of this tool until i wrote my own with literally the exactly the same name that did literally exactly the same thing, forgot to put it in PATH, and got a suggestion from `command-not-found` asking if i wanted to install it, lol.
- `vim --remote filename` or `code filename` or `emacsclient filename`, all of which get you 12, kinda. the problem with this is that they don't all support `file:line`, and it means you have to modify this whenever you switch editors. admittedly most people don't switch editors that often, lol.
## what have we learned?
- terminals are a lot more powerful than people think! by using terminals that let you script them, you can do quite a lot of things.
- you can kinda sorta replicate most of these features without scripting your terminal, as long as you don't mind tying yourself to an editor.
- doing this requires quite a lot of work, because no one who builds these tools thought of these features ahead of time.

hopefully this was interesting! i am always curious what tools people use and how - feel free to [email me] about your own setup :)

[email me]: mailto:blog@jyn.dev
[fish]: https://fishshell.com/
[zoxide]: https://github.com/ajeetdsouza/zoxide/
[fzf]: https://github.com/junegunn/fzf
[qf]: https://git.causal.agency/src/tree/bin/qf.c
[jvns]: https://jvns.ca/blog/2025/06/10/how-to-compile-a-c-program/
[e]: https://github.com/kilobyte/e
