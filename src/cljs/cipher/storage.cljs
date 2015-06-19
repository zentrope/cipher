(ns cipher.storage)

(def store (.-localStorage js/window))

(defn set-item
  [name value]
  (.setItem store name value))

(defn get-item
  ([name]
   (get-item name nil))
  ([name default]
   (or (.getItem store name) default)))

(defn del-item
  [name]
  (.removeItem store name))
