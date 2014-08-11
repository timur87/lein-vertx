(defproject lein-vertx "0.3.1"
  :description "leiningen plugin for vertx development"
  :url "https://www.github.com/isaiah/lein-vertx"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.0.0"
  :dependencies [[io.vertx/vertx-platform "2.1.1"]
                 [me.raynes/fs "1.4.5"]
                 [org.clojure/data.json "0.2.3" :exclusions [org.clojure/clojure]]]
  :eval-in-leiningen true)
