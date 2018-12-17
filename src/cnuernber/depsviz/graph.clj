(ns cnuernber.depsviz.graph
  (:require [clojure.set :as c-set]))


(defn edges
  [graph]
  (get graph :edges))


(defn edges->map
  [graph key-fn val-fn]
  (->> (edges graph)
       (group-by key-fn)
       (map (fn [[k v]]
              [k (map val-fn v)]))
       (into {})))


(defn parent->child-map
  [graph]
  (edges->map graph first second))


(defn child->parent-map
  [graph]
  (edges->map graph second first))


(defn empty-graph
  "Create an empty graph, which is stored as a map of:
  {:edges [] adjacency list of [id id]
   :id->node-map {} each node has an id and a type
   }"
  []
  {:nodes   {}
   :edges   #{}})


(defn node-exists?
  [graph node-id]
  (get-in graph [:nodes node-id]))


(defn get-node
  [graph node-id]
  (let [retval (node-exists? graph node-id)]
    (when-not retval
      (throw (ex-info "Failed to find node:"
                      {:node-id node-id
                       :nodes (keys (get graph :nodes))})))
    retval))


(defn add-node
  "Add a node to the graph with a list of predecessors.  If the node has no id one will
  be generated; if it does and it is not unique and exception will be thrown.
  If any of the predecessors does not exist an error will be thrown.  Returns a pair
  of [graph node-id]"
  [graph node]
  (when-not (contains? graph :nodes)
    (throw (ex-info "nil graph in add-node"
                    {:graph graph
                     :node node})))
  (when-not (:id node)
    (throw (ex-info "Node has no id" {})))
  (when-let [existing (node-exists? graph (:id node))]
    (throw (ex-info "Graph already contains node with id"
                    {:id (:id node)
                     :existing existing})))
  (assoc-in graph [:nodes (get node :id)] node))


(defn add-edge
  [graph parent-id child-id]
  (update graph :edges conj [parent-id child-id]))


(defn parent-seq
  [graph]
  (map first (edges graph)))


(defn child-seq
  [graph]
  (map second (edges graph)))


(defn parent-set
  [graph]
  (-> (parent-seq graph)
      set))


(defn child-set
  [graph]
  (-> (child-seq graph)
      set))


(defn set->ordered-vec
  [item-set item-seq]
  (->> (filter item-set item-seq)
       distinct
       vec))


(defn roots
  [graph]
  (-> (c-set/difference (parent-set graph) (child-set graph))
      (set->ordered-vec (parent-seq graph))))


(defn single-islands
  "Things that have no connections at all.  Can be interpreted as either
  root or leaf."
  [graph]
  (let [node-set (set (keys  (:nodes graph)))]
    (c-set/difference node-set (parent-set graph) (child-set graph))))


(defn leaves
  [graph]
  (-> (c-set/difference (child-set graph) (parent-set graph))
      (set->ordered-vec (child-seq graph))))


(defn dfs-seq
  "Get a sequence of ids in dfs order."
  [graph]
  (let [p->c-map (-> (parent->child-map graph)
                     (assoc :roots (roots graph)))]

    (->> (tree-seq #(contains? p->c-map %)
                   #(get p->c-map %)
                   :roots)
         (drop 1)
         ;;Account for cases where the graph has multiple roots.  by taking the last occurance
         ;;of a multiply-occuring node.  this ensures that a child will not get visited until
         ;;after every parent has been visited.
         reverse
         distinct
         reverse)))


(defn relative-dfs-seq
  [graph node-id]
  (let [p->c-map (parent->child-map graph)]
    (tree-seq #(contains? p->c-map %)
              #(get p->c-map %)
              node-id)))
