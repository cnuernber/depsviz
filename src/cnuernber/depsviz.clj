(ns cnuernber.depsviz
  (:require [clojure.tools.deps.alpha :as deps]
            [cnuernber.depsviz.graph :as deps-graph]
            [clojure.edn :as edn]
            [clojure.tools.deps.alpha.util.maven :as mvn]
            [clojure.set :as c-set]
            [dorothy.core :as dorothy-core]
            [dorothy.jvm :as dorothy-jvm]
            [clojure.java.browse :refer [browse-url]])
  (:gen-class))


;;We need this one inside job.
(defn deps->tools-graph
  [deps-edn]
  (let [deps-edn (cond
                   (string? deps-edn)
                   (-> (slurp deps-edn)
                       edn/read-string)
                   (map? deps-edn)
                   deps-edn)]
    (#'deps/expand-deps (:deps deps-edn)
                        nil nil {:mvn/repos mvn/standard-repos} false)))


(defn- expand-path
  [path->nodes path]
  (when-let [target-item (last path)]
    (let [target-path (vec (butlast path))]
      (get-in path->nodes [target-path target-item]))))


(defn dot-node-id
  [[proj-name maven-coords]]
  (str proj-name (gensym)))


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
                                              (map #(assoc % :dot-node-id (dot-node-id (:id %))))
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
                   :dot-node-id (dot-node-id [root-name root-version])}
        root-edges (->> all-roots
                        (map (partial vector (:id root-node)))
                        set)]
    (-> graph
        (deps-graph/add-node root-node)
        (update :edges c-set/union root-edges))))


(defn merge-nodes-by
  [graph key-fn filter-fn]
  (->> (:nodes graph)
       vals
       (group-by key-fn)
       (map (fn [[item-key nodes-to-merge]]
              (when (and (> (count nodes-to-merge) 1)
                         (first (filter filter-fn nodes-to-merge)))
                nodes-to-merge)))
       (remove nil?)
       (reduce
        (fn [graph node-merge-seq]
          (if-let [target-node (->> (filter filter-fn node-merge-seq)
                                    first)]
            (let [target-id (:id target-node)]
              (->> (remove filter-fn node-merge-seq)
                   (reduce (fn [graph {:keys [id] :as merge-node}]
                             (let [edges-to-from (filter #(contains? (set %) id) (:edges graph))
                                   edges-to (filter #(= id (second %)) edges-to-from)
                                   edges-from (filter #(= id (first %)) edges-to-from)]
                               (-> graph
                                   (update :nodes dissoc id)
                                   (update :edges
                                           (fn [edge-set]
                                             (let [edge-set (c-set/difference edge-set
                                                                              (set edges-to)
                                                                              (set edges-from))]
                                               (c-set/union edge-set (->> edges-to
                                                                          (map #(-> (assoc % 1 target-id)
                                                                                    (conj merge-node)))
                                                                          set))))))))
                           graph)))
            graph))
        graph)))


(defn node-name
  [node-id]
  (format "%s\n%s" (first node-id) (second node-id)))


(defn selected?
  [node]
  (= (:select node)
     (:location node)))


(defn build-dot
  [fname options]
  (let [project (edn/read-string (slurp fname))
        deps-type-tree (deps->tools-graph project)
        deps-graph (-> (invert-tools-deps deps-type-tree)
                       (merge-nodes-by :name selected?))
        node-list (->> (deps-graph/dfs-seq deps-graph)
                       (map (partial deps-graph/get-node deps-graph)))
        root (first node-list)
        node-list (rest node-list)
        root-name (node-name (:id root))
        dot-seq (->> node-list
                     (map (fn [{:keys [select location] :as node}]
                            (let [item-name (node-name (:id node))]
                              [(:dot-node-id node) (merge {:label item-name}
                                                          (when-not (selected? node)
                                                            {:color :red}))])))
                     (concat [{:attrs {:rankdir :LR} :type ::dorothy-core/graph-attrs}
                              [(:dot-node-id root) {:label root-name :shape :doubleoctagon}]]))]
    (->> (:edges deps-graph)
         (map (fn [[lhs rhs conflict-info]]
                (let [parent-node (deps-graph/get-node deps-graph lhs)
                      child-node (deps-graph/get-node deps-graph rhs)]
                  [(:dot-node-id parent-node) (:dot-node-id child-node)
                   (merge
                    {}
                    (when conflict-info
                      {:color :red
                       :label (str (:location conflict-info))
                       :penwidth 2
                       :weight 500}))])))
         (concat dot-seq)
         dorothy-core/digraph
         dorothy-core/dot)))


(defn -main
  [& args]
  (let [fname (or (first args) "deps.edn")]
    (-> (build-dot fname {})
        (dorothy-jvm/save! "target/dependencies.pdf" {:format :pdf}))
    (browse-url "target/dependencies.pdf")
    (shutdown-agents)))
