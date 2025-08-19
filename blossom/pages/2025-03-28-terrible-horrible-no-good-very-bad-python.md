---
title:  Terrible Horrible No Good Very Bad Python
date:   2025-03-28
description: new and exciting ways to write buggy code
taxonomies:
  tags:
    - python
---

time for everyone's favorite game!!

what does this code do?

```python
def foo():
  try:
    return os._exit()
  finally:
    return False

import os
foo()
```

<style type="text/css">
    ol { list-style-type: upper-alpha; }
</style>

does it:
1. return None
2. return False
3. throw an exception
4. exit the process without printing anything
5. something even more devious

sit with it. have a good think. explain your answers.

ready?

<details><summary>ok fine what does it do</summary>

returns `False`. want to know why?

<details><summary>yes just tell me already >:(</summary>

normally, `os._exit` exits the process without running "cleanup handlers" (`finally` blocks). however, it takes one argument. this snippet forgets to pass in the exit code, so instead of exiting, it throws `TypeError`. then the `finally` block silently swallows the exception because of the `return`.

`return` from a `finally` block is in fact so commonly misused that the python developers [plan](https://discuss.python.org/t/pep-765-disallow-return-break-continue-that-exit-a-finally-block/71348) to make it emit a `SyntaxWarning` in a future release.

one might be mislead that `import os` comes after the function is defined. but python has dynamic scoping, so that's fine.

one might also mix up `sys.exit` with `os._exit`. `sys.exit` works by raising a [`SystemExit`](https://docs.python.org/3/library/exceptions.html#SystemExit) exception, which would be caught and swallowed by the `finally` block. but [`_exit`](https://docs.python.org/3/library/os.html#os._exit) directly exits the process:

> Exit the process with status n, without calling cleanup handlers, flushing stdio buffers, etc.

in fact, it doesn't even call atexit handlers, not even if we directly use libc:
```
>>> import atexit
>>> atexit.register(lambda: print('hi'))
<function <lambda> at 0x73c740cf2830>
>>> from ctypes import *
>>> libc = cdll.LoadLibrary("libc.so.6")
>>> libc.on_exit(CFUNCTYPE(c_int, c_voidp)(lambda status, _: print(status)))
0
>>> import os
>>> os._exit(1)
# no output
```

</details>
</details>

yes someone did write this code by accident and yes they were very confused. i thought it was a bug in cpython until i figured it out.

you're welcome!!
