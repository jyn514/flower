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

# sources
pages = Path('pages')
postprocessors = Path('postprocessors')
public = Path('public')

# internals
build = Path('.build')
scripts = Path('scripts')
blossom_p = Path('blossom')
# TODO: globs
blossom_fs = [blossom_p/"core.clj", blossom_p/"deps.edn"]
blossom = 'clj -M --main blossom'

all_pages = os.listdir(pages)

# if this links the file into public/, it MUST return None
def register(writer, f):
    out, in_ = path.splitext(f)
    in_ = in_[1:]
    base = Path(out)[1:]
    # if in_ == 'clj':
    #     writer.build(build/base, 'blossom', f, implicit=blossom)
    #     return build/base
    if in_ == 'md':  # page
        json_in = build/base + '.md.json'
        md = build/base + '.md'
        writer.build(json_in, 'frontmatter', f, implicit=blossom_fs)
        writer.build(md, 'page', json_in, implicit=blossom_fs)
        # TODO: parse frontmatter to see if there's a custom preprocessor
        # TODO: templates
        # TODO: templates should be able to embed custom metadata, so it doesn't have to be duplicated between each page.
        # but for now just hard-code the original metadata.
        # json_out = build/base + '.embed.md.json'
        embedded = build/base + '.embed.md'
        template = "FIXME BAD YOU DON'T HAVE DEPENDENCY TRACKING"
        writer.build(embedded, 'template', [md, json_in], variables={"page": f, "template": template})

        writer.build(public/base + '.html', 'markdown', embedded)
    #     out += '.html'
    #     generated = out+'.pmd'
    #     writer.build(outputs=build/generated, rule='frontmatter', inputs=[f], implicit=['scripts/split.py', build])
    #     pollen_build(writer, out, generated)

def gen(writer):
    # build munging
    writer.variable('builddir', {build})
    writer.rule(name='tmpdir', command=f'mkdir -p {build}', description='create build dir')
    writer.build(build, 'tmpdir')
    writer.rule(name='link', command='ln -f $in $out',
                description='link $in into build dir')

    # pages, templates, postprocessors
    writer.rule(name='page', command=f'{blossom} render-page < $in > $out',
                description='render $in using clojure')
    writer.rule(name='template', command=f'{blossom} embed-template < $in > $out',
                description='embed $page into $template using clojure')
    writer.rule(name='postprocessor', command=f'{blossom} postprocess < $in > $out',
                description='transform $html with $postprocessor using clojure')
    writer.rule(name='frontmatter', command=f'{blossom} split-frontmatter < $in > $out')

    # built-in custom commands
    writer.rule(name='markdown', command=f'pulldown-cmark -TFSULG $in > $out',
                description='render markdown -> HTML: $in -> $out')

    # writer.rule(name='watch', command='scripts/watch.sh',
    #             description='watch the site for changes')
    # writer.build('watch', 'watch')

    for f in all_pages:
        f = pages/f
        while True:
            if new := register(writer, f):
                f = new
                continue
            if f[-2] == build:
                writer.build(public/f,  'link', build/f)
            break


    writer.rule(name='ninja-meta', command=scripts/'gen.py',
                description='rebuild build.ninja itself')
    writer.build('build.ninja', 'ninja-meta', [scripts/'gen.py', pages],
                 variables={'generator':'true'})

if __name__=='__main__':
    buf = io.StringIO()
    gen(ninja.Writer(buf))
    with open('build.ninja', 'w') as fd:
        fd.write(buf.getvalue())
