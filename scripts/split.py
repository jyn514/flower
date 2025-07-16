#!/usr/bin/env python3
import io; from sys import argv
from datetime import date
# pip install python-frontmatter
import frontmatter

def rkts(pys):return '"'+repr(pys)[1:-1]+'"'
def rkt(py):
    if isinstance(py, (int, str)):
        return rkts(py)
    elif isinstance(py, list):
        return "'({})".format(' '.join(rkt(v) for v in py))
    elif isinstance(py, dict):
        vals = ' '.join(("{} {}".format(rkt(k), rkt(v)) for k, v in py.items()))
        return f"(hash {vals})"
    elif isinstance(py, date):
        return rkts(str(py))
    else:
        raise ValueError(f"don't know how to convert {py} ({type(py)}) to racket")

def load(in_, out):
    post = frontmatter.load(in_)
    buf = io.StringIO()
    buf.write('#lang pollen\n')
    for k, v in post.to_dict().items():
        if k == 'content': continue
        buf.write(f'◊(define {k} {rkt(v)})\n')
    buf.write('\n')
    buf.write(post.content)
    with open(out, 'w') as fd:
        fd.write(buf.getvalue())

if __name__ == '__main__':
    load(argv[1], argv[2])
