# depsviz

[![Clojars Project](https://img.shields.io/clojars/v/cnuernber/depsviz.svg)](https://clojars.org/cnuernber/depsviz)

Visualize deps.edn or project.clj dependencies.  Based on the excellent [vizdeps](https://github.com/clj-commons/vizdeps/) project.


## Usage

```bash
clojure -Sdeps '{:deps {cnuernber/depsviz {:mvn/version "0.5"}}}' -m cnuernber.depsviz -i test/data/deps.edn
clojure -Sdeps '{:deps {cnuernber/depsviz {:mvn/version "0.5"}}}' -m cnuernber.depsviz -i test/data/project.clj
```

More example usages are [here](scripts/build-docs.sh).

The graphs can get quite [large](docs/full-example.pdf).  There are two methods to cut down on them:

* [pruning](docs/prune-example.pdf)
* [focusing](docs/focus-example.pdf)

## License

Copyright © 2018 Chris Nuernberger

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
