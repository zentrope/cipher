(ns cipher.socket
  (:require-macros
   [cljs.core.async.macros :refer [go]])
  (:require
   [cljs.core.async :as a :refer [put! chan timeout]]
   [cljs.reader :as reader]))

;;-----------------------------------------------------------------------------
;; Private
;;-----------------------------------------------------------------------------

(defn ^:private ws-route
  [path]
  (let [loc (.-location js/window)
        host (.-hostname loc)
        port (.-port loc)
        proto (if (= (.-protocol loc) "https:") "wss:" "ws:")]
    (str proto "//" host ":" port path)))

(defn ^:private test-socket
  [url ch]
  (let [sock (js/WebSocket. url "unauthorized")]
    (aset sock "onerror" #(put! ch :error))
    (aset sock "onclose" #(put! ch :closed))
    (aset sock "onopen" #(put! ch :opened))
    sock))

(defn ^:private close-test-socket!
  [s c]
  (aset s "onclose" nil)
  (.close s)
  (a/close! c))

(declare open!)
(declare close!)

(defn ^:private re-connect!
  [{:keys [url ch] :as socket}]
  (close! socket)
  (put! ch [:socket/down])
  (go (loop [c (chan)
             attempts 1
             new-ws (test-socket url c)]
        (if (= (<! c) :opened)
          (close-test-socket! new-ws c)
          (do (close-test-socket! new-ws c)
              (put! ch [:socket/retry-fail {:count attempts}])
              (<! (timeout 5000))
              (let [nc (chan)]
                (recur nc (inc attempts) (test-socket url nc))))))
      (open! socket)))

;;-----------------------------------------------------------------------------
;; Public
;;
;; Lifecycle events placed on event-ch (as well as raw msg):
;;
;;   [:socket/open                            ] -- when the socket is open
;;   [:socket/close                           ] -- when the socket is closed
;;   [:socket/error       {:keys [err]}       ] -- on socket error
;;   [:socket/down                            ] -- socket's down (server-down)
;;   [:socket/retry-fail  {:keys [count]}     ] -- retry to server failed
;;   [:socket/send-fail   {:keys [reason msg]}] -- send failure
;;-----------------------------------------------------------------------------

(defn open!
  [{:keys [url ws ch connected? auth-token] :as socket}]
  (let [sock (js/WebSocket. url @auth-token)]
    (aset sock "onerror"   #(put! ch [:socket/error {:err %}]))
    (aset sock "onmessage" #(put! ch (reader/read-string (.-data %))))
    (aset sock "onclose"   #(re-connect! socket))
    (aset sock "onopen"    #(put! ch [:socket/open]))
    (reset! ws sock)
    (reset! connected? true)))

(defn close!
  [{:keys [url ws ch connected?] :as socket}]
  (aset @ws "onclose" nil)
  (.close @ws)
  (reset! ws nil)
  (reset! connected? false))

(defn reconnect!
  [socket]
  (close! socket)
  (reset! (:auth-token socket) "unauthorized")
  (open! socket))

(defn send!
  [{:keys [ws ch connected?] :as socket} msg]
  (if @connected?
    (try
      (.send @ws (pr-str msg))
      (catch js/Error e
        (put! ch [:socket/error {:err e}])))
    (put! ch [:socket/send-fail {:reason :not-connected :msg msg}])))

(defn socket!
  [path event-ch auth-token]
  {:url (ws-route path)
   :auth-token (atom auth-token)
   :connected? (atom false)
   :ws (atom nil)
   :ch event-ch})
