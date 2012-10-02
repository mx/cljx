(ns cljx.core
  (:use [cljx.rules :only [cljs-rules]]
        [clojure.java.io :only [reader make-parents]])
  (:require [clojure.string :as string]
            kibit.check)
  (:import [java.io File]))

(defn warning-str [orig-path]
  (str ";;This file autogenerated from \n;;\n;;  " orig-path "\n;;\n"))

;;Taken from clojure.tools.namespace
(defn cljx-source-file?
  "Returns true if file is a normal file with a .cljx extension."
  [^File file]
  (and (.isFile file)
       (.endsWith (.getName file) ".cljx")))

(defn find-cljx-sources-in-dir
  "Searches recursively under dir for CLJX files.
Returns a sequence of File objects, in breadth-first sort order."
  [^File dir]
  ;; Use sort by absolute path to get breadth-first search.
  (sort-by #(.getAbsolutePath ^File %)
           (filter cljx-source-file? (file-seq dir))))

(defn munge-forms
  [reader rules]
  (->> (kibit.check/check-reader reader
                                 :rules rules
                                 :guard identity
                                 :resolution :toplevel)
       (map #(or (:alt %) (:expr %)))
       (remove #(= % :cljx.core/exclude))))

(defn generate
  ([cljx-path output-path extension]
     (generate cljx-path output-path extension
               cljs-rules))

  ([cljx-path output-path extension rules]
     (println "Rewriting" cljx-path "to" output-path
              (str "(" extension ")")
              "with" (count rules) "rules.")
     
     (doseq [f (find-cljx-sources-in-dir (File. cljx-path))]

       (let [munged-forms (munge-forms (reader f) rules)
             generated-f  (File. (-> (.getPath f)
                                     (string/replace cljx-path output-path)
                                     (string/replace #"cljx$" extension)))]

         (make-parents generated-f)
         (spit generated-f
               (str (warning-str (.getPath f))
                    (string/join "\n" munged-forms)))))))

(defn- cljx-compile [builds]
  "The actual static transform, separated out so it can be called repeatedly."
  (doseq [{:keys [source-paths output-path extension rules include-meta]
           :or {extension "clj" include-meta false}} builds]
    (let [rules (if-not (symbol? rules)
                  ;; TODO now that we are evaluating within the context of the
                  ;; user's project, there should be no reason for this eval to
                  ;; exist any more
                  (eval rules)
                  (do
                    (require (symbol (namespace rules)))
                    @(resolve rules)))]
      (doseq [p source-paths]
        (binding [*print-meta* include-meta]
          (generate p output-path extension rules))))))