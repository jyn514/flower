#!/usr/bin/env python3
import os; from os import path
import ninja_syntax as ninja

class Path(os.PathLike):
    def __init__(self,p): self.p=p
    def __truediv__(self, new): return Path(os.path.join(self.p,new))
    def __fspath__(self): return str(self.p)
    def __str__(self): return self.p.__str__()
    def __add__(self, right): return self.p.__add__(right)
    def replace(self, old, new): return self.p.replace(old, new)
content = Path('content')
public = Path('public')
build = Path('.build')

def pollen_build(writer, out, in_, tmp=None):
    if path.splitext(out)[0] == 'template':
        return

    if tmp == None:
        tmp = out
    writer.build(public/out, 'pollen', build/in_, implicit=build,
                 variables={'tmp':build/tmp})

def gen(writer):
    writer.rule(name='tmpdir', command=f'mkdir -p {build}', description='create build dir')
    writer.build(build, 'tmpdir')
    writer.rule(name='link', command='ln -f $in $out',
                description='link $in into build dir')

    # https://docs.racket-lang.org/reference/logging.html#%28tech._log._receiver%29
    writer.rule(name='pollen', command=f'PLTSTDERR=warning@pollen raco pollen render $in && mv $tmp $out',
               description='generate $out from a pollen source file')
    writer.rule(name='frontmatter', command='scripts/split.py $in $out',
               description='transform $in to $out')

    # writer.rule(name='watch', command='scripts/watch.sh',
    #             description='watch the site for changes')
    # writer.build('watch', 'watch')

    for f in os.listdir(content):
        out, in_ = path.splitext(f)
        # https://docs.racket-lang.org/pollen/File_formats.html
        in_ = in_[1:]
        if in_ in ['pp', 'pmd', 'pm', 'ptree', 'scrbl', 'p']:
            writer.build(build/f,  'link', [content/f], implicit=build)
            pollen_build(writer, out, f)
        elif in_ == 'md':
            out += '.html'
            generated = out+'.pmd'
            writer.build(outputs=build/generated, rule='frontmatter', inputs=[content/f], implicit=['scripts/split.py', build])
            pollen_build(writer, out, generated)

    writer.rule(name='ninja-meta', command='scripts/gen.py',
                description='rebuild build.ninja itself')
    writer.build('build.ninja', 'ninja-meta', ['scripts/gen.py', content],
                 variables={'generator':'true'})

if __name__=='__main__':
    with open('build.ninja', 'w') as writer:
        gen(ninja.Writer(writer))
