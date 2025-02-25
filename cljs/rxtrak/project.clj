(defproject rxtrak "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.238"]
                 [com.cognitect/transit-cljs "0.8.243"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [funcool/bide "1.7.0"]
                 [clj-http "3.7.0"]
                 [cljs-http "0.1.44"]
                 [cheshire "5.8.0"]
                 [com.taoensso/tufte "1.1.2"]
                 ;; todo bring up to date
                 [tiltontec/matrix "4.1.7-SNAPSHOT"]
                 [tiltontec/mxxhr "0.1.5-SNAPSHOT"]
                 [tiltontec/mxweb "0.2.0-SNAPSHOT"]]

  :source-paths ["src"]

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]}

  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.1.3"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]
                   :resource-paths ["resources" "target"]
                   ;; need to add the compliled assets to the :clean-targets
                   :clean-targets ^{:protect false} ["target/public" :target-path]}})

