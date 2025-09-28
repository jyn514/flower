---
preprocessors: []
---
sunflower uses the following special characters outside US-ASCII: `◊«»`.
this section documents how to type them in various environments.

## operating systems

### macOS (default)
- `◊`: Option+Shift+V
- `«`: Option+\\
- `»`: Option+Shift+\\

### macOS (rebinding)

go to Settings -> Keyboard -> Text Replacements and enter the replacements of your choice.
[Matthew Buttrick](https://docs.racket-lang.org/pollen/pollen-command-syntax.html#(part._.How_.M.B_types_the_lozenge)) also recommends [Typinator](https://ergonis.com/typinator).

### Windows (default)

- `◊`: holding down Alt, type 9674 on the numpad.
- `«`: holding down Alt, type 0171 on the numpad.
- `»`: holding down Alt, type 0187 on the numpad.

### Windows (rebinding)

I've heard good things about [WinCompose](https://github.com/samhocevar/wincompose).
Alternatively, there's always [AutoHotKey](https://www.autohotkey.com/):
```
; Binds Alt-f to ◊, Alt-j to «, Alt-k to ».
!f:: Send {U+25CA}
!j:: Send {U+00AB}
!k:: Send {U+00bB}
```

### Linux (default)
- `◊`: Ctrl+Shift+U 2 5 C A Enter
- `«`: Ctrl+Shift+U A B Enter
- `»`: Ctrl+Shift+U B B Enter

### Linux (rebinding)

you have several options here.

#### Compose key

This requires you to have a compose key somewhere on your keyboard.
See [the Arch Wiki](https://wiki.archlinux.org/title/Xorg/Keyboard_configuration#Configuring_compose_key).
On GNOME, you can configure it in Settings -> Keyboard -> Compose Key.

- `◊`: no default binding. Instead you can use the alias `⋄`: Compose < >
- `«`: Compose < < 
- `»`: Compose > >

#### key remapping daemon

i like [keyd](https://github.com/rvaiya/keyd/).
a lot of people i know like [kmonad](https://github.com/kmonad/kmonad).
i cannot in good conscience recommend `xmodmap`, but it is an option.

## editors

### vscode (rebinding)

Open a snippets file (File -> Preferences -> Configure Snippets -> New Global Snippets File -> "flower").
Add the following config:
```json
{
	"Lozenge": {
		"prefix": "\\f",
		"body": ["◊"]
	},
	"Left Guillemot": {
		"prefix": "\\j",
		"body": ["«"]
	},
	"Right Guillemot": {
		"prefix": "\\k",
		"body": ["»"]
	}
}
```

### vim, neovim, emacs Evil mode

- `◊`: Ctrl-K L Z
- `«`: Ctrl-K < <
- `»`: Ctrl-K > >

### neovim (rebinding)

use `vim.keymap.set`:

```lua
vim.keymap.set('i', '\\f', '◊', { desc = "Lozenge" })
vim.keymap.set('i', '\\j', '«', { desc = "Sunflower open quote" })
vim.keymap.set('i', '\\k', '»', { desc = "Sunflower close quote" })
```

### emacs

`(global-set-key "\M-\\" "◊")`, or `C-\ rfc1345 RET` to toggle an input mode where `&LZ` (<<, >>) produces `◊` («, »).

## jyn, how do you type it?

on Windows, i use the neovim rebinding with no OS-level binding.
on my home desktop, i have this bound to the [symbol layer of my keyboard](https://configure.zsa.io/moonlander/layouts/KW9E9/latest/3).
