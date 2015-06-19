(ns cipher.main
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [cljs.core.async :as async]
   [clojure.string :as s]
   [cipher.socket :as socket]
   [om.core :as om :include-macros true]
   [sablono.core :as html :refer-macros [html]]))

(enable-console-print!)

;;-----------------------------------------------------------------------------

(defn pchan
  [f ch]
  (go-loop []
    (when-let [v (async/<! ch)]
      (try
        (f v)
        (catch js/Error e
          (async/put! ch [:system/err {:value v :exception e}])))
      (recur))))

;;-----------------------------------------------------------------------------

(def MAX_BUFFER 100)

(defn conj-max
  [data value max-size]
  (let [v (conj data value)]
    (->> (reverse v) (take max-size) reverse vec)))

(defn event-fn
  [state event-ch socket [topic data :as msg]]
  (println "recv>" (pr-str msg))
  (case topic
    :client/handle (do (om/update! state :handle data)
                       (socket/send! socket [:client/new-name data]))
    :client/leave  (do (om/update! state :handle nil)
                       (socket/send! socket [:client/withdraw]))
    :client/message (let [handle (:handle @state)]
                      (socket/send! socket [:client/message data]))
    :socket/open (if-let [handle (:handle @state)]
                   (socket/send! socket [:client/new-name handle])
                   (async/put! event-ch [:client/new-name (str (random-uuid))]))

    :server/message
    (om/transact! state :queue #(conj-max % data MAX_BUFFER))
    nil))

;;-----------------------------------------------------------------------------
;; Component utilities

(defn deliver!
  [owner msg]
  (let [ch (om/get-shared owner :ch)]
    (async/put! ch msg)))

(defn focus!
  [el-id]
  (when-let [el (.getElementById js/document el-id)]
    (.focus el)))

(defn value!
  [e]
  (.-value (.-target e)))

(defn update!
  [e owner kw]
  (om/set-state! owner kw (value! e)))

(defn submit-on-cr!
  [e owner kw topic]
  (when (= (.-keyCode e) 13)
    (let [msg (om/get-state owner kw)]
      (deliver! owner [topic msg])
      (om/set-state! owner kw "")
      (.stopPropagation e))))

;;-----------------------------------------------------------------------------
;; Components

(defn message-component
  [{:keys [handle message] :as data} owner]
  (om/component
   (html
    [:div.message
     [:div.handle handle]
     [:div.text message]])))

(defn message-log-component
  [data owner]
  (om/component
   (html
    [:section#messages {:className (when-not (:handle name) "cover")}
     ;;[:h2 "message log"]
     [:div.message-list
      (om/build-all message-component (:queue data) {:key :id})]])))

(defn gather-name-component
  [data owner]
  (reify

    om/IInitState
    (init-state [_]
      {:handle ""})

    om/IDidMount
    (did-mount [_]
      (focus! "widget"))

    om/IRenderState
    (render-state [_ {:keys [handle]}]
      (html
       [:input {:ref "widget"
                :id "widget"
                :type "text"
                :value handle
                :placeholder "Who do you want to be?"
                :onKeyDown #(submit-on-cr! % owner :handle :client/handle)
                :onChange #(update! % owner :handle)}]))))

(defn title-bar-component
  [data owner]
  (om/component
   (html
    [:section#title
     [:div.title "Cipher Chat"]
     (if-let [handle (:handle data)]
       [:div.handle handle]
       [:div.gather (om/build gather-name-component data)])])))

(defn type-message-component
  [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:message ""})

    om/IDidMount
    (did-mount [_]
      (focus! "typer"))

    om/IRenderState
    (render-state [_ {:keys [message]}]
      (html
       [:section#sender
        (when-not (s/blank? (:handle data))
          [:div.typer
           [:input {:type "text"
                    :id "typer"
                    :value message
                    :placeholder "Type your message here"
                    :onKeyDown #(submit-on-cr! % owner :message :client/message)
                    :onChange #(update! % owner :message)}]])]))))

(defn status-bar-component
  [data owner]
  (om/component
   (html
    [:section#status
     [:div.copy "(c) 2015 Zentrope LLC"]
     (when-not (nil? (:handle data))
       [:div.signout
        [:button {:onClick #(do (deliver! owner [:client/leave nil])
                                (.stopPropagation %))} "LEAVE"]])])))

(defn root-component
  [data owner]
  (om/component
   (html
    [:section#root
     (om/build title-bar-component data)
     (when (s/blank? (:handle data))
       (om/build gather-name-component data))
     (om/build message-log-component data)
     (om/build type-message-component data)
     (om/build status-bar-component data)])))

;;-----------------------------------------------------------------------------

(defonce state
  (atom {:handle nil
         :queue []}))

(defonce event-ch
  (async/chan))

(defonce socket
  (socket/socket! "/ws" event-ch "cipher"))

;;-----------------------------------------------------------------------------

(defn mount-root
  [state event-ch socket]
  (let [options {:target (. js/document (getElementById "mount"))
                 :shared {:ch event-ch}
                 :opts {}}]
    (om/root root-component state options)))

(defn reload
  []
  (mount-root state event-ch socket))

(defn main
  []
  (println "Welcome to the Cipher Web Client")
  (pchan (partial event-fn (om/root-cursor state) event-ch socket) event-ch)
  (mount-root state event-ch socket)
  (socket/open! socket))

(set! (.-onload js/window) main)
