(ns cipher.main
  (:require
   [aleph.http :as http]
   [clojure.core.async :as async]
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]
   [clojure.string :as s]
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
  (str (gensym "anon-")))

(defn- now
  []
  (System/currentTimeMillis))

(defn conj-max
  [data value max-size]
  (let [v (conj data value)]
    (->> (reverse v) (take max-size) reverse vec)))

(defn mk-msg
  [handle text]
  [:server/message {:id (gensym "id") :handle handle :message text :ts (now)}])

(defn trim-to
  [s size]
  (let [s (s/trim (or s ""))]
    (if (<= (count s) size)
      s
      (subs s size))))

(defn conn-watch
  [k r o n]
  (log/debug "WATCH: (" (count n) "): "
             (reduce-kv (fn [a k v]
                          (conj a (str "stream/" v))) [] n)))

;;-----------------------------------------------------------------------------
;; Server protocol
;;-----------------------------------------------------------------------------

(def MAX_HISTORY 20)

(def history (atom []))
(def conns (atom {}))

(add-watch conns :debug conn-watch)

(defn add-stream!
  [stream name]
  (swap! conns assoc stream name))

(defn del-stream!
  [stream]
  (swap! conns dissoc stream))

(defn catch-up!
  [stream]
  (doseq [msg @history]
    (stream/put! stream (pr-str msg))))

(defn send-anon!
  [stream token]
  @(stream/put! stream (pr-str [:server/token token])))

;; API
;; [:client/new-name "string"
;; [:client/message "string"
;; [:client/withdraw nil
;; [:client/ping]

(defmulti on-msg!
  (fn [stream [topic data]]
    topic))

(defmethod on-msg! :default
  [stream [topic data :as msg]]
  (log/warn "Unhandled -> " (pr-str msg)))

(defmethod on-msg! :client/ping
  [stream [topic data :as msg]]
  (stream/put! stream (pr-str [:server/pong])))

(defmethod on-msg! :client/new-name
  [stream [topic data :as msg]]
  (let [[root id] (s/split data #"-" 2)
        handle (str (trim-to root 20) "-" (trim-to id 40))]
    (swap! conns assoc stream handle)))

(defmethod on-msg! :client/withdraw
  [stream [topic data :as msg]]
  (swap! conns assoc stream (uuid)))

(defmethod on-msg! :client/message
  [stream [topic data :as msg]]
  (let [h (get @conns stream)
        text (trim-to data 512)
        msg (mk-msg h text)]
    (when-not (s/blank? text)
      (swap! history #(conj-max % msg MAX_HISTORY))
      (doseq [[s _] @conns]
        (stream/put! s (pr-str msg))))))

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
    (async/go (log/info "client connected")
              (let [token (uuid)]
                (add-stream! stream token)
                (send-anon! stream token))
              (catch-up! stream)
              (loop []
                (when-let [msg @(stream/take! stream)]
                  (log/debug "recv>" msg)
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
        addr (java.net.InetSocketAddress. "127.0.0.1" 2112)
        httpd (http/start-server #(route! routes %) {:socket-address addr})]
    (log/info "Server running on port 2112.")
    (deref lock)))
