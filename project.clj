(defproject cnuernber/depsviz "0.3-SNAPSHOT"
  :description "View deps.edn transitive dependencies.  Inspred "
  :url "http://github.com/cnuernber/depsviz"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/tools.cli "0.4.1"]
                 [com.stuartsierra/dependency "0.2.0"]
                 [dorothy "0.0.7"]
                 [medley "1.0.0"]
                 [org.clojure/tools.deps.alpha "0.5.460"]]
  :main cnuernber.depsviz
  :aot [cnuernber.depsviz])
