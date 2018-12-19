(ns cnuernber.depsviz.utils)


(defn dot-node-id
  [[proj-name maven-coords]]
  (str proj-name (gensym)))
