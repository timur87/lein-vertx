(ns leiningen.vertx
  (:use [leiningen.help :only [help-for]]
        [clojure.java.shell])
  (:require [leiningen.core.eval :as eval]
            [leiningen.core.main :as main]
            [clojure.pprint :as pprint]
            [lein-vertx.core :as core])
  (:import (java.io FileNotFoundException File)))

(defn runmod 
  "Run the main function specified from command line or under [:vertx :main] in project.clj"
  [project modowner modname version & args]
  (if (and modowner modname version)
    (apply core/invoke-vertx project "runmod"
           (core/get-mod-id  modowner modname version)
           args)
    (main/abort (str ":modowner, :modname and :version must be provided in vertx description" 
                     "provided values are: \n"
                     "\nmodowner: "  modowner
                     "\nmodname " modname
                     "\nversion: " version))))

(defn buildmod
  "Run the main function specified from command line or under [:vertx :main] in project.clj"
  [project modowner modname version & args]
  (if (and modowner modname version)
    (core/create-mod project (-> project :vertx :main) args)
    (main/abort (str "Error :modowner, :modname and :version must be provided in vertx description\n" 
                     "Provided values are: \n"
                     "\nmodowner: "  modowner
                     "\nmodname " modname
                     "\nversion: " version))))

(defn vertx
  "Leiningen plugin to run vertx verticle."
   {:help-arglists '([subtask [args...]])
    :subtasks [#'runmod #'buildmod]}
  ([project]
     (println (help-for "vertx")))
  ([project subtask & args]
     (case subtask
       "runmod" (if (first args)
               (apply runmod project args)
               (apply runmod project (-> project :vertx :modowner) (-> project :vertx :modname) (-> project :vertx :version) args))
;;       "zip" (core/buildmod project (-> project :vertx :main) args)
       "buildmod" (apply buildmod project (-> project :vertx :modowner) (-> project :vertx :modname) (-> project :vertx :version) args)
       (println (help-for "vertx")))))
