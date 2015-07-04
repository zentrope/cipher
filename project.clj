(defproject com.zentrope/cipher "1"

  :description
  "Web client/server for anonymous chat."

  :url
  "https://github.com/zentrope/cipher"

  :license
  {:name "EPL" :url "http://bit.ly/1EXoLjp"}

  :dependencies
  [[org.clojure/clojure "1.7.0"]
   [org.clojure/core.async "0.1.346.0-17112a-alpha"]
   [org.clojure/tools.logging "0.3.1"]
   [ch.qos.logback/logback-classic "1.1.3"]
   [aleph "0.4.1-alpha1"]

   ;; Override core.async deps to shut off warnings.
   [org.clojure/core.cache "0.6.4"]
   [org.clojure/core.memoize "0.5.7"]]

  :main ^:skip-aot
  cipher.main

  :source-paths
  ["src/clj" "src/cljc"]

  :clean-targets ^{:protect false}
  ["resources/public/out"
   "resources/public/main.js"
   "resources/public/main.css"
   :target-path]

  :auto-clean false

  :aliases
  {"server"  ["trampoline" "run"]
   "client"  ["trampoline" "figwheel" "dev"]
   "css"     ["trampoline" "garden" "auto"]
   "uberjar" ["do" "clean" ["cljsbuild" "once" "release"]
              ["garden" "once"] "uberjar"]
   "boot"    ["do" "clean" ["cljsbuild" "once" "release"]
              ["garden" "once"] "run"]}

  :garden
  {:builds [{:id "dev"
             :source-paths ["src/css"]
             :stylesheet cipher.styles/screen
             :compiler {:output-to "resources/public/main.css"
                        :pretty-print? true}}]}

  :cljsbuild
  {:builds [{:id "dev"
             :source-paths ["src/cljs" "src/cljc"]
             :figwheel {:on-jsload "cipher.main/reload"
                        :websocket-url "ws://localhost:3449/figwheel-ws"
                        :heads-up-display false}
             :compiler {:output-to "resources/public/main.js"
                        :output-dir "resources/public/out"
                        :main cipher.main
                        :optimizations :none
                        :asset-path "out"
                        :source-map true
                        :source-map-timestamp true}}
            {:id "release"
             :source-paths ["src/cljs"]
             :compiler {:output-to "resources/public/main.js"
                        :main cipher.main
                        :pretty-print true
                        :optimizations :whitespace}}]}

  :figwheel {:http-server-root "public"
             :server-port 3449
             :repl false
             :css-dirs ["resources/public"]}

  :profiles
  {:uberjar {:aot :all}

   :dev
   {:plugins
    [[lein-ancient "0.6.7"]
     [lein-cljsbuild "1.0.6"]
     [cider/cider-nrepl "0.9.1" :exclusions [org.clojure/tools.reader]]
     [lein-garden "0.2.7-SNAPSHOT" :exclusions
      [org.apache.commons/commons-compress]]
     [lein-figwheel "0.3.7" :exclusions [org.clojure/tools.namespace
                                         org.clojure/clojure
                                         org.codehaus.plexus/plexus-utils]]]

    :dependencies
    [[org.clojure/tools.nrepl "0.2.10"] ;; override lein
     [org.clojure/clojurescript "0.0-3308"]
     [figwheel "0.3.7"]
     [org.omcljs/om "0.8.8"]
     [sablono "0.3.4"]
     [garden "1.2.5"]]}})
