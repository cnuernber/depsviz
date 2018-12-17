(ns cnuernber.depsviz
  (:require [clojure.tools.deps.alpha :as deps]
            [cnuernber.depsviz.graph :as deps-graph]
            [clojure.edn :as edn]
            [clojure.tools.deps.alpha.util.maven :as mvn]
            [clojure.set :as c-set]
            [dorothy.core :as dorothy-core]
            [dorothy.jvm :as dorothy-jvm]
            [clojure.java.browse :refer [browse-url]]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [clojure.java.io :as io])
  (:gen-class))


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
  (let [deps-type-tree (deps->tools-graph fname options)
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
                              [(:dot-node-id root) {:label root-name :shape :doubleoctagon}]]))
        retval (->> (:edges deps-graph)
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
                    dorothy-core/dot)]
    retval))


(def cli-help ["-h" "--help" "This usage summary."])

(def cli-save-dot ["-s" "--save-dot" "Save the generated GraphViz DOT file well as the output file."])

(def cli-no-view
  ["-n" "--no-view" "If given, the image will not be opened after creation."
   :default false])

(defn ^:private allowed-extension
  [path]
  (let [x (str/last-index-of path ".")
        ext (subs path (inc x))]
    (#{"png" "pdf"} ext)))

(defn cli-output-file
  [default-path]
  ["-o" "--output-file FILE" "Output file path. Extension chooses format: pdf or png."
   :id :output-path
   :default default-path
   :validate [allowed-extension "Supported output formats are 'pdf' and 'png'."]])

(def cli-vertical
  ["-v" "--vertical" "Use a vertical, not horizontal, layout."])

(defn conj-option
  "Used as :assoc-fn for an option to conj'es the values together."
  [m k v]
  (update m k conj v))

(defn ^:private usage
  [command summary errors]
  (->> [(str "Usage: depsviz [fname?] [options]")
        ""
        "Options:"
        summary]
       (str/join \newline)
       println)

  (when errors
    (println "\nErrors:")
    (doseq [e errors] (println " " e)))

  nil)


(def vizdeps-cli-options
  [["-d" "--dev" "Include :dev dependencies in the graph."]
   ["-f" "--focus ARTIFACT" "Excludes artifacts whose names do not match a supplied value. Repeatable."
    :assoc-fn conj-option]
   ["-H" "--highlight ARTIFACT" "Highlight the artifact, and any dependencies to it, in blue. Repeatable."
    :assoc-fn conj-option]
   ["-i" "--input FNAME" "File to draw dependencies from"
    :id :input
    :default "deps.edn"]
   cli-no-view
   (cli-output-file "dependencies.pdf")
   ["-p" "--prune" "Exclude artifacts and dependencies that do not involve version conflicts."]
   cli-save-dot
   cli-vertical
   cli-help])


(defn parse-cli-options
  "Parses the CLI options; handles --help and errors (returning nil) or just
  returns the parsed options."
  [command cli-options args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (if (or (:help options) errors)
      (usage command summary errors)
      options)))


(defn- extension
  [^String item]
  (let [last-idx (.lastIndexOf item ".")]
    (if (> last-idx 0)
      (.substring item (+ last-idx 1))
      "")))


(defn doit
  [args]
  (let [options (parse-cli-options "depsviz" vizdeps-cli-options args)
        out-format (-> (:output-path options)
                       extension
                       keyword)]
    (when-not (.exists (io/file (:input options)))
      (throw (ex-info "Input file does not exist:" {:input (:input options)})))
    (-> (build-dot (:input options) options)
        (dorothy-jvm/save! (:output-path options) {:format :pdf}))
    (when-not (:no-view options)
      (browse-url (:output-path options)))))


(defn -main
  [& args]
  (doit args)
  (shutdown-agents))
