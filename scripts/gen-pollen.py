def pollen_build(writer, out, in_, tmp=None):
    if path.splitext(out)[0] == 'template':
        return

    if tmp == None:
        tmp = out
    writer.build(public/out, 'pollen', build/in_, implicit=build,
                 variables={'tmp':build/tmp})

# https://docs.racket-lang.org/reference/logging.html#%28tech._log._receiver%29
writer.rule(name='pollen', command=f'PLTSTDERR=warning@pollen raco pollen render $in && mv $tmp $out',
            description='generate $out from a pollen source file')
writer.rule(name='frontmatter', command='scripts/split.py $in $out',
            description='transform $in to $out')

if in_ in ['pp', 'pmd', 'pm', 'ptree', 'scrbl', 'p']:
    writer.build(build/f,  'link', [content/f], implicit=build)
    pollen_build(writer, out, f)
elif in_ == 'md':
    out += '.html'
    generated = out+'.pmd'
    writer.build(outputs=build/generated, rule='frontmatter', inputs=[content/f], implicit=['scripts/split.py', build])
    pollen_build(writer, out, generated)
