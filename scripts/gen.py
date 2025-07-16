#!/usr/bin/env python3
import pathlib, os, io; from os import path; from pathlib import PurePath
import ninja_syntax as ninja

class Path(os.PathLike):
    def __init__(self,p): self.p=p
    def __getitem__(self, key): return Path(path.join(*PurePath(self.p).parts[key]))
    def __truediv__(self, new): return Path(path.join(self.p,new))
    def __fspath__(self): return str(self.p)
    # boring
    def __str__(self): return self.p.__str__()
    def __add__(self, right): return self.p.__add__(right)
    def replace(self, old, new): return self.p.replace(old, new)
content = Path('content')
public = Path('public')
build = Path('.build')
scripts = Path('scripts')
blossom = Path('blossom')

# if this links the file into public/, it MUST return None
def register(writer, f):
    out, in_ = path.splitext(f)
    in_ = in_[1:]
    base = Path(out)[1:]
    if in_ == 'clj':
        writer.build(build/base, 'blossom', f, implicit=blossom)
        return build/base
    elif in_ == 'md':
        writer.build(public/base + '.html', 'markdown', f)
    #     out += '.html'
    #     generated = out+'.pmd'
    #     writer.build(outputs=build/generated, rule='frontmatter', inputs=[f], implicit=['scripts/split.py', build])
    #     pollen_build(writer, out, generated)

def gen(writer):
    writer.rule(name='tmpdir', command=f'mkdir -p {build}', description='create build dir')
    writer.build(build, 'tmpdir')
    writer.rule(name='link', command='ln -f $in $out',
                description='link $in into build dir')
    writer.rule(name='blossom', command=f'clojure -Sdeps {blossom}/deps.edn -M {blossom}/parse.clj $in $out',
                description='render $in -> $out using clojure')
    writer.rule(name='markdown', command=f'pulldown-cmark -TFSULG $in > $out',
                description='render markdown -> HTML: $in -> $out')

    # writer.rule(name='watch', command='scripts/watch.sh',
    #             description='watch the site for changes')
    # writer.build('watch', 'watch')

    for f in os.listdir(content):
        f = content/f
        while True:
            if new := register(writer, f):
                f = new
                continue
            if f[-2] == build:
                writer.build(public/f,  'link', build/f)
            break


    writer.rule(name='ninja-meta', command='scripts/gen.py',
                description='rebuild build.ninja itself')
    writer.build('build.ninja', 'ninja-meta', ['scripts/gen.py', content],
                 variables={'generator':'true'})

if __name__=='__main__':
    buf = io.StringIO()
    gen(ninja.Writer(buf))
    with open('build.ninja', 'w') as fd:
        fd.write(buf.getvalue())
