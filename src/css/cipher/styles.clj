(ns cipher.styles
  (:require
   [garden.def :refer [defstyles]]
   [garden.units :refer [px pt ms vw vh percent]]))

(def globals
  [[:body
    {:font-family ["helvetica", "sans-serif"]
     :font-weight 200
     :background-color :#ccc
     :padding 0
     :margin 0}]
   [:*
    {:box-sizing :border-box}]
   [:a
    {:text-decoration :none
     :cursor :pointer
     :color :#369}
    [:&:hover
     {:color :darkred}]]])

;;-----------------------------------------------------------------------------

(def title-bar
  [[:section#title {:position :fixed
                    :top 0 :left 0 :right 0 :height (px 40)
                    :background-color :#303e4d
                    :border-bottom [[(px 1) :solid :black]]}
    [:div.title {:position :absolute
                 :bottom (px 3)
                 :left (px 15)
                 :color :#aaa
                 :letter-spacing (pt 1)
                 :font-size (pt 18)
                 :font-weight 100}]
    [:div.handle {:position :absolute
                  :padding 0
                  :padding-right (px 15)
                  :bottom (px 3)
                  :right (px 0)
                  :text-align :right
                  :color :lime
                  :letter-spacing (pt 1)
                  :font-size (pt 18)
                  :width (px 300)
                  :overflow :hidden
                  :text-overflow :ellipsis
                  :white-space :nowrap
                  :font-weight 100
                  :cursor :pointer}]
    [:div.gather {:position :absolute
                  :right 0
                  :top 0
                  :bottom 0
                  :width (px 300)}
     [:input {:width (percent 100)
              :height (percent 100)
              :outline :none
              :border :none
              :padding 0
              :margin 0
              :color :lime
              :background-color :#222
              :letter-spacing (pt 1)
              :padding-left (px 18)
              :padding-top (px 4)
              :font-size (pt 15)}]
     ["::-webkit-input-placeholder" {:color :#448}]
     ["::-moz-placeholder" {:color :#77c}]]]])

;;-----------------------------------------------------------------------------

(def messages
  [[:section#messages {:position :fixed
                       :overflow :auto
                       :top (px 40)
                       :bottom (px 70)
                       :left 0
                       :right 0
                       :background-color :#202a33
                       :padding (px 20)}
    [:&.cover {:bottom (px 30)}]
    [:div.message-list {:margin-top (px 10)
                        :margin-bottom (px 50)}
     [:div.message {:position :relative
                    :margin-bottom (px 10)}
      [:div.handle {:position :absolute
                    :top (px 7)
                    :font-weight 100
                    :left (px 10)
                    :overflow :hidden
                    :width (px 100)
                    :color :dodgerblue
                    :white-space :nowrap
                    :text-overflow :ellipsis
                    :font-size (pt 11)}]
      [:div.text {:padding-left (px 120)
                  :padding-top (px 4)
                  :font-weight 100
                  :color :peru
                  :font-size (pt 14)}]]]]])

(def sender
  [[:section#sender {:position :fixed
                     :bottom (px 30)
                     :left 0
                     :right 0
                     :height (px 40)}
    [:div.typer {:position :absolute
                 :top 0
                 :left 0
                 :right 0
                 :bottom 0
                 :width (percent 100)
                 :height (percent 100)
                 :background-color :aliceblue
                 :text-align :center}
     [:input {:width (percent 100)
              :height (percent 100)
              :border :none
              :outline :none
              :background-color :#111
              :font-weight 200
              :color :lime
              :padding-left (px 20)
              :margin 0
              :letter-spacing (pt 1)
              :padding 0
              :font-size (pt 15)}]
     ["::-webkit-input-placeholder" {:color :#448}]
     ["::-moz-placeholder" {:color :#77c}]]]])

(def status-bar
  [[:section#status {:position :fixed
                     :left 0 :right 0 :bottom 0
                     :height (px 30)
                     :background-color :#303e4d
                     :border-top [[(px 1) :solid :#333]]}
    [:div.copy {:position :absolute
                :top (px 8)
                :left (px 15)
                :right (px 15)
                :text-align :center
                :margin 0
                :color :royalblue
                :font-weight 400
                :font-size (pt 8)}]
    [:div.signout {:position :absolute
                   :top (px 5)
                   :right (px 15)
                   :margin 0}
     [:button {:margin 0
               :cursor :pointer}]]]])

(defstyles screen
  globals
  title-bar
  messages
  sender
  status-bar)
