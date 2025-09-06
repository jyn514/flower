## setup

### [install ninja](https://github.com/ninja-build/ninja/wiki/Pre-built-Ninja-packages)

### install the flower binary

two options:
#### download a static binary
1. go to https://github.com/jyn514/flower/actions?query=event%3Apush+branch%3Adev+is%3Asuccess
2. click on the latest successful action
3. scroll down to "Artifacts"

#### build from source

See [CONTRIBUTING.md](../CONTRIBUTING.md).

## create your site

1. `mkdir my-site`
2. `cd my-site`
3. `flower new`
4. `flower watch`

`flower new` generates the skeleton of a site in the current directory.
feel free to edit any files it generates.

add your posts in `pages`. for more information, see [the guide](docs/guide.md).
