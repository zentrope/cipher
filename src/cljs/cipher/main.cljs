(ns cipher.main
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [cljs.core.async :as async]
   [clojure.string :as s]
   [cipher.socket :as socket]
   [om.core :as om :include-macros true]
   [sablono.core :as html :refer-macros [html]]
   [goog.date.relative :as rel]))

(enable-console-print!)

;;-----------------------------------------------------------------------------
;; Utils
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

(defn datef
  [ts]
  (rel/format ts))

;;-----------------------------------------------------------------------------
;; State management
;;-----------------------------------------------------------------------------

(def MAX_BUFFER 100)

(defn conj-max
  [data value max-size]
  (let [v (conj data value)]
    (->> (reverse v) (take max-size) reverse vec)))

(defn event-fn
  [state event-ch socket [topic data :as msg]]
  ;; (println "recv>" (pr-str msg))
  (case topic

    :client/handle
    (let [handle (s/replace (:token @state) "anon" data)]
      (om/transact! state #(assoc % :handle handle :edit? false))
      (socket/send! socket [:client/new-name handle]))

    :client/edit
    (om/update! state :edit? true)

    :client/message
    (let [handle (:handle @state)]
      (socket/send! socket [:client/message data]))

    :server/token
    (om/transact! state #(cond-> %
                           (nil? (:handle %)) (assoc :handle data)
                           true (assoc :token data)))

    :server/message
    (om/transact! state :queue #(conj-max % data MAX_BUFFER))

    nil))

;;-----------------------------------------------------------------------------
;; Component utilities
;;-----------------------------------------------------------------------------

(defn deliver!
  [owner msg]
  (let [ch (om/get-shared owner :ch)]
    (async/put! ch msg)))

(defn by-id
  [el-id]
  (.getElementById js/document el-id))

(defn focus!
  [el-id]
  (when-let [el (by-id el-id)]
    (.focus el)))

(defn value!
  [e]
  (.-value (.-target e)))

(defn update!
  [e owner kw]
  (om/set-state! owner kw (value! e)))

(defn submit-on-cr!
  [e owner kw topic]
  (when (= (.-keyCode e) 27)
    (when-let [orig (om/get-state owner :original)]
      (om/set-state! owner kw orig)
      (deliver! owner [topic orig])
      (.stopPropagation e)))
  (when (= (.-keyCode e) 13)
    (let [msg (om/get-state owner kw)]
      (deliver! owner [topic msg])
      (om/set-state! owner kw "")
      (.stopPropagation e))))

(defn sync-down!
  [el-id]
  (when-let [el (by-id el-id)]
    (.scrollIntoView el false)))

;;-----------------------------------------------------------------------------
;; Components
;;-----------------------------------------------------------------------------

(defn message-component
  [{:keys [id handle message ts rel] :as data} owner]
  (om/component
   (html
    [:div.message {:id (str id)}
     [:div.handle handle]
     [:div.text message]
     [:div.date (if rel rel (datef ts))]])))

(defn message-log-component
  [data owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (sync-down! "ml"))
    om/IDidUpdate
    (did-update [_ _ _]
      (sync-down! "ml"))
    om/IRender
    (render [_]
      (html
       [:section#messages {:className (when (:edit? data) "cover")}
        [:div#ml.message-list
         (om/build-all message-component (:queue data) {:key :id})]]))))

(defn gather-name-component
  [data owner]
  (reify

    om/IInitState
    (init-state [_]
      (let [original (first (s/split (:handle data) "-"))]
        {:handle original
         :original original}))

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
                :maxLength 20
                :autoComplete "off"
                :placeholder "Who do you want to be?"
                :onKeyDown #(submit-on-cr! % owner :handle :client/handle)
                :onChange #(update! % owner :handle)}]))))

(defn title-bar-component
  [data owner]
  (om/component
   (html
    [:section#title
     [:div.title "Cipher Chat"]
     (if-not (:edit? data)
       [:div.handle
        {:onClick #(deliver! owner [:client/edit])}
        (first (s/split (:handle data) "-"))]
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
        (when-not (:edit? data)
          [:div.typer
           [:input {:type "text"
                    :id "typer"
                    :value message
                    :maxLength 512
                    :autoComplete "off"
                    :placeholder "Type your message here"
                    :onKeyDown #(submit-on-cr! % owner :message :client/message)
                    :onChange #(update! % owner :message)}]])]))))

(defn status-bar-component
  [data owner]
  (om/component
   (html
    [:section#status
     [:div.copy "Copyright \u00a9 2015 Zentrope LLC. All rights reserved."]])))

(defn root-component
  [data owner]
  (om/component
   (html
    [:section#root
     (om/build title-bar-component data)
     (when (:edit? data)
       (om/build gather-name-component data))
     (om/build message-log-component data)
     (om/build type-message-component data)
     (om/build status-bar-component data)])))

;;-----------------------------------------------------------------------------
;; State
;;-----------------------------------------------------------------------------

(defonce state
  (atom (let [token (str (gensym "anon-"))]
          {:edit? false
           :handle token
           :token token
           :queue []})))

(defonce event-ch
  (async/chan))

(defonce socket
  (socket/socket! "/ws" event-ch "cipher"))

;;-----------------------------------------------------------------------------
;; Bootstrap
;;-----------------------------------------------------------------------------

(defn relative-time-loop
  [state]
  (go (loop []
        (<! (async/timeout (* 1000 30)))
        (om/transact! state :queue  (fn [q] (mapv #(assoc % :rel (datef (:ts %))) q)))
        (recur))))

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
  (socket/open! socket)
  (relative-time-loop (om/root-cursor state)))

(set! (.-onload js/window) main)
