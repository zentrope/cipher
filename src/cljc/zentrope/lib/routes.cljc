(ns zentrope.lib.routes
  (:require
   [clojure.string :as s]))

(defn- parse-uri
  [uri]
  (->> (s/split uri #"/")
       (filterv #(not (s/blank? %)))))

(defn- compile-route
  [route]
  (->> (parse-uri route)
       (mapv #(if (= (first %) \:) (keyword (subs % 1)) %))))

(defn- match=
  [route-part uri-part]
  (cond
    (keyword? route-part) true
    (= route-part uri-part) true
    :else false))

(defn match'
  [rs us]
  (if (not= (count rs) (count us))
    nil
    (loop [[r & rs] rs
           [u & us] us
           data {}]
      (cond
        (nil? r) data
        (not (match= r u)) nil
        :else (recur rs us (if (keyword? r) (assoc data r u) data))))))

;;-----------------------------------------------------------------------------
;; Public

(defn match?
  [route uri]
  (let [rs (compile-route route)
        us (parse-uri uri)]
    (match' rs us)))

(defn match-routes
  [routes uri]
  (let [u (parse-uri uri)]
    (loop [[r & rs] (keys routes)]
      (if (nil? r)
        nil
        (if-let [data (match' r u)]
          {:handler (get routes r) :data data}
          (recur rs))))))

(defn make-routes
  [route-map]
  (reduce-kv (fn [a k v] (assoc a (compile-route k) v)) {} route-map))

(comment

  (def routes
    (make-routes
     {"/accounts/:id/thing" :route1
      "/accounts/thing"     (fn [req] [req])
      "/accounts/"          :route3}))

  (def uri "/accounts/23/thing")

  (match-routes routes uri)

  )
