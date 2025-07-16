#!/usr/bin/env python3
import os; from os import path
import ninja_syntax as ninja

class Path(os.PathLike):
    def __init__(self,p): self.p=p
    def __truediv__(self, new): return os.path.join(self.p,new)
    def __fspath__(self): return str(self.p)
content = Path('content')
build = Path('public')

def pollen_build(writer, in_, out):
    if path.splitext(out)[0] == 'template':
        return
    writer.build(build / out, 'pollen', content / in_,
                    variables={'tmp':content / out})

def gen(writer):
    writer.rule(name='pollen', command='raco pollen render $in && mv $tmp $out',
               description='generate an output from a pollen source file')
    writer.rule(name='frontmatter', command='scripts/split.py $in $out',
               description='transform $in to $out')

    writer.rule(name='ninja-meta', command='scripts/gen.py',
                description='rebuild build.ninja itself')
    writer.build('build.ninja', 'ninja-meta', ['scripts/gen.py'],
                 variables={'generator':'true'})

    # writer.rule(name='watch', command='scripts/watch.sh',
    #             description='watch the site for changes')
    # writer.build('watch', 'watch')

    for f in os.listdir(content):
        out, in_ = path.splitext(f)
        # https://docs.racket-lang.org/pollen/File_formats.html
        in_ = in_[1:]
        if in_ in ['pp', 'pmd', 'pm', 'ptree', 'scrbl', 'p']:
            pollen_build(writer, f, out)
            # if path.splitext(out)[0] == 'template':
            #     continue
            # writer.build(build / out, 'pollen', content / f,
            #              variables={'tmp':content / out})
        elif in_ == 'md':
            tmp = out+'.html.pp'
            print(content/f)
            writer.build(content / tmp, 'frontmatter', content / f, implicit='scripts/split.py')
            pollen_build(writer, f, tmp)

if __name__=='__main__':
    with open('build.ninja', 'w') as writer:
        gen(ninja.Writer(writer))
