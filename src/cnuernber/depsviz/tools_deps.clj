(ns cnuernber.depsviz.tools-deps
  (:require [clojure.tools.deps.alpha :as deps]
            [cnuernber.depsviz.graph :as deps-graph]
            [cnuernber.depsviz.utils :as utils]
            [clojure.edn :as edn]
            [clojure.tools.deps.alpha.util.maven :as mvn]
            [clojure.set :as c-set]))


(defn- expand-path
  [path->nodes path]
  (when-let [target-item (last path)]
    (let [target-path (vec (butlast path))]
      (get-in path->nodes [target-path target-item]))))


;;We need this one inside job.
(defn deps->tools-graph
  [deps-map args-map]
  (let [deps-map (cond
                   (string? deps-map)
                   (-> (slurp deps-map)
                       edn/read-string
                       (assoc :mvn/repos mvn/standard-repos))
                   (map? deps-map)
                   deps-map)
        {:keys [extra-deps default-deps override-deps verbose]} args-map
        deps (merge (:deps deps-map) extra-deps)]
    (-> deps
        (#'deps/canonicalize-deps deps-map)
        (#'deps/expand-deps default-deps override-deps deps-map verbose))))



(defn invert-tools-deps
  "We need to build a top-down graph of what is going on.  The tools.deps is built from
  the opposite perspective as the vizdeps graph; so we need to 'invert' it of sorts."
  [tools-graph]
  (let [path-seq
        (->> tools-graph
             (mapcat (fn [[proj-name {:keys [paths select]}]]
                       (->> paths
                            (mapcat
                             (fn [[item-version path-set]]
                               (map vector path-set (repeat {:name proj-name
                                                             :location item-version
                                                             :select select
                                                             :id [proj-name item-version]}))))))))
        ;;Group things with same path and allow a lookup from symbol->data
        path->nodes (->> (group-by first path-seq)
                         (map (fn [[k v-seq]]
                                [k (->> v-seq
                                        (map second)
                                        ;;Careful to avoid potentially losing information
                                        (group-by :name))]))
                         (into {}))
        graph (->> path->nodes
                   ;;Shortest paths first
                   (sort-by (comp count first))
                   (reduce (fn [graph [path node-map]]
                             (let [node-seq (->> (mapcat second node-map)
                                                 set)
                                   ;;Add all nodes to graph if they aren't already added
                                   graph (->> node-seq
                                              (remove #(deps-graph/node-exists? graph (:id %)))
                                              (map #(assoc % :dot-node-id (utils/dot-node-id (:id %))))
                                              (reduce deps-graph/add-node graph))]
                               (->> (expand-path path->nodes path)
                                    (reduce #(update %1 :edges
                                                     c-set/union
                                                     (->> node-seq
                                                          (map (comp (partial vector (:id %2)) :id))
                                                          set))
                                            graph))))
                           (deps-graph/empty-graph)))
        all-roots (c-set/union (deps-graph/roots graph)
                               (deps-graph/single-islands graph))
        root-name (symbol "root")
        root-version {:mvn/version "1.0.0"}
        root-node {:name root-name
                   :location root-version
                   :select root-version
                   :id [root-name root-version]
                   :dot-node-id (utils/dot-node-id [root-name root-version])}
        root-edges (->> all-roots
                        (map (partial vector (:id root-node)))
                        set)]
    (-> graph
        (deps-graph/add-node root-node)
        (update :edges c-set/union root-edges))))


(defn load-graph
  [fname options]
  (-> (deps->tools-graph fname options)
      invert-tools-deps))
