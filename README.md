# depsviz

[![Clojars Project](https://img.shields.io/clojars/v/cnuernber/depsviz.svg)](https://clojars.org/cnuernber/depsviz)

Visualize deps.edn or project.clj dependencies.  Based on the excellent [vizdeps](https://github.com/clj-commons/vizdeps/) project.


## Usage


You must install graphviz:

```
sudo apt install graphviz
```

```
brew install graphviz
```


### Simple Command Line

First install the script:

```bash
wget https://raw.githubusercontent.com/cnuernber/depsviz/master/scripts/depsviz && \
sudo cp depsviz /usr/local/bin
```

Next go into your project directory and type:
```bash
depsviz
```

The system will search for project.clj or deps.edn and display dependencies.


### Raw Command Line

```bash
clojure -Sdeps '{:deps {cnuernber/depsviz {:mvn/version "0.6"}}}' -m cnuernber.depsviz -i test/data/deps.edn
clojure -Sdeps '{:deps {cnuernber/depsviz {:mvn/version "0.6"}}}' -m cnuernber.depsviz -i test/data/project.clj
```

More example usages are [here](scripts/build-docs.sh).

The graphs can get quite [large](docs/full-example.pdf).  There are two methods to cut down on them:

* [pruning](docs/prune-example.pdf)
* [focusing](docs/focus-example.pdf)


#### Full List of Options
```bash
Usage: depsviz [options]

Options:
  -f, --focus ARTIFACT                           Excludes artifacts whose names do not match a supplied value. Repeatable.
  -H, --highlight ARTIFACT                       Highlight the artifact, and any dependencies to it, in blue. Repeatable.
  -i, --input FNAME                              File to draw dependencies from. Defaults to (first-that-exists ["deps.edn" "project.clj"]).
  -w, --with-profiles PROFILE                    List of leiningen profiles (defaults to user).  Additive only.  Repeatable.
  -r, --remove ARTIFACT                          Excludes artifaces whose names match supplied value (defaults to org.clojure). Repeatable.
  -n, --no-view                                  If given, the image will not be opened after creation.
  -o, --output-file FILE       dependencies.pdf  Output file path. Extension chooses format: pdf or png.
  -p, --prune                                    Exclude artifacts and dependencies that do not involve version conflicts.
  -s, --save-dot                                 Save the generated GraphViz DOT file well as the output file.
  -v, --vertical                                 Use a vertical, not horizontal, layout.
  -h, --help                                     This usage summary.
```

## License

Copyright © 2018 Chris Nuernberger

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
