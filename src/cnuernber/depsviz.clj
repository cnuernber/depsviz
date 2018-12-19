(ns cnuernber.depsviz
  (:require [cnuernber.depsviz.graph :as deps-graph]
            [cnuernber.depsviz.tools-deps :as tools-deps]
            [cnuernber.depsviz.leiningen :as leiningen]
            [clojure.set :as c-set]
            [dorothy.core :as dorothy-core]
            [dorothy.jvm :as dorothy-jvm]
            [clojure.java.browse :refer [browse-url]]
            [clojure.string :as str]
            [clojure.tools.cli :as cli]
            [clojure.java.io :as io])
  (:gen-class))


(defn selected?
  [node]
  (= (:select node)
     (:location node)))


(defn do-prune
  "Prune all items that are a conflict or are dependent something that has a conflict."
  [graph]
  (->> (:edges graph)
       (filter #(> (count %) 2))
       (map second)
       (deps-graph/keep-only graph)))


(defn do-focus
  [graph node-names]
  (->> node-names
       (mapcat (partial deps-graph/find-nodes graph))
       (deps-graph/keep-only graph)))


(defn do-highlight
  [graph node-names]
  (let [child->parent-map (deps-graph/child->parent-map graph)]
    (->> node-names
         (mapcat (partial deps-graph/find-nodes graph))
         (mapcat (partial deps-graph/path-to-root child->parent-map))
         distinct
         (reduce #(update-in %1 [:nodes %2] assoc :highlight? true)
                 graph))))


(defn process-graph-options
  [graph options]
  (let [{:keys [prune focus highlight]} options]
    (cond-> graph
      prune
      do-prune
      focus
      (do-focus focus)
      highlight
      (do-highlight highlight))))


(defn node-name
  [node-id]
  (format "%s\n%s" (first node-id) (second node-id)))


(defn graph->dot
  [deps-graph options]
  (let [deps-graph (process-graph-options deps-graph options)
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
                                                            {:color :red})
                                                          (when (:highlight? node)
                                                            {:color :blue}))])))
                     (concat [{:attrs {:rankdir (if (:vertical options)
                                                  :TB
                                                  :LR)}
                               :type ::dorothy-core/graph-attrs}
                              [(:dot-node-id root) {:label root-name :shape :doubleoctagon}]]))]
    (->> (:edges deps-graph)
         (map (fn [[lhs rhs conflict-info]]
                (let [parent-node (deps-graph/get-node deps-graph lhs)
                      child-node (deps-graph/get-node deps-graph rhs)]
                  [(:dot-node-id parent-node) (:dot-node-id child-node)
                   (merge
                    {}
                    (when (:highlight? child-node)
                      {:pendwidth 2
                       :weight 500
                       :color :blue})
                    (when conflict-info
                      {:color :red
                       :label (str (:location conflict-info))
                       :penwidth 2
                       :weight 500}))])))
         (concat dot-seq)
         dorothy-core/digraph
         dorothy-core/dot)))


(defn- extension
  [^String item]
  (let [last-idx (.lastIndexOf item ".")]
    (if (> last-idx 0)
      (.substring item (+ last-idx 1))
      "")))


(defn build-dot
  [fname options]
  (-> (case (extension fname)
        "edn" (tools-deps/load-graph fname options)
        "clj" (leiningen/lein->graph fname options))
      (deps-graph/merge-nodes-by :name selected?)
      (graph->dot options)))


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
  [["-f" "--focus ARTIFACT" "Excludes artifacts whose names do not match a supplied value. Repeatable."
    :assoc-fn conj-option]
   ["-H" "--highlight ARTIFACT" "Highlight the artifact, and any dependencies to it, in blue. Repeatable."
    :assoc-fn conj-option]
   ["-i" "--input FNAME" "File to draw dependencies from"
    :id :input]
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


(defn doit
  [args]
  (let [options (parse-cli-options "depsviz" vizdeps-cli-options args)
        out-format (-> (:output-path options)
                       extension
                       keyword)
        input-file (or (:input options)
                       (->> ["deps.edn" "project.clj"]
                            (filter #(.exists (io/file %)))
                            first))]
    (when-not  (.exists (io/file input-file))
      (throw (ex-info "Input file does not exist:" {:input input-file})))
    (let [dot-data (build-dot input-file options)
          output-path (:output-path options)]

      (dorothy-jvm/save! dot-data output-path {:format :pdf})

      (when (:save-dot options)
        (let [x (str/last-index-of output-path ".")
              dot-path (str (subs output-path 0 x) ".dot")
              ^File dot-file (io/file dot-path)]
          (spit dot-file dot-data)))

      (when-not (:no-view options)
        (browse-url output-path)))))


(defn -main
  [& args]
  (doit args)
  (shutdown-agents))
