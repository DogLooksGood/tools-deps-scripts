(ns tools-deps-scripts.figwheel
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

;;;; Figwheel script
;; Two ways to configure, they effect in order:
;; 1. Command line arguments,
;;   :main         - defaults to main.js
;;   :output-to    - defaults to target/main.js
;;   :output-dir   - defaults to target/main
;; 2. A figwheel.edn file
;; {:all-builds []}

(try (require '[figwheel-sidecar.repl-api
                :as figwheel
                :refer [start-figwheel! cljs-repl]])
     (catch Exception ex
       (println "Can't found figwheel-sidecar in classpath.")
       (System/exit 1)))

;;; middleware config
(def nrepl-middleware
  (cond-> []
    (try (require 'cider-nrepl) (catch Exception e))
    (conj "cider.nrepl/cider-middleware")

    (try (require 'cemerick.piggieback) (catch Exception e))
    (conj "cemerick.piggieback/wrap-cljs-repl")))

(def figwheel-options
  {:nrepl-port 9999
   :nrepl-middleware nrepl-middleware})

;;; Load figwheel.edn
(def fig-edn
  (let [fig-edn-file (io/file "figwheel.edn")]
    (when (.exists fig-edn-file)
      (edn/read-string (slurp fig-edn-file)))))

(defn fig-config [args]
  (merge {:figwheel-options figwheel-options}
         (if fig-edn
           fig-edn
           (let [arg-map         (apply hash-map args)
                 output-to       (get arg-map ":output-to" "target/main.js")
                 output-dir      (get arg-map ":output-dir" (str/replace output-to #"\.js" ""))
                 main            (symbol (get arg-map ":main"))
                 compiler-config {:main          main
                                  :output-to     output-to
                                  :output-dir    output-dir
                                  :optimizations :none
                                  :source-map    true}]
             {:all-builds [{:id           "dev"
                            :figwheel     true
                            :source-paths ["src/main/cljs"]
                            :compiler     compiler-config}]}))))

(defn -main [& args]
  (-> args
      fig-config
      start-figwheel!))
