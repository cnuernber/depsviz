(defproject cnuernber/depsviz "0.7-SNAPSHOT"
  :description "View deps.edn transitive dependencies.  Inspred "
  :url "http://github.com/cnuernber/depsviz"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-tools-deps "0.4.1"]]
  :middleware [lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
  :lein-tools-deps/config {:config-files [:install :user :project]}
  :main cnuernber.depsviz
  :aot [cnuernber.depsviz])
