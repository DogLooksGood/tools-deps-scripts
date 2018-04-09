(ns tools-deps-scripts.figwheel
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.tools.cli :refer [parse-opts]]))

;;;; Figwheel script
;; Two ways to configure, they effect in order:
;; 1. Command line arguments,
;;   :main         - defaults to main.js
;;   :output-to    - defaults to target/main.js
;;   :output-dir   - defaults to target/main
;; 2. A figwheel.edn file
;; {:all-builds []
;;  :build-ids []}

(try (require '[figwheel-sidecar.repl-api
                :as figwheel
                :refer [start-figwheel! cljs-repl]])
     (catch Exception ex
       (println "Can't found figwheel-sidecar in classpath.")
       (System/exit 1)))

(def classpaths (System/getProperty "java.class.path"))

(defn make-source-paths []
  (->> (str/split classpaths #":")
       (filterv #(or (str/starts-with? % "src")
                     (str/starts-with? % "test")))))

;;; middleware config
(def nrepl-middleware
  (cond-> []
    (try (require 'cider-nrepl) true (catch Exception e))
    (conj "cider.nrepl/cider-middleware")

    (try (require 'cemerick.piggieback) true (catch Exception e))
    (conj "cemerick.piggieback/wrap-cljs-repl")))

(def figwheel-options
  {:nrepl-port 9999
   :nrepl-middleware nrepl-middleware})

(defn nrepl-config [arg-map]
  (let [port (:port arg-map)]
    {:nrepl-port port
     :nrepl-middleware nrepl-middleware}))

;;; Load figwheel.edn
(def fig-edn
  (let [fig-edn-file (io/file "figwheel.edn")]
    (when (.exists fig-edn-file)
      (edn/read-string (slurp fig-edn-file)))))

(defn fig-config [{:keys [output-to output-dir main] :as arg-map}]
  (merge {:figwheel-options (nrepl-config arg-map)}
         (if fig-edn
           fig-edn
           (if-not main
             (do (println "--main MAIN must be provided when there's no figwheel.edn")
                 (System/exit 1))
             (let [compiler-config {:main          main
                                    :output-to     output-to
                                    :output-dir    output-dir
                                    :optimizations :none
                                    :source-map    true}]
               {:all-builds [{:id           "dev"
                              :figwheel     true
                              :source-paths (make-source-paths)
                              :compiler     compiler-config}]})))))

(def cli-options
  [["-m" "--main MAIN" "Main namespace."
    :required true]
   ["-o" "--output-to OUTPUT" "Entry of output JS file."
    :default "target/main.js"]
   ["-d" "--output-dir DIR" "Directory to put JS files."
    :default "target/main"]
   ["-r" "--repl" "If launch CLJS REPL"
    :default false]
   ["-p" "--port PORT" "Port of "
    :default 9999 :parse-fn #(Integer/parseInt %)]])

(defn -main [& args]
  (let [arg-map (:options (parse-opts args cli-options))]
    (->> arg-map
         (fig-config)
         (start-figwheel!))
    (when (:repl arg-map)
      (cljs-repl))))
