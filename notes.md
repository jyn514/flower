watched build.ninja to rerun `ninja -t inputs`, but of course that doesn't work if we don't know we need to rerun ninja to regenerate build.ninja
i would normally expect that to come from the depfile, but `ninja -t inputs` is missing depfiles.
something buggy in flower.fs? docs/.build/build.clj.d is missing `pages` directory

need to test fork of ninja.
