(ns cnuernber.depsviz.leiningen
  (:require  [leiningen.core.user :as user]
             [leiningen.core.classpath :as classpath]
             [leiningen.core.project :as project]
             [leiningen.core.main :as main]
             [cemerick.pomegranate.aether :as aether]
             [cnuernber.depsviz.graph :as deps-graph]
             [cnuernber.depsviz.utils :as utils]
             [clojure.set :as c-set]))


(defn- dep-vec->node-id
  [dep-vec]
  [(first dep-vec) {:mvn/version (second dep-vec)}])


(defn- ensure-node
  "Ensure node exists.
  returns graph"
  [graph dep-key]
  (if (get graph [:nodes dep-key])
    graph
    (-> graph
        (assoc-in [:nodes dep-key]
                  {:id dep-key
                   :name (first dep-key)
                   :location (second dep-key)
                   :dot-node-id (utils/dot-node-id dep-key)})
        ;;First one wins unless specifically overridden via project.clj
        (update-in [:selected (first dep-key)] #(or % (second dep-key))))))


(defn- get-dependencies
  [project graph dependency]
  (let [parent-key (dep-vec->node-id dependency)
        dep-list (-> (#'classpath/get-dependencies
                      :dependencies nil
                      (assoc project :dependencies [dependency]))
                     (get dependency))]
    (reduce (fn [graph dep-vec]
              (let [dep-key (dep-vec->node-id dep-vec)]
                (if (get-in graph [:nodes dep-key])
                  graph
                  (-> graph
                      (ensure-node dep-key)
                      (update :edges conj [parent-key dep-key])
                      (#(get-dependencies project % dep-vec))))))
            graph
            dep-list)))


(defn user-agent []
  (format "Leiningen/%s (Java %s; %s %s; %s)"
          "2.18.3" (System/getProperty "java.vm.name")
          (System/getProperty "os.name") (System/getProperty "os.version")
          (System/getProperty "os.arch")))


(defn configure-http
  "Set Java system properties controlling HTTP request behavior.  From leiningin-core/main"
  []
  (System/setProperty "aether.connector.userAgent" (user-agent))
  (when-let [{:keys [host port non-proxy-hosts]} (classpath/get-proxy-settings)]
    (System/setProperty "http.proxyHost" host)
    (System/setProperty "http.proxyPort" (str port))
    (when non-proxy-hosts
      (System/setProperty "http.nonProxyHosts" non-proxy-hosts)))
  (when-let [{:keys [host port]} (classpath/get-proxy-settings "https_proxy")]
    (System/setProperty "https.proxyHost" host)
    (System/setProperty "https.proxyPort" (str port))))


(defn lein->graph
  "Command-line entry point."
  [fname options]
  (try
    (project/ensure-dynamic-classloader)
    (aether/register-wagon-factory! "http" #'main/insecure-http-abort)
    (configure-http)
    (let [project (project/read fname)
          profiles (if-not (:dev options)
                   [:user]
                   [:user :dev])
          project (project/set-profiles project profiles)
          root-name (symbol (-> project :group str) (-> project :name str))
          root-version {:mvn/version (:version project)}
          root-id [root-name root-version]
          graph (-> (reduce (fn [graph dependency]
                              (let [dep-key (dep-vec->node-id dependency)
                                    graph (-> (ensure-node graph dep-key)
                                              (assoc-in [:selected (first dep-key)] (second dep-key)))]
                                (get-dependencies project graph dependency)))
                            (assoc (deps-graph/empty-graph)
                                   :selected {})
                            (:dependencies project))
                    (assoc-in [:nodes root-id]
                              {:id root-id
                               :name root-name
                               :location root-version
                               :select root-version
                               :dot-node-id (utils/dot-node-id root-id)})
                    (update :edges c-set/union (->> (:dependencies project)
                                                    (map (comp (partial vector root-id) dep-vec->node-id))
                                                    set)))]
      (update graph :nodes
              (fn [node-map]
                (->> node-map
                     (map (fn [[node-id node-val]]
                            [node-id (assoc node-val :select
                                            (get-in graph [:selected (first node-id)]))]))
                     (into {})))))))
