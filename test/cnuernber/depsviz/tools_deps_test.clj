(ns cnuernber.depsviz.tools-deps-test
  (:require [clojure.test :refer :all]
            [cnuernber.depsviz.tools-deps :as tools-deps]
            [clojure.set :as c-set]))


(deftest find-all-deps
  (is (= #{}
         (c-set/difference '#{[root {:mvn/version "1.0.0"}]
                             [clj-commons/fs {:mvn/version "1.5.0"}]
                             [com.taoensso/encore {:mvn/version "2.102.0"}]
                             [com.taoensso/nippy {:mvn/version "2.15.0-alpha8"}]
                             [com.taoensso/truss {:mvn/version "1.5.0"}]
                             [net.jpountz.lz4/lz4 {:mvn/version "1.3"}]
                             [org.apache.commons/commons-compress {:mvn/version "1.8"}]
                             [org.clojure/clojure {:mvn/version "1.5.1"}]
                             [org.clojure/clojure {:mvn/version "1.9.0"}]
                             [org.clojure/core.specs.alpha {:mvn/version "0.1.24"}]
                             [org.clojure/spec.alpha {:mvn/version "0.1.143"}]
                             [org.clojure/tools.reader {:mvn/version "1.3.2"}]
                             [org.iq80.snappy/snappy {:mvn/version "0.4"}]
                             [org.tukaani/xz {:mvn/version "1.5"}]
                             [org.tukaani/xz {:mvn/version "1.8"}]}
                          (->> (tools-deps/load-graph "test/data/issue2.edn" {})
                               :nodes
                               keys
                               set)))))
