(ns lein-vertx.core
  (:require [clojure.string :as string]
            [clojure.java.io :as io]
            [leiningen.core.classpath :as classpath]
            [leiningen.core.project :as project]
            [leiningen.core.eval :as eval]
            [leiningen.core.user :as user]
            [leiningen.core.main :refer [debug apply-task info]]
            [clojure.data.json :as json]
            [me.raynes.fs :as fs])
  (:import [java.io FileOutputStream BufferedOutputStream]
           [java.util.zip ZipEntry ZipOutputStream]
           [java.util.regex Pattern]))


(def ^:internal vertx-deps-project
  "Dummy project used to resolve vertx deps for the classpath of the subprocess"
  {:dependencies '[[io.vertx/vertx-core "2.1.1"]
                   [io.vertx/vertx-platform "2.1.1"]
                   [io.vertx/-hazelcast "2.1.1"]]
   :repositories [["central" {:url "http://repo1.maven.org/maven2/" :snapshots false}]
                  ["sonatype" {:url "http://oss.sonatype.org/content/repositories/snapshots" :snapshots true}]
                  ["bintray" {:url "http://dl.bintray.com"}]]})

(defn libs
  "Resolve dependencies jars of the project."
  [project]
  (classpath/resolve-dependencies :dependencies (project/unmerge-profiles project [:provided])))


(defn ^:internal generate-mod-id 
  "Returns module id"
  [project]
  (str (-> project :vertx :modowner) "~" (-> project :vertx :modname) "~" (-> project :vertx :version)))

(defn ^:internal get-mod-id
  "generates a module id from project description"
  [modowner modname version]
  (str modowner "~" modname "~" version))

(defn get-mods-path
  []
  (str "build" java.io.File/separator "mods" java.io.File/separator))

(defn get-mod-path 
  [project] 
  (str (get-mods-path) (generate-mod-id project) java.io.File/separator))

(defn get-abs-mod-path 
  [project] 
  (.getAbsolutePath (io/file (get-mod-path project))))

(defn get-abs-mods-path
  "path for all modules"
  [project]
  (.getAbsolutePath (io/file (get-mods-path))))

(defn get-deps-path 
  [project] 
  (str (get-mods-path) "deps" java.io.File/separator))

(defn get-lib-path 
  [project] 
  (str (get-mod-path project) "lib" java.io.File/separator))

(defn create-build-folder
  [project]
  (fs/mkdirs (get-mod-path project)))

(defn target-file
  [file-path file-name]
  (if (.endsWith file-path java.io.File/separator)
    (str file-path file-name)
    (str file-path java.io.File/separator file-name)))

(defn lib-target-file [src-file target-folder]
  (target-file target-folder (.getName src-file)))

(defn copy-dependencies
  [project target-folder]
  (info "Dependencies will be copied from :dependencies")
  (fs/mkdirs (fs/file target-folder))
  (let [dependencies (libs project)]
    (doseq [dependency dependencies]
;;      (debug (str "Copying " dependencies " to " (lib-target-file dependencies project)))
      (debug (str "Copying " dependency " to "(lib-target-file dependency target-folder)))
      (fs/copy dependency (lib-target-file dependency target-folder)))))

(defn compile-mod
  [project]
  (info "Java files will be compiled and placed in module folder")
  (apply-task "javac" (assoc project :compile-path (get-mod-path project)) nil))
;; compile java

(defn copy-folder-contents-recursively
  [folder-path project]
  (if (fs/exists? folder-path)
    (let [folder-paths (fs/iterate-dir folder-path)]
      (doseq [folder folder-paths]
        (let [root (get folder 0)
              folders (get folder 1)
              files (get folder 2)
              target-root-path (str
                                (get-mod-path project)
                                (if (last (string/split (.getAbsolutePath root) (re-pattern  folder-path)))
                                  (last (string/split (.getAbsolutePath root) (re-pattern  folder-path)))
                                  "")) 
              source-root-path (.getAbsolutePath root)]
          (doseq [folder folders]
            (fs/copy-dir (str source-root-path folder) (target-file target-root-path folder)))
          (doseq [file files]
            (info folder-path " --- " (last (string/split (.getAbsolutePath root) (re-pattern  folder-path))))
            (info (str target-root-path))
            (info (str source-root-path file) ":" (target-file target-root-path file))
            (fs/copy+ (str source-root-path java.io.File/separator file) (target-file target-root-path file))))))))

(defn copy-resources
  [project]
  (info "Resources will be copied from :resource-paths")
  (doseq [resource-folder (:resource-paths project)]
    (copy-folder-contents-recursively resource-folder project)))

(defn generate-lib-paths
  [project]
  "Setting class path"
  (map (memfn getAbsolutePath)
       (filter (fn [file]
                 (.endsWith (.getName file) ".jar"));; remove non lib paths
               (file-seq (io/file (get-lib-path project))))))

(defn ^:internal generate-class-path
  "Creates a classpath for the subprocess.
   It consists of:
   * the classpath for the project
   * the plugin conf-dir
   * the vertx jars"
  [project]
  (string/join java.io.File/pathSeparatorChar
            (concat
             ;; exclude the :provided profile, which should be used to
             ;; bring in vertx deps for local dev that we don't want
             ;; on the container classpath
             [(get-abs-mod-path project)]
             (generate-lib-paths project)
             (map (memfn getAbsolutePath)
                  (classpath/resolve-dependencies :dependencies vertx-deps-project)))))

(defn read-mod [project] 
  "Read module file"
  (json/read-json (slurp (str (get-mod-path project) "mod.json"))))

(defn get-main [project]
  "Returns main verticle described in module file"
  (:main (read-mod project)))

(defn ^:internal runmod
  "Generates the command for the subprocess."
  [project args]
  (concat [(System/getenv "LEIN_JAVA_CMD")
;;           (str "-Djava.util.logging.config.file=" (conf-file-path "logging.properties"))
           (str "-Dvertx.mods=" (get-abs-mods-path project))
           "-classpath"
           (generate-class-path project)
           "org.vertx.java.platform.impl.cli.Starter"]
          args))

(defn copy-sources
  [project]
  (info "Other defined sources under :source-paths will copied without any compliation")
  (doseq [source-folder (:source-paths project)]
    (copy-folder-contents-recursively source-folder project)))

(defn create-mod
  [project & args]
  (create-build-folder project)
  (copy-dependencies project (get-lib-path project))
  (compile-mod project)
  (copy-sources project)
  (copy-resources project)
  (copy-dependencies project (get-deps-path project)))

(defn pullindeps
  [project & args]
  (create-build-folder project)
  (copy-dependencies project (get-deps-path project)))

(defn create-mod-link 
  [project & args]
  (let [file-name (str (get-mod-path project) "module.link")]
    (fs/create (fs/file file-name))
    (spit file-name fs/*cwd*)))

(defn init-mod
  [project & args]
  (create-build-folder project)
  (create-mod-link project))

(defn invoke-vertx
  "Invokes vertx in the given project."
  [project & args]
  (info (runmod project args))
  (apply eval/sh (runmod project args)))

