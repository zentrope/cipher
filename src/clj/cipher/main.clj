(ns cipher.main
  (:require
   [aleph.http :as http]
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [manifold.deferred :as d]
   [manifold.stream :as stream]
   [zentrope.lib.routes :as routes])
  (:gen-class))

;;-----------------------------------------------------------------------------
;; Request helpers
;;-----------------------------------------------------------------------------

(defn jar-dir?
  [^java.net.URL url]
  (let [^java.net.JarURLConnection conn (.openConnection url)
        file (.getJarFile conn)
        path (.getEntryName conn)
        dir (.getEntry file (if (.endsWith path "/") path (str path "/")))]
    (and dir (.isDirectory dir))))

(defn- mime-for
  [^java.lang.String path]
  (cond
    (.endsWith path "css") "text/css"
    (.endsWith path "html") "text/html"
    (.endsWith path "js") "application/javascript"
    (.endsWith path "png") "image/png"
    :else "text/plain"))

(defn- uuid
  []
  (str (java.util.UUID/randomUUID)))

;;-----------------------------------------------------------------------------
;; Server protocol
;;-----------------------------------------------------------------------------

(def conns (atom {}))

(add-watch conns :debug (fn [k r o n]
                          (log/info "WATCH: "
                                    (reduce-kv (fn [a k v]
                                                 (conj a (str "stream/" v))) [] n))))

(add-watch conns :count (fn [k r o n]
                          (log/info "WATCH:" (count n))))

(defn add-stream!
  [stream name]
  (swap! conns assoc stream name))

(defn del-stream!
  [stream]
  (swap! conns dissoc stream))

;; API
;; [:client/new-name "string"
;; [:client/message "string"
;; [:client/withdraw nil

(defmulti on-msg!
  (fn [stream [topic data]]
    topic))

(defmethod on-msg! :default
  [stream [topic data :as msg]]
  (log/warn "Unhandled -> " (pr-str msg)))

(defmethod on-msg! :client/new-name
  [stream [topic data :as msg]]
  (swap! conns assoc stream data))

(defmethod on-msg! :client/withdraw
  [stream [topic data :as msg]]
  (swap! conns assoc stream (uuid)))

(defmethod on-msg! :client/message
  [stream [topic data :as msg]]
  (let [h (get @conns stream)]
    (doseq [[s _] @conns]
      (stream/put! s (pr-str [:server/message {:id (gensym "id")
                                               :handle h
                                               :message data}])))))

;;-----------------------------------------------------------------------------
;; Route handlers
;;-----------------------------------------------------------------------------

(defn- home-page
  [req]
  {:status 200
   :headers {"content-type" "text/html"}
   :body (slurp (io/resource "public/index.html"))})

(defn- ping
  [req]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "ok"})

(defn- chat
  [{:keys [headers] :as req}]
  (let [auth-token (:sec-websocket-protocol headers)
        headers {:headers {"sec-websocket-protocol" auth-token}}
        stream @(http/websocket-connection req headers)]
    (log/info "connected:" auth-token)
    (async/go (log/info "client connect")
              (add-stream! stream auth-token)
              (loop []
                (when-let [msg @(stream/take! stream)]
                  (log/info "recv>" msg)
                  (on-msg! stream (edn/read-string msg))
                  (recur)))
              (log/info "client disconnected")
              (del-stream! stream))))

(defn- not-found
  [req]
  {:status 404 :body "Not-found." :headers {"content-type" "text/plain"}})

(defn- resources
  [req]
  (letfn [(good-response [rsrc path]
            {:status 200
             :body (slurp rsrc)
             :headers {"content-type" (mime-for path)}})]
   (let [url (io/resource (.replaceAll (str "public/" (:uri req)) "//" "/"))]
     (if (.startsWith (str url) "file")
       (let [doc (io/file url)]
         (if (and doc (.exists doc) (not (.isDirectory doc)))
           (good-response doc (.getPath doc))
           (not-found req)))
       (if (and url (not (jar-dir? url)))
         (good-response url (str url))
         (not-found req))))))

;;-----------------------------------------------------------------------------
;; Route dispatching
;;-----------------------------------------------------------------------------

(defn- gen-routes
  []
  [{:route "/" :method :get :handler home-page :middleware nil}
   {:route "/ping" :method :get :handler ping :middleware nil}
   {:route "/ws" :method :get :handler chat :middleware nil}])

(defn route!
  [routes req]
  (loop [routes (filter #(= (:request-method req) (:method %)) routes)]
    (if-let [{:keys [route handler middleware]} (first routes)]
      (if (routes/match? route (:uri req))
        (if middleware
          ((middleware handler) req)
          (handler req))
        (recur (rest routes)))
      (resources req))))

;;-----------------------------------------------------------------------------
;; Bootstrap
;;-----------------------------------------------------------------------------

(defn -main
  [& args]
  (log/info "Welcome to the Cipher App")
  (let [lock (promise)
        routes (gen-routes)
        httpd (http/start-server #(route! routes %) {:port 2112})]
    (log/info "Server running on port 2112.")
    (deref lock)))
